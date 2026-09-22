# 智循书堂 → 校园二手商品交易系统 · 改造复刻计划

> 文档定位：这是我们的"施工图"。它有三个作用 ——
> ① 记录**已经跑通的原始项目**（阶段 0，已完成）；
> ② 把**要改什么、怎么改、为什么这么改**写成可验收的清单；
> ③ 给出一条**从最小案例开始、逐步迭代**的学习路线，而不是一次性丢给你 2 万行代码。
>
> 阅读顺序建议：先看【一】确认现状 → 看【二】建立架构直觉 → 看【四】知道我们要去哪 → 然后按【五】一节一节做。

---

## 一、阶段 0 现状：原始项目已跑通 ✅

### 1.1 四服务运行状态（已实测验证）

| 服务 | 端口 | 技术栈 | 状态 | 验证方式 |
|------|------|--------|------|----------|
| MySQL | **3307** | MySQL 8.0.43 | ✅ 运行中 | `book_cycle` 库 7 张表 |
| 业务后端 | 8080 | Spring Boot 3.2.0 + MyBatis-Plus 3.5.5 | ✅ 运行中 | `GET /api/categories` → 200，116 个分类 |
| AI 微服务 | 8000 | FastAPI + BM25 | ✅ 运行中 | `GET /health` → 已加载 54 本书 |
| 前端 | 5173 | Vue 3.4 + Vite 5 + Element Plus | ✅ 运行中 | 首页渲染正常，代理链路通 |

**端到端链路已实测**：浏览器 5173 → `/api/*` 代理 → 后端 8080 → MySQL 3307 ✅；5173 → `/ai/*` 代理 → AI 8000 → BM25 检索 ✅。

### 1.2 环境与原文档不符的 5 个坑（已修复，你必须知道）

交接文档写的是"照做即可"，但实际有 5 处对不上。这些是真实踩坑记录，**答辩如果被问"你搭环境遇到什么问题"，这就是现成素材**：

| # | 文档说 | 实际情况 | 处理方式 |
|---|--------|----------|----------|
| 1 | `mvn -v` 应能直接运行 | 本机 **没装 Maven**，也不在 PATH | 复用 `~/.m2/wrapper/dists` 下已有的 Maven 3.9.14，脚本自动探测 |
| 2 | MySQL root 密码是 `0428` | **不是**，`0428`/空密码都 1045 拒绝；重置需管理员权限 | 另建**独立实例**在 3307（数据目录 `D:\code-javaweb\.mysql-book`），root 密码 `123456`，不碰系统那个 3306 |
| 3 | JDK 17 | 本机是 **JDK 24**（`D:\jdk`） | 见 #4、#5 |
| 4 | （未提及） | Spring Boot 3.2.0 默认 Lombok 1.18.30 **不支持 JDK 21+ 注解处理**，`@Data` 不生成 getter/setter → 几十个"找不到符号"编译错误 | `pom.xml` 显式升 Lombok 到 `1.18.44` |
| 5 | （未提及） | **JDK 23+ 默认关闭隐式注解处理**，Lombok 依然不生效 | `maven-compiler-plugin` 加 `<proc>full</proc>` + `annotationProcessorPaths` |

> ⚠️ 坑 4、坑 5 是**递进关系**：只改 Lombok 版本不够，必须同时开 `proc:full`。这是本项目环境适配的核心结论。
>
> 📌 你原计划装 JDK 17 是"更规范"的方案，但既然 pom 后面本来就要改，升 Lombok 更省事。**这个决定随时可以推翻** —— 见【十一】决策记录 D2。

### 1.3 已交付的运维脚本（`scripts/`）

| 脚本 | 作用 |
|------|------|
| `env.ps1` | 环境探测总闸：自动找 JDK / Maven，集中管理端口与数据库账号 |
| `db-start.ps1` | 启动专用 MySQL 实例（幂等，已运行则跳过）+ 连接自检 |
| `start-backend.ps1` | 启动后端（自动注入 DB 环境变量） |
| `start-ai.ps1` | 启动 AI 微服务 |
| `start-web.ps1` | 启动前端（缺 node_modules 会自动 install） |
| `status.ps1` | **一键体检**：4 端口 + 6 条接口链路 + 数据库表数/记录数 |
| `stop-all.ps1` | 停止全部（只停我们自己的 3307，不碰系统 MySQL80） |

> 🔧 踩坑记录：PowerShell 5.1 读取 `.ps1` 时若无 BOM 会按 ANSI 解析，中文全变乱码并报语法错。**所有脚本已存为 UTF-8 with BOM**，后续你新增脚本要注意这点。

### 1.4 数据库现状

7 张表：`user`(6) / `book`(100) / `category`(116) / `order_info`(33) / `cart`(1) / `address`(2) / `user_behavior`(**0**)。

测试账号（**明文密码，改造第一站就要修**）：

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `admin123` | admin |
| `testuser` / `seller01~03` / `usery` | `123456` | user / seller |

---

## 二、系统架构与数据流（先建立直觉，再碰代码）

### 2.0 最重要的一张图：两个项目的关系

**我们同时维护两个独立项目，它们不共用代码，只共用「技术选型」和「数据库实例」。**

```
┌──────────────────────────────────────────────────────────────────┐
│ D:\code-javaweb\book\              ← 【参考项目】原始开源项目      │
│   状态：✅ 已跑通，全程保持可运行，一个字都不改（除了改 DB 配置）  │
│   作用：① 当作"标准答案"...                                        │
│         更准确说：当作【解剖标本】—— 读懂它，再自己写一遍          │
│   数据库：book_cycle（旧 schema：book / order_info / cart ...）    │
└──────────────────────────────────────────────────────────────────┘
                              ↓ 你阅读、理解、模仿
┌──────────────────────────────────────────────────────────────────┐
│ D:\code-javaweb\Second-handTradingSystem\  ← 【我的项目】          │
│   状态：🚧 从零搭建中，按 STAGE 逐步长大                           │
│   作用：你要交的作业本体（含 Redis + AI 增量）                     │
│   数据库：全新独立库（新 schema：product / product_order / ...）   │
└──────────────────────────────────────────────────────────────────┘
                              ↓ 两者共用
                    ┌──────────────────────┐
                    │ MySQL 实例 :3307     │
                    │ 两个库互不干扰        │
                    └──────────────────────┘
```

**为什么不直接复制参考项目再改？**（这是你明确否决的路线，理由成立）

- 一次性面对 2 万行陌生代码，无法建立"为什么这么写"的直觉；
- 命名重构（方案丙）会横跨 DB+Java+Python+Vue，在陌生代码上做等于盲改；
- 正确做法：**每一层都自己写一遍**，写的过程中自然理解每一层在干什么。

**核心纪律**：
1. **参考项目不许改**（只改过 `application.yml` 的数据库指向，其他不动）——它是你的"对照实验组"；
2. 新项目**每个 STAGE 只加一小块可运行的功能**，跑通了、你懂了，才进入下一个；
3. 两个项目的**端口必须错开**，否则不能同时运行对照（分配见【四】）。

### 2.1 参考项目的四层架构（先读懂它）

```
┌──────────────────────────────────────────────────────────┐
│ 前端 Vue3 :5173  （Vite dev server）                       │
│  · 页面：首页 / 列表 / 详情 / 发布 / 暂存架 / 订单 / 后台    │
│  · 状态：token 和 user 存在 localStorage（没有用 Pinia！）   │
└───────────┬──────────────────────────┬───────────────────┘
            │ /api/*                   │ /ai/*  ← 关键：直接打 AI，绕过 Java
            ▼                          ▼
┌───────────────────────┐   ┌──────────────────────────────┐
│ Spring Boot :8080     │   │ FastAPI :8000                 │
│ · Controller 层       │   │ · /api/ai/chat   多轮问答      │
│ · Interceptor JWT鉴权 │   │ · /api/ai/recommend 智能推荐   │
│ · Service 层          │   │ · BM25 检索 + 大模型 fallback  │
│ · Mapper (MyBatis-Plus)│  │ · 直接连 MySQL 读语料          │
│   + JdbcTemplate裸SQL │   │   （不经 Java）                │
└───────────┬───────────┘   └──────────┬───────────────────┘
            └──────────┬────────────────┘
                       ▼
              ┌──────────────────┐
              │ MySQL :3306      │
              │ book_cycle 7 表  │
              └──────────────────┘
```

### 2.2 三条必须记住的数据流

**流 1：普通业务请求（以"查看商品列表"为例）**

```
BookList.vue
  → api/book.js  getBookList()        baseURL='/api'
  → axios GET /api/books?page=1
  → Vite 代理（/api → localhost:8080，不改写路径）
  → JwtInterceptor 校验 Authorization: Bearer <token>
      ├─ 通过 → request.setAttribute("currentUserId", id)
      └─ 失败 → 401 JSON
  → BookController.list()  ← 注意：这里用 JdbcTemplate 拼 SQL，没用 MyBatis-Plus
  → Result.success({records, total})
  → 前端 request.js 拦截器判断 res.code===200 → 返回 res
```

**流 2：登录与鉴权（这是最小可运行案例，见【五】A 阶段）**

```
Login.vue → POST /api/user/login {username, password}
  → UserServiceImpl.login()
      ├─ 查用户名 → 不存在抛异常
      ├─ 查 status → 0 抛"账号已被禁用"
      ├─ 比对密码 ← 现在是明文 equals()，改造点！
      └─ JwtUtil.generateToken(id, username, role)
  → 返回 User（含 token，password 置 null）
  → 前端 localStorage.setItem('token'/'user')
  → 后续请求由 request.js 自动带 Authorization 头
  → 路由守卫 router.beforeEach 检查 requiresAuth / requiresAdmin
```

**流 3：AI 对话（理解"Java 如何调用 Python"的关键 —— 答案是：现在压根没调！）**

```
AiChat.vue
  → api/ai.js  aiChat()   baseURL='/ai'  ← 独立 axios 实例，不走 request.js
  → POST /ai/api/ai/chat
  → Vite 代理：rewrite 剥掉 /ai 前缀 → 转发到 localhost:8000/api/ai/chat
  → main.py chat()
      ├─ retriever.search(message)      BM25 检索商品语料
      ├─ llm.chat(history, msg, sources) 大模型生成（无 Key 则降级为规则拼接）
      └─ sessions dict 存多轮历史 ← 存内存！重启即丢，这是 Redis 的首选切入点
  → 返回 {reply, sources[], provider, provider_chain}
```

> 🎯 **教学要点**：现状是"前端直连 Python"。实验报告里如果你想写"Java 调用 Python 服务"，那就需要**新增一条 Java → Python 的链路**（见 R 阶段 R3），比如 Java 端在商品详情页调用 Python 的"生成推荐理由"接口。这是个能写进简历的真实增量。

### 2.3 几个容易懵的名词

- **JWT**：一段带签名的字符串，服务器不存它，靠签名自证真伪。登录成功后前端存起来，之后每个请求都带上，服务器解出来就知道"你是谁"。→ 所以**服务器是无状态的**，也就**没法服务端主动登出**（这正好是 Redis 做黑名单的场景）。
- **拦截器 Interceptor**：请求进 Controller 之前的关卡。本项目两个：`JwtInterceptor`（查登录）、`AdminInterceptor`（查管理员角色）。
- **UserContext**：不是 ThreadLocal，而是从 `RequestContextHolder` 里取当前请求，再读 `request.getAttribute`。作用是让 Service 层不用层层传 userId。
- **MyBatis-Plus vs JdbcTemplate**：本项目**混用**。MyBatis-Plus 走实体+Mapper（如 `bookMapper.selectById`），JdbcTemplate 直接写 SQL 字符串（如 `BookController.list`）。混用是历史包袱，也是很好的重构练习素材。

---

## 三、现状代码体检：必须知道的 10 个问题

这些是**真实存在的**问题，按"改造优先级"排序。每一行都给了文件位置，你自己去核对。

| # | 问题 | 位置 | 危害 | 对应阶段 |
|---|------|------|------|----------|
| 1 | **密码明文存储与比对** | `UserServiceImpl.java:65,110,116`；DB `user.password` 存 `admin123` | 严重安全事故；面试必问 | **A** |
| 2 | **未注册分页插件，`selectPage` 静默失效** | 全项目只有 `WebConfig.java` 一个配置类，无 `MybatisPlusInterceptor` Bean；但 `UserServiceImpl:126`、`AdminServiceImpl:38,57,81` 都在用 `selectPage` | 数据量一大就返回全表，越界数据泄漏 | **A** |
| 3 | **`BookServiceImpl.getById()` 有副作用+空指针风险** | `BookServiceImpl.java:32-38`：每次查详情都 `viewCount+1` 并 `updateById`；且 `book` 为 null 时第 34 行直接 NPE | 读接口写库；商品不存在时 500 | **B** |
| 4 | **暂存架列表 N+1 查询 + 脏写浏览量** | `CartController.java:35-45` 循环里调 `bookService.getById()`，每个购物车项既多查一次库、又把 viewCount 加 1 | 购物车一刷新，商品浏览量暴涨 | **E** |
| 5 | **分类硬编码且 ID 是错的** | `BookPublish.vue:29,37`、`BookDetail.vue:68,76` 硬编码 `156`/`164`；`BookReview.vue:97,105` 映射表同样硬编码。**实际库中一级分类 ID 是 117~125，156/164 是子分类** | 用户发布时选的分类是错的 | **C** |
| 6 | **并发下单没有真正加锁** | `OrderController.java:358-362` `checkBookLock()` 只是重新查一次状态，注释自己都承认"可结合 Redis 分布式锁" | 同一件商品能被两人同时买走 | **F** |
| 7 | **状态码魔法数字散落各处** | book.status：0待审/1在售/2已售出/4已拒绝（`AdminServiceImpl:72`）；order.status：0待付款/1已付款/2已发货/3已完成/4已取消（`OrderController:466-473`） | 无枚举，改一处漏一处 | **B/F** |
| 8 | **`deleted` 逻辑删除配置是死的** | `application.yml:30-32` 配了 `logic-delete-field: deleted`，但 7 张表**都没有 deleted 列**，也没实体带 `@TableLogic` | 删除即物理删除，不可恢复 | **B** |
| 9 | **异常被吞、细节外泄** | `CartController`、`UploadController` 里 `e.printStackTrace()` + 把 `e.getMessage()` 拼进返回给前端 | 泄漏内部信息；日志无结构 | 全程 |
| 10 | **`user_behavior` 表 0 条记录，完全没用上** | 表已建（`behavior_type` 1浏览/2加购/3购买），`UserBehaviorMapper` 存在但全项目无人写入、无人读取 | 协同过滤只能退化用订单表；这是**推荐系统最该补的数据源** | **R** |

---

## 四、改造总目标与验收标准

### 4.1 一句话目标

把"**高校二手教材循环交易系统**"改造为"**校园二手商品交易系统**"，并叠加 **Redis 缓存**与 **AI 智能搜索/个性化推荐**，产出可运行系统 + 实验报告 + 答辩 PPT。

### 4.2 最终验收清单

| 维度 | 验收标准 |
|------|----------|
| 业务改造 | 全站文案无"教材/书籍/图书"残留（业务语义层面）；商品字段通用化（ISBN→品牌/型号可选，品相→成色） |
| Redis | 至少 3 个场景落地，且有**可截图的命中证据**（`keyspace_hits` 增长 / 日志 / 响应耗时对比） |
| AI | 有 Java→Python 的真实调用链路；推荐结果带"推荐理由"；检索质量可演示 |
| 安全 | 密码 BCrypt 加密；分页真实生效；并发下单不超卖 |
| 工程 | 至少一套单元测试；`status.ps1` 全绿；README 能让他人复现 |
| 文档 | 实验报告（需求→设计→实现→测试）+ 答辩 PPT + 演示视频 |

### 4.3 命名重构决策：**方案丙（彻底重构）已确认** ✅

**决策**：表名、接口路径、字段名、路由、包路径**全部去书籍化**。不做"只改文案"的妥协版。

带来的工作量与顺序（**必须按这个顺序做，否则一改就崩**）：

| 顺序 | 改什么 | 风险 | 为什么放这个位置 |
|------|--------|------|------------------|
| 1 | **先设计新数据库**（独立库，与参考项目完全隔离） | 低 | 后面所有代码都依赖它，必须先冻结 |
| 2 | 后端实体类 + `@TableName` + Mapper | 中 | 编译期就能发现绝大部分问题 |
| 3 | 后端 Controller 路径 + Service 方法名 | 中 | 与实体一起改，IDE 重构可辅助 |
| 4 | Python 侧的表名/字段名 SQL | 低 | 只有 `db.py` 少数几处 |
| 5 | 前端 `api/*.js` 路径 + 路由 `/book/*`→`/product/*` | 中 | 接口冻结后再改，可用 Network 面板逐条验证 |
| 6 | 前端页面文案（85 处） | 极低 | 纯字符串替换，最后做 |
| 7 | 全流程回归 | — | `status.ps1` + 手工走一遍下单 |

> ⚠️ **命名重构最大的坑**：`book_id` 这种外键字段会同时出现在 Java 实体、MyBatis-Plus 的 `LambdaQueryWrapper`、JdbcTemplate 的裸 SQL、Mapper XML、Python 的 SQL、前端的 `bookId` 变量里。
> **漏改任何一处，症状都是同一个：`Unknown column 'xxx' in 'field list'`。** 遇到这个报错，就回到【七】清单逐项核对。

**具体的命名映射表**（新项目统一按这个来）：

| 旧（参考项目） | 新（重构后） |
|----------------|--------------|
| 库 `book_cycle` | `secondhand_shop`（**独立新库**，与参考项目互不干扰） |
| 表 `book` | `product` |
| 表 `order_info` | `product_order` |
| 表 `cart` | `cart_item` |
| 表 `user_behavior` | `user_behavior`（语义本就通用，保留） |
| 表 `address` / `user` / `category` | 保留 |
| 字段 `book_id` | `product_id` |
| 字段 `book_title` | `product_title` |
| 字段 `isbn` | `brand`（品牌/型号，可选字段） |
| 字段 `condition_level` | 保留（"成色"，二手商品通用） |
| 字段 `seller_id` | 保留 |
| 接口 `/api/books` | `/api/products` |
| 接口 `/api/admin/books/*` | `/api/admin/products/*` |
| 路由 `/book/list` | `/product/list` |
| 路由 `/book/detail/:id` | `/product/detail/:id` |
| 路由 `/book/publish` | `/product/publish` |
| Java 类 `Book` / `BookController` / `BookService` / `BookMapper` | `Product` / `ProductController` / `ProductService` / `ProductMapper` |
| Java 包 `com.bookcycle` | **保留**（改包名收益低、风险高，不值当） |
| 前端目录 `views/book/` | `views/product/` |

### 4.4 两套端口与数据库分配（避免两个项目冲突）✅

既然"参考项目"和"我的项目"要能**同时运行**（方便对照调试），端口必须错开：

| 服务 | 参考项目（`D:\code-javaweb\book`） | 我的项目（工作目录） |
|------|-----------------------------------|---------------------|
| MySQL | **3306**（系统服务，两项目共用同一实例） | 3306 |
| 数据库 | `book_cycle`（旧 schema） | **`secondhand_shop`**（新 schema） |
| 后端 | 8080 | **8081** |
| AI 服务 | 8000 | **8001** |
| 前端 | 5173 | **5174** |

> **为什么数据库共用一个实例、只分库？**
> MySQL 服务本身就是"一个实例跑多个库"，两套 schema 放同一实例里互不干扰，最省事。
> 3307 那个临时实例已经停掉了（数据仍留在 `D:\code-javaweb\.mysql-book\data`，需要时可恢复），**以后统一用 3306**。
>
> ⚠️ 前端端口变了，`vite.config.js` 里的代理目标也要跟着改：`8081` 和 `8001`。

---

## 五、迭代路线图：从最小案例到完整系统

> 每一阶段都遵循同一个节奏：**先跑通 → 再读懂 → 再改一处 → 再验证**。
> 每阶段结束都会 `git commit`，出问题随时回滚。

### 阶段 A：最小垂直切片 —— 我要能登录（打通全栈认知）

**为什么从这里开始**：登录是唯一一条"前端表单 → 接口 → 数据库 → 返回 → 状态持久化 → 影响后续请求"的完整闭环。做完它就理解了整个分层。

| 步骤 | 做什么 | 你要亲手改的地方 |
|------|--------|------------------|
| A1 | 打开 `http://localhost:5173/login`，用 `admin/admin123` 登录，**打开 F12 Network 面板**，观察 `/api/user/login` 的请求头、响应体、以及 localStorage 里出现的 `token`/`user` | 只观察，不改 |
| A2 | 读懂登录链路：`Login.vue` → `api/user.js` → `UserController.login` → `UserServiceImpl.login` → `JwtUtil` | 画出调用链 |
| A3 | 读懂鉴权链路：`request.js` 请求拦截器加头 → `JwtInterceptor` 校验 → `UserContext` 取 userId | 在 `JwtInterceptor` 打一行日志自己验证 |
| A4 | **改造①：密码 BCrypt 加密**（对应问题 #1） | `pom.xml` 加 `spring-security-crypto`（只要 crypto，不引整个 Security）；`register` 加密；`login` 用 `matches()`；给 6 个存量账号写数据迁移 SQL |
| A5 | **改造②：注册分页插件**（对应问题 #2） | 新建 `config/MybatisPlusConfig.java`，注册 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)` |
| A6 | 验证：重新登录成功；`GET /api/admin/users?page=1&size=2` 真的只返回 2 条 | 用 `Invoke-RestMethod` 或浏览器验证 |

**验收**：`status.ps1` 全绿；用 BCrypt 后的密码能正常登录；分页确实截断了数据。
**面试怎么说**：*"我发现项目密码是明文比对，改成了 BCrypt。同时排查出 MyBatis-Plus 分页插件没注册导致 selectPage 静默失效——这类问题不报错但结果错，属于隐蔽 bug。"*

---

### 阶段 B：商品列表与详情（核心读链路 + 第一次业务改造）

| 步骤 | 做什么 | 动手点 |
|------|--------|--------|
| B1 | 读懂 `BookList.vue`：搜索/分类筛选/分页参数怎么传 | 观察 Network |
| B2 | 读懂 `BookController.list()`：它是 **JdbcTemplate 裸 SQL**，不是 MyBatis-Plus | 对比 `BookServiceImpl` 的写法，体会两种方式差异 |
| B3 | **改造③：修掉详情接口的副作用与 NPE**（问题 #3） | `BookServiceImpl.getById()` 拆成"纯查询"和"独立记浏览量"两步，加 null 判断 |
| B4 | **改造④：状态码枚举化**（问题 #7） | 新建 `common/BookStatus.java` / `OrderStatus.java` 枚举，替换魔法数字 |
| B5 | **改造⑤：第一层文案改造**——把列表/详情页的"教材"改成"商品" | `BookList.vue`(8处) / `BookDetail.vue`(15处) |
| B6 | **改造⑥：修掉硬编码分类**（问题 #5） | `BookPublish.vue`、`BookDetail.vue` 改为从 `GET /api/categories/tree` 动态取，删掉 156/164 硬编码 |

**验收**：商品列表能搜、能筛、能翻页；详情页打开后浏览量只 +1（不再被反复叠加）；发布页分类下拉是真实数据。
**面试怎么说**：*"修复了详情接口在 GET 里写库的问题，把副作用拆出去；还把硬编码的分类 ID 改成接口动态获取，原来的 ID 根本对不上数据库。"*

---

### 阶段 C：发布与审核（写链路 + 状态机入门）

| 步骤 | 做什么 | 动手点 |
|------|--------|--------|
| C1 | 读懂发布流程：`BookPublish.vue` → `POST /api/books` → 落库 `status=0`（待审核） | 观察插入后的 status |
| C2 | 读懂审核流程：`BookReview.vue` → `PUT /api/admin/books/{id}/approve` → status=1 | 用 admin 账号走一遍 |
| C3 | **改造⑦：`picture` 上传链路**（商品图片比教材图更通用） | `UploadController`，注意 `upload.path` 是相对路径，切目录启动会跑偏 |
| C4 | **改造⑧：文案改造**——"发布教材"→"发布商品"、"教材审核"→"商品审核" | `Layout.vue`(4处，导航菜单) / `BookPublish.vue`(8处) / `BookReview.vue`(6处) |

**验收**：能发一个商品 → 管理员审核通过 → 前台能看到；导航菜单文字全变"商品"。

---

### 阶段 D：暂存架（购物车）—— 引出缓存思想

| 步骤 | 做什么 | 动手点 |
|------|--------|--------|
| D1 | 读懂 `CartController.list()` 的 N+1 问题（问题 #4） | 数一数 1 个购物车项查了几次库 |
| D2 | **改造⑨：消除 N+1** | 用 `selectBatchIds(bookIds)` 一次查完，再在内存里拼装 |
| D3 | **改造⑩：浏览量不再因加购而虚增** | 用"纯查询"方法替代 `bookService.getById()` |
| D4 | 文案改造 | `Cart.vue`(6处) |

> 🎯 **这里埋一个伏笔**：D2 的"一次批量查"其实也可以用 Redis 缓存整个商品信息来替代。做完 E/F 阶段后回头看这里，你会理解"缓存和批量查询解决的是不同问题"。

---

### 阶段 E：订单（最复杂的业务链路，务必画状态机）

**订单状态机（背下来）**：

```
   [创建]  status=0 待付款
     │ 买家 pay
     ▼
          status=1 已付款 ──── 卖家 ship ──▶ status=2 已发货
     │                                          │
     │ 买家 cancel                              │ 买家 complete
     ▼                                          ▼
   status=4 已取消                        status=3 已完成
   （同时把商品 status 恢复为 1）
```

| 步骤 | 做什么 | 动手点 |
|------|--------|--------|
| E1 | 用两个浏览器（或隐私窗口）分别登买家/卖家，完整走一遍下单→付款→发货→收货 | 每步查一次数据库 status |
| E2 | **改造⑪：并发超卖防护**（问题 #6） | 先用乐观锁（`UPDATE ... WHERE status=1` 判断影响行数），**这比直接上 Redis 分布式锁更能讲清原理** |
| E3 | 读懂 `OrderController` 的 `@Transactional` 加在 Controller 上（不规范） | 思考为什么通常应加在 Service 层 |
| E4 | 文案改造 | `OrderList.vue` / `OrderCheckout.vue`(8处) / `OrderManage.vue` |

**验收**：两个并发请求同时买最后一件商品，只有一个成功。
**面试怎么说**：*"下单链路我先用乐观锁解决超卖，通过 UPDATE 影响行数判断，比分布式锁更轻量；如果是分布式部署才会考虑 Redis 锁。"*

---

### 阶段 F：管理后台 + 收尾

| 步骤 | 做什么 |
|------|--------|
| F1 | 读懂 `AdminServiceImpl.getStatistics()` 的统计口径（`totalAmount` 只统计 status=3） |
| F2 | 文案改造 `Stats.vue` / `UserManage.vue` / `UserCenter.vue` |
| F3 | 全站文案终检：`grep -rn "教材\|书籍\|图书" src/` 应只剩注释 |
| F4 | 全流程回归 + `git tag stage-1-业务改造完成` |

---

### 阶段 G：Redis 集成（你的重点诉求，单独成阶段）

> ⚠️ **前置条件：本机需要装 Redis。** 你之前没学过 Redis，所以这里先讲"装在哪、怎么连"，再讲"用在哪"。
> 我建议的安装方式见【十一】决策记录 **D3**（推荐 Memurai，Windows 原生 Redis 兼容实现，装完即用）。

#### G0：Redis 基础（半天，必须动手敲）

用 `redis-cli` 亲手敲一遍：`SET/GET/DEL/EXPIRE/TTL/KEYS/INCR/HSET/HGETALL/LPUSH/LRANGE`，理解"内存键值库 + 过期时间"这两个核心概念。
**验收**：能解释 `SET k v EX 60` 和 `TTL k` 的关系。

#### G1：接入 Spring（配置层）

| 步骤 | 内容 |
|------|------|
| G1.1 | `pom.xml` 加 `spring-boot-starter-data-redis`（含 Lettuce 客户端 + Spring Cache 抽象） |
| G1.2 | `application.yml` 配 `spring.data.redis.host/port`（**注意 Spring Boot 3 是 `spring.data.redis`，不是 2.x 的 `spring.redis`**，这是个高频坑） |
| G1.3 | 新建 `config/RedisConfig.java`：自定义 `RedisTemplate<String,Object>` 的序列化器（**默认 JDK 序列化会让 key 变成乱码 `\xac\xed...`，必须换成 String + Jackson**） |
| G1.4 | `@EnableCaching` + 写一个 `/api/debug/redis/ping` 测试接口 |

#### G2：场景① 缓存热门商品列表（对应 `RecommendServiceImpl.getHotBooks`）

- 加 `@Cacheable(value="hotBooks", key="#limit")`
- 更新/审核/下架商品时 `@CacheEvict`
- **必须产出对比证据**：写个脚本连续打 100 次接口，记录"第一次（查库）"和"后续（命中缓存）"的耗时差；再用 `redis-cli INFO stats` 看 `keyspace_hits` 增长。
- **答辩话术**：*"热门榜是典型的读多写少，用 `@Cacheable` 缓存，TTL 5 分钟，商品变更时主动 `@CacheEvict` 避免脏数据。"*

#### G3：场景② 缓存 AI 推荐结果

- AI 服务的响应包含大模型调用，**耗时最长、最适合缓存**
- 缓存键用 `ai:recommend:{query}:{limit}`，`key` 里带查询词
- 注意：**推荐结果对时效性敏感**，TTL 设短（如 1 小时），并说明"为什么选这个 TTL"

#### G4：场景③ 存用户会话（顺手解决一个真 bug）

- 现状：`ai-service/main.py:30` 的 `sessions` 是**进程内 dict，重启即丢**，多实例不共享
- 改造：把会话历史写进 Redis（`ai:session:{session_id}`，用 `LPUSH`+`LTRIM` 保留最近 N 轮）
- **这条最有说服力**：说明你不是"为了用而用"，而是 Redis 恰好解决了真实缺陷
- 进阶（可选）：JWT 登出黑名单（因为 JWT 无状态，服务端主动登出必须靠外部存储）

**验收**：三个场景都有可截图证据；`redis-cli KEYS "hotBooks*"` 能看到缓存键；关掉 Redis 后应用能降级不崩（加分项）。

---

### 阶段 H：AI 模块学习与改造

| 步骤 | 内容 |
|------|------|
| H1 | **先配 Key**：现在没有 API Key，AI 是"降级模式"（纯 BM25 检索，`llm_enabled=false`）。去智谱开放平台注册拿免费 Key 填进 `ai-service/.env`，重启即可 |
| H2 | 读懂 RAG 链路：`retriever.py`（BM25 打分）→ `llm.py`（拼 prompt 调大模型 / fallback 切模型 / Function Calling 查库存） |
| H3 | **改造⑫：Java 调用 Python**（补上架构图里缺的那条线） | 新建 Java 侧 `AiClient`（用 `RestClient`/`WebClient`）调用 Python；在商品详情页展示"AI 推荐理由" |
| H4 | **改造⑬：让 AI 用上 `user_behavior` 表**（问题 #10） | 现状推荐只看订单表；补上"浏览/加购"行为写入，让推荐数据源变厚 |
| H5 | 文案改造：`AiChat.vue` 的提示语（3 处） |

**验收**：详情页能看到 AI 生成的推荐理由；AI 推荐结果会随你的浏览行为变化。

---

### 阶段 I：测试与交付

| 步骤 | 内容 |
|------|------|
| I1 | 单元测试：项目里已有 1 个 `RecommendServiceImplTest`（Mockito），照它的样子给 `UserServiceImpl`（登录/注册）、订单状态流转补测试 |
| I2 | 实验报告：需求分析 → 系统设计（架构图/ER图/状态机）→ 实现 → 测试 → 总结 |
| I3 | 答辩 PPT：重点放**问题-方案-证据**（明文密码→BCrypt、超卖→乐观锁、N+1→批量查、无缓存→Redis 三场景） |
| I4 | 演示视频：4 服务启动 → 完整下单链路 → Redis 命中证据 → AI 对话 |
| I5 | 补 README：让陌生人能按文档把项目跑起来 |

---

## 六、工期估算（按你单人、边学边做）

| 阶段 | 内容 | 预估工时 | 累计 |
|------|------|----------|------|
| 0 | 环境搭建 | ✅ 已完成 | — |
| A | 登录 + 密码加密 + 分页插件 | 1 天 | 1 天 |
| B | 商品列表/详情 + 文案改造 | 1.5 天 | 2.5 天 |
| C | 发布与审核 | 1 天 | 3.5 天 |
| D | 暂存架 + N+1 | 0.5 天 | 4 天 |
| E | 订单状态机 + 超卖 | 1.5 天 | 5.5 天 |
| F | 后台 + 文案终检 | 0.5 天 | 6 天 |
| G | **Redis 集成（含学基础）** | 2 天 | 8 天 |
| H | **AI 改造（含配 Key、学 RAG）** | 2 天 | 10 天 |
| I | 测试 + 报告 + PPT + 视频 | 3 天 | 13 天 |

> 约 **2 周**。如果时间紧，可压缩顺序：**A → B → F（文案）→ G（Redis）→ I（文档）**，把 C/D/E/H 作为"有则加分"。

---

## 七、改造点总清单（可直接当 checklist 用）

| 编号 | 改造点 | 阶段 | 类型 | 风险 |
|------|--------|------|------|------|
| ① | 密码 BCrypt 加密 + 数据迁移 | A | 安全 | 低 |
| ② | 注册 MyBatis-Plus 分页插件 | A | Bug 修复 | 低 |
| ③ | 详情接口副作用拆分 + NPE 防护 | B | Bug 修复 | 低 |
| ④ | 状态码枚举化 | B | 可维护性 | 低 |
| ⑤ | 第一层文案改造（前端 85 处 + 后端 7 处） | B/C/D/E/F/H | 业务 | 极低 |
| ⑥ | 分类硬编码 → 动态接口 | B | Bug 修复 | 低 |
| ⑦ | 图片上传路径健壮性 | C | 健壮性 | 低 |
| ⑧ | 暂存架 N+1 消除 | D | 性能 | 低 |
| ⑨ | 订单并发超卖（乐观锁） | E | 并发 | **中** |
| ⑩ | Redis 场景①热门榜缓存 | G | 缓存 | 低 |
| ⑪ | Redis 场景②AI 推荐结果缓存 | G | 缓存 | 低 |
| ⑫ | Redis 场景③AI 会话持久化 | G | 缓存/架构 | 中 |
| ⑬ | Java 调用 Python（推荐理由） | H | AI 集成 | 中 |
| ⑭ | `user_behavior` 行为数据写入 | H | AI/推荐 | 中 |
| ⑮ | 表名/接口名/字段名"去书籍化"（第二层） | 选做 | 结构重构 | **高** |

---

## 八、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 改一半跑不起来 | 进度崩 | **每阶段 git commit**；改前先 `git tag`；`status.ps1` 当回归测试 |
| JDK 24 与老依赖不兼容 | 编译失败 | 已解决 Lombok；若再遇问题，装 JDK 17 并只改 Maven 的 `JAVA_HOME` 指向，不动系统 PATH |
| MySQL 3307 实例被误删 | 数据全丢 | 数据目录已独立；**建议现在就把 `D:\code-javaweb\.mysql-book\data` 复制一份备份** |
| Redis 装不上 | G 阶段卡住 | G1~G3 可改用 `spring-boot-starter-cache` + Caffeine 先做通，**答辩时说明"缓存抽象一致，只是实现换了"**（不如 Redis 亮眼，但不会卡死） |
| 第二层重构改崩 | 大范围返工 | **放到最后做**，且优先只改路由和前端文案 |
| AI Key 额度耗尽 | 演示失败 | `.env` 已配多模型 fallback（DeepSeek→智谱→硅基流动→百炼），多注册两家免费额度 |
| 时间不够 | 交付不完整 | 按【六】的压缩顺序砍阶段，保证 A/B/F/G/I 五块完整 |

---

## 九、学习资源（只给必要的最少集）

| 主题 | 看什么 |
|------|--------|
| Spring Boot 分层 | 先把自己项目的 Controller→Service→Mapper 三层画清楚，比看教程管用 |
| MyBatis-Plus | 官方文档的「分页插件」和「条件构造器」两节 |
| Redis | `redis-cli` 官方命令文档 + Spring Data Redis 的 `RedisTemplate` |
| Spring Cache | `@Cacheable/@CachePut/@CacheEvict` 三个注解的 `key`/`condition`/`unless` 参数 |
| JWT | 理解 Header.Payload.Signature 三段式结构即可，不必深挖算法 |
| RAG / BM25 | 读你自己项目的 `retriever.py`（才 50 行）+ 理解"关键词打分排序"就够 |
| Vue 3 | `<script setup>` 语法 + `ref/reactive/onMounted` + `vue-router` |

> ❌ **不要**在现阶段学：微服务、Docker、K8s、消息队列。与你的目标无关，只会分散精力。

---

## 十、下一步做什么

### 阶段 0 已完成 ✅
参考项目四服务全部跑通，且**统一跑在 3306**：
- MySQL 3306（系统服务）：`book_cycle` 库 7 张表已导入
- 后端 8080 / AI 8000 / 前端 5173：全部运行中，`status.ps1` 全绿

### 现在进入阶段 1：搭建"我的项目"骨架

**你要做的第一件事**（不急，先建立认知）：
1. 打开 `http://localhost:5173` ，点右上角**登录**
2. 用 `admin` / `admin123` 登录
3. **按 F12 打开开发者工具 → Network 面板**，重新登录一次，点开 `login` 这条请求，观察三样东西：
   - **请求体**：`{username, password}` —— 前端发了什么
   - **响应体**：`{code, message, data:{..., token}}` —— 后端回了什么
   - **Application → Local Storage**：登录后多了 `token` 和 `user` 两项

**然后告诉我你看到了什么**（或有什么看不懂），我再带你进入阶段 1 的第一步：建"我的项目"的数据库和最小后端骨架。

> 📌 **纪律提醒**：接下来每一步我都会**先讲意图和调用关系，再让你自己动手**，只有你明确说"帮我写"我才写代码。
> 参考项目只当标本读，一行都不改。

---

## 十一、决策记录

### D1：业务改造做到哪一层？✅ **已确认：方案丙（彻底重构命名）**
- ~~方案甲~~：只做第一层（文案+展示）。—— 已否决
- ~~方案乙~~：第一层 + 只改路由。—— 已否决
- **方案丙 ✅ 采纳**：表名 `book`→`product`、接口 `/api/books`→`/api/products`、字段 `book_id`→`product_id`、路由 `/book/*`→`/product/*`、Java 类 `Book*`→`Product*` 全部改。
> 执行顺序**必须**按【4.3】的 1→7 步走（先冻数据库 → 后端实体 → Controller → Python → 前端 api → 前端文案 → 回归），否则必然一改就崩。
> 好消息：因为新项目是**从零写**的，我们一开始就用新命名，**根本不存在"改"的过程** —— 参考项目只当标本看。

### D2：JDK 策略？✅ **已确认：方案甲**
- **方案甲 ✅ 采纳**：留在 JDK 24（`D:\jdk`），靠 Lombok 1.18.44 + `proc:full` 适配。已实测跑通。
- ~~方案乙~~：装 JDK 17。—— 暂不需要
> 若后续遇到更多 JDK 24 兼容问题，再花 20 分钟装 JDK 17 并让 Maven 指向它。

### D3：Redis 怎么装？⏸ **暂缓（以后再说）**
- 方案甲：Memurai（Windows 原生、Redis 协议兼容、`winget` 可装）
- 方案乙：WSL2 里装真 Redis（最正统，首次约 30 分钟）
- 方案丙：Docker Desktop 跑 `redis:7`
> 阶段 G 才用到，到时候再定。**不阻塞任何前期工作。**

### D4：数据库与端口分配 ✅ **已确认**
- 参考项目：`book_cycle` 库 / 后端 8080 / AI 8000 / 前端 5173 —— 保持不变
- 我的项目：`secondhand_shop` 库 / 后端 **8081** / AI **8001** / 前端 **5174**
- MySQL 统一用 **3306**（系统服务）；3307 临时实例已停用（数据保留在磁盘）
> ⚠️ 你自己的项目库名若想换，随时说，改一处文档 + 一处 `env.ps1` 即可。

---

## 十二、阶段 0 已完成产物 ✅

```
D:\code-javaweb\Second-handTradingSystem\      ← 【我的项目】工作目录
├── 交接.md              # 原始交接文档
├── PLAN.md              # ← 本文件，施工图
├── .gitignore
├── scripts\             # 运维脚本（全部 UTF-8 with BOM，PowerShell 5.1 才不乱码）
│   ├── env.ps1          # 环境探测总闸（JDK / Maven / 端口 / 数据库账号）
│   ├── db-init.ps1      # 建库 + 导入建表 SQL（支持 -DbName / -Force）
│   ├── start-backend.ps1
│   ├── start-ai.ps1
│   ├── start-web.ps1
│   ├── status.ps1       # 一键体检：4 端口 + 6 条接口链路 + 数据库
│   └── stop-all.ps1     # 只停应用，不动 MySQL 服务
└── .git                 # 已有提交，可回滚

D:\code-javaweb\book\          ← 【参考项目】原始开源项目，已跑通，保持不动
D:\code-javaweb\.mysql-book\   ← 临时 MySQL 实例（3307，已停用，数据保留备用）
```

**当前运行状态（`status.ps1` 实测全绿）**：

| 服务 | 端口 | 状态 |
|------|------|------|
| MySQL（系统服务 MySQL80） | 3306 | ✅ `book_cycle` 7 张表，book 100 / user 6 |
| 后端 Spring Boot | 8080 | ✅ 已连 3306 |
| AI 微服务 FastAPI | 8000 | ✅ BM25 加载 54 条语料 |
| 前端 Vue3 | 5173 | ✅ 首页正常，代理链路通 |

**下一步**：进入【五】的阶段 A（最小垂直切片：登录），我带你一步步做，每完成一小步就停下来讲解。

**下一步**：确认【十一】的 D1/D2/D3 三个决策 → 我从阶段 A 开始带你做，一次只做一小步，每步都验证。
