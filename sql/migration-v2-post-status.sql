-- ============================================================
-- 帖子审核：给 post 表加 status 列
-- 对应《可行性分析》3.2 数据模型里 post 表的改动
--
-- 为什么单独写一个文件，而不是重跑 init.sql：
--   init.sql 第 19~24 行是 DROP TABLE，重跑会把你现有的账号、
--   已发的帖子全部清掉。这个文件只加列，不动任何已有数据。
--   以后改表结构都照这个方式来。
-- ============================================================

USE `forum`;

-- ---------- ① 加列 ----------
-- DEFAULT 0 = 新帖默认「待审」，数据库这一层就挡住了，
-- 就算哪天代码忘了 setStatus，插进去的也是待审而不是直接发布。
ALTER TABLE post
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '0 待审 / 1 已通过 / 2 已驳回' AFTER category_id;

-- ---------- ② 加索引 ----------
-- 以后的列表查询是「先按 status 筛，再按 created_at 排序」，
-- 所以建 (status, created_at) 联合索引，不是单给 status 建。
-- 单列 status 区分度太低（只有三个值），MySQL 基本不会用它。
ALTER TABLE post
    ADD KEY idx_post_status_created (status, created_at);

-- ---------- ③ 存量数据 ----------
-- ⚠️ 这一句不能少。
-- 加列的那一瞬间，表里已有的帖子 status 全是默认值 0，也就是「待审」。
-- 不更新的话，之前发过的帖会从列表里凭空消失，而你会以为是查询写错了。
-- 它们是在有审核功能之前发的，视同已经通过。
UPDATE post SET status = 1;

-- ---------- 跑完自查 ----------
DESC post;
-- 期望能看到 status 这一列，Default 是 0

SELECT id, title, status FROM post;
-- 期望已有帖子的 status 是 1
