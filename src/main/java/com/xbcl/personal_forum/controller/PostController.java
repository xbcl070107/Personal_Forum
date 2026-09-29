package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.PostPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Post;
import com.xbcl.personal_forum.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 帖子接口。
 *
 * <p>发帖：POST http://localhost:9090/dev-api/post/publish
 * <br>待审列表：GET http://localhost:9090/dev-api/post/pending
 * <br>审核：POST http://localhost:9090/dev-api/post/audit?postId=1&amp;pass=true
 *
 * <p>这些接口<b>都不在白名单里</b>，所以请求必须先过 JwtInterceptor ——
 * 不带 token 打过来会直接 401，根本走不到方法体。
 *
 * <p>Controller 这一层只干三件事：收参数、调 Service、包成 Result。
 * 一旦你在这里写起了 if / for / SQL，说明这段代码放错层了。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
public class PostController {

    /**
     * 管理员的角色名。
     *
     * <p>必须和 user 表 role 列里实际存的值一字不差。
     * 写成小写 'admin' 或者数字 1，判断就永远不成立 ——
     * 这种错不会报任何异常，只会安静地一直返回 403，
     * 然后你会去怀疑 token、怀疑拦截器、怀疑前端的请求头，就是不怀疑这一行。
     */
    private static final String ROLE_ADMIN = "ADMIN";

    private final PostService postService;

    /**
     * 发帖。
     *
     * <p>POST /dev-api/post/publish
     * <br>请求体：{"title":"第一帖","content":"测试正文","categoryId":1}
     * <br>请求头：Authorization: Bearer &lt;token&gt;
     * <br>响应体：{"code":200,"data":1,"message":"ok"}，data 是新帖 id
     *
     * <p>这一行是整个方法里最要紧的：
     * <pre>Long userId = (Long) request.getAttribute("userId");</pre>
     * 值不是前端传的，是 JwtInterceptor 从 token 里解出来挂上去的。
     * 因为拦的是同一份 request，所以这里能取到 —— 这也意味着
     * 这个接口一旦被加进白名单，拦截器不跑，这行就会取到 null，
     * 然后 insert 撞上 user_id 的 NOT NULL 报错。
     */
    //@RequestBody：告诉 Spring 从请求体（JSON）中反序列化出 PostPublishDTO 对象。
    //@Valid：触发 JSR-303 参数校验。如果 DTO 里有 @NotBlank、@NotNull 等注解，
    // 校验失败会自动抛出 MethodArgumentNotValidException，由全局异常处理器统一处理。
    @PostMapping("/publish")
    public Result<Long> publish(@RequestBody @Valid PostPublishDTO dto,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(postService.publish(dto, userId));
    }

    /**
     * 待审列表，给管理员用。
     *
     * <p>GET /dev-api/post/pending
     * <br>请求头：Authorization: Bearer &lt;管理员的 token&gt;
     * <br>响应体：{"code":200,"data":[{"id":3,"title":"...","status":0,...}],"message":"ok"}
     *
     * <p>路径写 "/pending" 而不是 "/list/pending"，是因为前面 @RequestMapping("/post")
     * 已经拼过一次了，完整地址是 /post/pending。
     */
    @GetMapping("/pending")
    public Result<List<Post>> listPending(HttpServletRequest request) {
        // 先验权限，再干活。顺序反了的话，普通用户也能把待审内容拉出来。
        checkAdmin(request);
        return Result.success(postService.listPending());
    }

    /**
     * 审核一条帖子：通过或者驳回。
     *
     * <p>POST /dev-api/post/audit?postId=3&amp;pass=true
     * <br>请求头：Authorization: Bearer &lt;管理员的 token&gt;
     * <br>pass=true 通过，pass=false 驳回
     *
     * <p>为什么不把参数放在 JSON 请求体里：
     * 只有两个简单参数，也没有需要 @Valid 校验的字段（postId 和 pass
     * 少了任何一个，Spring 自己就会报 400，用不着我们写）。
     * 真有一个带五六个字段的审核表单，再建 DTO 也不迟。
     */
    @PostMapping("/audit")
    public Result<Void> audit(@RequestParam Long postId,
                              @RequestParam Boolean pass,
                              HttpServletRequest request) {
        checkAdmin(request);
        postService.audit(postId, pass);

        // 没有数据要返回，但也不能返回 void —— 那样前端拿到的响应体是空的，
        // 解析 res.data.code 时会报错。统一返回 Result，失败由异常处理器负责。
        return Result.success(null);
    }

    /**
     * 校验当前请求是不是管理员。
     *
     * <p>role 和 userId 一样，是 JwtInterceptor 从 token 里解出来挂在 request 上的，
     * 所以这里<b>不用查数据库</b>。
     *
     * <p>代价是：role 是登录那一刻签进 token 的。把某个账号改成 ADMIN 之后，
     * 他手上的旧 token 里还是 USER，必须重新登录才生效；
     * 反过来把管理员降权，旧 token 也还能继续用。
     * 想做到即时生效，只能每次都去 user 表查一遍 —— 这个项目不值得。
     *
     * <p>抛 403 而不是 401：401 是「你没登录」，403 是「你登录了，但没这个权限」。
     * 前端要拿 401 去跳登录页，别把这两个混了。
     */
    private void checkAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");

        // ROLE_ADMIN 写前面。role 为 null 时也能安全地走完，不用额外判空
        if (!ROLE_ADMIN.equals(role)) {
            throw new BusinessException(403, "只有管理员能审核帖子");
        }
    }
}
