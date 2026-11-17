package com.xbcl.personal_forum.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

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
 * （addCorsMappings）也写在同一个类里。
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
     * 上传图片落盘的根目录，和 FileService 读的是同一个配置项。
     *
     * <p>为什么不直接把路径写死成 {@code ./uploads}：部署时目录很可能不在
     * 工作目录下（挂载盘、别的分区），写死之后只能改代码重新打包。
     */
    @Value("${forum.upload.dir:./uploads}")
    private String uploadDir;

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
     * <p>白名单里这几个各自的理由：
     * <ul>
     *   <li>/auth/register、/auth/login —— 还没登录的人当然没有 token</li>
     *   <li>/category/list —— 板块下拉框，游客也要能看</li>
     *   <li>/post/list —— 帖子列表，游客可见</li>
     *   <li>/item/category/list —— 集市的分类 tab，游客可见</li>
     *   <li>/uploads/** —— 图片是页面里 &lt;img src&gt; 直接请求的，
     *       浏览器发这个请求<b>不会带 Authorization 头</b>，
     *       不放行的话所有图片都是 401 的灰框</li>
     *   <li>/v3/api-docs/**、/swagger-ui/** 等 —— Swagger 文档页自己发的请求，
     *       它们不带 token；不放行的现象是文档页一直提示
     *       "Failed to load API definition"</li>
     * </ul>
     * 以后加「帖子详情」这类游客可见的接口时，记得往这里补一行。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)//装哪个拦截器
                .addPathPatterns("/**")//管哪些路径
                //放哪些路径（白名单）
                .excludePathPatterns(
                        "/auth/register",
                        "/auth/login",
                        "/category/list",
                        "/post/list",        // 帖子列表，游客可见
                        "/item/category/list", // 商品分类 tab，游客可见
                        "/item/list",        // 集市列表，游客可见（W3；接口已挂上，逻辑在 ItemService.listMarket 待实现）
                        "/uploads/**",       // 上传的图片，由下面 addResourceHandlers 直接吐文件

                        // ---- Swagger / OpenAPI 文档（配置见 config/OpenApiConfig）----
                        // 这几条不放行的话：文档页能打开，但页面去拉 /v3/api-docs 的 JSON 时
                        // 会被这个拦截器 401 掉，界面上一直报 "Failed to load API definition"。
                        // 和上面几条一样，这里写的是去掉 /dev-api 之后的部分。
                        "/v3/api-docs/**",  // OpenAPI 文档 JSON（springdoc 生成）
                        "/swagger-ui/**",   // Swagger UI 的静态资源（js / css）
                        "/swagger-ui.html", // 文档页入口（会跳转到 /swagger-ui/index.html）
                        "/webjars/**"       // Swagger UI 依赖的前端库（webjar 里的）
                );
    }

    /**
     * 把「/uploads/** 这个 URL」映射到「磁盘上的上传目录」。
     *
     * <p>没有这段，FileService 返回的 URL 打开就是 404 —— 文件明明在磁盘上，
     * 但没有一个 Controller 认领这个路径。Spring 的静态资源处理可以直接
     * 把 URL 接到目录上，不用为此写一个读文件、写响应的 Controller。
     *
     * <p>三个细节：
     * <ul>
     *   <li>location 必须是 {@code file:} 前缀。不写会被当成 classpath 路径，
     *       于是去 jar 里找 uploads 目录，永远找不到</li>
     *   <li>路径要 <b>绝对化 + 归一化</b>：{@code ./uploads} 在不同工作目录下
     *       指向不同地方，绝对化之后行为才是确定的</li>
     *   <li>末尾那个 "/" 不能丢。少了它不是"映射到 uploads"，而是
     *       "映射到名为 uploads 的资源"，子路径匹配全部失效</li>
     * </ul>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        // toUri() 对「还不存在的目录」不会补末尾斜杠，这里手动保证
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
