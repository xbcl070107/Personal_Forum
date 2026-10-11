package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.vo.FavoriteVO;
import com.xbcl.personal_forum.service.FavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口。和 LikeController 一个形状，路径换成 /post/favorite。
 *
 * <p>POST /dev-api/post/favorite?postId=1 —— 翻面（收藏 ↔ 取消）
 * <br>GET&nbsp; /dev-api/post/favorite?postId=1 —— 查状态
 *
 * <p>响应：{"code":200,"data":{"favorited":true,"count":2},"message":"ok"}
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/post")
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 翻面 */
    @PostMapping("/favorite")
    public Result<FavoriteVO> favorite(@RequestParam Long postId, HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return Result.success(favoriteService.toggle(postId, userId));
    }

    /** 查状态 */
    @GetMapping("/favorite")
    public Result<FavoriteVO> status(@RequestParam Long postId, HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return Result.success(favoriteService.status(postId, userId));
    }
}
