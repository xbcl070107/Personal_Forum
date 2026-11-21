package com.xbcl.personal_forum.service;

import com.xbcl.personal_forum.common.RedisKeys;
import com.xbcl.personal_forum.pojo.vo.ItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品详情的缓存层（W7 骨架）。
 *
 * <p><b>为什么单独一个类，不直接写在 ItemService 里：</b>
 * <ul>
 *   <li>ItemService 已经 400 多行了，再塞缓存读写会过「一个 Service 不超过 400 行」的线；</li>
 *   <li>缓存是「怎么存」的问题，详情是「给谁看」的问题。混在一起之后，
 *       改 TTL 要读业务逻辑，改权限判断要碰 Redis 命令 —— 两种改动会互相踩。</li>
 * </ul>
 *
 * <p><b>这个类只做四件事：</b>读详情缓存、写详情缓存、删详情缓存、记浏览量。
 * 商品不存在要不要写空值、下架商品能不能进缓存，这些"策略"由调用方
 * （{@link ItemService#detail}）决定，这里只保证「按 key 存取失败时不会把请求打挂」。
 *
 * <p><b>所有方法都必须在 Redis 故障时安全降级：</b>缓存挂了论坛不能跟着挂。
 * 读失败 → 当成未命中，回源查库；写失败 → 记 warn，请求照常返回；
 * 浏览量失败 → 直接丢，不重试（它本来就不是精确值）。
 *
 * <h3>缓存里存什么、不存什么</h3>
 * <ul>
 *   <li><b>存</b>：整份 {@link ItemVO} 的 JSON（含 shopName、images、stock、viewCount）</li>
 *   <li><b>不存</b>：任何和"看的人"有关的东西（是否收藏、是否是我发的、能不能买）——
 *       同一个 key 存进去，第二个用户就会看到第一个用户的数据</li>
 *   <li><b>不存</b>：下架商品。下架后只有店主能看，缓存一份"谁都能读"的副本会越权</li>
 * </ul>
 *
 * <p><b>为什么用 StringRedisTemplate，不用 RedisTemplate：</b>
 * 后者要配序列化器，配成 JDK 序列化会在 Redis 里看到乱码、换语言读不了；
 * 配成 Jackson 又要处理泛型擦除（{@code List<ItemVO>} 读回来变成 List&lt;LinkedHashMap&gt;）。
 * 本项目只存单个对象的 JSON 字符串，自己调 {@link ObjectMapper} 序列化最直白：
 * 存进去的是什么，redis-cli 里看到的就是什么。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCacheService {

    /**
     * 只用来做字符串读写的模板（value 全是 String）。
     * Spring Boot 看到 spring-boot-starter-data-redis 就自动配好了，不用写 Config。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 和 ItemService 注入的是同一个 bean（Spring Boot 自动配的 Jackson 3）。
     * ⚠️ 包名是 tools.jackson，不是 com.fasterxml.jackson。
     */
    private final ObjectMapper objectMapper;

    /** 详情缓存的基准 TTL（秒）。见 application.yaml 的 forum.item.cache */
    @Value("${forum.item.cache.detail-ttl-seconds:120}")
    private long detailTtlSeconds;

    /** TTL 随机抖动上限（秒）：实际 TTL = 基准 + [0, jitter) */
    @Value("${forum.item.cache.detail-ttl-jitter-seconds:30}")
    private long detailTtlJitterSeconds;

    /** 空值缓存的 TTL（秒）。空值只是挡穿透，不用留太久 */
    @Value("${forum.item.cache.null-ttl-seconds:60}")
    private long nullTtlSeconds;

    /**
     * 缓存查询结果。三种状态必须能分开，否则调用方没法决定要不要查库：
     * <ul>
     *   <li>{@code hit=true, vo!=null} —— 命中正文，直接返回</li>
     *   <li>{@code hit=true, vo==null} —— 命中"这个 id 不存在"的标记（空值缓存），直接 404</li>
     *   <li>{@code hit=false} —— 没命中，调用方去查库</li>
     * </ul>
     *
     * <p>为什么用 record 不用两个 boolean 参数：两个 boolean 一定能拼出
     * "既命中又没值又没标空"这种非法组合，调用方每次都要多想一遍。
     */
    public record CacheHit(boolean hit, ItemVO vo) {

        public static CacheHit miss() {
            return new CacheHit(false, null);
        }

        public static CacheHit nullMarked() {
            return new CacheHit(true, null);
        }

        public static CacheHit of(ItemVO vo) {
            return new CacheHit(true, vo);
        }
    }

    /**
     * 读详情缓存。
     *
     * <p><b>被谁调用</b>：{@code ItemService.detail} 的第一行。
     *
     * <p><b>返回值</b>：三种情况见 {@link CacheHit}。两种情况都算"命中"，
     * 区别只在 vo 是不是 null。
     *
     * <p><b>失败策略</b>：Redis 连不上、JSON 解析失败，统统记 warn 并返回
     * {@code CacheHit.miss()} —— 让它退化成"每次都查库"，论坛还能用。
     *
     * <p><b>注意</b>：key 是两个，先读正文 {@code RedisKeys.itemDetail(itemId)}，
     * 读不到再读空值标记 {@code RedisKeys.itemDetailNull(itemId)}。
     * 顺序不能反：正文是常态，空值是少数，先读常态的能少一次往返。
     */
    public CacheHit getDetail(Long itemId) {
        if (itemId == null) {
            return CacheHit.miss();
        }
        try {
            // 1. 先读正文缓存：常态走这里，一次往返就够
            //    ps：get 返回 null 有两种情况 —— key 不存在，或者 key 存在但值是空字符串。
            //        本项目只会往里写 JSON，所以 null 一律当未命中
            String json = stringRedisTemplate.opsForValue().get(RedisKeys.itemDetail(itemId));
            if (json != null) {
                ItemVO vo = objectMapper.readValue(json, ItemVO.class);
                // ps：读到的是 "null" 时 readValue 不抛异常、直接返回 null，
                //     而 CacheHit.of(null) 和 nullMarked() 长得一模一样 ——
                //     不能让一条脏数据把在售商品判成 404，当未命中处理
                if (vo == null) {
                    log.warn("缓存里存的是 null，按未命中处理：itemId={}", itemId);
                    return CacheHit.miss();
                }
                return CacheHit.of(vo);
            }

            // 2. 正文没有，再读空值标记：这是少数情况，多一次往返无所谓
            String nullMark = stringRedisTemplate.opsForValue().get(RedisKeys.itemDetailNull(itemId));
            if (nullMark != null) {
                return CacheHit.nullMarked();
            }

            // 3. 两个都没有 → 调用方回源查库
            return CacheHit.miss();
        } catch (JacksonException e) {
            // 4. 内容坏了（改了 VO 字段、格式变了）：这条缓存永远好不了，
            //    顺手删掉让它自愈 —— 否则一直到 TTL 过期都是"命中率 0 但也不报错"
            log.warn("缓存内容解析失败，删除该 key：itemId={}", itemId, e);
            stringRedisTemplate.delete(RedisKeys.itemDetail(itemId));
            return CacheHit.miss();
        } catch (Exception e) {
            // 5. Redis 连不上 / 超时：降级查库，论坛不能跟着挂
            //    ps：这里什么都不删 —— 删也得连 Redis，只会再抛一次
            log.warn("读商品详情缓存失败，降级查库：itemId={}, err={}", itemId, e.getMessage());
            return CacheHit.miss();
        }
    }

    /**
     * 把一份详情写进缓存。
     *
     * <p><b>被谁调用</b>：{@code ItemService.detail} 回源查库成功、且商品<b>在售</b>之后。
     *
     * <p><b>写什么</b>：{@code objectMapper.writeValueAsString(vo)} 的结果，
     * TTL = {@code detailTtlSeconds + 随机 [0, detailTtlJitterSeconds)}。
     *
     * <p><b>为什么要加随机抖动：</b>如果所有商品都是 120 秒 TTL，一批商品
     * 可能在同一秒被预热、又在同一秒集体过期，Redis 那一下把流量全放回数据库（雪崩）。
     * 加个 0~30 秒的抖动，过期时间自然打散 —— 只改一行代码，不用引入任何框架。
     *
     * <p><b>失败策略</b>：只记 warn，不抛。缓存写不进去最多是下次再回源，不影响这次返回。
     */
    public void putDetail(ItemVO vo) {
        // 没有 id 就拼不出 key，相当于调用方用错了 —— 静默 return 会很难查
        if (vo == null || vo.getId() == null) {
            log.warn("putDetail 收到没有 id 的 VO，跳过写缓存");
            return;
        }
        try {
            // 基准 TTL + [0, jitter) 抖动，避免集体过期引发雪崩
            // ps：nextLong(0) 会抛 IllegalArgumentException，所以 jitter 为 0 时要绕开
            long jitter = detailTtlJitterSeconds <= 0
                    ? 0
                    : ThreadLocalRandom.current().nextLong(detailTtlJitterSeconds);
            Duration ttl = Duration.ofSeconds(detailTtlSeconds + jitter);

            String json = objectMapper.writeValueAsString(vo);
            stringRedisTemplate.opsForValue().set(RedisKeys.itemDetail(vo.getId()), json, ttl);
        } catch (Exception e) {
            // 写失败只影响下次回源，不影响本次返回
            log.warn("写商品详情缓存失败：itemId={}, err={}", vo.getId(), e.getMessage());
        }
    }

    /**
     * 写"这个商品 id 不存在"的空值标记（防缓存穿透）。
     *
     * <p><b>被谁调用</b>：{@code ItemService.detail} 查库返回 null 时。
     *
     * <p><b>为什么需要它：</b>有人拿 id=999999999 一直刷详情接口，
     * 缓存永远不命中、每次都要查数据库 —— 一个不存在的 id 能把库打满。
     * 把"查过了，没有"记 60 秒，同一批请求直接 404，库一点都不用碰。
     *
     * <p><b>⚠️ 只在"库里根本没有这条记录"时调</b>。下架商品不能写空值：
     * 店主打开自己的下架商品是能看到的，写个空值会让店主 60 秒内也 404。
     *
     * <p><b>失败策略</b>：只记 warn，不抛。
     */
    public void putNull(Long itemId) {
        if (itemId == null) {
            return;
        }
        try {
            // 值本身没有意义，存在性就是信息
            // ps：别写 JSON、别写时间戳 —— 越简单越不会解析失败
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.itemDetailNull(itemId), "1", Duration.ofSeconds(nullTtlSeconds));
        } catch (Exception e) {
            log.warn("写商品空值缓存失败：itemId={}, err={}", itemId, e.getMessage());
        }
    }

    /**
     * 删缓存（正文 + 空值标记一起删）。
     *
     * <p><b>被谁调用</b>：{@code ItemService.changeStatus} 改完上下架状态之后。
     * 下架必须立刻让缓存失效，否则集市上已经点不到这件商品，详情页却还能打开。
     *
     * <p><b>为什么两个 key 一起删：</b>先查不存在的 id 写了空值、之后这个 id 被占用
     * （AUTO_INCREMENT 不会回退，但数据迁移/手动插入有可能），
     * 只删正文会留下一个 60 秒的假 404。一起删最省心。
     *
     * <p><b>失败策略</b>：只记 warn，不抛。删不掉最坏是慢一个 TTL 才生效。
     */
    public void evict(Long itemId) {
        if (itemId == null) {
            return;
        }
        try {
            // 正文 + 空值标记一起删，避免残留假 404
            // ps：delete(Collection) 返回删掉的数量，这里不需要它；分两次 delete(String) 也行，
            //     但那就是两次网络往返，列表版本一次就发完
            stringRedisTemplate.delete(List.of(
                    RedisKeys.itemDetail(itemId),
                    RedisKeys.itemDetailNull(itemId)));
        } catch (Exception e) {
            log.warn("删商品详情缓存失败：itemId={}, err={}", itemId, e.getMessage());
        }
    }

    /**
     * 记一次浏览（Redis 里累加，不碰数据库）。
     *
     * <p><b>被谁调用</b>：{@code ItemService.detail} —— 只要不是 404（商品真的存在），
     * 缓存命中与否都要调。
     *
     * <p><b>怎么记</b>：Hash 结构 {@code RedisKeys.itemViewPending()}，
     * field = 商品 id 的字符串，value = 增量。用 {@code HINCRBY} 一条命令完成"读 + 加 + 写"，
     * 并发下不会丢（Redis 单线程执行命令）。
     *
     * <p><b>为什么不直接 UPDATE item SET view_count = view_count + 1：</b>
     * 那样每打开一次详情就是一次数据库写，商品详情页是全站最热的读接口，
     * 为了一个数字把所有读请求变成写请求，缓存的意义就少一半。
     *
     * <p><b>失败策略</b>：catch 住记 debug（不是 warn）—— 浏览量丢了就丢了，
     * 用 warn 会让 Redis 一挂就刷屏。
     */
    public void incrView(Long itemId) {
        if (itemId == null) {
            return;
        }
        try {
            // HINCRBY 一条命令完成读加写，Redis 单线程执行，并发安全
            // ps：Hash 的 field 必须是 String —— StringRedisTemplate 的序列化器
            //     不会帮你把 Long 转成字符串，直接传 Long 会 ClassCastException
            stringRedisTemplate.opsForHash().increment(
                    RedisKeys.itemViewPending(), String.valueOf(itemId), 1);
        } catch (Exception e) {
            // 浏览量丢了就丢了，用 debug 避免 Redis 挂了刷屏
            log.debug("记录浏览量失败：itemId={}, err={}", itemId, e.getMessage());
        }
    }

    /**
     * 取走所有"还没落库"的浏览量增量（由定时任务调用）。
     *
     * <p><b>被谁调用</b>：{@code task/ItemViewFlushTask}，每 30 秒一次。
     *
     * <p><b>返回</b>：{@code Map<商品id, 增量>}，没有增量时返回空 Map（不是 null）。
     *
     * <p><b>语义是"取走"不是"看一眼"</b>：返回的同时要删掉 Redis 里的 Hash，
     * 否则下一轮会把同一批数字再加一遍，浏览量越涨越离谱。
     * 删的时机在 HGETALL 之后、返回之前 —— 这中间新来的浏览会一起被删掉，
     * 最多丢一个窗口（毫秒级）的量，浏览量不值得为此加 Lua 脚本。
     *
     * <p><b>失败策略</b>：Redis 连不上就返回空 Map，定时任务空转一轮，
     * 下一轮 Redis 恢复了会把这期间攒的一起取走（数据没丢，只是晚一点落库）。
     */
    public Map<Long, Long> takeViews() {
        try {
            // 1. 一次把整张 Hash 拉回来
            //    ps：entries 的泛型是 Object，field 和 value 实际都是 String
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(RedisKeys.itemViewPending());
            if (raw == null || raw.isEmpty()) {
                // ps：空的时候不要 delete —— 删一个不存在的 key 也是一次白白往返
                return Collections.emptyMap();
            }

            // 2. 转成 Map<Long, Long>：一条脏数据只跳过它自己，不能拖累整批
            //    用 Order 保序只是为了日志好读，落库本来就不在乎顺序
            Map<Long, Long> result = new LinkedHashMap<>(raw.size());
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                try {
                    Long id = Long.parseLong(String.valueOf(e.getKey()).trim());
                    Long delta = Long.parseLong(String.valueOf(e.getValue()).trim());
                    // ps：同一个 id 理论上只出现一次，merge 是为了脏数据下也不丢增量
                    result.merge(id, delta, Long::sum);
                } catch (NumberFormatException ex) {
                    log.warn("浏览量计数里有脏数据，跳过：field={}, value={}", e.getKey(), e.getValue());
                }
            }

            // 3. 数据已经在手上了，才可以删 —— 顺序反了就是"读了个空、把数据删了"
            //    ps：这一瞬间新来了浏览会一起被删掉，最多丢一个窗口（毫秒级），
            //        浏览量不值得为此上 Lua
            stringRedisTemplate.delete(RedisKeys.itemViewPending());

            return result;
        } catch (Exception e) {
            // Redis 连不上：返回空 Map 让定时任务空转一轮，数据还在 Redis 里，下轮再取
            log.warn("取浏览量增量失败，本轮跳过：err={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 把落库失败的浏览量塞回 Redis（由 {@code ItemViewFlushTask} 调用）。
     *
     * <p><b>为什么需要它</b>：{@link #takeViews()} 是"取走"，返回值一给出去，
     * Redis 里那批计数就已经没了。如果落库时某几条 UPDATE 失败，这几条增量会彻底消失 ——
     * 这个方法负责把它们 HINCRBY 回去，等下一轮 flush 再试。
     *
     * <p><b>被谁调用</b>：只被 {@code ItemViewFlushTask.flush()} 的失败分支调用，别处没有使用场景。
     *
     * <p><b>前置条件</b>：传进来的必须是"已经 takeViews 取走、但落库失败"的那部分，
     * 别拿它当"增加浏览量"用（会增加的话是 {@link #incrView}）。
     *
     * <p><b>失败策略</b>：补回去也失败就只记 warn —— 这时候 Redis 本身出了问题，
     * 重试也是一样的结果，留给运维看日志。
     */
    public void addViewsBack(Map<Long, Long> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Long> e : deltas.entrySet()) {
            // 每条单独 try/catch，避免第一条失败把后续全丢
            try {
                stringRedisTemplate.opsForHash().increment(
                        RedisKeys.itemViewPending(), String.valueOf(e.getKey()), e.getValue());
            } catch (Exception ex) {
                log.warn("浏览量补回失败：itemId={}, delta={}", e.getKey(), e.getValue(), ex);
            }
        }
    }
}
