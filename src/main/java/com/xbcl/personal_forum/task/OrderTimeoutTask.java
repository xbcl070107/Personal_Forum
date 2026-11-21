package com.xbcl.personal_forum.task;

import com.xbcl.personal_forum.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时关单定时任务（W6）。
 *
 * <p>它只做一件很窄的事：隔一段时间看一眼「有没有过点了还没付款的单」，
 * 有就交给 OrderService 一单一单地关。业务规则不在这里，这里只负责「什么时候调」。
 *
 * <p><b>三件要说清楚的事：</b>
 * <ol>
 *   <li><b>为什么不把这个方法写在 OrderService 里。</b>两个原因：
 *       ① 日志上分得清是谁在跑 —— 定时任务的异常和 HTTP 请求的异常完全不是一回事；
 *       ② <b>更重要的</b>：关单要「一单一个事务」，而循环必须写在<b>另一个 Bean</b> 里调
 *       OrderService.closeTimeoutOrder —— 如果循环写在 OrderService 内部，
 *       那就是同类自调用，不走 Spring 代理，@Transactional 等于没写（回滚失效）。</li>
 *   <li><b>为什么用 fixedDelay 而不是 fixedRate。</b>fixedDelay = 上一轮<b>结束</b>后再等 N 毫秒；
 *       fixedRate = 每 N 毫秒<b>必须开始</b>一轮。这一轮扫 200 单花了 3 秒，
 *       fixedRate 会把下一轮标记成「已经迟到了」，连着补跑，越积越多；
 *       fixedDelay 永远不会堆积。定时任务绝大多数时候都该用 fixedDelay。</li>
 *   <li><b>为什么骨架期是关着的。</b>OrderService.closeTimeoutOrder 现在还是抛 501 的骨架，
 *       开着的话每分钟都会在控制台刷一条错误日志，淹掉你自己在调的接口。
 *       实现完把 application.yaml 里的 forum.order.timeout-scan-enabled 改成 true。</li>
 * </ol>
 *
 * <p>三个配置项（application.yaml 的 forum.order 下，都有默认值，不配也能启动）：
 * <ul>
 *   <li>timeout-scan-interval-ms —— 两轮之间隔多久，默认 60000（1 分钟）</li>
 *   <li>timeout-batch-size —— 一轮最多关多少单，默认 200</li>
 *   <li>timeout-scan-enabled —— 总开关，默认 false</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderService orderService;

    /** 一轮最多处理多少单。太小关得慢（积压），太大单轮占用连接时间长 */
    @Value("${forum.order.timeout-batch-size:200}")
    private int batchSize;

    /** 总开关，见类注释第 3 条 */
    @Value("${forum.order.timeout-scan-enabled:false}")
    private boolean enabled;

    /**
     * 扫一轮：先只查 id（不占事务），再逐单调用 closeTimeoutOrder（每单一个事务）。
     *
     * <p><b>为什么逐单、而不是一个事务关 200 单：</b>
     * <ul>
     *   <li>200 单里只要有一单因为诡异数据抛异常，整批回滚 —— 下一轮又从第一单开始，
     *       遇到同一单又回滚，<b>整个关单功能永远卡死</b>。逐单的话坏单只卡它自己。</li>
     *   <li>一个事务里扫表 + 200 次 UPDATE，锁和数据库连接被占用的时间会被放大 200 倍。</li>
     * </ul>
     *
     * <p><b>为什么整个方法包一层 try/catch：</b>定时任务跑在调度线程里，
     * 异常抛出去不会变成谁的 500，只是被 Spring 记一条错误日志，然后下一轮照跑 ——
     * 效果和我们自己 catch 差不多，但日志噪音大、还看不出是哪一轮。
     * 这里 catch 住记 warn，顺带把异常一起带上，排查时能直接看到堆栈。
     * 真正会让它连续失败的是数据库连不上，那属于运维要处理的事。
     */
    @Scheduled(fixedDelayString = "${forum.order.timeout-scan-interval-ms:60000}")
    public void scan() {
        if (!enabled) {
            return;
        }
        try {
            List<Long> ids = orderService.findTimeoutOrderIds(batchSize);
            if (ids.isEmpty()) {
                return;   // 大部分轮次都是这里：空扫，连日志都不用打
            }

            int closed = 0;
            for (Long id : ids) {
                // 逐单事务：false 表示这单已经不用关了（别人先关 / 用户刚好付了），跳过即可
                if (orderService.closeTimeoutOrder(id)) {
                    closed++;
                }
            }
            log.info("超时关单：扫到 {} 单，实际关闭 {} 单", ids.size(), closed);
        } catch (Exception e) {
            // 这一轮整体失败（数据库超时之类）。下一轮 fixedDelay 会照常重跑，不用手动补
            log.warn("超时关单这一轮失败，下一轮再试：{}", e.getMessage(), e);
        }
    }
}
