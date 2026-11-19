-- ============================================================
-- 商城 P1：6 张表 + 给 category 加 scope
-- 对应《社区电商可行性分析》3.2.1 总览 / 3.2.2 逐表字段
--
-- 为什么单独一个文件（和 v2 / v3 一样）：
--   init.sql 开头是 DROP TABLE，重跑会把账号、帖子、评论全清掉。
--   这个文件只建新表、只给 category 加一列，不碰已有数据。
--
-- ⚠️ 和 v2 / v3 的区别：这个脚本只跑一次。
--   新表写的是裸 CREATE TABLE（不是 IF NOT EXISTS），
--   第二遍执行会报 "Table 'shop' already exists"。
--   故意这样：迁移脚本是「一次性事实记录」，
--   真写错了就 DROP 这 6 张表重来，而不是让一个能反复执行的脚本
--   悄悄跳过没建成的表。
--
-- 六张表分别是（P1 全部数据层）：
--   shop       一个卖家开的一家店（一人一店）
--   item       店里的一件商品（价格 + 库存）
--   address    一个用户的收货地址
--   orders     一笔订单（一个买家 + 一家店 + 一个状态）
--   order_item 订单里买了什么（价格/标题快照）
--   stock_log  库存流水（回补的幂等闸门）
--
-- 统一约定（和 init.sql 一致）：
--   InnoDB / utf8mb4_unicode_ci / 主键 BIGINT 自增 / 金额 BIGINT 存「分」
--   时间用 DATETIME 不用 TIMESTAMP / **不加外键**，只加索引
-- ============================================================

USE `forum`;

-- ---------- ① shop 店铺 ----------
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

-- ---------- ② item 商品 ----------
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

-- ---------- ③ address 收货地址 ----------
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

-- ---------- ④ orders 订单 ----------
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

-- ---------- ⑤ order_item 订单明细 ----------
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

-- ---------- ⑥ stock_log 库存流水 ----------
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

-- ---------- ⑦ 现有表改造：category 加 scope ----------
-- 不新建「商品分类」表，复用 category：scope='POST' 是帖子板块，
-- scope='ITEM' 是商品分类。加了 DEFAULT 'POST'，存量 3 个板块自动归位，
-- 不用回填 UPDATE。
ALTER TABLE category
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'POST'
        COMMENT 'POST 帖子板块 / ITEM 商品分类' AFTER sort_order;

-- 商品分类种子数据（集市列表的分类 tab 用）。
-- 名字故意和帖子板块不重复，避免以后合并分类时撞名。
INSERT INTO category (name, sort_order, scope)
VALUES ('数码', 1, 'ITEM'),
       ('图书', 2, 'ITEM'),
       ('服饰', 3, 'ITEM'),
       ('生活', 4, 'ITEM'),
       ('虚拟', 5, 'ITEM');

-- ---------- 跑完自查 ----------
SHOW TABLES;
-- 期望多出 shop / item / address / orders / order_item / stock_log 六张

SELECT id, name, sort_order, scope FROM category ORDER BY scope, sort_order;
-- 期望：原三个板块 scope 是 POST，新五个是 ITEM

DESC item;
-- 期望 price_cents 是 bigint、stock 是 int unsigned
