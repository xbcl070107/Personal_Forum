package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。
 *
 * <p>POST http://localhost:9090/dev-api/file/upload（multipart/form-data，字段名 file）
 * <br>响应体：{"code":200,"data":"/dev-api/uploads/images/2026-11/3f2a.jpg","message":"ok"}
 *
 * <p>返回的是 <b>URL</b> 不是文件名也不是磁盘路径：
 * 前端拿到之后要 (1) 立刻 &lt;img :src&gt; 预览、(2) 把同一串存进商品 images 里，
 * 两种用法都要求它是个能直接请求的地址。磁盘路径交出去既没用又泄露目录结构。
 *
 * <p>要登录才能传：不在白名单里。公开的上传接口等于白送一个图床，
 * 几小时就会被人拿去存各种东西。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    /**
     * 上传一张图片，返回可直接访问的 URL。
     *
     * <p>字段名必须是 {@code file}：前端 FormData 里键名对不上时，
     * Spring 会报 "Required part 'file' is not present"，而不是"没传文件"，
     * 这个错很容易被误读成网络问题。
     */
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.uploadImage(file));
    }
}
