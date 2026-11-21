package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单表的 Mapper。
 *
 * <p>表名是保留字 orders，实体上用 @TableName("orders") 标注过了，
 * 这里不用重复写。W5 下单前只建表不写业务；W6 生命周期用 MP 的条件更新就够了，
 * 只有一个统计要手写 SQL —— 「这个商品还有几笔没走完的单」，见 {@link #countUnfinishedByItem}。
 */
@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {

    /**
     * 数一件商品身上「还没走完」的订单笔数（W6：决定商品能不能下架）。
     *
     * <p>「没走完」= status 0（待付款）或 1（待发货）。已发货（2）、已完成（3）、
     * 已取消（4）都不算 —— 商品已经离手或这笔交易已经作废，卖家下架不影响任何人。
     *
     * <pre>
     * 这是跨两张表的统计，订单状态在 orders、商品在 order_item 上，MP 的 wrapper 一次只查一张表，
     * 所以手写：
     *
     * SELECT COUNT(*) FROM orders o
     *   JOIN order_item oi ON oi.order_id = o.id
     *  WHERE oi.item_id = #{itemId}
     *    AND o.status IN (0, 1)
     *    AND o.is_deleted = 0
     *    AND oi.is_deleted = 0
     * </pre>
     *
     * <p><b>手写 SQL 必须自己带 is_deleted = 0：</b>MyBatis-Plus 的逻辑删除只加在它自己生成的
     * SQL 上，手写的它一个字都不管。漏掉的话，被软删的历史订单会把商品永久锁死不能下架
     * （一个已经看不见的订单，永远「没走完」）。
     *
     * <p>为什么用 COUNT 而不是先查 list 再 size()：这里只要一个数字，
     * 查整行再丢掉纯属浪费；而且商品下架按钮是个高频操作，别让它扫一堆不用的列。
     *
     * @param itemId 商品 id
     * @return 未完成订单笔数，0 表示可以安心下架
     */
    @Select("""
            SELECT COUNT(*)
              FROM orders o
              JOIN order_item oi ON oi.order_id = o.id
             WHERE oi.item_id = #{itemId}
               AND o.status IN (0, 1)
               AND o.is_deleted = 0
               AND oi.is_deleted = 0
            """)
    long countUnfinishedByItem(@Param("itemId") Long itemId);
}
