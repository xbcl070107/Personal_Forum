package com.xbcl.personal_forum;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.pojo.entity.Item;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * W7 验收测试：100 个线程同时抢同一件商品的库存，看会不会超卖。
 *
 * <p><b>它到底在验什么</b>：{@code ItemMapper.deductStock} 那条 SQL ——
 * <pre>
 * UPDATE item SET stock = stock - #{qty}, sold_count = sold_count + #{qty}
 *  WHERE id = #{itemId} AND is_deleted = 0 AND status = 1 AND stock &gt;= #{qty}
 * </pre>
 * 并发安全<b>不是靠 Java 锁</b>，是靠最后那个 {@code stock >= qty}：
 * 100 个线程的 UPDATE 在数据库里是排队执行的，后到的线程拿到的是"别人已经减过的 stock"，
 * 减到 0 之后条件不满足，影响行数就是 0 —— 于是它老老实实回滚、提示"库存不足"。
 *
 * <p><b>要证明的两件事</b>
 * <ol>
 *   <li>成功次数 == 库存数（不多不少，多一个就是超卖）</li>
 *   <li>结束后 stock 正好是 0，没有变成负数（条件更新真的挡住了）</li>
 * </ol>
 *
 * <p><b>为什么用 @SpringBootTest 而不是 Mockito</b>：这一条要验的是<b>数据库的条件更新</b>。
 * 把 Mapper mock 掉，等于验"我自己写的那套假逻辑"，什么也证明不了。
 *
 * <p><b>会动到真实数据，但会还原</b>：从库里挑一件在售商品，抢完之后把成功的数量
 * 用 {@code restoreStock} 还回去。所以：<b>别拿有真实用户订单在跑的商品来试</b>；
 * 本地开发库无所谓。挑不到商品时整个测试<b>跳过</b>（不是失败）——
 * 新环境还没发过商品，不应该因为"没数据"把构建搞红。
 */
@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private ItemMapper itemMapper;

    /** 抢库存的线程数。要明显大于库存数，否则测不出"多出来的那批被挡住了"。 */
    private static final int THREADS = 100;

    @Test
    void 一百个线程抢同一件商品_成功次数必须等于库存() throws Exception {
        // 1. 挑一件在售、还有库存的商品
        //    ps：按 id 升序拿第一件，测试结果稳定；last("limit 1") 是 MyBatis-Plus 的逃生口
        List<Item> candidates = itemMapper.selectList(Wrappers.<Item>lambdaQuery()
                .eq(Item::getStatus, Item.STATUS_ON)
                .gt(Item::getStock, 0)
                .orderByAsc(Item::getId)
                .last("limit 1"));
        Item target = candidates.isEmpty() ? null : candidates.get(0);
        Assumptions.assumeTrue(target != null,
                "库里没有在售且库存 > 0 的商品，跳过（先调一次 POST /dev-api/item 发个商品）");
        assertNotNull(target.getStock());

        Long itemId = target.getId();
        int initialStock = target.getStock();
        int expectedSuccess = Math.min(initialStock, THREADS);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger exceptions = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        try {
            // 2. 起 100 个线程，全部卡在 startGate 上等同一个发令枪
            //    ps：不能"边起线程边抢" —— 那样先起的线程可能已经抢完了，根本没有并发
            for (int i = 0; i < THREADS; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        // 3. 每个线程只抢 1 件
                        //    ps：deductStock 返回影响行数：1 = 抢到，0 = 库存不足（不是异常）
                        int rows = itemMapper.deductStock(itemId, 1);
                        if (rows == 1) {
                            success.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                }, "stock-racer-" + i).start();
            }

            // 4. 放枪，最多等 30 秒
            startGate.countDown();
            boolean finished = done.await(30, TimeUnit.SECONDS);

            // 5. 三条硬断言
            //    ps：exceptions 必须是 0 —— 条件更新不该抛异常，抛了说明有别的坑（死锁、连接池打满）
            assertEquals(true, finished, "100 个线程 30 秒内没跑完，可能是数据库连接池被打满了");
            assertEquals(0, exceptions.get(), "抢库存过程中抛了异常，看控制台堆栈");
            assertEquals(expectedSuccess, success.get(),
                    "成功次数必须正好等于库存（少 = 少卖了，多 = 超卖）");

            // 6. 收尾核对：库里的 stock 应该正好减掉成功次数
            Item after = itemMapper.selectById(itemId);
            assertNotNull(after);
            assertEquals(initialStock - expectedSuccess, after.getStock().intValue(),
                    "库存应该正好减掉成功次数，不能是负数");
        } finally {
            // 7. 不管断言过没过，都把抢掉的库存还回去
            //    ps：restoreStock 是无条件加回去的（它不知道是谁扣的），所以只能靠"成功了多少还多少"
            if (success.get() > 0) {
                itemMapper.restoreStock(itemId, success.get());
            }
        }
    }
}
