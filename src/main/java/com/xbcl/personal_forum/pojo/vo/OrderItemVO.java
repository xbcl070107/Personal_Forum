package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.OrderItem;
import lombok.Data;

/**
 * 订单里的一行商品 —— 下单那一刻的<b>快照</b>（W5）。
 *
 * <p>为什么叫快照：标题、封面、单价都是从 item 表拷过来的副本。
 * 之后卖家改价、改名、下架甚至删掉商品，历史订单里显示的仍然是买家当初买的东西。
 * 这就是 order_item 表存在的全部理由。
 *
 * <p>所以这里<b>不要</b>写成 join item 表取标题 —— 那样「历史订单」会随着商品改名
 * 而变形，对账时会出大事（用户会说"我明明买的是 A 价格"）。
 *
 * <p>金额全部是「分」，且这一行自己就带齐了算账需要的三个数：
 * subtotal（小计）、discount（这行优惠）、pay（这行实付）。
 * P1 没有券，discount 恒 0，但字段先留着，W7 做券时不用改表结构。
 */
@Data
public class OrderItemVO {

    private Long itemId;

    private String itemTitle;

    /** 商品主图快照，可能为 null（发布商品时没传图） */
    private String itemCoverUrl;

    /** 下单时的单价，单位分 */
    private Long priceCents;

    private Integer quantity;

    /** 小计 = priceCents × quantity */
    private Long subtotalCents;

    private Long discountCents;

    /** 这一行的实付 = subtotal - discount */
    private Long payCents;

    public static OrderItemVO of(OrderItem orderItem) {
        OrderItemVO vo = new OrderItemVO();
        vo.setItemId(orderItem.getItemId());
        vo.setItemTitle(orderItem.getItemTitle());
        vo.setItemCoverUrl(orderItem.getItemCoverUrl());
        vo.setPriceCents(orderItem.getPriceCents());
        vo.setQuantity(orderItem.getQuantity());
        vo.setSubtotalCents(orderItem.getSubtotalCents());
        vo.setDiscountCents(orderItem.getDiscountCents());
        vo.setPayCents(orderItem.getPayCents());
        return vo;
    }
}
