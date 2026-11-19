package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单明细实体，对应 order_item 表。
 *
 * <p>title / cover / price 都是从 item 上拷下来的<b>快照</b>，
 * 不是关联查询出来的。原因：商品之后可以改标题、改价、甚至下架，
 * 但「当时花了多少钱买的是什么」不能跟着变 —— 订单是历史凭证。
 *
 * <p>P1 一单只有一件商品（item_count = 1），但表按多行建：
 * 以后加购物车或拆单，结构不用动。
 *
 * <p>discount_cents / pay_cents 是券分摊用的：
 * 按 subtotal 比例分摊，余数给 subtotal 最大的那一行，
 * 保证明细实付之和严格等于订单实付（差 1 分钱对账就对不上）。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long itemId;

    /** 下单时的商品标题快照 */
    private String itemTitle;

    /** 下单时的封面快照（images_json 里的第一张） */
    private String itemCoverUrl;

    /** 下单价（分） */
    private Long priceCents;

    private Integer quantity;

    /** 小计 = price * quantity */
    private Long subtotalCents;

    /** 券分摊到本行（分） */
    private Long discountCents;

    /** 本行实付（分） */
    private Long payCents;

    private LocalDateTime createdAt;

    private Integer isDeleted;
}
