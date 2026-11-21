-- ============================================================
-- 冒险者协会 · 建库建表脚本（完整版）
-- 对应《个人论坛《冒险者协会》-可行性分析》3.2 数据模型
--
-- 这个文件已经合并了原来分开的四个迁移脚本：
--   migration-v2-post-status.sql   帖子审核 status
--   migration-v3-nickname.sql      用户昵称 nickname
--   migration-v4-shop.sql          商城 6 张表 + 板块 scope
--   migration-v4b-item-view.sql    商品浏览量 view_count
--
-- 新环境只需要执行这一份。四个 migration 文件仍留在 sql/ 目录里，
-- 不删 —— 它们记录了「哪一版加了什么」，出问题时能对得上历史；
-- 但不要再执行它们，否则会重复加列/重复建表。
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
-- 先删「子表」再删「父表」：mysql 里没有外键，顺序其实无所谓，
-- 但按依赖顺序写，以后真加外键时这段不用改。
DROP TABLE IF EXISTS stock_log;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS address;
DROP TABLE IF EXISTS item;
DROP TABLE IF EXISTS shop;
DROP TABLE IF EXISTS post_favorite;
DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

-- ---------- 1. user 用户 ----------
CREATE TABLE `user` (
    id            BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    username      VARCHAR(32)  NOT NULL                          COMMENT '登录名，唯一',
    -- 昵称：展示用。故意**不建唯一键** —— 允许重名是需求，不是漏了。
    -- 唯一性由上面的 username 负责，两者职责不要混。
    -- 这里也故意不给 DEFAULT：忘了 setNickname 时让数据库直接报错，
    -- 而不是安安静静插一个空昵称，等到页面上才看出来。
    nickname      VARCHAR(32)  NOT NULL                          COMMENT '昵称，展示用，允许重复',
    password_hash VARCHAR(100) NOT NULL                          COMMENT 'BCrypt 哈希后的密码，不存明文',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER'           COMMENT 'ADMIN / USER',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- ---------- 2. category 板块 / 商品分类 ----------
-- 帖子和商品共用一张分类表，靠 scope 区分：
--   POST —— 帖子板块（日常 / 技术 / 水贴）
--   ITEM —— 商品分类（数码 / 图书 / …）
-- 不拆两张表是因为两边的字段完全一样，拆开只会让「分类列表」多一个接口。
CREATE TABLE category (
    id         BIGINT      NOT NULL AUTO_INCREMENT           COMMENT '主键',
    name       VARCHAR(32) NOT NULL                          COMMENT '板块名',
    sort_order INT         NOT NULL DEFAULT 0                COMMENT '排序，数值小的排前面',
    scope      VARCHAR(16) NOT NULL DEFAULT 'POST'           COMMENT 'POST 帖子板块 / ITEM 商品分类',
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
    -- 审核状态：先审后发。只有 status = 1 的帖子才会出现在列表和详情里。
    -- DEFAULT 0 是双保险 —— 哪天代码忘了 setStatus，插进去的也是待审，不是直接发布。
    status      TINYINT      NOT NULL DEFAULT 0                COMMENT '0 待审 / 1 已通过 / 2 已驳回',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0                COMMENT '0 正常 / 1 已删（软删除）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_post_user_id (user_id),
    KEY idx_post_created_at (created_at),
    KEY idx_post_category_id (category_id),
    -- 列表查询是「按 status 筛 + 按 created_at 排」，所以是联合索引。
    -- 单给 status 建没用：它只有三个值，区分度太低，MySQL 不会走。
    KEY idx_post_status_created (status, created_at)
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

-- ============================================================
-- 以下是商城部分（原 migration-v4-shop.sql）。
-- 统一约定和上面一样：InnoDB / utf8mb4_unicode_ci / 主键 BIGINT 自增 /
-- 金额 BIGINT 存「分」/ 时间 DATETIME / **不加外键**，只加索引。
-- ============================================================

-- ---------- 7. shop 店铺 ----------
-- 一行 = 一个卖家开的店。uk_owner 保证一人一店。
-- 店不硬删，只把 status 改成 0（关闭）—— 这样 uk_owner 永远不会和
-- 逻辑删除打架（把店软删了、再想重开，唯一键会挡住 INSERT）。
CREATE TABLE shop (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id  BIGINT            NOT NULL COMMENT '店主 user.id',
    name           VARCHAR(32)       NOT NULL COMMENT '店名',
    avatar_url     VARCHAR(255)      NULL     COMMENT '店铺头像 URL',
    description    VARCHAR(255)      NULL     COMMENT '店铺简介',
    type           TINYINT           NOT NULL DEFAULT 1 COMMENT '1 个人店 / 2 公会店（v2 只标记不做权限）',
    status         TINYINT           NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 关闭',
    follower_count INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '关注数（占位，v2 不做关注）',
    created_at     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT           NOT NULL DEFAULT 0,
    UNIQUE KEY uk_owner (owner_user_id),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='店铺';

-- ---------- 8. item 商品 ----------
-- stock 是**唯一权威库存**，秒杀也以它为准；Redis 里的只是镜像。
-- 售罄 = stock 0，不新增「已售罄」状态 —— 状态越少，状态机越不容易错。
-- images_json 存 JSON 数组字符串（最多 9 个 URL），不单开一张图片表：
-- 商品图只跟商品走、没有独立的查询需求，拆表只会多一次 join。
CREATE TABLE item (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id      BIGINT            NOT NULL COMMENT '所属店铺 shop.id',
    category_id  BIGINT            NULL     COMMENT '商品分类，指向 category(scope=ITEM)',
    title        VARCHAR(80)       NOT NULL COMMENT '标题',
    description  TEXT              NULL     COMMENT '详情，纯文本（不做富文本）',
    price_cents  BIGINT            NOT NULL COMMENT '单价（分）',
    stock        INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '库存（权威）',
    sold_count   INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '已售，只有支付成功才 +1',
    -- view_count 和 stock 的区别：
    --   stock      —— 权威值，写库即生效，下单靠它做条件更新
    --   view_count —— 统计值，Redis 里先攒，定时任务每 30 秒累加进库，
    --                 允许滞后、允许丢一点点（崩溃时最多丢一个落库周期）。
    view_count   INT UNSIGNED      NOT NULL DEFAULT 0 COMMENT '浏览量，Redis 定时落库，允许滞后',
    images_json  TEXT              NULL     COMMENT '图片 URL 的 JSON 数组，最多 9 个',
    status       TINYINT           NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
    created_at   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT           NOT NULL DEFAULT 0,
    KEY idx_shop_status (shop_id, status, id),
    KEY idx_shop_time   (shop_id, created_at),
    KEY idx_price       (price_cents),
    KEY idx_category    (category_id, status, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商品';

-- ---------- 9. address 收货地址 ----------
-- is_default 不建唯一键：软删 + 唯一键会打架。
-- 设默认时用事务「先把该用户全部置 0，再置 1」。
CREATE TABLE address (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '所属用户',
    receiver_name VARCHAR(32)  NOT NULL COMMENT '收货人',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
    region        VARCHAR(64)  NOT NULL COMMENT '省市区',
    detail        VARCHAR(128) NOT NULL COMMENT '详细地址',
    is_default    TINYINT      NOT NULL DEFAULT 0 COMMENT '1 默认地址',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    KEY idx_user (user_id, is_deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='收货地址';

-- ---------- 10. orders 订单 ----------
-- 一个买家 + 一家店 = 一笔订单（跨店要拆单，v2 不做购物车）。
-- 三个唯一键各管一件事：
--   uk_order_no      订单号唯一
--   uk_client_token  前端重复提交幂等（NULL 不冲突，所以没传就不管）
--   uk_user_coupon   一张券只能用一次（D 阶段才会用到，先建好）
-- 收货信息是**下单时快照**，之后用户改地址不影响已有订单。
CREATE TABLE orders (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单号 OD+日期+补零id',
    user_id          BIGINT       NOT NULL COMMENT '买家',
    shop_id          BIGINT       NOT NULL COMMENT '卖家店铺（一单只含一家店）',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待付款 / 1 已付款 / 2 已发货 / 3 已完成 / 4 已取消',
    total_cents      BIGINT       NOT NULL COMMENT '商品总额（分）',
    discount_cents   BIGINT       NOT NULL DEFAULT 0 COMMENT '优惠（分）',
    pay_cents        BIGINT       NOT NULL COMMENT '实付（分），服务端算，不信前端',
    user_coupon_id   BIGINT       NULL     COMMENT '用了哪张用户券',
    client_token     VARCHAR(64)  NULL     COMMENT '前端幂等令牌 UUID',
    item_count       INT          NOT NULL DEFAULT 1 COMMENT '商品件数合计',
    receiver_name    VARCHAR(32)  NOT NULL,
    receiver_phone   VARCHAR(20)  NOT NULL,
    receiver_region  VARCHAR(64)  NOT NULL,
    receiver_detail  VARCHAR(128) NOT NULL,
    tracking_no      VARCHAR(64)  NULL     COMMENT '卖家填的快递单号',
    shipping_company VARCHAR(32)  NULL     COMMENT '快递公司',
    expire_at        DATETIME     NOT NULL COMMENT '待付款截止（下单 + 15 分钟）',
    paid_at          DATETIME     NULL,
    shipped_at       DATETIME     NULL,
    finished_at      DATETIME     NULL,
    closed_at        DATETIME     NULL,
    close_reason     VARCHAR(32)  NULL     COMMENT 'TIMEOUT / BUYER_CANCEL / SELLER_REJECT',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_client_token (user_id, client_token),
    UNIQUE KEY uk_user_coupon (user_coupon_id),
    KEY idx_user_status (user_id, status, id),
    KEY idx_shop_status (shop_id, status, id),
    KEY idx_status_expire (status, expire_at) COMMENT '超时关单扫表专用'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='订单';

-- ---------- 11. order_item 订单明细 ----------
-- 标题 / 封面 / 单价都是**快照**：商品之后改价、改标题、被删，
-- 订单里看到的历史不能跟着变。
-- P1 一单只有一件商品，但表还是按多行的结构建（拆单、多商品是迟早的事）。
CREATE TABLE order_item (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    item_id        BIGINT       NOT NULL,
    item_title     VARCHAR(80)  NOT NULL COMMENT '下单价快照',
    item_cover_url VARCHAR(255) NULL     COMMENT '下单封面快照',
    price_cents    BIGINT       NOT NULL COMMENT '下单价（分）',
    quantity       INT          NOT NULL DEFAULT 1,
    subtotal_cents BIGINT       NOT NULL COMMENT '小计（分）',
    discount_cents BIGINT       NOT NULL DEFAULT 0 COMMENT '券分摊到本行（分）',
    pay_cents      BIGINT       NOT NULL COMMENT '本行实付（分）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     TINYINT      NOT NULL DEFAULT 0,
    KEY idx_order (order_id),
    KEY idx_item  (item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='订单明细';

-- ---------- 12. stock_log 库存流水 ----------
-- 这张表不是日志，是**幂等闸门**：
-- 取消订单要回补库存，而取消 / 超时关单 / 卖家拒单可能都走到同一笔订单，
-- 靠 uk_order_item_reason 保证「同一订单同一原因只回补一次」。
-- 重复回补的后果是库存凭空变多 —— 不报错，查不出来。
CREATE TABLE stock_log (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT      NOT NULL,
    item_id      BIGINT      NOT NULL,
    delta        INT         NOT NULL COMMENT '负数=扣减，正数=回补',
    before_stock INT         NOT NULL COMMENT '变化前库存',
    after_stock  INT         NOT NULL COMMENT '变化后库存',
    reason       VARCHAR(32) NOT NULL COMMENT 'ORDER / CANCEL / TIMEOUT / SECKILL_FAIL',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_item_reason (order_id, item_id, reason)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='库存流水';

-- ---------- 预置数据 ----------
-- 板块不做在线增删改（见可行性分析 1.3），所以直接写死在这里。
INSERT INTO category (name, sort_order, scope)
VALUES ('日常', 1, 'POST'),
       ('技术', 2, 'POST'),
       ('水贴', 3, 'POST');

-- 商品分类（集市列表的分类 tab 用）。
-- 名字故意和帖子板块不重复，避免以后合并分类时撞名。
INSERT INTO category (name, sort_order, scope)
VALUES ('数码', 1, 'ITEM'),
       ('图书', 2, 'ITEM'),
       ('服饰', 3, 'ITEM'),
       ('生活', 4, 'ITEM'),
       ('虚拟', 5, 'ITEM');

-- ---------- 跑完自查 ----------
-- 下面几句是给你自己看的，执行一下确认结果对。
SHOW TABLES;
-- 期望 12 行：address / category / comment / item / order_item / orders /
--             post / post_favorite / post_like / shop / stock_log / user

SELECT id, name, sort_order, scope FROM category ORDER BY scope, sort_order;
-- 期望 8 行：POST 3 个（日常/技术/水贴）+ ITEM 5 个（数码/图书/服饰/生活/虚拟）

DESC item;
-- 期望 price_cents 是 bigint、stock 是 int unsigned，
--      并且 sold_count 后面有 view_count（default 0，不是 NULL）
