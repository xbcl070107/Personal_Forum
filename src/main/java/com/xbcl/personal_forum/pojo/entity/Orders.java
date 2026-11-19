package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体，对应 <b>orders</b> 表。
 *
 * <p>类名用了不可数名词的复数（Orders），和 plan 里的约定一致 ——
 * 因为 order 在 Java 里不算敏感词，但在 SQL 里 ORDER 是关键字，
 * 表名统一带 s，实体名跟着表名走，省得每次写 @TableName。
 *
 * <p>W5 / W6 才会写 Service，现在只有表 / 实体 / Mapper。
 *
 * <p>状态流转一律用<b>条件更新</b>（CAS），不要「先查再判断再更新」：
 * <pre>
 * UPDATE orders SET status=1, paid_at=NOW() WHERE id=? AND status=0;
 * </pre>
 * 影响行数 0 就说明状态已经变了（别人先点了），
 * 要返回明确的业务错误，不能静默当成功。
 *
 * <p>金额三个字段都是「分」：total（商品总额）、discount（优惠）、
 * pay（实付 = total - discount）。实付永远服务端算，不信前端传上来的数。
 */
@Data
@TableName("orders")
public class Orders {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号，先 INSERT 拿到 id 再回写：OD + yyyyMMdd + 10 位补零 id */
    private String orderNo;

    /** 买家 user.id */
    private Long userId;

    /** 卖家店铺 shop.id，一单只含一家店 */
    private Long shopId;

    /** 0 待付款 / 1 已付款 / 2 已发货 / 3 已完成 / 4 已取消 */
    private Integer status;

    /** 商品总额（分） */
    private Long totalCents;

    /** 优惠（分） */
    private Long discountCents;

    /** 实付（分） */
    private Long payCents;

    /** 用掉的 user_coupon.id，D 阶段才有值 */
    private Long userCouponId;

    /** 前端幂等令牌（UUID）。重复提交靠 uk_client_token 挡住 */
    private String clientToken;

    /** 商品件数合计 */
    private Integer itemCount;

    // ---- 收货信息快照：下单那一刻拷进来，之后改地址不影响历史订单 ----
    private String receiverName;
    private String receiverPhone;
    private String receiverRegion;
    private String receiverDetail;

    /** 快递单号 / 快递公司，卖家发货时填 */
    private String trackingNo;
    private String shippingCompany;

    /** 待付款截止时间（下单 + 15 分钟） */
    private LocalDateTime expireAt;

    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime closedAt;

    /** 关单原因：TIMEOUT / BUYER_CANCEL / SELLER_REJECT */
    private String closeReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;

    public static final int STATUS_UNPAID = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_SHIPPED = 2;
    public static final int STATUS_FINISHED = 3;
    public static final int STATUS_CANCELED = 4;
}
