package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水实体，对应 stock_log 表。
 *
 * <p>这张表看起来像日志，实际上是<b>幂等闸门</b>：
 * 取消订单 / 超时关单 / 卖家拒单可能都会走到「回补库存」这一步，
 * 靠唯一键 uk_order_item_reason（订单 + 商品 + 原因）保证同一件事只做一次。
 *
 * <p>没有 is_deleted 列 —— 流水一旦写就不改不删，这是审计数据的规矩。
 * 所以这个实体里也没有 isDeleted 字段，MyBatis-Plus 的逻辑删除对它不生效。
 *
 * <p>W5 才会写 Service（条件扣库存 + 写流水在一个事务里）。
 */
@Data
@TableName("stock_log")
public class StockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long itemId;

    /** 负数=扣减，正数=回补 */
    private Integer delta;

    private Integer beforeStock;

    private Integer afterStock;

    /** ORDER / CANCEL / TIMEOUT / SECKILL_FAIL */
    private String reason;

    private LocalDateTime createdAt;
}
