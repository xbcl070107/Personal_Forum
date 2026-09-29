package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论实体，对应数据库里的 comment 表（见 sql/init.sql）。
 *
 * <p>驼峰字段名到数据库列名的转换，靠 application.yaml 里那句
 * map-underscore-to-camel-case: true —— postId 去找 post_id，createdAt 去找 created_at。
 * 名字对不上是运行时才炸的 Unknown column，所以字段名和列名必须严格对应。
 *
 * <p>⚠️ 这个类的字段集合，必须和 comment 表的列集合完全一致，一个不多一个不少。
 * post 表有 updated_at，comment 表没有 —— 从 Post 抄的时候别顺手带过来。
 * MyBatis-Plus 是照着实体字段拼 INSERT 的列名的，实体多一个字段，SQL 就多一列，
 * 数据库当场翻脸。
 *
 * <p>评论不做审核，所以这里也没有 status 字段：能看到的评论就是正常的，
 * 不存在「审核中」这种中间态。
 */
@Data
@TableName("comment")
public class Comment {

    /** 主键，交给数据库的 AUTO_INCREMENT 生成，插入后 MyBatis-Plus 会回填 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子，存 post.id。表上是 NOT NULL —— 没有归属帖子的评论，数据库不收 */
    private Long postId;

    /** 评论人，存 user.id。和发帖一样，这个人是谁由后端从 token 里取，不看前端传什么 */
    private Long userId;

    /**
     * 评论内容。数据库那列是 VARCHAR(500)，超一个字符就抛异常，
     * 所以 CommentPublishDTO 上配了 @Size(max = 500) —— 这两个数是绑在一起的，
     * 哪天改列宽，两边得一起改。
     */
    private String content;

    /**
     * 逻辑删除标记：0 = 正常，1 = 已删。
     *
     * <p>⚠️ 字段名一个字都不能改。application.yaml 里写着 logic-delete-field: isDeleted，
     * MyBatis-Plus 就是靠这个名字认出「哪一列是删除标记」。名字对不上，
     * deleteById() 会退化成真的 DELETE FROM comment —— 评论物理消失，而且不报错。
     *
     * <p>类型必须是 Integer 而不是 boolean：数据库那列是 tinyint；
     * 而且 Lombok 给 boolean 生成的 getter 叫 isDeleted()，Jackson 会把它
     * 序列化成 "deleted"，前端拿到的字段名就和别的实体对不上了。
     */
    private Integer isDeleted;

    /**
     * 评论时间。数据库那列有 DEFAULT CURRENT_TIMESTAMP，
     * 所以 Java 这边不设它 —— 留 null，INSERT 语句里干脆不带这一列。
     */
    private LocalDateTime createdAt;
}
