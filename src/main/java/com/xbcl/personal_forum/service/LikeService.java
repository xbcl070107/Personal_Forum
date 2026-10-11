package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.mapper.LikeMapper;
import com.xbcl.personal_forum.pojo.entity.Like;
import com.xbcl.personal_forum.pojo.vo.LikeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 点赞业务。
 *
 * <p>接口只有一个动作，不带「我要赞」还是「我要取消」的参数 ——
 * 后端自己看现在什么状态，再翻面（toggle）。前端也别自己判断，
 * 它记错一次状态，结果就整个反了。
 *
 * <p>「帖子存在且已过审」这个前提交给 PostService.requireApproved 统一判断，
 * 这里不重复写一份 —— 规则只有一处，改的时候不会漏。
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeMapper likeMapper;

    /** 只为了调 requireApproved，本类不碰任何帖子数据 */
    private final PostService postService;

    /**
     * 翻面：赞过就取消，没赞过就赞。
     *
     * @return 翻面之后的最新状态和总数
     */
    public LikeVO toggle(Long postId, Long userId) {

        // 1. 待审的、被驳回的、已经删掉的帖，都不该被点赞。
        //    和发评论是同一个前提 —— 这些动作的前提都是「这帖游客看得见」。
        postService.requireApproved(postId);

        // 2. 先删。
        //
        //    ⚠️ 这个 delete 是真的 DELETE。post_like 表上没有 is_deleted 列，
        //    MyBatis-Plus 的逻辑删除管不到它。取消点赞就是把这一行删掉 ——
        //    点赞记录不需要留痕，也不需要「恢复」。
        int deleted = likeMapper.delete(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getPostId, postId));

        boolean liked;

        if (deleted > 0) {
            // 删到行了 → 之前赞过 → 这一次是取消
            liked = false;

        } else {
            // 没删到 → 没赞过 → 插一条
            try {
                Like like = new Like();
                like.setUserId(userId);
                like.setPostId(postId);
                likeMapper.insert(like);
                liked = true;

            } catch (DuplicateKeyException e) {
                // 走到这里说明：在我「删」和「插」之间，另一个请求已经插进去了。
                // 双击点赞的第二下就会这样。
                // 对第二下来说，结果和插入成功是一个意思（现在是已赞），所以吞掉。
                // 这是本类里唯一能吞异常的地方，别把范围放大，更别换成 Exception。
                liked = true;
            }
        }

        return new LikeVO(liked, countOf(postId));
    }

    /**
     * 查当前状态。详情页加载时调一次，用来把按钮画对 ——
     * 不查的话，刷新页面后按钮会永远是「未赞」的样子。
     */
    public LikeVO status(Long postId, Long userId) {

        postService.requireApproved(postId);

        boolean liked = likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getPostId, postId)) > 0;

        return new LikeVO(liked, countOf(postId));
    }

    /**
     * 数一遍这个帖被赞了多少次。
     *
     * <p>每次都重新数，不用「原来多少 ±1」去猜 —— 并发下那个数已经不准了，
     * 而这里只是一次走 idx_like_post_id 的 count，便宜。
     */
    private long countOf(Long postId) {
        return likeMapper.selectCount(
                new LambdaQueryWrapper<Like>().eq(Like::getPostId, postId));
    }
}
