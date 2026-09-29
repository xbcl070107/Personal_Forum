package com.xbcl.personal_forum.controller;

import com.xbcl.personal_forum.common.Result;
import com.xbcl.personal_forum.pojo.dto.CommentPublishDTO;
import com.xbcl.personal_forum.pojo.vo.CommentVO;
import com.xbcl.personal_forum.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 发表评论。
     *
     * <p>POST /dev-api/comment/publish
     * <br>要登录，不在白名单里 —— 白名单是给「游客也能看」的接口用的，
     * 往里面塞一个写接口，等于给匿名用户开了口子。
     *
     * @return 新评论的 id
     */
    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody CommentPublishDTO dto, HttpServletRequest request) {
        // 评论人从 token 里取，不看请求体 —— 前端传什么都可能是伪造的
        Long userId = (Long) request.getAttribute("userId");

        return Result.success(commentService.publish(dto, userId));
    }
    /**
     * 一条帖子的评论列表，按时间正序。
     *
     * <p>GET /dev-api/comment/list?postId=1
     * <br>要登录，没进白名单 —— 帖子详情都要登录，它的评论区没有单独放开的理由。
     *
     * <p>不需要 HttpServletRequest：这个接口不关心「你是谁」，
     * 所有登录用户看到的是同一份列表。
     */
    @GetMapping("/list")
    public Result<List<CommentVO>> list(@RequestParam Long postId) {

        return Result.success(commentService.listByPost(postId));
    }
}
