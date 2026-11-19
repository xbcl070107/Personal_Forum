package com.xbcl.personal_forum.service;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.xbcl.personal_forum.common.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

/**
 * 图片上传。
 *
 * <p>整件事只有三步：<b>收下文件 → 存到硬盘 → 返回一个能直接放进 img src 的 URL</b>。
 * 中间的判断都是门卫，去掉也能跑，只是不安全。
 *
 * <p>这一版用 Hutool 替掉了两段手写代码：
 * <ul>
 *   <li>{@code FileTypeUtil.getType(流)} 代替手写的魔数比较（{@code & 0xFF} 那一堆）：
 *       它内部读的也是文件头那几个字节，只是表已经写好了，还顺带认识 gif/bmp/pdf 等，
 *       所以拿它当白名单的判据比只认三种更准</li>
 *   <li>{@code IdUtil.simpleUUID()} 代替 {@code UUID.randomUUID().toString().replace("-", "")}</li>
 * </ul>
 *
 * <p>没换成 Hutool 的两处，原因都是「异常类型对不上，换了反而更难查」：
 * {@code FileUtil.writeFromStream} 会把 IOException 包成运行时异常 IORuntimeException，
 * 外面 {@code catch (IOException)} 接不住；{@code file.transferTo()} 的临时文件行为依赖容器。
 * 所以落盘仍然是 NIO 的 {@code Files.copy}。
 */
@Service
public class FileService {

    /**
     * 允许的图片类型。
     *
     * <p>注意这里比的是「文件头认出来的类型」，不是用户给的扩展名 ——
     * 把 shell.jsp 改名成 cat.jpg 传上来，FileTypeUtil 认出的是 jsp，这里就直接被挡掉。
     * 不收 gif：动图在详情页排版难看，也不拿来做封面。
     */
    private static final Set<String> ALLOWED_TYPE = Set.of("jpg", "png", "webp");

    /** 单文件上限 5MB。application.yaml 里 spring.servlet.multipart 也有一道，两道都要改 */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** URL 里用的前缀，必须和 WebMvcConfig 里 /uploads/** 的静态映射、以及 context-path 对得上 */
    @Value("${forum.upload.url-prefix:/dev-api/uploads}")
    private String urlPrefix;

    /** 落盘根目录。相对路径是相对「启动时的工作目录」，不是 classpath */
    @Value("${forum.upload.dir:./uploads}")
    private String uploadDir;

    /** 启动时把目录建好。等到第一次上传才创建的话，第一个用户上传失败，日志里才看到问题 */
    @PostConstruct
    void init() {
        try {
            Files.createDirectories(uploadRoot());
        } catch (IOException e) {
            // 目录都建不出来，这个功能就是坏的，启动阶段就炸掉比运行时 500 好查
            throw new IllegalStateException("上传目录创建失败：" + uploadDir, e);
        }
    }

    /**
     * 保存一张图片。
     *
     * @return 可直接塞进 img src 的 URL，例如
     *         {@code /dev-api/uploads/images/2026-11/3f2a...jpg}
     */
    public String uploadImage(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片不能超过 5MB");
        }

        String type = detectType(file);
        if (!ALLOWED_TYPE.contains(type)) {
            throw new BusinessException("只支持 jpg / png / webp 格式");
        }

        // 2026-11 这种目录名：一个目录塞几十万个文件，ls 和备份都会变慢
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // 名字自己生成，不用原始文件名：原始名可能是 ../../application.yaml 或 a.jsp;
        String filename = IdUtil.simpleUUID() + "." + type;

        Path dir = uploadRoot().resolve("images").resolve(month);
        File target = dir.resolve(filename).toFile();

        try {
            FileUtil.mkParentDirs(target);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(500, "图片保存失败");
        }

        // 返回 URL 而不是磁盘路径：前端拿到就能直接 <img :src="url">，也不泄露服务器目录结构
        return urlPrefix + "/images/" + month + "/" + filename;
    }

    /**
     * 认这张图到底是什么格式。
     *
     * <p>先看文件头（可信），认不出来才退回扩展名（不可信，只当兜底）——
     * 兜底这一支是给 Hutool 版本太老、不认识 webp 的情况准备的。
     */
    private String detectType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            String magic = FileTypeUtil.getType(in);
            if (magic != null && !magic.isBlank()) {
                return normalizeType(magic);
            }
        } catch (IOException e) {
            throw new BusinessException("图片读取失败");
        }
        return normalizeType(FileUtil.extName(file.getOriginalFilename()));
    }

    /** jpeg 和 jpg 是同一种东西，统一成 jpg，白名单只写一次 */
    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        String lower = type.trim().toLowerCase(Locale.ROOT);
        return "jpeg".equals(lower) ? "jpg" : lower;
    }

    private Path uploadRoot() {
        // ./uploads 是相对路径，相对的是「启动时的工作目录」；IDEA 里跑和 java -jar 跑可能不是同一个。
        // toAbsolutePath() 固定下来，normalize() 把 .. 这种多余段消掉。
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}
