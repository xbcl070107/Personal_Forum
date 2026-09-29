package com.xbcl.personal_forum.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论列表里的一条，专门给前端看的形状。
 *
 * <p>为什么不直接返回 Comment 实体：
 * comment 表里只有 user_id，前端拿到 1 是查不出「谁说的」的 ——
 * 它要再发一个请求去查用户，20 条评论就是 20 个请求。
 * 所以这里多一个 username 字段，由后端一次查好填进来。
 *
 * <p>顺带的好处：以后 comment 表加列（比如点赞数），
 * 只要不改这个 VO，前端拿到的结构就不会变。
 *
 * <p>注意这里<b>没有</b> isDeleted —— 逻辑删除标记是给数据库看的，
 * 前端不需要知道「这一行其实还在，只是被标了删」。MyBatis-Plus 查的时候
 * 会自动带上 is_deleted = 0，删除的评论根本不会出现在这个列表里。
 */
@Data
public class CommentVO {

    /** 评论 id。前端要删除某条评论时得用它 */
    private Long id;

    /** 评论人 id。前端点用户名跳个人主页的时候要用 */
    private Long userId;

    /** 评论人登录名。由 Service 批量查出来回填，不是数据库里直接查的 */
    private String username;

    /** 评论内容 */
    private String content;

    /** 评论时间，前端格式化成「3 分钟前」这种 */
    private LocalDateTime createdAt;
}