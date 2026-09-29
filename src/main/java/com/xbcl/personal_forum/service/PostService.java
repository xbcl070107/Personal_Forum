package com.xbcl.personal_forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xbcl.personal_forum.common.BusinessException;
import com.xbcl.personal_forum.mapper.CategoryMapper;
import com.xbcl.personal_forum.mapper.PostMapper;
import com.xbcl.personal_forum.pojo.dto.PostPublishDTO;
import com.xbcl.personal_forum.pojo.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子业务。目前有发帖和审核，列表 / 详情 / 删帖后面往下加。
 *
 * <p>新增任何「读帖子」的方法时，记住 status 这个字段：
 * MyBatis-Plus 的逻辑删除（isDeleted）会自动带上条件，status 不会，
 * 每个查询都得自己写。写漏了，待审和已驳回的帖就直接漏给前端了。
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;

    /** 用来校验发帖时选的板块真的存在 */
    private final CategoryMapper categoryMapper;

    /**
     * 发帖。注意发出来的帖子是「待审」状态，不是直接发布。
     *
     * @param dto    前端传的标题 / 正文 / 板块
     * @param userId 作者，由 Controller 从 token 里取出来传进来 ——
     *               故意不放进 dto，前端就伪造不了作者
     * @return 新帖的 id
     *         <p><b>这个 id 前端暂时用不了。</b>帖子还是待审，详情接口查不到它。
     *         前端发帖成功后就停在原地弹一句「已提交，等待审核」，
     *         别拿这个 id 跳详情页 —— 跳过去是个 404，看起来像发帖失败了。
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

        // 3. 待审。数据库那列写的是 DEFAULT 0，不给也能跑，
        //    但那样「发帖 = 待审」这条规则就只藏在建表语句里，看代码看不出来。
        //    显式写出来，以后改审核流程的人一眼就知道入口在哪。
        post.setStatus(Post.STATUS_PENDING);

        // 4. 落库。@TableId(type = IdType.AUTO) 的作用就在这：
        //    insert 之后新 id 会回填到 post 对象上，可以直接读出来用。
        postMapper.insert(post);

        return post.getId();
    }

    /**
     * 待审列表，给管理员用。
     *
     * @return 所有待审的帖子，按提交时间<b>正序</b>（最早的排最前，先来先审）
     */
    public List<Post> listPending() {

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, Post.STATUS_PENDING)
                .orderByAsc(Post::getCreatedAt);

        // is_deleted 不用写。MyBatis-Plus 的逻辑删除会自动加上 is_deleted = 0，
        // 这就是它比手写 SQL 省事的地方。
        return postMapper.selectList(wrapper);

        // 数据量大了再换成分页（项目里已经配了分页插件）：
        // return postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 审核一条帖子：通过或者驳回。
     *
     * <p>权限判断不在这里 —— Service 不该认识 HttpServletRequest。
     * 「是不是管理员」由 Controller 从 token 里取 role 判断，见 PostController#checkAdmin。
     *
     * @param postId 要审的帖子
     * @param pass   true 通过 / false 驳回
     * @throws BusinessException 帖子不存在，或者已经不是待审状态了
     */
    public void audit(Long postId, boolean pass) {

        // 用「条件更新」，不是「先查再改」。
        //
        // 先 selectById 看一眼 status 是不是 0，然后再 update —— 那样有竞态：
        // 两个管理员同时点，两边都查到是「待审」，两边都通过，看起来没事，
        // 但「先驳回再点通过」这种情况状态就会乱跳。
        //
        // 条件写在 WHERE 里，让数据库自己保证原子性：
        // 只有 status 还等于 0 的行会被更新，第二次执行影响的函数就是 0 行。
        LambdaUpdateWrapper<Post> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Post::getStatus, pass ? Post.STATUS_APPROVED : Post.STATUS_REJECTED)
                .eq(Post::getId, postId)
                .eq(Post::getStatus, Post.STATUS_PENDING);

        // update(null, wrapper)：entitiy 传 null，因为要改的值都在 wrapper 的 set 里。
        // 返回的是「影响行数」，不是成功与否 —— 这个值必须看。
        int rows = postMapper.update(null, wrapper);

        if (rows == 0) {
            // 走到这说明 WHERE 没匹配上。可能是 id 写错了，也可能是这条已经被审过了。
            // 分不清具体是哪种，就只说事实，别替用户下结论。
            throw new BusinessException("帖子不存在，或者已经被审核过了");
        }
    }
}
