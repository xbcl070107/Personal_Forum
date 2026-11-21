-- ============================================================
-- migration-v4b-item-view.sql  ——  W7 商品浏览量字段
--
-- ⚠️ 为什么叫 v4b 不叫 v5：
--    18 周计划里 v5 已经预留给「私聊」（v5-chat.sql），v6 优惠券、v7 秒杀。
--    W7 不是新阶段，只是给 P1 的 item 表补一列，所以挂在 v4 后面。
--    如果以后脚本编号对不上，以 docs/W7-商品详情缓存与并发-骨架与验收.md 为准。
--
-- 用法：mysql -u root -p forum < sql/migration-v4b-item-view.sql
-- ============================================================

-- view_count 和 stock 的区别：
--   stock      —— 权威值，写库即生效，下单靠它做条件更新
--   view_count —— 统计值，Redis 里先攒（HINCRBY），定时任务每 30 秒累加进库，
--                 允许滞后、允许丢一点点（服务崩溃时最多丢一个落库周期）。
--                 所以它放在 sold_count 后面，语义上属于「统计数据」那一组。
ALTER TABLE item
    ADD COLUMN view_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览量，Redis 定时落库，允许滞后'
        AFTER sold_count;

-- ---------- 跑完自查 ----------
DESC item;
-- 期望：sold_count 后面多出 view_count，类型 int unsigned，默认 0

SELECT id, title, stock, sold_count, view_count FROM item ORDER BY id LIMIT 10;
-- 期望：老商品 view_count 全是 0，不是 NULL
