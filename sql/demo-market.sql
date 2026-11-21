-- 集市演示数据：6 家店 + 19 件商品（配图在 uploads/images/2026-11/market/）
-- 只执行一次；图片 URL 带 /dev-api 前缀，由后端的 /uploads/** 静态映射提供。
USE forum;
START TRANSACTION;

INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (8, '铜须的铁砧', '/dev-api/uploads/images/2026-11/market/ember-short-sword.jpg', '老铜须留下的铁砧，如今由他女儿看着火。接修补，也卖几件打好的家伙。', 1, 1, 128, '2026-11-05 09:00:00', '2026-11-05 09:00:00', 0);
INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (6, '月见草药剂室', '/dev-api/uploads/images/2026-11/market/moonheal-potion.jpg', '只在月圆前后开门。瓶子上的标签歪歪扭扭，但从来没喝坏过谁。', 1, 1, 96, '2026-11-05 10:30:00', '2026-11-05 10:30:00', 0);
INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (7, '渡鸦书摊', '/dev-api/uploads/images/2026-11/market/north-road-map.jpg', '摊主白天睡觉，书自己会翻页。地图、旧志、没人读得懂的抄本，论斤卖。', 2, 1, 214, '2026-11-06 14:00:00', '2026-11-06 14:00:00', 0);
INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (9, '北境毛皮行', '/dev-api/uploads/images/2026-11/market/frostwolf-cloak.jpg', '从霜牙关一路背回来的皮子，带着松脂和雪的味道。', 1, 1, 173, '2026-11-07 08:20:00', '2026-11-07 08:20:00', 0);
INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (11, '星穹观象台', '/dev-api/uploads/images/2026-11/market/brass-astrolabe.jpg', '修表是营生，观星是毛病。黄铜仪器多半不准，但好看。', 1, 1, 87, '2026-11-08 19:40:00', '2026-11-08 19:40:00', 0);
INSERT INTO shop(owner_user_id,name,avatar_url,description,type,status,follower_count,created_at,updated_at,is_deleted)
VALUES (10, '游吟诗人旧货铺', '/dev-api/uploads/images/2026-11/market/old-lute.jpg', '上一任主人唱完最后一首歌就走了，留下的东西都在这里。', 2, 1, 156, '2026-11-09 12:10:00', '2026-11-09 12:10:00', 0);

INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=8), 6, '手锻短剑「余烬」', '剑身有一道烧不掉的暗红纹，据说是炉火自己咬出来的。配旧皮鞘，刃口重新开过。', 128000, 2, 1, 486, '["/dev-api/uploads/images/2026-11/market/ember-short-sword.jpg"]', 1, '2026-11-19 10:24:00', '2026-11-19 10:24:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=8), 6, '矮人胫甲（一对）', '三层熟铁叠打，内衬羊毛。护住小腿，也护住蹲在炉前的膝盖。', 46000, 3, 0, 132, '["/dev-api/uploads/images/2026-11/market/dwarf-bracers.jpg"]', 1, '2026-11-18 15:02:00', '2026-11-18 15:02:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=8), 7, '修补用铁砧（小号）', '巴掌大的袖珍铁砧，真能敲。附带一盒补锅钉，走夜路防身也不亏。', 89000, 1, 0, 77, '["/dev-api/uploads/images/2026-11/market/pocket-anvil.jpg"]', 1, '2026-11-17 09:41:00', '2026-11-17 09:41:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=6), 7, '月见草止血药剂（三瓶装）', '味道像泡了太久的甘草。协会认证的止血配方，喝完别马上跑。', 6800, 12, 37, 903, '["/dev-api/uploads/images/2026-11/market/moonheal-potion.jpg"]', 1, '2026-11-20 08:15:00', '2026-11-20 08:15:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=6), 7, '睡眠香囊', '薰衣草、干橘皮和一点点说不清的东西。挂在床头，梦会短一些。', 3200, 20, 12, 264, '["/dev-api/uploads/images/2026-11/market/sleep-sachet.jpg"]', 1, '2026-11-16 21:10:00', '2026-11-16 21:10:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=6), 4, '炼金温度计（黄铜）', '量药汤的温度，也能量地窖。刻度是手刻的，零下十度那格有点歪。', 15500, 4, 2, 158, '["/dev-api/uploads/images/2026-11/market/brass-thermometer.jpg"]', 1, '2026-11-15 13:36:00', '2026-11-15 13:36:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=7), 5, '《北境古道图》手绘残卷', '标了七条商路，其中两条现在通向悬崖。摊主说地图比路活得久。', 24000, 1, 0, 311, '["/dev-api/uploads/images/2026-11/market/north-road-map.jpg"]', 1, '2026-11-14 11:05:00', '2026-11-14 11:05:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=7), 5, '无名法师的空白笔记', '三百页，一个字没有。纸是好纸，写字不洇，适合记仇。', 9900, 5, 3, 147, '["/dev-api/uploads/images/2026-11/market/blank-grimoire.jpg"]', 1, '2026-11-13 16:48:00', '2026-11-13 16:48:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=7), 5, '铜版画：协会旧厅', '印的是三十年前的协会大厅，门口那棵树现在比楼高。', 4500, 6, 5, 88, '["/dev-api/uploads/images/2026-11/market/guild-etching.jpg"]', 1, '2026-11-12 10:20:00', '2026-11-12 10:20:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=9), 6, '霜牙关狼皮斗篷', '整张狼皮裁的，领口缝了防风毛边。雪夜里能顶半床被子。', 72000, 2, 1, 402, '["/dev-api/uploads/images/2026-11/market/frostwolf-cloak.jpg"]', 1, '2026-11-19 18:30:00', '2026-11-19 18:30:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=9), 6, '旅人皮靴（旧）', '鞋底补过两次，走山路依然跟脚。左脚比右脚松半码，前任主人大概是个瘸子。', 18000, 1, 0, 96, '["/dev-api/uploads/images/2026-11/market/traveler-boots.jpg"]', 1, '2026-11-11 09:12:00', '2026-11-11 09:12:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=9), 6, '羊毛围巾（未染）', '北境羊毛，手纺的，扎脖子。洗两次就软了。', 5600, 10, 12, 221, '["/dev-api/uploads/images/2026-11/market/wool-scarf.jpg"]', 1, '2026-11-10 14:55:00', '2026-11-10 14:55:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=11), 4, '黄铜星盘（可修）', '刻度盘缺了一角，指针还转。钟表匠说能修，但要等他有空。', 96000, 1, 0, 523, '["/dev-api/uploads/images/2026-11/market/brass-astrolabe.jpg"]', 1, '2026-11-18 07:40:00', '2026-11-18 07:40:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=11), 5, '观星笔记 1998—2003', '某位不肯署名的观星者留下的五本笔记，月亮画得比字好。', 12000, 1, 0, 173, '["/dev-api/uploads/images/2026-11/market/stargazer-notes.jpg"]', 1, '2026-11-09 22:18:00', '2026-11-09 22:18:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=11), 7, '沙漏（十分钟）', '烧瓶改的，漏得比标称快一点。适合煮蛋，不适合炼金。', 7500, 3, 2, 64, '["/dev-api/uploads/images/2026-11/market/ten-minute-hourglass.jpg"]', 1, '2026-11-08 12:02:00', '2026-11-08 12:02:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=10), 7, '旧鲁特琴（缺一根弦）', '琴箱有一道裂缝，低音反而更暖。缺的那根弦可以自己配。', 38000, 1, 0, 289, '["/dev-api/uploads/images/2026-11/market/old-lute.jpg"]', 1, '2026-11-17 20:06:00', '2026-11-17 20:06:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=10), 8, '点歌券·写给赶路的人', '游吟诗人记性不好，但第四首永远会唱。凭券点一首，路远也给你唱完。', 1200, 99, 41, 655, '["/dev-api/uploads/images/2026-11/market/song-request-ticket.jpg"]', 1, '2026-11-20 16:22:00', '2026-11-20 16:22:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=10), 5, '手抄歌本《给赶路的人》', '十二首歌，三首没唱完。最后一页写着：如果迷路，就唱第四首。', 6800, 4, 6, 198, '["/dev-api/uploads/images/2026-11/market/road-songbook.jpg"]', 1, '2026-11-07 11:33:00', '2026-11-07 11:33:00', 0);
INSERT INTO item(shop_id,category_id,title,description,price_cents,stock,sold_count,view_count,images_json,status,created_at,updated_at,is_deleted)
VALUES ((SELECT id FROM shop WHERE owner_user_id=10), 4, '铜哨（协会旧制）', '吹响时声音很尖，能传过两个山头。协会早年用它召集人手。', 2200, 8, 25, 412, '["/dev-api/uploads/images/2026-11/market/guild-whistle.jpg"]', 1, '2026-11-06 09:00:00', '2026-11-06 09:00:00', 0);

COMMIT;

-- 自查：店铺 9 家、商品 22 件（原有 3 店 3 品 + 本次 6 店 19 品）
SELECT COUNT(*) AS shops FROM shop WHERE is_deleted = 0;
SELECT COUNT(*) AS items FROM item WHERE is_deleted = 0;
SELECT s.name AS shop, COUNT(i.id) AS items FROM shop s LEFT JOIN item i ON i.shop_id=s.id AND i.is_deleted=0 GROUP BY s.id ORDER BY s.id;
