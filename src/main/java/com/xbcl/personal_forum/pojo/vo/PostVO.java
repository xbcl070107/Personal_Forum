package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.Post;
import com.xbcl.personal_forum.pojo.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条帖子，专门给前端看的形状。
 *
 * <p>为什么不直接返回 Post 实体：post 表里只有 user_id，前端拿到 4 是查不出
 * 「谁发的」的 —— 列表页上一排帖子全显示成「用户 4」。所以要在这里多带
 * 作者的两个名字，由 Service 批量查出来回填（和 CommentVO 是同一套做法）。
 *
 * <p>这也是前端那个「作者名靠 userId 猜」的临时方案能撤掉的原因：
 * 以前接口不给名字，前端只能按 userId 推一个假名，结果同一个人在
 * 帖子头部和评论区显示成两个不同的名字。
 *
 * <p>注意这里是<b>两个</b>名字字段，别混用：
 * <ul>
 *   <li>{@link #nickname} —— 展示名，可重复。页面上显示的是它</li>
 *   <li>{@link #username} —— 登录名，唯一。用来区分两个同名的人、
 *       以及给前端算一个稳定的头像颜色</li>
 * </ul>
 *
 * <p>注意这里没有 isDeleted：逻辑删除标记是给数据库看的，
 * 前端不需要知道「这一行其实还在，只是被标了删」。MyBatis-Plus 查询时
 * 自动带上 is_deleted = 0，删掉的帖子根本不会出现在返回里。
 * （这一点和 CommentVO 保持同一个口径。）
 */
@Data
public class PostVO {

    /** 帖子 id */
    private Long id;

    /** 作者 id。前端判断「这条是不是我发的」要用它 */
    private Long userId;

    /** 作者登录名，唯一。不是展示名，别直接显示给用户 */
    private String username;

    /** 作者昵称，展示用。页面上显示作者就显示它 */
    private String nickname;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 所属板块 id。板块名前端自己按 /category/list 映射 */
    private Long categoryId;

    /** 审核状态：0 待审 / 1 已通过 / 2 已驳回 */
    private Integer status;

    /** 发布时间 */
    private LocalDateTime createdAt;

    /** 最后修改时间 */
    private LocalDateTime updatedAt;

    /**
     * 作者被物理删除（或者 id 查不到人）时的占位名。
     * 和 CommentService 用的是同一句文案，同一个现象在两处别长得不一样。
     */
    public static final String UNKNOWN_AUTHOR = "已注销";

    /**
     * 由实体加作者组装出一个 VO。
     *
     * <p>做成静态工厂而不是在 Service 里逐字段 set：字段一共十个，
     * 将来 Post 加一列就得回来补一处，漏了不报错、只是前端少个字段。
     * 收在这里，加列时至少只有一个地方要看。
     *
     * @param author 作者。可能为 null（作者被物理删除了）——
     *               那种情况下两个名字字段都给占位名，前端不会显示空
     */
    public static PostVO of(Post post, User author) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setUserId(post.getUserId());

        if (author == null) {
            vo.setUsername(UNKNOWN_AUTHOR);
            vo.setNickname(UNKNOWN_AUTHOR);
        } else {
            vo.setUsername(author.getUsername());
            // displayName() 里带了「昵称为空就退回 username」的兜底
            vo.setNickname(author.displayName());
        }

        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setCategoryId(post.getCategoryId());
        vo.setStatus(post.getStatus());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());
        return vo;
    }
}
