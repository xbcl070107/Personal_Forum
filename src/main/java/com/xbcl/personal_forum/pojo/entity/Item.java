package com.xbcl.personal_forum.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体，对应 item 表。
 *
 * <p>三个字段要记住各自的权威性：
 * <ul>
 *   <li>stock —— 库存<b>唯一权威</b>。秒杀时 Redis 里的是镜像，最后对账还看它</li>
 *   <li>sold_count —— 只有<b>支付成功</b>才 +1。下单不 +1（不然没付款的订单也算卖掉了）</li>
 *   <li>status —— 1 上架 / 0 下架。<b>售罄不在这里</b>，售罄 = stock 等于 0</li>
 * </ul>
 *
 * <p>images_json 存的是 JSON 数组字符串（最多 9 个 URL），
 * 到 Service 层才解析成 List&lt;String&gt; 放进 ItemVO ——
 * 实体不做字符串到集合的转换，保持「一行对应一个对象」。
 */
@Data
@TableName("item")
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属店铺 shop.id */
    private Long shopId;

    /** 商品分类，指向 category 表里 scope='ITEM' 的那几行；可以为空 */
    private Long categoryId;

    /** 标题，最长 80 字 */
    private String title;

    /** 详情，纯文本，不做富文本 */
    private String description;

    /** 单价，单位<b>分</b>（1000 = 10.00 元）。全项目金额都用分，不用 double */
    private Long priceCents;

    /** 库存，权威值 */
    private Integer stock;

    /** 已售数量，只统计支付成功的 */
    private Integer soldCount;

    /** 图片 URL 的 JSON 数组字符串，例如 ["/dev-api/uploads/images/2026-11/a.jpg"] */
    private String imagesJson;

    /** 1 上架 / 0 下架 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer isDeleted;

    public static final int STATUS_ON = 1;
    public static final int STATUS_OFF = 0;
}
