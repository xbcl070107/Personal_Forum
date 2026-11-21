package com.xbcl.personal_forum.task;

import com.xbcl.personal_forum.mapper.ItemMapper;
import com.xbcl.personal_forum.service.ItemCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 浏览量落库任务（W7）：把 Redis 里攒着的浏览数，批量写回 {@code item.view_count}。
 *
 * <p><b>为什么要攒</b>：详情页是全站最热的接口，每看一次就 {@code UPDATE item} 一次，
 * 直接把库按在地上摩擦 —— 而且这一列对准确性要求很低（"这件商品有 37 人看过"，
 * 差 3 个没人发现）。所以改成：看一次只在 Redis 里 {@code HINCRBY}（纯内存，O(1)），
 * 攒一阵子再一次性落库。
 *
 * <p><b>一轮做什么</b>
 * <ol>
 *   <li>{@link ItemCacheService#takeViews()} —— 拿走上一次 flush 之后攒下的
 *       {@code Map<商品id, 增量>}，<b>同时把 Redis 里那批计数清掉</b>（边拿边清是一次原子操作）</li>
 *   <li>逐条 {@code itemMapper.incrViewCount(id, delta)}，即
 *       {@code UPDATE item SET view_count = view_count + ? WHERE id = ? AND is_deleted = 0}</li>
 *   <li>打一行日志：这回落了几件商品、一共多少浏览</li>
 * </ol>
 *
 * <p><b>会丢数据吗</b>：会。Redis 挂了、或者进程在这两步中间被杀掉，最多丢上一次 flush 之后的增量。
 * 这是<b>主动接受</b>的取舍：浏览量不值得为了"一次都不丢"去上事务/消息队列。
 * 反过来说，<b>不要</b>拿 view_count 做任何计费、排名分成的依据。
 *
 * <p><b>为什么不用 @Transactional</b>：这个任务本来就是"逐条 UPDATE 累加"，
 * 每条自己就是一个原子语句（{@code view_count = view_count + ?} 不会丢更新），
 * 没有"要么全成功要么全失败"的业务含义 —— 落 8 条成功 2 条失败，下次那 2 条会再来。
 *
 * <p><b>失败怎么处理</b>：单个商品失败只 {@code log.warn} 跳过，不能因为一件商品
 * 的数据问题让整批数都落不下去（和 {@code OrderTimeoutTask} 的取舍一样）。
 * 注意：{@code takeViews()} 已经把 Redis 里那批清掉了，所以失败的那几条这次就真的丢了 ——
 * 为了让这个损失可控，失败时<b>把增量塞回 Redis</b>（见方法体 todo 第 4 步）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemViewFlushTask {

    private final ItemCacheService itemCacheService;
    private final ItemMapper itemMapper;

    /**
     * 两轮之间隔多久（fixedDelay：上一轮跑完再等这么久，不是每 30 秒跑一次）。
     *
     * <p>30 秒是个折中：再短就变成"每几秒写一次库"，等于没省；
     * 再长的话，关掉页面 10 分钟后再刷新，数字还是老的可能让人觉得"没记上"。
     */
    @Scheduled(fixedDelayString = "${forum.item.view.flush-interval-ms:30000}",
            initialDelayString = "${forum.item.view.flush-initial-delay-ms:60000}")
    public void flush() {
        // ps：开关默认 false。这个任务依赖 Redis，本机没装 Redis 时
        //     不应该每 30 秒刷一条连接失败日志
        if (!enabled) {
            return;
        }

        // 拿到的是"上一次 flush 之后的新增浏览数"，Redis 那边已经被清掉了
        Map<Long, Long> deltas = itemCacheService.takeViews();
        if (deltas == null || deltas.isEmpty()) {
            // 没有浏览就什么都不做，也不打日志
            return;
        }

        Map<Long, Long> failed = new LinkedHashMap<>();
        int okCount = 0;
        long totalDelta = 0;

        for (Map.Entry<Long, Long> e : deltas.entrySet()) {
            Long itemId = e.getKey();
            Long delta = e.getValue();
            try {
                int rows = itemMapper.incrViewCount(itemId, delta);
                if (rows == 0) {
                    // 商品被硬删了（WHERE 里有 is_deleted = 0），跳过即可，不算错
                    continue;
                }
                okCount++;
                totalDelta += delta;
            } catch (Exception ex) {
                // 单条失败不能整批停，记下来循环结束后补回 Redis
                log.warn("浏览量落库失败：itemId={}, delta={}", itemId, delta, ex);
                failed.put(itemId, delta);
            }
        }

        // ps：必须在 takeViews 之后单独补，不能指望下一轮"重新拿"—— 拿的时候就已经清了
        // ps：补回去也不是绝对可靠（补的时候 Redis 又挂了就没了），但已经比"直接丢"好
        if (!failed.isEmpty()) {
            itemCacheService.addViewsBack(failed);
        }

        // 每天几百条，不刷屏；没数据时上面已经 return 了，所以这行只在真落了东西时打
        if (okCount > 0) {
            log.info("浏览量落库：{} 件商品，共 {} 次浏览，失败 {} 件", okCount, totalDelta, failed.size());
        }
    }

    /**
     * 是否开启这个任务。
     *
     * <p>默认 {@code false}：本机没装 Redis 时，让它每 30 秒抛一次连接异常毫无意义。
     * 装好 Redis、配置连上、验收通过之后，在 {@code application.yaml} 里改成 {@code true}。
     */
    @Value("${forum.item.view.flush-enabled:false}")
    private boolean enabled;
}
