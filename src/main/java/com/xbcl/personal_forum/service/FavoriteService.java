package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xbcl.personal_forum.mapper.FavoriteMapper;
import com.xbcl.personal_forum.pojo.entity.Favorite;
import com.xbcl.personal_forum.pojo.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 收藏业务。和 LikeService 是同一套动作，换成 post_favorite 表。
 *
 * <p>为什么不合成一个「InteractionService」：点赞和收藏现在是长得一样，
 * 但它们的语义不一样 —— 收藏是「我以后要回来看」，点赞只是「我看过了」。
 * 将来收藏要加「我的收藏」列表、要给作者看谁收藏了，改动只会落在这一边。
 * 硬合成一个类，到时候就会开始在方法里写 if (isFavorite)。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    /** 只为了调 requireApproved，本类不碰任何帖子数据 */
    private final PostService postService;

    /**
     * 翻面：收藏过就取消，没收藏过就收藏。
     *
     * @return 翻面之后的最新状态和总数
     */
    public FavoriteVO toggle(Long postId, Long userId) {

        postService.requireApproved(postId);

        // 这个 delete 是真的 DELETE。post_favorite 表上没有 is_deleted 列。
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPostId, postId));

        boolean favorited;

        if (deleted > 0) {
            favorited = false;

        } else {
            try {
                Favorite favorite = new Favorite();
                favorite.setUserId(userId);
                favorite.setPostId(postId);
                favoriteMapper.insert(favorite);
                favorited = true;

            } catch (DuplicateKeyException e) {
                // 并发双击的第二下：唯一索引 uk_fav_user_post 拦住了重复插入。
                // 对它来说结果同样是「已收藏」，所以吞掉。
                favorited = true;
            }
        }

        return new FavoriteVO(favorited, countOf(postId));
    }

    /**
     * 查当前状态。详情页加载时调一次。
     */
    public FavoriteVO status(Long postId, Long userId) {

        postService.requireApproved(postId);

        boolean favorited = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getPostId, postId)) > 0;

        return new FavoriteVO(favorited, countOf(postId));
    }

    /** 数一遍这个帖被收藏了多少次 */
    private long countOf(Long postId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getPostId, postId));
    }
}
