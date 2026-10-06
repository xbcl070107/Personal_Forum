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
├── components/          共用组件（EmptyState 等）
├── router/index.js      路由表 + 登录/管理员守卫
├── store/user.js        当前登录用户（token 存在 localStorage）
├── utils/format.js      时间格式化、状态映射、作者身份
└── views/               七个页面
```

## 改样式先看这里

配色、圆角、阴影、字号全部收在 `style.css` 第 1 节的 CSS 变量里，
下面的规则只引用变量。想调主题改那几行，不用去各个 `.vue` 里翻。

规矩是：**改 Element Plus 自带组件的外观 → 写 `style.css`；
改某个页面自己的布局 → 写那个页面里。**

## 两个已知的取舍

1. **帖子作者名是前端推的，不是真名。** 后端列表 / 详情接口只返回
   `userId`，没有作者名（见 `PostService.listApproved`，返回的是裸 `Post`）。
   所以作者名和头像色由 `userId` 稳定推导，见 `utils/format.js` 的
   `authorOf`。同一个人永远同一个名字和颜色，但换成真名需要后端加 VO。

2. **`public/login.html` 是调试用的旁路页面。** 它往 localStorage 塞一个
   假 token 和管理员身份，然后跳进应用，方便在没有账号时直接看页面。
   不是正常流程的一部分，用 `npm run preview` 才访问得到。
