# 后端代码风格改造与 Sa-Token 迁移方案

> **目标**：把 Ai-campus 后端改造成黑马点评（hm-dianping）那种"Controller 只转发、Service 接口 + 实现、统一 Result 返回"的经典三层结构，同时把 JWT 换成 Sa-Token。接口契约、数据库、业务规则全部不变，现有功能不退化。
>
> **基线**：`graduation-main`（20b1d23，工作区干净）
> **改造分支**：`refactor/hmdp-style-satoken`（从基线新建，不动 main）
> **方案状态**：待确认后执行

---

## 1. 调研结论

### 1.1 两个项目的风格差异

| 维度 | 黑马点评（hm-dianping） | 当前 Ai-campus | 改造动作 |
| --- | --- | --- | --- |
| 统一返回 | `Result{success, errorMsg, data, total}`，`Result.ok()/fail()` | `R{code, message, data}`，`R.ok()/fail()` | 新建 `Result`，保留 `code/message` 兼容字段 |
| 分层 | Controller → `IXxxService` → `XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx>` | Controller → `XxxService`（具体类） | 51 个 Service 拆接口 + 实现，单表服务继承 `ServiceImpl` |
| 依赖注入 | `@Resource` 字段注入 | `@RequiredArgsConstructor` + `private final` | 统一 `@Resource` |
| 数据访问 | `ServiceImpl` 自带 `baseMapper`，`query()/lambdaQuery()/save()/getById()` | 注入 Mapper + `LambdaQueryWrapper` | 单表 CRUD 改用内置方法，跨表才注入 Mapper |
| 包结构 | `controller/service/impl/mapper/entity/dto/utils/config` | `module/<业务域>/controller/service/mapper/entity/dto/vo` | 保留业务域分组，内部结构与黑马点评一致 |
| 鉴权 | Redis 存 token + `UserHolder` ThreadLocal + 双拦截器 | JWT 无状态 + Redis 黑名单/撤销时间 + `UserContext` | Sa-Token 服务端会话 + 单拦截器 + `kickout` |
| Controller 职责 | 收参数、调 Service、返回 Result | 已比较薄，但仍有分页上限、开关判断、`if/throw` 共 16 处 | 全部下沉到 Service，方法体 ≤ 5 行 |
| 注释 | 业务说明为主 | 大量 `P1-1`、`BUG-06`、`R5`、`共享约定 #N` 内部编号 | 改为业务语言，保留必要的并发/安全说明 |

### 1.2 规模数据

- 437 个 Java 文件（main 约 382，test 55）
- 46 个 Controller、275 个接口、51 个 Service、63 个 Mapper、43 个 Entity、51 个 DTO、45 个 VO
- 124 个文件使用 `R`，263 处 `R.ok(...)`
- JWT 只集中在 4 个核心文件：`JwtUtils`、`JwtInterceptor`、`AdminInterceptor`、`AuthService`
- WebSocket 已用一次性 ticket（`ChatWsTicketService` / `DrawGuessWsTicketService`），与 JWT 解耦
- 前端两个 `request.js` 依赖 `res.code === 200`、`res.message`；AI SSE 有 1 处兜底解析 `code/message`
- 55 个测试类，全部 Mockito 单测（无 `@SpringBootTest`），适合做改造安全网
- 预计触碰约 190 个文件：新增约 85（Result、40 个接口、40 个实现、Token 组件），修改约 100，删除 4

### 1.3 现状中要保留的优点

- 已经是 Controller / Service / Mapper / Entity 分层，改造是"接口化 + 返回体统一"，不是推倒重来
- 63 个 Mapper 全部 `BaseMapper` + 注解 SQL，没有 XML，符合 MyBatis-Plus 风格
- 275 个接口有《接口说明书》，20 类 e2e 回归，可作为契约不变的验收依据
- 单测不依赖外部服务，能在每批改造后快速验证

---

## 2. 改造原则（硬约束）

1. **接口契约不变**：URL、HTTP 方法、请求参数、响应 `data` 结构、WebSocket 消息、SSE 事件格式全部不变。
2. **数据库不变**：表结构、SQL、迁移脚本零改动。
3. **业务规则不变**：审核、预约、活动、候补、聊天、AI 等逻辑只搬家，不改语义。
4. **前端零改动**（推荐方案）：`Result` 保留 `code/message` 兼容字段，两个 `request.js` 和 AI SSE 无需修改。
5. **每批可编译、可测试、可回滚**：分支内按批次提交，每批跑完编译 + 单测 + 对应 e2e 才进入下一批。
6. **不为了形式而拆**：定时任务、限流器、校验器、权限组件保留为普通 `@Component`，不强行套 `IXxxService`。

---

## 3. 目标风格规范（验收清单）

### 3.1 目录与分层

```text
module/<业务域>
├── controller      // 只收参数、调 Service、返回 Result
├── service         // IXxxService 接口
│   └── impl        // XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx>
├── mapper          // BaseMapper
├── entity          // @TableName 实体
├── dto / vo        // 请求 / 响应对象
```

规则：

- Controller 方法体不超过 5 行，不出现 `if`、`throw`、配置读取、Mapper 注入。
- Service 接口只声明业务方法，不暴露 Mapper。
- **单表为主**的 Service 继承 `ServiceImpl<XxxMapper, Xxx>`，CRUD 用 `save/getById/updateById/removeById/query().eq(...)`。
- **跨表编排**的 Service（如 `IdleService` 用 3 个 Mapper）只做 `IXxxService + XxxServiceImpl`，内部注入所需 Mapper，不强行绑定一个主表。
- 禁止 Controller 直接注入 Mapper（现状 4 处，改造时清理）。

### 3.2 统一返回体 `Result`

黑马点评同款主结构 + 两个兼容字段：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result implements Serializable {
    private Boolean success;   // 成功 true / 失败 false
    private String errorMsg;   // 失败信息
    private Object data;       // 业务数据
    private Long total;        // 分页总数（可空）
    private Integer code;      // 兼容字段：前端拦截器
    private String message;    // 兼容字段：前端拦截器
}
```

语义：

- `Result.ok(data)` → `success=true, code=200, message="success"`
- `Result.ok(data, total)` → 同时填 `total`
- `Result.fail(code, msg)` → `success=false, errorMsg=msg, message=msg`
- `Result.fail(ResultCode)` / `Result.fail(ResultCode, msg)` → 沿用现有错误码枚举

分页仍返回 `PageResult{total, pages, list}`，不改成 `Result.ok(list, total)`，避免 53 + 21 个前端页面全部改分页读取。

### 3.3 Service 方法的返回类型

**采用"Service 返回业务对象，Controller 包 Result"**：

```java
// Good
public PageResult<IdleItemVO> list(String keyword, String category, int pageNum, int pageSize) { ... }

// Controller
return Result.ok(idleService.list(keyword, category, pageNum, pageSize));
```

理由：项目里 Service 之间互相调用有 152 处。如果 Service 统一返回 `Result`，内部调用要 `.getData()`、还要处理失败分支，会把简单逻辑复杂化。黑马点评本身也有 Controller 直接 `Result.ok(...)` 的写法（`ShopController#saveShop`）。

> 如果你坚持"Service 也必须返回 Result"，可以作为可选项，但改造量和后续维护成本会明显增加。

### 3.4 注入与命名

- 统一 `@Resource`（`jakarta.annotation.Resource`，Spring Boot 3）。
- 命名：`IXxxService` / `XxxServiceImpl` / `XxxController` / `XxxMapper` / `XxxDTO` / `XxxVO`。
- 实体保留 `@TableName`、`@TableId(type = IdType.AUTO)`。

### 3.5 注释

- 类头：这个类做什么、对应哪个业务。
- 方法头：参数含义、返回、关键业务规则。
- 删除 `P1-1`、`BUG-06`、`R5`、`共享约定 #N` 等内部编号，改成业务语言。
- 保留并发、安全、状态机的必要说明，不写"修复了什么 bug"的流水账。

---

## 4. Sa-Token 迁移设计

### 4.1 依赖调整

```xml
<!-- 新增 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
    <version>1.46.0</version>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
    <version>1.46.0</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

<!-- 删除 -->
<dependency>io.jsonwebtoken:jjwt-api</dependency>
<dependency>io.jsonwebtoken:jjwt-impl</dependency>
<dependency>io.jsonwebtoken:jjwt-jackson</dependency>
```

### 4.2 配置

```yaml
sa-token:
  token-name: Authorization       # 前端继续用 Authorization: Bearer <token>
  token-prefix: Bearer
  timeout: 604800                 # 7 天，与现 JWT 一致
  active-timeout: -1              # 不做活跃续期，固定 7 天
  is-concurrent: true             # 允许多端同时登录
  is-share: false                 # 每次登录生成新 token
  is-read-cookie: false
  is-read-header: true
  is-read-body: false
  token-style: uuid
  is-log: false
```

Redis 复用项目已有的 `RedisTemplate<String, Object>`（`RedisConfig` 已配置 `StringRedisSerializer` + `GenericJackson2JsonRedisSerializer`）。

> 序列化风险：Sa-Token 的 `SaSession` 用 Jackson 多态序列化，需要实测"登录 → 重启后端 → 会话仍在"。如果 `sa-token-redis-template` 不兼容，按顺序切换备选：`sa-token-redis-jackson` → 自定义 `SaTokenDao`（`StringRedisTemplate` + JSON）→ `sa-token-redisson`（复用现有 `RedissonClient`）。

### 4.3 双账号体系（学生 / 管理员）

两套独立的 `StpLogic`，会话 key 隔离，避免 user id 和 admin id 相同导致会话串号：

```java
public final class StpKit {
    public static final StpLogic STUDENT = new StpLogic("student"); // satoken:student:*
    public static final StpLogic ADMIN   = new StpLogic("admin");   // satoken:admin:*
}
```

- 学生 token 只能通过学生拦截器，管理员 token 只能通过管理拦截器，天然解决"管理员 Token 不能当学生 Token 用"。
- 前端 token 仍存 `localStorage`，仍用 `Authorization: Bearer`，前端零改动。

### 4.4 登录 / 校验 / 踢人对照

| 场景 | 原 JWT 实现 | Sa-Token 实现 |
| --- | --- | --- |
| 学生登录 | `jwtUtils.generate(uid, "student")` | `StpKit.STUDENT.login(uid)`，返回 `getTokenValue()` |
| 管理员登录 | `jwtUtils.generate(adminId, "admin")` | `StpKit.ADMIN.login(adminId)` |
| 学生请求校验 | 解析 JWT + 黑名单 + 撤销时间 + 临时密码判断 | `STUDENT.getLoginIdByToken(token)`，再查禁用状态 |
| 管理员请求校验 | 解析 JWT + `requireActive` + iat 比较 | `ADMIN.getLoginIdByToken(token)` + 查库状态 |
| 禁用学生 | 写 Redis 黑名单 + `auth:revoked-at` | `STUDENT.kickout(uid)` |
| 管理员删除/禁用/降权 | 写 `auth:revoked-at` | `ADMIN.kickout(id)` |
| 改密 / 重置密码 | `auth:revoked-at` | `kickout(uid)`，强制重登 |
| 临时密码到期 | JWT exp 截短 | `SaLoginModel.setTimeout(剩余秒数)` |
| 7 天有效期 | JWT exp | `sa-token.timeout=604800` |

**收益**：删掉 `auth:blacklist:*`、`auth:revoked-at:*`、"撤销时间 vs iat"这套绕的逻辑，权限代码会明显变短、变好懂。

### 4.5 兼容点

| 场景 | 处理方式 |
| --- | --- |
| SSE `EventSource` 不能带 Header | 拦截器继续支持 `?token=`，用 `getLoginIdByToken` 校验 |
| WebSocket 握手 | 一次性 ticket 机制不变，`issue(uid)` 的 uid 仍来自 `UserContext` |
| 维护模式 | `MaintenanceInterceptor` 改为返回 `Result.fail(503, "系统维护中")` |
| 未登录响应 | 维持 HTTP 200 + `code=401`，前端 `responseAction(401)` 继续跳登录 |
| 旧 JWT | 切换后全部失效，用户需重新登录（一次性影响，可接受） |

### 4.6 删除清单

- `utils/JwtUtils.java`
- `interceptor/JwtInterceptor.java`
- `pom.xml` 中 3 个 jjwt 依赖
- `application.yml` 中 `jwt.secret` / `jwt.expire-days`
- `AdminPermissionService.revokeToken/restoreToken` 及相关 Redis key
- `AdminUserService` 中 `auth:blacklist` 写入逻辑
- `JwtUtilsTest`、`JwtInterceptorTest` 改写为 Sa-Token 版本

---

## 5. 分批实施计划

| 批次 | 范围 | 主要文件 | 验收 | 提交 |
| --- | --- | --- | --- | --- |
| 0 基线 | 不改代码，建分支，跑现有单测 + 关键 e2e，存基线报告 | 0 | 基线全绿 | `chore: baseline before refactor` |
| 1 Sa-Token | pom、配置、`Result` 之外的鉴权链路：`TokenService`、`StpKit`、`LoginInterceptor`、`AdminInterceptor`、`AuthService`、`AdminUserService`、`WebMvcConfig`、`MaintenanceInterceptor`、`GlobalExceptionHandler`、WebSocket ticket 调用点、5 个鉴权测试类 | 约 30 | 编译 + 单测 + 登录/管理端/WS/SSE 冒烟 | `refactor(auth): migrate JWT to Sa-Token` |
| 2 Result | 新增 `Result`，全量替换 `R`（124 文件 / 263 处），异常处理、拦截器、46 Controller | 约 130 | 编译 + 单测 + e2e 01/02 + 接口冒烟 | `refactor(common): unify Result response` |
| 3 风格化 A | 简单模块：favorite、message、notice、partner、qa、report、friend、user、auth、site | 约 60 | 对应 e2e 05-12 | `refactor(modules): hmdp-style batch A` |
| 4 风格化 B | 核心模块：idle、lostfound、post、activity | 约 50 | e2e 03/04/05/08 | `refactor(modules): hmdp-style batch B` |
| 5 风格化 C | chat、drawgame、upload、admin、ai | 约 100 | e2e 13/14/15-17/18 + 管理端专项 | `refactor(modules): hmdp-style batch C` |
| 6 回归 | 全量单测 + 20 类 e2e + 专项 + 文档 | 文档 2-3 个 | 全部通过 | `test: full regression after refactor` |

每批固定 SOP：

1. 在当前分支上只改本批范围。
2. Docker Maven 编译：`mvn -B -DskipTests package`。
3. 单测：`mvn -B test`（55 类，约 298 项）。
4. 起 Compose 全栈，跑本批对应 e2e。
5. 提交并记录到《重构回归报告》。
6. 失败则 `git revert` 本批提交，修完再重提，不跨批堆问题。

### 5.1 Service 接口化例外名单（保留为普通组件）

- `ActivityReminderTask`、`ActivityStatusSyncTask`、`DrawGuessRoundTimer`（定时任务）
- `ChatRateLimiter`、`ChatContextValidator`（限流/校验组件）
- `AdminPermissionService`（权限组件，被拦截器直接调用）
- `SystemConfigHolder`（配置持有）
- `AiGatewayService`、`AgentModelClient`、`SensitiveWordService`（网关/客户端/基础设施）
- `AdminOperationAspect`（切面）

其余约 40 个业务 Service 拆 `IXxxService + XxxServiceImpl`。

### 5.2 单表 Service 继承 `ServiceImpl` 的建议映射

| Service | 继承 |
| --- | --- |
| `UserService` | `ServiceImpl<UserMapper, User>` |
| `IdleService` | `ServiceImpl<IdleItemMapper, IdleItem>`（另外注入预约/评价 Mapper） |
| `PostService` | `ServiceImpl<PostMapper, Post>` |
| `ActivityService` | `ServiceImpl<ActivityMapper, Activity>` |
| `LostFoundService` | `ServiceImpl<LostFoundMapper, LostFound>` |
| `NoticeService` | `ServiceImpl<NoticeMapper, Notice>` |
| `QuestionService` | `ServiceImpl<QuestionMapper, Question>` |
| `MessageService` | `ServiceImpl<MessageMapper, Message>` |
| `FavoriteService` | `ServiceImpl<FavoriteMapper, Favorite>` |
| `StudyPartnerService` | `ServiceImpl<PartnerMapper, Partner>` |
| `ReportService` | `ServiceImpl<ReportMapper, Report>` |
| `ChatService` | 跨表编排，不继承，注入所需 Mapper |
| `AdminContentService` / `StatsService` / `AuditService` | 跨表编排，不继承 |

> 实施时以实际主表为准；如果 `ServiceImpl` 自带方法与现有业务方法签名冲突（如 `update`、`save`、`list`），优先给业务方法改名（如 `updateItem`），不要覆盖 MP 内置方法。

---

## 6. 测试方案

### 6.1 环境

| 用途 | 方案 |
| --- | --- |
| 编译 / 单测 | Docker `maven:3.9-eclipse-temurin-21`（本机无系统 JDK/Maven，镜像已存在） |
| 全栈 | `docker compose up -d --build --wait`（MySQL / Redis / backend / student / admin） |
| e2e | Playwright（chromium 已安装），需设置 `E2E_TEST_PASSWORD`、`E2E_ADMIN_PASSWORD`、`PLAYWRIGHT_PATH` |
| 测试数据 | 复用 Compose 初始化脚本与演示账号；不新增数据库迁移 |

### 6.2 测试矩阵

| 层级 | 入口 | 覆盖 | 通过标准 |
| --- | --- | --- | --- |
| 编译 | `mvn -B -DskipTests package` | 全部 main 代码 | BUILD SUCCESS |
| 单测 | `mvn -B test` | 55 个测试类，约 298 项 | 0 失败，跳过项与基线一致 |
| 接口冒烟 | 新增 `scripts/smoke-api.sh`（建议） | 275 个接口的"未登录 401 / 登录 200"双态 + `Result` 结构 | 无 500、无结构错位 |
| 浏览器 e2e | `node tests/browser/e2e/run-all.cjs` | 20 类功能 | 与基线一致，0 失败 |
| 专项 | `campus-workflows.cjs`、`activity-schedule.cjs`、`agent-closure.cjs`、`rag-eval.cjs` | 候补、改约、日程、助手、RAG | 全部通过 |
| 手工 | 清单 | 登录/注册/验证码/找回密码/改密/禁用/多端登录/旧 token 失效 | 逐项打勾 |

### 6.3 功能对照清单（20 类）

认证与权限、管理员审核、闲置预约、活动报名签到、失物认领、学习搭子、校园问答、校园动态、公告、消息、收藏、举报、聊天、上传、AI 助手、AI 故障/配额、搜索、3D 校园、管理端、RAG 与资料。每类至少 1 条 e2e + 关键接口冒烟，结果写入回归报告。

### 6.4 鉴权专项（Sa-Token 必测）

1. 学生登录 → 带 token 访问受保护接口 → 200。
2. 学生 token 访问 `/api/admin/**` → 401/403。
3. 管理员登录 → 访问管理接口 → 200；管理员 token 访问学生接口 → 401。
4. 禁用学生 → 存量 token 立即 401；解封后重新登录可用。
5. 管理员删除/禁用/降权 → 存量 token 立即 401。
6. 改密/重置密码 → 旧 token 失效。
7. 临时密码登录 → 到期后 401。
8. 后端重启 → 已登录会话仍有效（Redis 持久化）。
9. 多端登录 → 两个 token 都可用；`kickout` 后都失效。
10. SSE `?token=`、WebSocket ticket 握手正常。

---

## 7. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
| --- | --- | --- | --- |
| Sa-Token 与现有 RedisTemplate 序列化不兼容 | 中 | 高（登录态丢失） | 首选 `sa-token-redis-template`；失败切 `sa-token-redis-jackson` / 自定义 Dao；"重启后会话仍在"作为验收 |
| `ServiceImpl` 方法与业务方法签名冲突 | 中 | 中 | 逐类编译；业务方法改名；不覆盖 MP 内置方法 |
| 拆接口后 Bean 注入/循环依赖 | 低 | 高（启动失败） | `@Resource` 字段注入；每批启动验证 |
| 返回体替换遗漏 | 中 | 中 | 全局 grep `R.`；接口冒烟脚本校验 `success/code/data` |
| 前端契约被破坏 | 低 | 高 | `Result` 保留 `code/message`；e2e 全量 |
| AI 模块复杂逻辑被误改 | 中 | 高 | 只改分层与返回体，不改 prompt/检索/网关；AI 专项回归 |
| 测试环境不稳定 | 中 | 中 | 固定 Compose 环境、复用基线账号；每批记录环境信息 |

---

## 8. 回滚

- 全程在新分支 `refactor/hmdp-style-satoken`，`graduation-main` 不动。
- 每批一个 commit，可单独 `git revert`。
- 整批回滚：`git checkout graduation-main`。
- 数据库零变更，无需数据回滚。
- Sa-Token 切换后旧 JWT 失效，回滚到 JWT 分支后需重新登录（一次性）。

---

## 9. 交付物

1. 新分支 `refactor/hmdp-style-satoken` 与改造后代码。
2. 《重构回归报告》（`文档/04-测试与实验/重构回归报告.md`）：基线对比、每批测试结果、遗留问题。
3. 《变更说明》：模块清单、`Result` 字段说明、Sa-Token 配置与运维说明。
4. 本方案文档。

---

## 10. 待确认事项（默认按推荐执行）

| # | 事项 | 推荐 | 备选 |
| --- | --- | --- | --- |
| 1 | 包结构 | 保留 `module/<业务域>` 分组，内部结构黑马点评化 | 完全扁平化成 hmdp 七包（风险高、同名冲突多） |
| 2 | 返回体 | `Result` 保留 `code/message` 兼容字段，前端零改动 | 纯 `Result{success,errorMsg,data,total}`，前端改 3 个文件 |
| 3 | 注入方式 | 全量 `@Resource` | 保留构造器注入 |
| 4 | 改造范围 | 全模块分批改造 | 只改学生端核心业务，AI/管理端保留现状 |
| 5 | 分支名 | `refactor/hmdp-style-satoken` | 自定义 |

如无异议，按推荐方案执行：先建分支，跑基线，再从批次 1（Sa-Token）开始。
