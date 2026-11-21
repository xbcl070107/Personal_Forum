# 冒险者协会 · 前端

个人论坛「冒险者协会」的前端。Vue 3 + Vite + Element Plus，
通过 JSON 接口和后端（Spring Boot，端口 9090）通信。

本项目的学习重心在后端，前端定位是「一个跑得起来、看得过去的外壳」。

## 怎么启动

```bash
npm install     # 第一次才需要
npm run dev     # 开发服务器
```

打开 **http://localhost:5173**。

⚠️ 必须从这个目录（`frontend/`）启动，`index.html` 在这里，
Vite 把它当项目根。不要直接双击 `index.html` 打开 ——
那是 `file://` 协议，Vue 的 ES module 会被浏览器拦掉，必然白屏。

## 前后端怎么连上的

浏览器只跟 5173 说话。前端发的 `/dev-api/xxx` 请求，由 Vite 开发服务器
在背后转发给 `http://localhost:9090/dev-api/xxx` —— 同源，所以不存在跨域。
配置在 `vite.config.js` 的 `server.proxy`。

**所以后端没起的话，页面能打开，但列表是空的。**

## 其他命令

```bash
npm run build     # 打包到 dist/
npm run preview   # 预览打包结果，用来验证构建产物
```

## 目录

```
src/
├── main.js              入口：注册 Pinia / Router / Element Plus
├── App.vue              顶栏 + 页脚 + <router-view>
├── style.css            全局设计令牌 + Element Plus 主题覆盖
├── api/                 每个后端模块一个文件，只负责发请求
├── components/          共用组件（EmptyState、PostActions、商城弹窗与订单动作）
├── router/index.js      路由表 + 登录/管理员守卫
├── store/user.js        当前登录用户（token 存在 localStorage）
├── utils/format.js      时间/金额格式化、状态映射、作者身份
├── utils/upload.js      图片选择 + 大小/类型校验 + 上传
└── views/               十五个页面（论坛 7 + 集市/订单/店铺/地址 8）
```

## 改样式先看这里

配色、圆角、阴影、字号全部收在 `style.css` 第 1 节的 CSS 变量里，
下面的规则只引用变量。想调主题改那几行，不用去各个 `.vue` 里翻。

规矩是：**改 Element Plus 自带组件的外观 → 写 `style.css`；
改某个页面自己的布局 → 写那个页面里。**

## 已对接的后端功能

| 功能 | 前端入口 | 后端接口 |
| --- | --- | --- |
| 注册 / 登录 | `views/Register.vue` `views/Login.vue` | `POST /auth/register`、`POST /auth/login` |
| 帖子列表 / 详情 | `views/Home.vue` `views/PostDetail.vue` | `GET /post/list`、`GET /post/{id}` |
| 发帖 | `views/PostEdit.vue` | `POST /post/publish` |
| 回帖 | `views/PostDetail.vue` | `GET /comment/list`、`POST /comment/publish` |
| 点赞 / 收藏 | `components/PostActions.vue` | `GET|POST /post/like`、`GET|POST /post/favorite` |
| 删帖 | 详情页底部 / 我的委托行内 | `DELETE /post/{id}` |
| 审核 | `views/Audit.vue` | `GET /post/pending`、`POST /post/audit` |
| 集市 / 商品详情 | `views/Market.vue` `views/ItemDetail.vue` | `GET /item/list`、`GET /item/{id}`、`GET /item/category/list` |
| 店铺主页 | `views/Shop.vue` | `GET /shop/{id}`、`GET /item/list?shopId=` |
| 开店 / 改店铺资料 | `views/Merchant.vue`、`components/ShopDialog.vue` | `GET /shop/mine`、`POST /shop`、`PUT /shop` |
| 发布 / 上下架商品 | `views/Merchant.vue`、`components/ItemDialog.vue` | `POST /item`、`GET /item/mine`、`PATCH /item/{id}/status` |
| 收货地址 | `views/Addresses.vue`、`components/AddressDialog.vue` | `GET /address/list`、`POST /address`、`PUT /address/{id}`、`PATCH /address/{id}/default`、`DELETE /address/{id}` |
| 结算下单 | `views/Checkout.vue` | `POST /order` |
| 订单列表 / 详情 | `views/Orders.vue` `views/OrderDetail.vue` | `GET /order/list`、`GET /order/{id}` |
| 支付 / 发货 / 确认 / 取消 | `components/OrderActions.vue` | `POST /order/{id}/pay|ship|confirm|cancel` |
| 商品图 / 店铺头像上传 | `components/ItemDialog.vue`、`ShopDialog.vue` | `POST /file/upload` |

### 点赞 / 收藏为什么要「先查再翻」

后端只有一个**翻面**接口：它自己判断该赞还是该取消。所以前端不能
自己记状态再翻 —— 记错一次（双击、多标签页）结果就整个反了。做法是：

1. 进详情页先 `GET` 一次，把按钮画对（不查的话刷新后永远是「未赞」）
2. 每次点击只上报「点了」，状态取响应里的 `liked` / `count`
3. 点击时先乐观翻转一下让手感跟上，请求失败再翻回来

另一个容易踩的点：后端 `requireApproved` 只允许**已过审**的帖被点赞/收藏，
待审和被驳回的帖会直接回 404。所以互动栏只在 `status === 1` 时显示
（`PostDetail.vue` 里的 `approved`）。

## 商城的几条规矩

### 金额只用「分」

后端所有金额都是 `*Cents` 整数（`priceCents`、`payCents`…），前端也只在最后
显示时才转成元 —— `utils/format.js` 的 `moneyText`。输入价格时用户在「元」里填，
提交前用 `yuanToCents` 乘 100 取整。

不直接在 JS 里用浮点算钱：`19.9 * 3` 可能得到 `59.699999999999996`，
四舍五入的位置差一点，对账就差一分。

### 下单靠 clientToken 防重复

`POST /order` 的 body 里必须带 `clientToken`，后端用 `(user_id, client_token)`
唯一键拦重复提交。前端在**进入结算页时生成一次**（`crypto.randomUUID()`，
老浏览器降级成时间戳 + 随机串），之后无论点几次提交、失败重试几次都用同一个。

如果每次点击都重新生成，唯一键就形同虚设 —— 网速慢的时候点两下就是两张订单。

命中重复提交时后端回 400「请勿重复提交」，前端不再留在结算页，直接跳订单列表：
那一单其实已经建好了。

### 订单没有 userId，买家/卖家是推出来的

`OrderVO` 里没有 `userId`，也没有 `role`。判断「我在这张单里是谁」只能看店：

```
myShop().id === order.shopId  →  我是卖家
否则                          →  我是买家
```

后端禁止买自己店里的商品，所以这两个身份不会同时成立。
`OrderDetail.vue` 靠这一条决定显示哪些按钮；`Orders.vue` 的买家 / 卖家视角
由页面顶部那个 tab 决定，切换 tab 只是换 `role` 参数，接口是同一个。

### 订单状态

| status | 文案 | 下一步等谁 |
| --- | --- | --- |
| 0 | 待付款 | 买家（15 分钟内，超时自动关闭） |
| 1 | 待发货 | 卖家填快递单号 |
| 2 | 待收货 | 买家确认收货 |
| 3 | 已完成 | — |
| 4 | 已取消 | — |

注意 1 的文案是「待发货」而不是「已付款」：状态条回答的是「现在等谁」，
不是「钱付了没」。取消单不画进步骤条，单独用一条提示说明。

### 商品没有「编辑」

后端只有 `POST /item`（发布）和 `PATCH /item/{id}/status`（上下架），
没有改标题 / 改价的接口。所以 `ItemDialog.vue` 只做发布，要改价就下架重发。
这不是前端偷懒，是当前后端的边界。

### 图片

`POST /file/upload` 的 multipart 字段名必须是 `file`，返回 `data` 是可直接
放进 `img src` 的 URL。`utils/upload.js` 统一校验 `image/*` 和 5MB 上限；
商品图最多 9 张，第一张作为列表封面。

## 用户名和昵称是两个东西

| | username | nickname |
| --- | --- | --- |
| 用途 | 登录凭证 | 展示名 |
| 唯一性 | **唯一**（表上有 `uk_user_username`） | **允许重复**（故意不建唯一键） |
| 页面上 | 不显示。只在顶栏悬停时以 `@xxx` 露出 | 所有地方显示的都是它 |
| 头像配色 | 用它当哈希键 | 不用 |

**为什么必须分开**：合成一个字段的话，就会卡在「我想叫提灯人，但已经有人叫了」——
要么让用户改自己想要的展示名，要么让两个同名的人互相抢注。

**头像色为什么按 username 算**：昵称允许重复，两个都叫「提灯人」的人如果按昵称取色，
会分到同一个颜色、同一个首字，在列表里完全分不出来。username 唯一，用它做键才能保证
「同一个人永远同色」且「不同的人不撞色」。见 `utils/format.js` 的 `authorOf`。

**后端接口里两个名字都给**：`PostVO` / `CommentVO` / `LoginVO` 同时带 `username` 和
`nickname`。前者用来算颜色、区分同名，后者用来显示。

## 三个已知的取舍

1. **帖子作者名是前端推的，不是真名。** 后端列表 / 详情接口只返回
   `userId`，没有作者名（见 `PostService.listApproved`，返回的是裸 `Post`）。
   所以作者名和头像色由 `userId` 稳定推导，见 `utils/format.js` 的
   `authorOf`。同一个人永远同一个名字和颜色，但换成真名需要后端加 VO。

2. **`public/login.html` 是调试用的旁路页面。** 它往 localStorage 塞一个
   假 token 和管理员身份，然后跳进应用，方便在没有账号时直接看页面。
   不是正常流程的一部分，用 `npm run preview` 才访问得到。
   ⚠️ 那个 token 是假的，过不了后端的 JWT 校验 —— 一调受保护的接口就会
   401，然后被 `request.js` 清掉。它只能用来**看**公开页面。

3. **删除按钮悬停才出现，用的是 `visibility` 而不是 `opacity`。**
   `opacity` 是渐变，移入/移出的那 0.24 秒里按钮处于半透明状态 ——
   看得见却摸不着，还能误点。`visibility` 是跳变的，只有「完整存在」
   和「完全不存在」两种状态。用不到鼠标的窄屏靠
   `@media (hover: none)` 让它常显。
