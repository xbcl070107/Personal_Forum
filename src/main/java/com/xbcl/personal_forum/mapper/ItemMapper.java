package com.xbcl.personal_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xbcl.personal_forum.pojo.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品表的 Mapper。
 *
 * <p>W2 只做"发布 + 看自己发的"，列表筛选（关键词/分类/价格区间）
 * 全部能用 selectPage + QueryWrapper 拼出来，所以暂时不写 XML。
 *
 * <p>W5 多了一个必须手写 SQL 的方法 {@link #deductStock}：扣库存必须是一条原子的条件更新，
 * 用 MP 的 updateById 写不出来（updateById 没法把"stock >= n"写进 WHERE）。
 */
@Mapper
public interface ItemMapper extends BaseMapper<Item> {

    /**
     * 扣库存：把「判断库存够不够」和「减库存」合并成一条原子 SQL（数据库层面的 CAS）。
     *
     * <p><b>为什么不能先查再改：</b>
     * <pre>
     *   请求 A: select stock → 1     请求 B: select stock → 1
     *   请求 A: 1 >= 1 通过          请求 B: 1 >= 1 通过
     *   请求 A: update stock = 0     请求 B: update stock = 0
     *   结果：卖出 2 件，库存只减了 1 件（超卖）
     * </pre>
     * 「读 → 判断 → 写」这三步之间没有任何锁，两个请求可以交替执行。
     * 把判断塞进 UPDATE 的 WHERE 里，数据库在执行这一条语句时会对行加锁，
     * 第二个请求要么等第一个提交（然后发现 stock 已经不够 → 0 行），
     * 要么拿到锁后重新判断 —— 无论哪种，都不会超卖。
     *
     * <p><b>返回值怎么看：</b>
     * <ul>
     *   <li>1 = 扣成功，库存真的减了 qty</li>
     *   <li>0 = WHERE 没命中，三种可能：库存不够 / 商品下架（status ≠ 1）/ 商品被删（is_deleted = 1）</li>
     * </ul>
     * 调用方只需要判断 {@code rows == 0} 就抛"商品已下架或库存不足"，
     * 不需要（也不应该）在调用前再 selectById 判断一次库存 —— 判断是白做的。
     *
     * <p><b>两个容易漏的点：</b>
     * <ul>
     *   <li>手写 SQL 不会自动加逻辑删除条件（MP 只接管它自己生成的那部分 SQL），
     *       所以 WHERE 里必须自己写 {@code is_deleted = 0}，否则能把已删除商品的库存扣成负数。</li>
     *   <li>MybatisPlusConfig 里的 BlockAttackInnerInterceptor 只拦"没有 WHERE 的 UPDATE/DELETE"，
     *       这条有 WHERE（主键），能正常执行。</li>
     * </ul>
     *
     * @param itemId 商品 id
     * @param qty    要扣的数量（调用前保证 ≥ 1）
     * @return 受影响行数：1 成功，0 失败
     */
    @Update("""
            UPDATE item
               SET stock = stock - #{qty}
             WHERE id = #{itemId}
               AND is_deleted = 0
               AND status = 1
               AND stock >= #{qty}
            """)
    int deductStock(@Param("itemId") Long itemId, @Param("qty") int qty);

    /**
     * 回补库存（W6）：取消订单 / 超时关单时把扣掉的还回去。
     *
     * <p>和 {@link #deductStock} 是一对，但 WHERE 条件<b>故意不一样</b>：
     * 扣的时候要卡「在售 + 没删 + 库存够」，还的时候什么都不卡，只认主键。
     *
     * <p><b>为什么还账不能带 status = 1 和 is_deleted = 0：</b>
     * 下单时商品在售，不代表取消时还在售 —— 卖家可能已经把它下架了，
     * 甚至下架之后又被软删了。这时候库存也是实实在在少掉的（当初扣过），
     * 不带条件地加回去才是对的。要是照着 deductStock 抄条件，就会出现
     * 「商品下架了 → 回补 0 行 → 库存永久少 N 件」，而且日志里看不出任何异常。
     *
     * <p><b>为什么没有「最多回到多少」的上限判断：</b>回补的数量来自 order_item 快照，
     * 不来自前端；能加超只可能是同一单被回补了两次，那是幂等没做好，
     * 靠 stock_log 的唯一键（uk_order_item_reason）兜底，而不是靠这里的 WHERE。
     *
     * @param itemId 商品 id
     * @param qty    要加回去的数量（调用前保证 ≥ 1）
     * @return 受影响行数：1 成功，0 说明商品连行都没了（物理删除），调用方记 warn 但不要抛
     */
    @Update("""
            UPDATE item
               SET stock = stock + #{qty}
             WHERE id = #{itemId}
            """)
    int restoreStock(@Param("itemId") Long itemId, @Param("qty") int qty);

    /**
     * 浏览量累加（W7）：把 Redis 里攒的一批增量一次性加进 item.view_count。
     *
     * <p>由 {@code task/ItemViewFlushTask} 每 30 秒调一次，一个商品调一次，
     * 不是每次浏览都调 —— 后者会把详情页的 QPS 直接打到数据库上，
     * 那就白做缓存了。</p>
     *
     * <p><b>为什么用 {@code view_count = view_count + #{delta}} 而不是先查再 set：</b>
     * 和扣库存的理由一样，但这里更宽松 —— 浏览量算错一点没人发现，
     * 所以并发下"后写的覆盖先写的"其实也能忍；写成自增只是为了少一次查询，
     * 顺带在落库任务重叠时也不会把数字改小。</p>
     *
     * <p><b>为什么带 is_deleted = 0：</b>手写 SQL 不走 MyBatis-Plus 的逻辑删除，
     * 不写的话会把浏览量加进已删除的行。加不到也没关系（0 行），调用方记 debug 即可，
     * <b>不要抛异常</b> —— 一个浏览量让整个落库批次失败不值得。</p>
     *
     * @param itemId 商品 id
     * @param delta  要加多少（来自 Redis 的增量，正常情况下 ≥ 1）
     * @return 受影响行数：1 加成功；0 说明商品不在了（已删 / 被硬删）
     */
    @Update("""
            UPDATE item
               SET view_count = view_count + #{delta}
             WHERE id = #{itemId}
               AND is_deleted = 0
            """)
    int incrViewCount(@Param("itemId") Long itemId, @Param("delta") long delta);
}
