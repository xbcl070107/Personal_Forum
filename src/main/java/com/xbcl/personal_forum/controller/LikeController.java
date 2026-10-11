package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.vo.LikeVO;
import com.xbcl.personal_forum.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞接口。
 *
 * <p>POST /dev-api/post/like?postId=1 —— 翻面（赞 ↔ 取消）
 * <br>GET&nbsp; /dev-api/post/like?postId=1 —— 查状态，详情页进来时调一次
 *
 * <p>前缀挂在 /post 下面，不单独开一个 /like —— 点赞是挂在帖子上的动作，
 * 放同一段前缀下，前端一眼看得出它属于谁。"/post/like" 是字面量，
 * Spring 匹配时优先于 PostController 的 "/post/{id}"，两边不会撞
 * （PostController 里的 "/post/my"、"/post/pending" 已经在这么并存的）。
 *
 * <p>⚠️ 字段必须写 final。@RequiredArgsConstructor 只给 final 字段
 * 生成构造器参数；不写 final 的话它生成的是一个空构造器，Spring 没东西可注入，
 * 三个字段就全是 null —— 编译能过，一调接口就 NPE。
 *
 * <p>这两个接口不在白名单里，不带 token 会被 JwtInterceptor 拦掉返回 401，
 * 根本走不到方法体。所以这里不用自己判「登录了没」——
 * 方法体里的 request.getAttribute("userId") 一定不是 null。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
public class LikeController {

    /** 只留这一个。postService / userService 在这里用不上 ——
     *  「现在是哪个人」是从 token 里取的，不查库 */
    private final LikeService likeService;

    /** 翻面。响应：{"code":200,"data":{"liked":true,"count":3},"message":"ok"} */
    @PostMapping("/like")
    public Result<LikeVO> like(@RequestParam Long postId, HttpServletRequest request) {

        // userId 是 JwtInterceptor 从 token 里解出来挂到 request 上的，
        // 前端伪造不了 —— 所以「谁点的赞」永远不听前端的
        Long userId = (Long) request.getAttribute("userId");

        return Result.success(likeService.toggle(postId, userId));
    }

    /** 查状态。返回的形状和上面那个完全一样，前端一个方法就能处理两种响应 */
    @GetMapping("/like")
    public Result<LikeVO> status(@RequestParam Long postId, HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return Result.success(likeService.status(postId, userId));
    }
}
