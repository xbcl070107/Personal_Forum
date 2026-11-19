package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺实体，对应 shop 表（见 sql/migration-v4-shop.sql）。
 *
 * <p>一个人只能开一家店：owner_user_id 上有唯一键 uk_owner。
 * Service 里会先查一次「有没有开过」，但那只防正常路径 ——
 * 两个请求同时开店时，兜底的还是数据库这个唯一键（撞了会抛 DuplicateKeyException）。
 *
 * <p>店铺<b>不做物理删除</b>：不再营业就 status 改 0。
 * 原因写在建表脚本里 —— 软删 + 唯一键会让「关了店再想重开」永远失败。
 */
@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店主 user.id。一个 user 只能有一行 */
    private Long ownerUserId;

    /** 店名，最长 32 字 */
    private String name;

    /** 店铺头像，存上传接口返回的 URL */
    private String avatarUrl;

    /** 店铺简介，最长 255 字 */
    private String description;

    /** 店铺类型：1 个人店 / 2 公会店（v2 只标记，不做权限） */
    private Integer type;

    /** 1 正常 / 0 关闭 */
    private Integer status;

    /** 关注数，v2 只占位不实现 */
    private Integer followerCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 逻辑删除标记。name 不能改，application.yaml 的 logic-delete-field 认它 */
    private Integer isDeleted;

    public static final int TYPE_PERSONAL = 1;
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_CLOSED = 0;
}
