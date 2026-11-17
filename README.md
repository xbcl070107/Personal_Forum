# 冒险者协会 · 个人论坛

一个前后端分离的个人论坛：**发帖 → 审核 → 上墙 → 评论 / 点赞 / 收藏** 主链路完整可跑。

后端 Spring Boot 4 + MyBatis-Plus + MySQL，前端 Vue 3 + Element Plus。

> **项目状态**：本地开发环境跑通，未部署公网，暂无自动化测试。

---

## 功能

**已完成**

| 模块 | 能力 |
| --- | --- |
| 账号 | 注册 / 登录，JWT 有效期 24h，密码 BCrypt 加盐；`username` 登录名唯一、`nickname` 展示名可重复 |
| 发帖 | 标题 + 正文 + 板块，发布后进入「待审核」，不会直接对外可见 |
| 审核 | 管理员专属审核台：待审列表 → 通过 / 驳回；同一帖只会被成功审核一次 |
| 浏览 | 板块筛选、分页（默认 10，上限 50）、按发布时间倒序；未登录可看列表 |
| 详情 | 已过审帖公开可读；作者本人和管理员可查看自己的待审帖，其余人 404 |
| 互动 | 一级评论（≤500 字）、点赞、收藏，均支持取消；一人一帖仅一条 |
| 个人中心 | 我发布的（带审核状态）、我收藏的 |
| 图片上传 | ≤5MB，仅 jpg / jpeg / png / webp，扩展名 + 文件头双重校验 |
| 接口文档 | springdoc 自动生成，带 Authorize 按钮，可在页面上直接调接口 |

**第一版明确不做**（范围外，各有理由）

- 楼中楼回复 —— 多级递归查询 + 树形渲染，第一版容易做脏，先只做一级评论
- 独立管理后台 —— 自用场景，管理动作只有审核，另做一套页面和权限拦截成本大于收益
- 板块在线增删改 —— 低频配置，SQL 预置即可
- 帖子搜索 —— 排序与分页先跑通，动态 SQL 留到后续迭代
- 第三方登录 / 找回密码 / 邮箱验证 —— 单人自用不需要

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.1.1、Spring MVC、MyBatis-Plus 3.5.17、JJWT 0.12.6、springdoc-openapi 3.1.1、Lombok、Bean Validation |
| 数据库 | MySQL 8.0，utf8mb4，InnoDB |
| 前端 | Vue 3.5、Vue Router、Pinia、Element Plus、Axios、Vite |
| 构建 | Maven（后端）、Vite（前端） |

---

## 快速开始

### 1. 环境要求

JDK 21 · Maven 3.9+ · MySQL 8.0 · Node.js 18+

### 2. 建库

```bash
mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql
```

`--default-character-set=utf8mb4` **不能省**。脚本里有中文板块名，用默认字符集的客户端导入会变成乱码且不报错，等页面显示出来才发现。

### 3. 本地配置

`src/main/resources/application-local.yaml` 已被 `.gitignore` 排除（里面是密码），需要自己创建：

```yaml
server:
  port: 9090
  servlet:
    context-path: /dev-api

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/forum?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: '你的数据库密码'

jwt:
  secret: 至少32个字符的随机字符串   # HS256 要求 ≥256 位，短了启动直接抛 WeakKeyException
  expire-hours: 24
```

`application.yaml` 中 `spring.profiles.active: local`，启动时会自动加载这个文件。

### 4. 启动后端

```bash
mvn spring-boot:run
```

- 服务地址：<http://localhost:9090/dev-api>
- 接口文档：<http://localhost:9090/dev-api/swagger-ui.html>

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。Vite 把 `/dev-api` 代理到 `localhost:9090`，浏览器侧不存在跨域；后端也没有额外配置 CORS。

### 6. 设置管理员

注册接口不开放角色字段（注册用户一律 `USER`）。需要管理员时手动改库，**改完必须重新登录**：

```sql
UPDATE `user` SET role = 'ADMIN' WHERE username = '你的用户名';
```

原因：`role` 在登录时被签进 JWT 的 claim，后续请求只解析 token、不查库。改库后旧 token 里仍是 `USER`，只有重新登录才会签发新 token。

---

## 接口一览

统一响应体：

```json
{ "code": 200, "message": "ok", "data": {} }
```

`code`：200 成功 / 400 参数或业务错误 / 401 未登录或 token 失效 / 403 无权限 / 404 资源不存在 / 500 服务端异常。

论坛部分共 17 个接口，均以 `/dev-api` 为前缀：

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 注册 | 公开 |
| POST | `/auth/login` | 登录，返回 token 与用户信息 | 公开 |
| GET | `/category/list` | 板块列表 | 公开 |
| GET | `/post/list` | 帖子列表（`categoryId` / `pageNum` / `pageSize`），只返回已过审 | 公开 |
| GET | `/post/{id}` | 帖子详情，作者与管理员可看未过审 | 登录 |
| POST | `/post/publish` | 发帖，落库即待审核 | 登录 |
| GET | `/post/my` | 我发布的帖子 | 登录 |
| DELETE | `/post/{id}` | 删帖（软删除，仅作者本人） | 登录 |
| GET | `/post/pending` | 待审核列表 | ADMIN |
| POST | `/post/audit` | 审核通过 / 驳回 | ADMIN |
| GET | `/comment/list` | 某帖的评论列表 | 登录 |
| POST | `/comment/publish` | 发表评论 | 登录 |
| POST | `/post/like` | 点赞 / 取消点赞（切换） | 登录 |
| GET | `/post/like` | 点赞数 + 当前用户是否已赞 | 登录 |
| POST | `/post/favorite` | 收藏 / 取消收藏（切换） | 登录 |
| GET | `/post/favorite` | 收藏数 + 当前用户是否已收藏 | 登录 |
| POST | `/file/upload` | 上传图片，返回可访问 URL | 登录 |

点赞、收藏、状态查询的 `postId` 走 query 参数，其余写操作走 JSON body。

---

## 数据库设计

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `user` | 用户 | `uk_user_username` 登录名唯一；`role` 为 `ADMIN` / `USER` |
| `category` | 板块 | `uk_category_name` 名称唯一；预置「日常 / 技术 / 水贴」 |
| `post` | 帖子 | `status` 0 待审 / 1 通过 / 2 驳回；`is_deleted` 逻辑删除；`idx_post_status_created (status, created_at)` |
| `comment` | 评论 | 一级评论；`is_deleted` 逻辑删除；`idx_comment_post_id` |
| `post_like` | 点赞关系 | `uk_like_user_post (user_id, post_id)` |
| `post_favorite` | 收藏关系 | `uk_fav_user_post (user_id, post_id)` |

几处设计考虑：

- **点赞 / 收藏的唯一键放在数据库而不是代码里**：「一人一帖只有一条」由 `uk_like_user_post` 保证，应用层不做「先查再插」，避免并发下的重复插入。
- **联合索引对应真实查询**：列表是 `WHERE status = 1 ORDER BY created_at DESC`，所以建 `(status, created_at)` 而不是给 `status` 单建索引（区分度太低，优化器不会走）。
- **逻辑删除只作用于 post / comment**：MyBatis-Plus 通过实体中的 `isDeleted` 字段识别，`user` / `category` / 点赞收藏表不参与，取消点赞是真正的 `DELETE`。
- **`status` 与 `is_deleted` 分工**：前者是业务态，每个查询都得自己写过滤条件；后者由框架自动拼接，两者不能混用。

---

## 目录结构

```
Personal_Forum/
├── sql/
│   └── init.sql                 建库建表 + 预置板块
├── src/main/java/com/xbcl/personal_forum/
│   ├── common/                  Result、BusinessException、全局异常处理
│   ├── config/                  JWT 拦截器、MVC 配置、分页插件、密码编码器、OpenAPI
│   ├── controller/              REST 入口（7 个）
│   ├── service/                 业务逻辑
│   ├── mapper/                  MyBatis-Plus BaseMapper，无 XML
│   ├── pojo/                    entity / dto / vo
│   └── util/                    JwtUtil
├── src/main/resources/
│   ├── application.yaml         公共配置（可提交）
│   └── application-local.yaml   本地密码（.gitignore）
└── frontend/
    ├── vite.config.js           /dev-api 开发代理
    └── src/
        ├── api/                 axios 封装 + 各模块请求
        ├── router/              路由 + 管理员守卫
        ├── store/               Pinia 用户状态
        ├── components/          EmptyState、PostActions
        └── views/               7 个页面
```

---

## 设计取舍

写这个项目时真正花了时间的几处，也是比较容易被追问的点：

1. **JWT 无状态**：`role` 签进 token claim，拦截器解析后直接放行，每个请求省一次用户表查询。代价是权限变更必须重新登录——这里选择了「简单 + 快」而不是「实时生效」。
2. **审核的并发控制**：审核用 `UPDATE post SET status = ? WHERE id = ? AND status = 0`，看影响行数判断是否成功。两个管理员同时点，只有一个能改成功，且天然幂等，不需要额外加锁。
3. **注册重名的竞态**：不在 Service 里「先查后插」，而是直接插入并捕获 `DuplicateKeyException`。查与插之间永远有窗口，唯一键才是最终裁决者。
4. **分页参数在服务端夹取**：`pageSize` 统一夹到 `[1, 50]`，防止前端传 `999999` 把库打穿。
5. **避免 N+1**：列表接口先用 `selectBatchIds` 一次查出所有作者，再在内存里组装，而不是循环里逐个查用户名。
6. **上传的三重防护**：不信 `Content-Type`（客户端可伪造），扩展名 + 文件魔数双重判断；文件名由 UUID 重新生成，规避目录穿越与后缀绕过；落盘按年月分目录。
7. **统一出口**：`Result<T>` + `@RestControllerAdvice`，业务异常抛 `BusinessException`，Controller 里不出现散落的 `if (null) return error`。

---

## 已知限制

- **无自动化测试**：只有 Spring 初始化时的 `contextLoads()` 空壳，接口层与业务层均未覆盖。
- **未部署**：仅本地 `localhost` 开发，仓库里没有 Nginx / Dockerfile 配置，`compose.yaml` 为空。
- **无登录日志**：`user` 表没有 `last_login_at`，无法统计活跃用户。
- **405 未统一处理**：方法不允许时会走框架默认响应，未纳入统一异常格式。
- **上传接口未接入页面**：`/file/upload` 后端已就绪，帖子编辑页暂未放上传控件。
- **演示数据不在仓库**：`sql/` 只有表结构和 3 个板块，用户与帖子需自行创建，换台机器不会自带样例数据。
- **交易模块仅有表结构**：`sql/migration-v4-shop.sql` 预置了后续阶段的建表语句，代码层尚未接入。

---

## 后续计划

- 补 MockMvc 接口测试与 Service 单元测试
- 部署到公网（Nginx 托管前端静态资源 + 反向代理后端）
- 帖子搜索、站内通知、用户主页

---

学习项目，未选择开源许可证。
