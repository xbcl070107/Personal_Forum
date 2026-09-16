package com.xbcl.personal_forum.config;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * JWT 拦截器：每个请求进 Controller 之前，先查一次「你是谁」。
 *
 * <p>它站在 DispatcherServlet 和 Controller 之间：
 * <pre>
 * 请求 → Tomcat → DispatcherServlet → 【preHandle】 → Controller → Service → Mapper
 *                                          │
 *                                    返回 true  = 放行，继续往 Controller 走
 *                                    返回 false = 到此为止，Controller 不会被调用
 * </pre>
 *
 * <p>@Component 让这个类进 Spring 容器（这样 WebMvcConfig 才能注入它），
 * 但 <b>光有 @Component 它不会被调用</b> —— 还得在 WebMvcConfig 里注册。
 * 漏了那一步的现象是：代码全对、启动也不报错，就是不拦。
 *
 * <p>这个类一共两个方法：
 * <ul>
 *   <li>{@link #preHandle} —— 主体。取 token、解析、挂信息到 request</li>
 *   <li>{@link #unauthorized} —— 工具方法。被上面调两次，专门负责「回 401 并拦下」</li>
 * </ul>
 *
 * <p>两个依赖：
 * <ul>
 *   <li>JwtUtil —— 解析 token，preHandle 里调它的 parse()</li>
 *   <li>ObjectMapper —— Jackson 的 JSON 转换器，Spring Boot 自动配好了一个，直接注入。
 *       回 401 时要把 Result 对象转成 JSON 字符串写进响应体。
 *       ⚠️ 包名是 {@code tools.jackson.databind}，不是网上常见的
 *       {@code com.fasterxml.jackson.databind} —— 这是 Spring Boot 4 用的 Jackson 3，
 *       包名整体挪了位置，抄老教程的 import 在这里编译不过。
 *       （顺带：Jackson 3 的 writeValueAsString 不再抛受检异常）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    /**
     * 在 Controller 之前执行。返回 true 放行，返回 false 拦下。
     *
     * <p>做三件事，顺序不能换：
     * <ol>
     *   <li>从请求头里抠出 token。没有就直接 401 —— 连解析都不用试</li>
     *   <li>解析 token。失败（过期 / 签名不对 / 不是这个后台签的）也 401</li>
     *   <li>把解析出来的身份挂到 request 上，给后面的 Controller 用</li>
     * </ol>
     *
     * <p>⚠️ 方法签名上那个 {@code throws Exception} 是被允许的，但你要拦下的情况
     * （token 过期、签名不对）<b>不要靠抛异常实现</b> —— 抛出去会被
     * GlobalExceptionHandler 接成 500「服务器出错」，而这里要的是 401「你没登录」。
     * 所以异常自己 catch，然后用 response 写 401。
     *
     * <p>@Override 表示这是在重写父类的方法（HandlerInterceptor 的默认实现是直接
     * return true）。写错方法名或参数类型时，编译器会因为这个注解直接报错，
     * 而不是像没写 @Override 那样「悄悄变成了一个没人调用的新方法」。
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // ① 取 token。空串和 "abc" 都会被 startsWith 挡掉，所以两个条件就够了
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(response);
        }
        String token = header.substring(7);   // "Bearer " 正好 7 个字符，从第 7 位往后切

        // ② 解析。claims 先声明、再在 try 里赋值：
        //    写成 try { Claims claims = ... } 的话，出了花括号外面就看不见它了。
        //    parse 内部会验签名和有效期，任何一种不对都会在这里抛异常。
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 过期、签名不对、格式不对全落在这。不打印堆栈 ——
            // 这是正常的业务分支（比如用户开了两个标签页），不是程序出错
            return unauthorized(response);
        }

        // ③ 把身份挂到 request 上，后面的 Controller 直接取，不用再解一遍 token。
        //
        //    为什么挂在 request 而不是存到某个静态变量：request 是「这一次请求」
        //    私有的，两个人同时发帖各拿各的，不会串。静态变量会被后来的人覆盖 ——
        //    现象是偶尔删掉了别人的帖子，而且很难复现。
        //
        //    字符串 key 容易写错（"userid" / "userId" 大小写），所以约定好固定三个，
        //    后面 PostController 里这样取：
        //        Long userId = (Long) request.getAttribute("userId");
        request.setAttribute("userId", Long.valueOf(claims.getSubject()));
        request.setAttribute("username", claims.get("username", String.class));
        request.setAttribute("role", claims.get("role", String.class));

        return true;
    }

    /**
     * 401 的统一出口：写 JSON 响应，然后返回 false。
     *
     * <p>为什么单独抽个方法：它有两个调用点（没带 token / token 不合法）。
     * 复制两遍的话，以后想改返回体（加字段、换文案）就得记得改两个地方，
     * 迟早漏一个，然后出现「有时候返回的 JSON 里少个字段」这种玄学 Bug。
     *
     * <p>方法体三行的顺序有讲究：
     * <ul>
     *   <li>先 setStatus(401) —— 让前端和浏览器知道这是「没登录」，
     *       前端 axios 拦截器一般就是按 401 统一跳登录页的</li>
     *   <li>再 setContentType —— 不写这个，浏览器可能按纯文本处理，
     *       前端拿到的就不是对象而是字符串了</li>
     *   <li>最后写 body。返回 {@code Result} 的对象结构，和正常接口保持一致，
     *       前端一套解析逻辑通吃</li>
     * </ul>
     *
     * <p>这里回 401 有个必然结果：它 <b>不经过 GlobalExceptionHandler</b>。
     * 拦截器在 Controller 之前，压根没进 Controller 那套异常处理。
     * 所以返回体是两个地方各写各的 —— 以后改 Result 的字段，
     * 记得这里也要看一眼。
     *
     * <p>return false 是这个方法的一部分：写完 401 不返回 false，
     * 请求会继续往 Controller 跑，等于没拦。
     */
    private boolean unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Result.error(401, "请先登录"))
        );
        response.getWriter().flush(); // 刷新缓冲区
        return false;
    }
}
