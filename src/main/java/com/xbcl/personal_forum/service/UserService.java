package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.UserMapper;
import com.xbcl.personal_forum.pojo.dto.LoginDTO;
import com.xbcl.personal_forum.pojo.dto.RegisterDTO;
import com.xbcl.personal_forum.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务层：注册、登录。
 *
 * <p>这个工程里所有 Service 都守同一套规矩：
 * <ul>
 *   <li>只做业务判断，不碰 HttpServletRequest / ResponseEntity 这类 Web 的东西</li>
 *   <li>不返回 Result —— Result 是 Controller 用的统一外壳，Service 只负责给数据</li>
 *   <li>出错就 throw BusinessException，不在这里 try-catch 吞掉</li>
 * </ul>
 *
 * <p>@RequiredArgsConstructor 是 Lombok 的注解：它为「所有没被初始化过的 final 字段」
 * 生成一个构造器。Spring 看到构造器里要 UserMapper 和 PasswordEncoder，
 * 就从容器里把对应的 Bean 塞进来 —— 这就是构造器注入，所以下面不用写 @Autowired。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 密码加密器。
     *
     * <p>类型写成接口 PasswordEncoder，不写 BCryptPasswordEncoder ——
     * 具体用哪种算法由 config/PasswordEncoderConfig 里的 @Bean 决定，
     * 以后想换实现，只动那个配置类，这个文件一行都不用改。
     *
     * <p>它有两个方法，别用混：
     * <ul>
     *   <li>encode(明文) → 注册时用。同一个密码每次出来的哈希都不一样（内部有随机盐）</li>
     *   <li>matches(明文, 哈希) → 登录时用。不能拿 encode 的结果去 equals 比对</li>
     * </ul>
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册。成功返回 void，失败往外抛 BusinessException。
     *
     * <p>为什么不返回 boolean 告诉调用方成功没成功：
     * 失败只有抛异常这一条路，返回值里再塞个布尔，调用方就有「忘了看」的可能。
     */
    public void register(RegisterDTO dto) {

        // 1. 查重。User::getUsername 是方法引用，
        //    字段名写错编译期就报错 —— 手写字符串 "username" 没这个保障。
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );

        // 9 成 9 的重名会在这里被拦住，报一句人话就够了
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 2. 拼实体。密码只以 BCrypt 哈希的形式落库，明文既不入库也不打日志。
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole("USER");

        // 3. 入库。id 和 created_at 交给数据库的自增和默认值，Java 这边不填。
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 上面那次 selectCount 和这次 insert 之间是有缝的：
            // 两个请求同时注册同一个用户名时，两边都会查到 0 条，
            // 然后第二个 insert 撞上唯一键 uk_user_username。
            // 这一步叫「把数据库的报错翻译成人话」——
            // 不翻译的话，它会一路冒到兜底处理器，用户收到的是 500「系统异常」。
            throw new BusinessException("用户名已存在");
        }
    }

    /**
     * 登录。成功返回查到的用户，失败抛 BusinessException。
     *
     * <p>返回的是实体 User，里面带着 passwordHash ——
     * Controller 往外拼响应时必须把它摘掉，不能整个吐给前端。
     */
    public User login(LoginDTO dto) {

        // 1. 按用户名查人。username 上有唯一键，所以 selectOne 最多只会拿到一条。
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );

        // 2. 查无此人。提示语和下面密码错的一模一样 —— 不是偷懒：
        //    分开提示等于告诉外人「这个用户名是存在的」，方便别人撞库。
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3. 校验密码。matches 会从库里那串哈希里把盐取出来，
        //    用同样的方式算一遍再比对，所以这里不能写 equals。
        boolean match = passwordEncoder.matches(
                dto.getPassword(),
                user.getPasswordHash()
        );

        if (!match) {
            throw new BusinessException("用户名或密码错误");
        }

        return user;
    }
}
