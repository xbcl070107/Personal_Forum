package com.xbcl.personal_forum.common;

/**
 * 全项目 Redis key 的唯一来源（W7）。
 *
 * <p><b>为什么单独一个类：</b>Redis 的 key 是"散落在各处、拼错了也不报错"的典型。
 * 一个地方写 {@code item:detail:7}，另一个地方写 {@code item:detail_7}，
 * 读的时候永远 miss、删的时候永远删不掉 —— 而且代码不报错，只能靠肉眼发现。
 * 所以所有 key 都从这个类拼出来，谁都不许手写字符串字面量。
 *
 * <p><b>命名规则：</b>{@code forum:业务:对象:标识}
 * <ul>
 *   <li>{@code forum:} —— 固定前缀。以后同一台 Redis 上跑别的项目（或本地开两个工程），
 *       不会互相覆盖；线上也可以用 {@code SCAN forum:item:detail:*} 只清自己的缓存。</li>
 *   <li>{@code item:detail:{id}} —— 商品详情缓存，值是 ItemVO 的 JSON</li>
 *   <li>{@code item:detail:null:{id}} —— 空值缓存（防穿透），值固定 "1"</li>
 *   <li>{@code item:view:pending} —— 一个 Hash，field = 商品 id，value = 还没落库的浏览增量</li>
 * </ul>
 *
 * <p><b>为什么全是 static 方法、不给 getter：</b>key 是"算出来的"，不是"存下来的"。
 * 写成一个常量类再加一堆 {@code public static final String ITEM_DETAIL = ...} 的话，
 * 带 id 的 key 就没法放进常量里，最后还是要在业务代码里拼字符串。
 *
 * <p><b>注意</b>：这个类不碰 Redis，只负责拼字符串。真正的读写都在
 * {@code service/ItemCacheService}，这样测试拼 key 的时候可以不起 Redis。
 */
public final class RedisKeys {

    /** 所有 key 的公共前缀。想换前缀只改这一处 */
    private static final String PREFIX = "forum:";

    /** 工具类，别 new */
    private RedisKeys() {
    }

    /**
     * 商品详情缓存的 key。
     *
     * @param itemId 商品 id
     * @return 形如 {@code forum:item:detail:7}
     */
    public static String itemDetail(long itemId) {
        return PREFIX + "item:detail:" + itemId;
    }

    /**
     * 商品"不存在"的空值标记 key（缓存穿透的第一道闸）。
     *
     * <p>单独一个 key 而不是往详情缓存里存 "NULL" 字符串：
     * 两者生命周期不同 —— 空值 60 秒就够了，详情正文可以缓存几分钟；
     * 混在一个 key 里，改 TTL 的时候必然有一个是错的。
     *
     * @param itemId 被查过但库里不存在的商品 id
     * @return 形如 {@code forum:item:detail:null:7}
     */
    public static String itemDetailNull(long itemId) {
        return PREFIX + "item:detail:null:" + itemId;
    }

    /**
     * 未落库的浏览量增量：Redis Hash，field = 商品 id，value = 增量。
     *
     * <p>为什么用"一个 Hash 装所有商品"而不是"每个商品一个 key"：
     * 落库时要遍历所有有增量的商品，一个 key 的话得先维护一个 id 集合（又多一个结构）；
     * Hash 的 {@code HGETALL} 一次就能拿全，而且商品 id 天然去重。
     *
     * @return 固定值 {@code forum:item:view:pending}
     */
    public static String itemViewPending() {
        return PREFIX + "item:view:pending";
    }
}
