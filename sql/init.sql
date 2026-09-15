-- ============================================================
-- 冒险者协会 · 建库建表脚本
-- 对应《个人论坛《冒险者协会》-可行性分析》3.2 数据模型
--
-- 怎么用：在 DataGrip / IDEA 的 Database 工具里打开这个文件，整个文件执行一遍。
-- 可以反复执行（先 DROP 再 CREATE）—— 但注意 ⚠️ 它会把旧数据清掉。
-- ============================================================

-- ---------- 建库 ----------
-- utf8mb4 才能存中文和 emoji。IF NOT EXISTS 保证重复执行不报错。
CREATE DATABASE IF NOT EXISTS `forum`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `forum`;

-- ---------- 清空旧表 ----------
-- ⚠️ 这一段会删掉已有数据。第一次跑无所谓，之后每次跑都相当于重置。
DROP TABLE IF EXISTS post_favorite;
DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

-- ---------- 1. user 用户 ----------
CREATE TABLE `user` (
    id            BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    username      VARCHAR(32)  NOT NULL                          COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL                          COMMENT 'BCrypt 哈希后的密码，不存明文',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER'           COMMENT 'ADMIN / USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- ---------- 2. category 板块 ----------
CREATE TABLE category (
    id         BIGINT      NOT NULL AUTO_INCREMENT           COMMENT '主键',
    name       VARCHAR(32) NOT NULL                          COMMENT '板块名',
    sort_order INT         NOT NULL DEFAULT 0                COMMENT '排序，数值小的排前面',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='板块';

-- ---------- 3. post 帖子 ----------
CREATE TABLE post (
    id          BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    user_id     BIGINT       NOT NULL                          COMMENT '作者',
    title       VARCHAR(100) NOT NULL                          COMMENT '标题',
    content     TEXT         NOT NULL                          COMMENT '正文',
    category_id BIGINT       NOT NULL                          COMMENT '所属板块',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0                COMMENT '0 正常 / 1 已删（软删除）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_post_user_id (user_id),
    KEY idx_post_created_at (created_at),
    KEY idx_post_category_id (category_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子';

-- ---------- 4. comment 回复 ----------
CREATE TABLE comment (
    id         BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    post_id    BIGINT       NOT NULL                          COMMENT '所属帖子',
    user_id    BIGINT       NOT NULL                          COMMENT '回复人',
    content    VARCHAR(500) NOT NULL                          COMMENT '回复内容',
    is_deleted TINYINT(1)   NOT NULL DEFAULT 0                COMMENT '0 正常 / 1 已删',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回复时间',
    PRIMARY KEY (id),
    KEY idx_comment_post_id (post_id),
    KEY idx_comment_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='回复';

-- ---------- 5. post_like 点赞 ----------
-- 「谁赞了哪个帖」是一件事，不是一样东西，所以它有自己的表。
CREATE TABLE post_like (
    id         BIGINT   NOT NULL AUTO_INCREMENT           COMMENT '主键',
    user_id    BIGINT   NOT NULL                          COMMENT '谁',
    post_id    BIGINT   NOT NULL                          COMMENT '赞了哪个帖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    -- ⚠️ 这一行是整张表里最值钱的：一人一帖只能有一条，
    -- 重复点赞会被数据库直接顶回去，不靠 Java 代码「先查再插」。
    UNIQUE KEY uk_like_user_post (user_id, post_id),
    KEY idx_like_post_id (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='点赞';

-- ---------- 6. post_favorite 收藏 ----------
CREATE TABLE post_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT           COMMENT '主键',
    user_id    BIGINT   NOT NULL                          COMMENT '谁',
    post_id    BIGINT   NOT NULL                          COMMENT '收藏了哪个帖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fav_user_post (user_id, post_id),
    KEY idx_fav_post_id (post_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='收藏';

-- ---------- 预置数据 ----------
-- 板块不做在线增删改（见可行性分析 1.3），所以直接写死在这里。
INSERT INTO category (name, sort_order)
VALUES ('日常', 1),
       ('技术', 2),
       ('水贴', 3);

-- ---------- 跑完自查 ----------
-- 下面两句是给你自己看的，执行一下确认结果对。
SHOW TABLES;
-- 期望 6 行：category / comment / post / post_favorite / post_like / user
SELECT * FROM category;
-- 期望 3 行：日常 / 技术 / 水贴
