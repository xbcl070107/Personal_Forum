package com.xbcl.personal_forum.service;

import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.mapper.PostMapper;
import com.xbcl.personal_forum.pojo.dto.PostPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 帖子业务。目前只有发帖，列表 / 详情 / 删帖后面往下加。
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;

    /** 用来校验发帖时选的板块真的存在 */
    private final CategoryMapper categoryMapper;

    /**
     * 发帖。
     *
     * @param dto    前端传的标题 / 正文 / 板块
     * @param userId 作者，由 Controller 从 token 里取出来传进来 ——
     *               故意不放进 dto，前端就伪造不了作者
     * @return 新帖的 id，前端拿它跳转到详情页
     */
    public Long publish(PostPublishDTO dto, Long userId) {

        // 1. 板块得真的存在。post 表上没建外键约束（建了删板块会很麻烦），
        //    所以「categoryId 是不是有效」得自己在代码里看，
        //    不然能发出一条 category_id 指向空气的帖子，详情页会查不到板块名。
        if (categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException("板块不存在");
        }

        // 2. 拼实体。只设用户填的那几个，
        //    isDeleted / createdAt / updatedAt 一律不碰：
        //    is_deleted 靠数据库的 DEFAULT 0，两个时间靠 DEFAULT CURRENT_TIMESTAMP。
        //    MyBatis-Plus 不会把 null 字段写进 INSERT 语句，所以它们是安全的。
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setCategoryId(dto.getCategoryId());

        // 3. 落库。@TableId(type = IdType.AUTO) 的作用就在这：
        //    insert 之后新 id 会回填到 post 对象上，可以直接读出来用。
        postMapper.insert(post);

        return post.getId();
    }
}
