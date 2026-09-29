package com.xbcl.personal_forum.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置。拦截器在这里「报备」，真正的拦截逻辑在 JwtInterceptor 里。
 *
 * <p>这个类自己不干活，只负责接线 —— 但少了它，拦截器写了也是白写：
 * Spring 不会自动认识你那个 HandlerInterceptor，得有人告诉它
 * 「这个关卡装在哪、管哪些路径、放哪些路径」。
 *
 * <p>@Configuration 表示这是一个配置类，Spring 启动时会读它；
 * 实现 WebMvcConfigurer 是「我要改 Spring MVC 的默认行为」的标准做法。
 * 这个接口还有很多别的可重写方法，以后前端联调要用的跨域
 * （addCorsMappings）、上传图片要用的静态资源映射（addResourceHandlers）
 * 也都写在同一个类里。
 *
 * <p>@RequiredArgsConstructor 是 Lombok 的：它会给下面那个 final 字段
 * 生成一个构造器，Spring 看到构造器就知道要注入什么，
 * 不需要在字段上加 @Autowired。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    /**
     * 注册拦截器。整个方法就在回答三个问题：装哪个、管哪些、放哪些。
     *
     * <p>三个调用是一根链子，一次写完，不能拆成三段：
     * <pre>
     * addInterceptor     装哪个拦截器
     * addPathPatterns    它管哪些路径。"/**" = 全部
     * excludePathPatterns 从上面那堆里挖掉哪些（白名单）
     * </pre>
     *
     * <p>⚠️ 白色名单里的路径 <b>不带 context-path</b>。
     * 你的接口地址是 {@code /dev-api/auth/login}，但那一段 /dev-api 是
     * application.yaml 里的 context-path，Spring 在做路径匹配之前已经把它剥掉了，
     * 所以这里写 {@code /auth/login}。
     * 写成 {@code /dev-api/auth/login} 的现象是：登录接口也被要求带 token，
     * 于是你永远拿不到 token，整个链路卡死在第一步。
     *
     * <p>白名单里这三个各自的理由：
     * <ul>
     *   <li>/auth/register、/auth/login —— 还没登录的人当然没有 token</li>
     *   <li>/category/list —— 板块下拉框，游客也要能看</li>
     * </ul>
     * 以后加「帖子列表」「帖子详情」这类游客可见的接口时，记得往这里补一行。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)//装哪个拦截器
                .addPathPatterns("/**")//管哪些路径
                //放哪些路径（白名单）
                .excludePathPatterns(
                        "/auth/register",
                        "/auth/login",
                        "/category/list"
                );
    }
}
