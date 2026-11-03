-- ============================================================
-- 昵称：给 user 表加 nickname 列
--
-- 【为什么要分 username 和 nickname 两个名字】
--   username —— 登录用的凭证，必须唯一（表上有 uk_user_username）
--   nickname —— 给别人看的名，可以不唯一
--
--   把这两件事塞进一个字段，就会卡在「我想叫『提灯人』，但已经有人叫了」
--   这种死路上：要么让用户改自己想要的展示名，要么让两个同名的人互相抢注。
--   分开之后，登录凭证保持唯一不变，展示名想叫什么就叫什么。
--
-- 【为什么不重跑 init.sql】
--   init.sql 里有 DROP TABLE，重跑会清掉所有账号、帖子和评论。
--   这个文件只加列 + 回填，不动任何已有数据。
-- ============================================================

USE `forum`;

-- ---------- ① 先加一个允许 NULL 的列 ----------
-- 分三步而不是一步到位，是因为「NOT NULL 且没有默认值」的列加到一个已有数据的表上，
-- MySQL 会拿隐式默认值（空字符串）去填每一行 —— 结果是一批空昵称，
-- 而且它不一定报错（取决于 sql_mode）。先允许 NULL，回填完之后再收紧，
-- 每一步的结果都是确定的。
ALTER TABLE `user`
    ADD COLUMN nickname VARCHAR(32) NULL
        COMMENT '昵称，展示用，允许重复' AFTER username;

-- ---------- ② 回填存量用户 ----------
-- 先让已有的每个人「昵称 = 用户名」，这样他们立刻有名字可显示，
-- 不会出现一片空白。想改昵称是之后的事。
UPDATE `user` SET nickname = username WHERE nickname IS NULL;

-- 单独给这个账号一个昵称。用户名保持 xbcl 不变（登录还是用它）。
UPDATE `user` SET nickname = '禤布出来' WHERE username = 'xbcl';

-- ---------- ③ 收紧成 NOT NULL ----------
-- ⚠️ 这里故意**不给 DEFAULT**。
-- 留一个默认空串的话，哪天代码忘了 setNickname，数据库会安安静静地
-- 插进去一个空昵称，页面上就是一片空白 —— 又要回头查半天。
-- 不给默认值，忘了设置就直接报错，错在写入那一刻而不是显示那一刻。
ALTER TABLE `user`
    MODIFY COLUMN nickname VARCHAR(32) NOT NULL
        COMMENT '昵称，展示用，允许重复';

-- ---------- ④ 不加唯一键 ----------
-- 这里特意什么都不做，写下来免得以后有人「顺手」补一个：
--   昵称允许重复是需求，不是漏了。
--   唯一性由 username 负责，两者职责不要混。

-- ---------- 跑完自查 ----------
DESC `user`;
-- 期望看到 nickname 列，Null 是 NO，Key 是空的（没有唯一键）

SELECT id, username, nickname, role FROM `user` ORDER BY id;
-- 期望：每个人的 nickname 都有值；xbcl 那行是「禤布出来」
