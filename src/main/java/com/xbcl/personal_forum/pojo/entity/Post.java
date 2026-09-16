package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子实体，对应数据库里的 post 表（见 sql/init.sql）。
 *
 * <p>字段名到列名的转换还是靠 application.yaml 里的
 * map-underscore-to-camel-case: true：userId → user_id，createdAt → created_at。
 */
@Data
@TableName("post")
public class Post {

    /** 主键，交给数据库的 AUTO_INCREMENT 生成 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者。存的是 user.id（数字），不是用户名 —— 用户名能改，id 不能 */
    private Long userId;

    /** 标题，最长 100 字（数据库那一列限的，超了会直接报错，Service 里要校验） */
    private String title;

    /** 正文，数据库那边是 TEXT，长度基本不用管 */
    private String content;

    /** 所属板块。存的是 category.id */
    private Long categoryId;

    /**
     * 0 = 正常，1 = 已删。
     *
     * <p>⚠️ 这个字段名一个字都不能改。application.yaml 里写着
     * logic-delete-field: isDeleted —— MyBatis-Plus 就是靠这个名字认出
     * 「哪一列是删除标记」的。名字对不上，deleteById() 会变成真的
     * DELETE FROM post，帖子和它的回复一起没了，而且不报错。
     */
    private Integer isDeleted;

    /** 发布时间。数据库那列有 DEFAULT CURRENT_TIMESTAMP，Java 这边不设置它 */
    private LocalDateTime createdAt;

    /** 最后修改时间。数据库那列有 ON UPDATE CURRENT_TIMESTAMP，同上，不设置 */
    private LocalDateTime updatedAt;
}