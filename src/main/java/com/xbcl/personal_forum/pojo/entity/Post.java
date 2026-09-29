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
     * 审核状态。取值用下面那三个常量，别直接写 0 / 1 / 2。
     *
     * <p>规则是「先审后发」：新帖进来是 0，管理员审过之后才可能变成 1。
     * 列表和详情查询都会带上 status = 1，所以 0 和 2 的帖子谁也看不见 ——
     * <b>包括作者自己</b>。发帖成功后前端只提示「已提交，别跳详情页」，
     * 跳过去也是查不到的。
     *
     * <p>和 isDeleted 的区别：isDeleted 是 MyBatis-Plus 的逻辑删除，
     * 查询时会自动带上条件；status 是普通列，<b>每个查询都得自己写</b>。
     * 以后新增任何「读帖子」的方法，先想一遍该不该过滤 status。
     */
    private Integer status;

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

    /*
     * 下面是 status 的三个取值。
     *
     * 为什么常量放在实体里：status 就是 post 表的一列，取值跟列定义绑在一起。
     * 以后改状态（比如加个「已隐藏」），改数据库列定义的时候一定会看见这里，
     * 不会出现「SQL 注释里写着 0/1/2，Java 里不知道在哪」的情况。
     *
     * 为什么用 int 而不是 enum：MyBatis-Plus 直接把 Integer 读写到 TINYINT，
     * 中间不用配 TypeHandler。枚举要额外处理，这个项目没必要。
     *
     * static final 的字段 Lombok 不会算进实体字段，
     * 所以不影响 @Data 生成的 getter / setter / toString。
     */
    /** 待审：刚发出来，只有管理员看得到 */
    public static final int STATUS_PENDING = 0;
    /** 已通过：正常显示 */
    public static final int STATUS_APPROVED = 1;
    /** 已驳回：审核没过，和待审一样谁也看不见 */
    public static final int STATUS_REJECTED = 2;
}