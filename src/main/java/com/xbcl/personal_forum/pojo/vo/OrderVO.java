package com.xbcl.personal_forum.pojo.vo;

import com.xbcl.personal_forum.pojo.entity.OrderItem;
import com.xbcl.personal_forum.pojo.entity.Orders;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一笔订单，给前端看的形状（W5）。
 *
 * <p>{@link #statusText} 是服务端给的文案：订单状态的中文说法只在 {@link #statusText(Integer)}
 * 里写一次，别让前端每个页面自己 switch —— 以后加"退款中"，只改这一处。
 *
 * <p>{@link #items} 只在「刚下单」和「订单详情」时填。
 * W6 做订单列表时，列表页可以不填，省掉一次 order_item 查询（一页 20 单就是 20 次）。
 *
 * <p>收货信息是<b>快照</b>（和 order_item 的理由一样）：
 * 用户之后改地址、删地址，都不影响已经下过的订单要寄到哪。
 * 所以这里是 receiverName / receiverPhone / ... 四个平铺字段，而不是一个 addressId。
 */
@Data
public class OrderVO {

    private Long id;

    /** 订单号，例如 OD202511190000000042 */
    private String orderNo;

    /** 0 待付款 / 1 待发货 / 2 待收货 / 3 已完成 / 4 已取消 */
    private Integer status;

    /** status 的中文说法，前端直接显示 */
    private String statusText;

    private Long shopId;

    /** 店名，由 Service 按 shopId 回填（同 ItemVO.shopName 的理由） */
    private String shopName;

    /** 商品总额，单位分 */
    private Long totalCents;

    private Long discountCents;

    /** 实付 = totalCents - discountCents */
    private Long payCents;

    /** 一共几件（P1 等于唯一那行的 quantity） */
    private Integer itemCount;

    private String receiverName;

    private String receiverPhone;

    private String receiverRegion;

    private String receiverDetail;

    /** 物流单号，未发货时为 null */
    private String trackingNo;

    /** 快递公司，未发货时为 null */
    private String shippingCompany;

    /** 未付款订单的到期时间（下单 + 15 分钟），前端用来倒计时 */
    private LocalDateTime expireAt;

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime closedAt;

    private LocalDateTime createdAt;

    private List<OrderItemVO> items;

    public static OrderVO of(Orders order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setShopId(order.getShopId());
        vo.setTotalCents(order.getTotalCents());
        vo.setDiscountCents(order.getDiscountCents());
        vo.setPayCents(order.getPayCents());
        vo.setItemCount(order.getItemCount());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverRegion(order.getReceiverRegion());
        vo.setReceiverDetail(order.getReceiverDetail());
        vo.setTrackingNo(order.getTrackingNo());
        vo.setShippingCompany(order.getShippingCompany());
        vo.setExpireAt(order.getExpireAt());
        vo.setPaidAt(order.getPaidAt());
        vo.setShippedAt(order.getShippedAt());
        vo.setFinishedAt(order.getFinishedAt());
        vo.setClosedAt(order.getClosedAt());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setItems(items == null ? List.of() : items.stream().map(OrderItemVO::of).toList());
        return vo;
    }

    /**
     * 状态文案。用 switch 表达式（Java 14+）：不写 break、漏了分支编译器会提醒你，比 if-else 链清楚。
     *
     * <p>注意文案是「下一步等谁」的口吻（待发货 = 等卖家），不是「已经发生了什么」。
     * 买家看到"待发货"才知道该等；写"已付款"他只会来问什么时候发。
     */
    public static String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case Orders.STATUS_UNPAID -> "待付款";
            case Orders.STATUS_PAID -> "待发货";
            case Orders.STATUS_SHIPPED -> "待收货";
            case Orders.STATUS_FINISHED -> "已完成";
            case Orders.STATUS_CANCELED -> "已取消";
            default -> "未知状态";
        };
    }
}
