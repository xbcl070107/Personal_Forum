package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.Shop;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一家店，给前端看的形状。
 *
 * <p>和 Shop 实体几乎一一对应，只多了 {@link #itemCount}：
 * 店铺详情页要显示"这家店有几件在售商品"，否则前端得再发一次 /item/list
 * 只为数个数。W2 先给这个字段，值由 ItemService 现数。
 *
 * <p>没有 isDeleted：逻辑删除是数据库的事，MyBatis-Plus 已经过滤过了。
 */
@Data
public class ShopVO {

    private Long id;

    /** 店主 user.id */
    private Long ownerUserId;

    /**
     * 店主昵称（W4 加）。
     *
     * <p>不是 shop 表的列，要拿 {@link #ownerUserId} 去查 user 表才有 ——
     * 这就是本周说的「关联查询」：VO 经常是好几张表拼出来的形状，
     * 但拼的过程放在 Service 里，VO 自己不认识 Mapper。
     *
     * <p>用 {@code User#displayName()} 取值（nickname 为空才退回 username），
     * 别直接 getNickname()，否则历史数据会让页面出现空白。
     */
    private String ownerNickname;

    /** 店名 */
    private String name;

    /** 头像 URL，可能为空 */
    private String avatarUrl;

    /** 简介，可能为空 */
    private String description;

    /** 1 个人店 / 2 公会店 */
    private Integer type;

    /** 1 正常 / 0 关闭 */
    private Integer status;

    /** 关注数（v2 不实现，恒为 0） */
    private Integer followerCount;

    /** 在售商品数。由 Service 回填，不是 shop 表的列 */
    private Integer itemCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 实体 → VO。itemCount 不在这里填（它要查 item 表），
     * 由 ShopService 组装完再 set 进去 —— 这个方法只负责"能一一对应的那些"。
     */
    public static ShopVO of(Shop shop) {
        ShopVO vo = new ShopVO();
        vo.setId(shop.getId());
        vo.setOwnerUserId(shop.getOwnerUserId());
        vo.setName(shop.getName());
        vo.setAvatarUrl(shop.getAvatarUrl());
        vo.setDescription(shop.getDescription());
        vo.setType(shop.getType());
        vo.setStatus(shop.getStatus());
        vo.setFollowerCount(shop.getFollowerCount());
        vo.setCreatedAt(shop.getCreatedAt());
        vo.setUpdatedAt(shop.getUpdatedAt());
        return vo;
    }
}
