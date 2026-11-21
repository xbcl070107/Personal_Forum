package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.Item;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一件商品，给前端看的形状。
 *
 * <p>和 Item 实体的唯一区别是 {@link #imagesJson} 变成了 {@link #images}：
 * 实体里存的是 JSON 字符串（一行对应一个对象，实体不做集合转换），
 * 到这一层由 Service 解析成 List，前端直接拿来铺图片格子。
 *
 * <p>多带了 {@link #shopName}：集市列表里每张卡片都要标卖家店名，
 * 不带的话前端对每件商品再发一次 /shop/{id}，一页 20 条就是 20 次多余请求。
 *
 * <p>没有 isDeleted（逻辑删除由 MyBatis-Plus 过滤）。sold_count 照常给：
 * 展示"已售 x 件"用，W2 恒为 0。
 */
@Data
public class ItemVO {

    private Long id;

    private Long shopId;

    /** 店名，由 Service 按 shopId 回填 */
    private String shopName;

    private Long categoryId;

    private String title;

    private String description;

    /** 单价，单位分。前端展示时自己除 100 */
    private Long priceCents;

    /** 库存 */
    private Integer stock;

    /** 已售，只统计支付成功的 */
    private Integer soldCount;

    /**
     * 浏览量（W7）。由 item 表定时落库，<b>允许滞后</b>：
     * 详情页从缓存里拿到的可能是几十秒前的数，刷新一次不一定会立刻 +1。
     * 想看到"当下就动"的话得每次查库，那缓存就白做了。
     */
    private Integer viewCount;

    /** 图片 URL 列表，可能为空列表（没传图）但不会是 null */
    private List<String> images;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 实体 → VO。
     *
     * @param images 已经解析好的图片列表，可以传 null，这里会换成空列表。
     *               <b>故意不在这里解析 imagesJson</b>：解析要处理脏数据，
     *               而"脏数据怎么算"是 Service 的策略，不是 VO 的形状问题。
     */
    public static ItemVO of(Item item, List<String> images) {
        ItemVO vo = new ItemVO();
        vo.setId(item.getId());
        vo.setShopId(item.getShopId());
        vo.setCategoryId(item.getCategoryId());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setPriceCents(item.getPriceCents());
        vo.setStock(item.getStock());
        vo.setSoldCount(item.getSoldCount());
        vo.setViewCount(item.getViewCount() == null ? 0 : item.getViewCount());
        vo.setImages(images == null ? List.of() : images);
        vo.setStatus(item.getStatus());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }
}
