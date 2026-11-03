package com.xbcl.personal_forum.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置。
 *
 * <p>这个类只干一件事：告诉 springdoc「这份文档叫什么、用什么方式鉴权」。
 * 业务代码一行都不用改 —— springdoc 启动时会自己扫描所有 @RestController，
 * 根据 @GetMapping / @PostMapping / DTO 上的字段生成文档。
 *
 * <p><b>本地怎么用：</b>启动项目，浏览器打开
 * <pre>
 * http://localhost:9090/dev-api/swagger-ui.html   ← 文档页面（平时开这个）
 * http://localhost:9090/dev-api/v3/api-docs       ← 文档 JSON 原文（给前端/工具用）
 * </pre>
 * 页面上的操作顺序：
 * <ol>
 *   <li>展开「POST /auth/login」→ Try it out → 填用户名密码 → Execute；</li>
 *   <li>从返回体里复制 data.token 的值；</li>
 *   <li>点页面右上角 Authorize，把 token 粘进去
 *       （<b>不用自己写 "Bearer " 前缀</b>，Swagger UI 会自动加）；</li>
 *   <li>之后所有带锁图标 🔒 的接口都能直接 Try it out —— 请求头里会自动带上
 *       {@code Authorization: Bearer <token>}，和 JwtInterceptor 取 token 的方式完全对得上。</li>
 * </ol>
 *
 * <p><b>为什么必须有下面这个 Bean：</b>
 * 不给 OpenAPI Bean 也能生成文档，但标题是默认的，而且<b>没有 Authorize 按钮</b> ——
 * 所有要登录的接口只能看、点不通。Bean 里配的其实是两件事：
 * <ul>
 *   <li>{@link Info}：文档的标题、描述、版本；</li>
 *   <li>{@link SecurityScheme} + {@link SecurityRequirement}：声明「本 API 用 HTTP Bearer
 *       方式鉴权」。Swagger UI 据此画出 Authorize 弹窗，并给每个接口的请求自动加
 *       Authorization 头。</li>
 * </ul>
 *
 * <p><b>想让文档更「中文」怎么办：</b>自动生成的接口说明是方法名、分组是类名
 * （如 user-controller），能用但不够友好。以后可以按需给代码加注解，不改业务逻辑：
 * <pre>
 * &#64;Tag(name = "认证接口")                            // 加在 Controller 类上，给整组起名
 * &#64;Operation(summary = "登录，成功返回 JWT")           // 加在方法上，写接口说明
 * &#64;Schema(description = "登录请求体")                 // 加在 DTO 类上
 * &#64;Schema(description = "用户名", example = "alice")  // 加在 DTO 字段上
 * </pre>
 * 这些注解由 springdoc 传递引入（swagger-annotations），不用再单独加依赖。
 *
 * <p><b>注意：本类里的中文注释不会出现在文档页面上。</b>
 * Swagger 读的是注解，不是 Javadoc —— 想让某段说明进文档，得写上面那几种注解。
 *
 * <p><b>部署到公网前：</b>把文档关掉，否则等于把全部接口清单和参数结构公开给所有人。
 * 在 application-prod.yaml（或生产环境变量）里加：
 * <pre>
 * springdoc:
 *   api-docs:
 *     enabled: false
 *   swagger-ui:
 *     enabled: false
 * </pre>
 * 关掉之后，WebMvcConfig 里给文档路径留的那几条白名单不影响任何东西 —— 路径不存在了而已。
 *
 * <p><b>依赖在哪儿：</b>pom.xml 里的 {@code springdoc-openapi-starter-webmvc-ui}，
 * 注意 3.x 才是给 Spring Boot 4 用的（本项目是 Boot 4.1.1）。
 * 旧的 springfox（Swagger 2 时代）不要混着引：两套东西会抢同一批 URL，启动就冲突。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 安全方案在 OpenAPI 里的名字。
     *
     * <p>它只是「引用名」：{@link Components} 里用这个名字登记方案，
     * {@link SecurityRequirement} 再用同一个名字引用它。两处必须是同一个字符串 ——
     * 写成两个不同的名字，现象是「Authorize 里填了 token，但接口请求没带上 Authorization」。
     */
    private static final String BEARER_AUTH = "bearerAuth";

    /**
     * 生成 OpenAPI 文档的顶层对象。
     *
     * <p>springdoc 启动时会找到这个 Bean（按类型找，方法名随便取），
     * 把它和扫描到的接口合并，最终输出到 /v3/api-docs。
     *
     * @return 文档元信息 + 鉴权方案
     */
    @Bean
    public OpenAPI forumOpenAPI() {
        return new OpenAPI()
                // ① 文档页顶部的信息。description 支持 Markdown，所以能写多行和列表
                .info(new Info()
                        .title("冒险者协会 · 个人论坛 API")
                        .version("v0.0.1")
                        .description("""
                                个人论坛「冒险者协会」的后端接口文档。

                                **怎么调需要登录的接口：**
                                1. 先调 `POST /auth/login` 拿到 token；
                                2. 点右上角 Authorize，把返回体里 `data.token` 的值粘进去（不用自己加 `Bearer ` 前缀）；
                                3. 之后带锁图标的接口都能直接 Try it out。

                                注意：本页只在本地开发时打开，部署到公网前请关掉。
                                """))
                // ② 登记鉴权方案：HTTP + bearer，也就是每个请求带一个
                //    "Authorization: Bearer <token>" 请求头 —— 和 JwtInterceptor 的取法一致。
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token。只填 token 本身，不要自己写 Bearer 前缀。")))
                // ③ 全局套用这个方案：每个接口都显示一把锁，Try it out 时自动带 Authorization 头。
                //    这一行不能省：只在 components 里登记方案的话，
                //    Authorize 里填了 token 接口也不会带 —— 现象是「登录了还一直 401」。
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
