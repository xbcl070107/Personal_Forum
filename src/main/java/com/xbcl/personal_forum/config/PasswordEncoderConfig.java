package com.xbcl.personal_forum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器的配置。
 *
 * <p>为什么不直接在 UserService 里 new 一个：
 * 那样每次用都要重新初始化一遍，也没法在测试里换成假的实现。
 * 放进 @Configuration 里当 @Bean，它就归 Spring 容器管 —— 全工程共用这一个实例。
 *
 * <p>@Configuration 表示「这个类是配置类，里面 @Bean 方法的返回值放进容器」。
 * @Bean 方法的方法名就成了这个 Bean 的名字（passwordEncoder）。
 *
 * <p>对应 UserService 里的 private final PasswordEncoder passwordEncoder ——
 * 那边声明的是接口，真正用哪个实现由这里决定。以后想换成 Argon2，
 * 只改这个文件，UserService 一行都不用动。
 *
 * <p>依赖是 spring-security-crypto（只管加密，不带 Web 安全）。
 * 别引成 spring-boot-starter-security —— 那个会给所有接口默认套一层登录拦截，
 * 现象是「什么都没改，接口全变 401」。
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt：同一个密码每次 encode 出来的哈希都不一样（内部带随机盐），
     * 校验只能用 matches(明文, 哈希)，不能拿两个哈希去比。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
