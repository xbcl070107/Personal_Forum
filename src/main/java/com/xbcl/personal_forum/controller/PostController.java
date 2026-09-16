package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.PostPublishDTO;
import com.xbcl.personal_forum.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子接口。
 *
 * <p>完整地址：POST http://localhost:9090/dev-api/post/publish
 *
 * <p>这个接口<b>不在白名单里</b>，所以请求必须先过 JwtInterceptor ——
 * 不带 token 打过来会直接 401，根本走不到这个方法。
 *
 * <p>Controller 这一层只干三件事：收参数、调 Service、包成 Result。
 * 一旦你在这里写起了 if / for / SQL，说明这段代码放错层了。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
public class PostController {

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
    @PostMapping("/publish")
    public Result<Long> publish(@RequestBody @Valid PostPublishDTO dto,
                                HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(postService.publish(dto, userId));
    }
}
