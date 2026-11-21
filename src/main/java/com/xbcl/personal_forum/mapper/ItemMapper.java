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
}
