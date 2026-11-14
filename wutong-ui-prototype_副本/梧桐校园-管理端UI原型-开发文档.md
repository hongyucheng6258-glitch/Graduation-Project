# 梧桐校园管理端 UI 原型开发文档

> 文档版本：v1.0  
> 编写日期：2026-11-13  
> 适用项目：`Ai-campus_副本`  
> 实现目标：在**不丢失现有业务、权限、路由和接口能力**的前提下，将管理端渐进升级为当前高保真原型的视觉与交互质量。

---

## 1. 文档用途与唯一基准

### 1.1 交付物

- 高保真原型：`wutong-ui-prototype/梧桐校园-管理端UI原型.html`
- 本开发文档：`wutong-ui-prototype/梧桐校园-管理端UI原型-开发文档.md`
- 原型素材：`wutong-ui-prototype/assets/`
- 管理端工程：`web/frontend/admin/`
- 后端工程：`web/backend/`

### 1.2 冲突时的优先级

1. **现有业务正确性、权限、安全、数据准确性和已有测试**。
2. 本文规定的路由、接口与业务差异处理。
3. 原型的布局、视觉、组件状态和交互反馈。
4. Three.js、颗粒纹理等非必要氛围效果。

不得为了实现原型中的演示动作而删除真实功能。例如：不能用原型里的简易搜索弹窗替换项目已有的全局搜索结果页，不能用通用占位页替换公告、举报、AI 配置、管理助手等完整页面。

### 1.3 原型与生产工程的差异处理

| 原型表现 | 生产实现要求 |
|---|---|
| 使用 `#dashboard`、`#users` 等 Hash 演示路由 | 使用现有 Vue Router 的 `/admin/...` 路由，保留标题、权限、面包屑和菜单元信息 |
| 顶部搜索提交后打开简易结果弹窗 | 顶部搜索跳转现有 `/admin/search?q=...`，保留范围筛选、分页、定位、返回和异常分组重试 |
| 内容管理、系统配置等显示演示卡片 | 复用现有完整业务页面，仅按本文统一页头、面板、表格、弹窗与状态样式 |
| “导出数据”只显示 Toast | 只在已有真实导出能力的位置提供下载；活动报名和签到使用现有 CSV 导出，不伪造其他模块导出 |
| 用户页出现“新增管理员” | 管理员新增仍放在 `/admin/admins`，不混入学生用户列表 |
| 重置密码提示“已发送通知” | 保留项目当前真实重置密码流程及后端语义，不能声称执行了不存在的通知服务 |
| AI 内容审核复用普通审核卡 | 保留 `AiContentAudit.vue` 的帖子、评论、回复、举报证据及完整审核流 |
| 演示数据、固定时间和统计值 | 全部替换为真实接口数据；接口无对应字段时不得编造 |
| 页面上可切换“正常/加载/空/异常” | 仅用于原型演示；生产环境根据请求状态自然呈现，不提供状态演示选择器 |

---

## 2. 现有技术基础与实施原则

### 2.1 已确认技术栈

- Vue 3.5、Vue Router 4、Pinia 2
- Vite 6
- Element Plus 2.13、@element-plus/icons-vue
- Axios 1.13
- ECharts 6、vue-echarts 8
- Spring Boot 3.2 / Java 17 后端
- 管理端开发端口：`5173`，`/api` 代理到 `http://localhost:18080`

### 2.2 实施原则

- 以现有 SFC 为基础渐进改造，不新建一套平行后台。
- 保留 `src/api/request.js` 的统一解包、鉴权、401 处理与错误提示。
- 保留 `AdminLayout.vue` 的菜单权限过滤、面包屑、全局搜索和通知能力。
- 保留 `AuditModule.vue` 的只读字段、详情、只读模式、分页、请求版本控制和待办刷新。
- 保留 `UserList.vue` 的权限错误、URL 查询参数、记录定位、状态操作和重置密码。
- 新增视觉样式应尽量集中在 Token、公共组件和布局层，减少每页独立调色。
- 不引入大型 UI 模板，不替换为另一套信息架构。

### 2.3 Tailwind 接入要求

原型使用 Tailwind 辅助响应式布局。生产工程可以保留现有 CSS，但新增布局工具类时必须避免 Tailwind Preflight 与 Element Plus 冲突。

建议使用 Tailwind 3.4.x，并启用前缀、关闭预重置：

```bash
cd web/frontend/admin
npm install -D tailwindcss@3.4.17 postcss autoprefixer
npx tailwindcss init -p
```

```js
// tailwind.config.js
export default {
  prefix: 'wt-',
  corePlugins: { preflight: false },
  content: ['./index.html', './src/**/*.{vue,js,mjs,ts}'],
  theme: { extend: {} },
  plugins: []
}
```

```css
/* src/styles/tailwind.css */
@tailwind base;
@tailwind components;
@tailwind utilities;
```

在 `main.js` 中于设计 Token 之后引入。已有类名保持不变，新工具类使用 `wt-` 前缀，例如 `wt-grid wt-grid-cols-2 wt-gap-4`。不要为了使用 Tailwind 而批量重写已有页面类名。

---

## 3. 信息架构与路由

### 3.1 导航分组

侧栏顺序保持以下五组，不因视觉改造调整：

1. 概览
2. 内容审核
3. 内容管理
4. 用户与社区
5. 系统管理

### 3.2 路由映射

| 分组 | 页面 | 生产路径 | 现有组件 | 主要改造 |
|---|---|---|---|---|
| 概览 | 数据看板 | `/admin/dashboard` | `dashboard/Dashboard.vue` | Bento 统计、待办、图表、快捷审核 |
| 内容审核 | 活动审核 | `/admin/audit/activity` | `audit/AuditModule.vue` | 审核卡、筛选、详情、通过/驳回 |
| 内容审核 | 闲置审核 | `/admin/audit/idle` | 同上 | 同上 |
| 内容审核 | 失物招领审核 | `/admin/audit/lostfound` | 同上 | 同上 |
| 内容审核 | 动态审核 | `/admin/audit/post` | 同上 | 同上，保留评论定位 |
| 内容审核 | 学习搭子审核 | `/admin/audit/partner` | 同上 | 同上 |
| 内容审核 | AI 内容审核 | `/admin/audit/ai` | `audit/AiContentAudit.vue` | 仅统一外壳和状态，保留风险证据流 |
| 内容管理 | 内容管理 | `/admin/content` | `content/ContentManage.vue` | 统一页头、工具栏、表格和详情 |
| 内容管理 | 公告管理 | `/admin/notice` | `notice/NoticeManage.vue` | 保留新建、编辑、发布、下线 |
| 内容管理 | 举报工单 | `/admin/report` | `report/ReportList.vue` | 保留处置流程和记录定位 |
| 用户与社区 | 用户管理 | `/admin/user` | `user/UserList.vue` | 响应式表格、筛选、状态操作 |
| 用户与社区 | 管理员管理 | `/admin/admins` | `admin/AdminList.vue` | 保留超管权限 |
| 系统管理 | 系统配置 | `/admin/system` | `system/SystemConfig.vue` | 分类卡片和即时保存反馈 |
| 系统管理 | AI 配置 | `/admin/ai` | `system/AiConfig.vue` | 模型配置与密钥安全 |
| 系统管理 | 提示词模板 | `/admin/ai/prompt` | `system/PromptTemplate.vue` | 保留版本管理 |
| 系统管理 | AI 调用日志 | `/admin/ai/log` | `system/AiCallLog.vue` | 统一表格、筛选和分页 |
| 系统管理 | 管理助手 | `/admin/assistant` | `assistant/Assistant.vue` | 保留会话、证据、审核确认卡和知识文件 |

隐藏但不可删除：

- `/admin/search`：全局搜索。
- `/admin/notice/new`、`/admin/notice/:id/edit`：公告编辑。
- `/admin/login`：管理员登录。
- 不存在的管理路径重定向到数据看板。

### 3.3 权限和菜单

继续使用：

- `route.meta.permission`
- `route.meta.superOnly`
- `adminMenu(routes, isSuper, can)`
- `pendingQueues()` 与 `pendingTotal()`

无权限入口不得仅通过 CSS 隐藏；仍须保留路由守卫和后端权限校验。审核、用户状态、密码重置等操作继续依赖后端 `@AdminOperation` 权限与日志能力。

---

## 4. 设计 Token

### 4.1 浅色主题

建议将下列值作为当前原型的精确覆盖层，放在现有 `tokens.css` 之后，避免影响旧变量的语义映射。

```css
:root {
  --paper: #f5f7fb;
  --surface: #ffffff;
  --surface-2: #f1f4fa;
  --surface-3: #e9eef6;

  --ink: #1b2437;
  --ink-2: #46536b;
  --ink-3: #69768d;

  --line: #e2e8f1;
  --line-strong: #cbd6e4;

  --brand: #2e5bff;
  --brand-strong: #1e47d6;
  --brand-soft: #edf1ff;

  --success: #237a50;
  --success-soft: #eaf7ef;
  --warning: #986117;
  --warning-soft: #fff5df;
  --error: #b5444c;
  --error-soft: #fff0f1;

  --side: #142139;
  --side-2: #1a2a47;
  --side-ink: #b9c4da;
  --side-muted: #7d8ca8;

  --r: 12px;
  --r-sm: 8px;
  --shadow: 0 1px 2px rgba(27,36,55,.03),
            0 8px 24px rgba(27,36,55,.04);
  --float: 0 24px 64px rgba(15,28,50,.18);
  --ease: cubic-bezier(.16,1,.3,1);
}
```

旧代码仍使用 `--side-bg`、`--side-ink-3` 等变量时，在覆盖层增加别名，不要立即全局搜索替换：

```css
:root {
  --side-bg: var(--side);
  --side-active: var(--brand);
  --side-hover: rgba(255,255,255,.06);
  --side-ink-3: var(--side-muted);
  --r-md: var(--r);
  --shadow-sm: var(--shadow);
  --shadow-lg: var(--float);
  --ease-out: var(--ease);
}
```

### 4.2 深色主题

```css
:root[data-theme="dark"] {
  --paper: #101724;
  --surface: #172131;
  --surface-2: #1d293b;
  --surface-3: #253249;
  --ink: #edf2fa;
  --ink-2: #bdc8d8;
  --ink-3: #8e9bb0;
  --line: #2a374b;
  --line-strong: #3c4c66;
  --brand: #82a0ff;
  --brand-strong: #a8bcff;
  --brand-soft: #23345b;
  --success: #72c69b;
  --success-soft: #19392d;
  --warning: #e3b56b;
  --warning-soft: #3a3020;
  --error: #ed9aa1;
  --error-soft: #40252c;
  --side: #0c1422;
  --side-2: #111d31;
  --shadow: 0 1px 2px rgba(0,0,0,.2),
            0 8px 24px rgba(0,0,0,.16);
}
```

### 4.3 字体与字阶

```css
--font-sans: Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
```

| 用途 | 字号/行高 | 字重 | 颜色 |
|---|---|---|---|
| 页面主标题 | 28px / 1.25；移动端 23px | 750 | `--ink` |
| 首屏欢迎标题 | 34px / 1.2；移动端 23px | 750 | `#fff` |
| 面板标题 | 15px / 1.4 | 700 | `--ink` |
| 统计数字 | 27px / 1.1；移动端 20px | 750 | `--ink` |
| 正文 | 13–14px / 1.6 | 400–500 | `--ink-2` |
| 表格 | 13px / 1.5 | 400–600 | 依字段语义 |
| 辅助信息 | 11–12px / 1.5 | 400–500 | `--ink-3` |
| 数据与编号 | 原字号 | 500–700 | 使用 `font-variant-numeric: tabular-nums` |

不得使用 Emoji 作为功能图标。图标统一使用现有 Element Plus Icons 或本地 SVG，默认尺寸 16–20px。

### 4.4 间距、边框与圆角

- 4px 基础网格：4、8、12、16、20、24、32、36、46。
- 桌面内容区：`24px 28px 40px`；宽屏：`32px 36px 46px`。
- 面板圆角：12px；控件圆角：8px；标签可用胶囊圆角。
- 面板边框：`1px solid var(--line)`。
- 禁止每层容器都使用大圆角和厚阴影。
- 阴影只用于面板层级、浮层和弹窗，不制造发光效果。

---

## 5. 应用外壳与响应式

### 5.1 桌面结构

```text
┌────────────── 260px 深蓝侧栏 ──────────────┬──────────────── 顶栏 66px ────────────────┐
│ 品牌                                       │ 面包屑 / 全局搜索 / 主题 / 通知 / 用户     │
│ 分组导航（独立滚动）                       ├──────────────────────────────────────────────┤
│                                            │ 内容区，纵向滚动                             │
│ 当前管理员 / 退出登录                      │ max-width: 1500px                            │
└────────────────────────────────────────────┴──────────────────────────────────────────────┘
```

- `.app`：`display:grid; grid-template-columns:260px minmax(0,1fr); height:100vh; overflow:hidden`。
- 侧栏：固定在第一列，`min-height:0`；导航区域独立 `overflow-y:auto`。
- 主区域：`min-width:0; display:flex; flex-direction:column`。
- 顶栏：高度 66px，底部分隔线，白色/深色表面，不做重度玻璃模糊。
- 内容区：唯一主滚动容器；`max-width:1500px; width:100%; margin:0 auto`。
- 页面切换：只更新主区域，侧栏与顶栏不重复创建。

### 5.2 侧栏

- 品牌区高 66px，底部 `1px` 分隔线。
- 标志：34×34px，校园蓝 9px 圆角，本地 SVG。
- 品牌名 15px/700，副标题 11px，使用 `--side-muted`。
- 分组标题：10.5px，左侧 14px，上间距 20px，字距约 `.13em`。
- 导航项：左右 9px 内边距，高 40px，8px 圆角，图标 17px。
- 默认文字：`--side-ink`；Hover：白色 6% 背景；激活：校园蓝背景、白字。
- 待办数：最小宽度 20px，高 20px，11px，激活时白色 20% 背景。
- 待办总数只合计 `activity/idle/lostfound/post/partner/report`；AI 风险复核为子集，不重复计数。
- 底部管理员区必须保留退出登录和当前角色。

### 5.3 顶栏

从左到右：

1. 移动端菜单按钮。
2. 面包屑：当前项加粗，父级使用 `--ink-3`。
3. 全局搜索：宽度 320px，输入高度 36px；支持 `Ctrl/Cmd + K`。
4. 主题切换。
5. 通知按钮与未读圆点。
6. 管理员头像、昵称和角色。

通知浮层：宽 320px，位于按钮下方，表面色背景、细边框、轻浮层阴影。点击外部、按 Escape 或页面跳转后关闭。通知与全局搜索事件不得冒泡成重复提交。

### 5.4 响应式断点

| 宽度 | 必须实现 |
|---|---|
| `>=1700px` | 内容 padding 32/36/46；统计间距 18；图表高 270；审核卡更宽松 |
| `1221–1699px` | 侧栏 260；统计 4 列；待办 5 列；主图表 1.55:1；快捷审核 1:1 |
| `901–1220px` | 待办 4 列；统计 2 列；主图表/快捷审核单列；搜索宽 280 |
| `681–900px` | 侧栏收为 72px，只显示图标；审核卡封面转为顶部通栏；内容 padding 20 |
| `<=680px` | 侧栏变抽屉；顶栏 58；隐藏搜索文字框；统计和待办均 2 列；内容 padding 16/12/30 |
| 减少动效 | 停止非必要动画，隐藏 Three.js 氛围，保留完整信息和操作 |

移动端抽屉：

- 宽度 `min(280px, 88vw)`，深蓝背景，覆盖全高。
- 打开后显示完整品牌、分组、文字和待办数。
- 背景遮罩 `rgba(9,18,32,.42)`。
- 打开后锁定主内容滚动，关闭后恢复。
- 点击导航项、遮罩或 Escape 后关闭。
- 不允许出现横向滚动条。

---

## 6. 通用组件规范

### 6.1 页面页头 `WtPageHeader`

- 左侧：主标题与一句功能性描述。
- 右侧：页面主操作；无操作时省略，不占空容器。
- 上下间距：底部 22px。
- 移动端纵向排列，操作区可横向滚动，不挤压标题。
- 描述只解释当前任务，不添加装饰性英文眉标。

### 6.2 面板 `WtPanel`

```css
.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r);
  box-shadow: var(--shadow);
}
.panel-head { padding: 19px 21px 0; }
.panel-body { padding: 18px 21px 21px; }
```

- 标题区可包含状态标签或单个轻量动作。
- 一个页面可以有多个面板，但不能把每行文字都再包成卡片。
- 加载、空、错误状态都留在原面板几何区域内，避免页面跳动。

### 6.3 按钮

| 类型 | 用途 | 视觉 |
|---|---|---|
| Primary | 审核通过、保存、确认 | 校园蓝底白字 |
| Default | 查看详情、取消、普通操作 | 表面色、细边框 |
| Danger | 驳回、禁用、删除 | 错误色文字/边框；最终确认可实色 |
| Ghost | 工具栏次级动作 | 无底色，Hover 使用 `--surface-2` |

- 默认高 36px，小按钮 32px；水平内边距 13px；圆角 8px。
- 所有异步按钮必须有 `loading` 并防重复提交。
- 禁用状态不仅降低透明度，还要设置 `cursor:not-allowed`。
- 图标按钮必须有 `aria-label` 和 tooltip。
- Hover 位移最多 1px；不使用弹跳和发光。

### 6.4 输入与筛选

- 输入高 36px，边框 `--line`，背景 `--surface`。
- Focus：边框 `--brand`，外环 `0 0 0 3px color-mix(...)` 或等效浅蓝环。
- 搜索框最多 80 字符，去除首尾空白。
- 筛选变化后页码回到 1；URL 中保留可分享的筛选状态。
- 移动端输入可占满一行，状态选择器宽度不挤压搜索。

### 6.5 状态标签

| 语义 | 前景/背景 |
|---|---|
| 正常、已通过、已启用 | `--success` / `--success-soft` |
| 待审核、待处理、未读 | `--warning` / `--warning-soft` |
| 已禁用、已驳回、错误 | `--error` / `--error-soft` |
| AI、信息、链接 | `--brand-strong` / `--brand-soft` |
| 草稿、普通记录 | `--ink-2` / `--surface-2` |

禁止仅靠颜色表达状态，必须同时有文字。

### 6.6 表格

- 表头：11px、字重 600、`--ink-3`、底部 1px 分隔线。
- 行高：约 52px；行分隔线 `--line`。
- 行 Hover：`--surface-2`。
- 学号、数量、日期使用等宽数字。
- 操作列右对齐；高风险操作保持明确文字。
- `min-width:900px` 的表格在窄屏内局部横向滚动，页面本身不横向溢出。
- 空表格仍保留表头，正文区域显示空状态。
- 分页、总数和筛选摘要位于表尾；移动端纵向排列。

### 6.7 弹窗

生产环境优先使用 Element Plus `el-dialog` / `ElMessageBox`，通过主题覆盖达到原型外观。

- 宽度：`min(560px, calc(100vw - 32px))`。
- 圆角 12px，边框和浮层阴影。
- 标题、正文、底部操作三段式；正文最多两行以上时使用滚动区。
- 打开后聚焦标题或第一个输入框；Tab 焦点限制在弹窗内。
- Escape、关闭按钮、取消按钮行为一致。
- 异步确认未结束前不得关闭。
- 危险操作必须说明对象和后果，不能只写“是否确认”。

### 6.8 Toast

- 位置：桌面右下；移动端距左右 16px，宽度自适应。
- 成功：校园蓝图标；失败：错误色；不使用 Emoji。
- 默认 3.2 秒，失败可延长或保持到用户关闭。
- 文案必须描述真实结果，例如“审核已通过”“已禁用用户「苏禾」”。
- 请求失败时不能只显示成功 Toast；成功后不得再被旧请求覆盖。

### 6.9 加载、空、异常状态

每个数据区域统一实现四态：

```text
loading → 有数据 data
        → 无数据 empty
        → 请求失败 error（保留重试）
```

- Loading：面板内骨架屏或既有 `v-loading`，不全屏闪烁。
- Empty：图标、原因、下一步动作；筛选导致为空时提供“重置筛选”。
- Error：说明“数据加载失败”，提供“重新加载”；不得清空原有查询条件。
- 局部失败不能拖垮整个页面；看板允许按模块独立显示错误。
- 请求需有版本号或取消机制，旧响应不得覆盖新筛选结果。

---

## 7. 数据看板实现

### 7.1 区块顺序

1. 欢迎横幅。
2. 4 个核心统计。
3. 5 个审核待办入口。
4. 近 30 天趋势 + 失物招领状态。
5. 模块发布量 + 待审闲置快捷审核。

### 7.2 欢迎横幅

- 桌面高度约 178px，深蓝到校园蓝的克制渐变。
- 左侧：当前时间问候、管理员称呼、基于真实待办数的摘要、两个快捷入口。
- 右侧：项目校园图 `assets/home-campus.webp`，仅作氛围。
- 可以叠加细网格与低透明度噪点，不能用光晕遮挡文字。
- 问候语使用客户端本地时间，并每 60 秒更新；避免水合/时区错误。

### 7.3 核心统计

| 卡片 | 字段 | 图标/颜色 | 说明 |
|---|---|---|---|
| 今日活跃 | `todayActiveUsers` | Users / 校园蓝 | 辅助文案固定为“今日访问用户” |
| 待办审核 | `pendingAudits` | Clipboard / 杏色预警 | 来自 overview；与角标口径保持协调 |
| 新增闲置 | `todayNewIdle` | Box / 成功绿 | 辅助文案“今日发布闲置” |
| AI 调用 | `todayAiCalls` | ChatDot / 信息蓝 | 辅助文案“今日 AI 请求” |

- 桌面 4 列，1220 以下 2 列。
- 不使用无依据的同比百分比；原型中的静态趋势百分比不得搬到生产。
- 数字必须支持 0，不能用 `value || '--'` 把 0 当无数据。

### 7.4 审核待办

- 顺序：活动、闲置、失物招领、校园动态、学习搭子。
- 数据：`statsPendingCounts()`。
- 每项整块可点击，并跳转对应待办视图：`{ query: { view:'pending' } }`。
- 角标请求失败时显示“—”，不能显示伪造的 0；卡片仍可进入列表。
- AI 风险复核不占第六张卡，保留在导航角标和审核入口中。

### 7.5 趋势图

原型视觉为一张校园蓝面积折线图。生产数据建议：

- 标题：`近30天用户趋势`。
- 数据：`statsTrend().dates` 与 `statsTrend().userGrowth`。
- 不伪造 `activeUsers` 字段。
- 关闭双 Y 轴；AI 调用已有独立统计卡，不硬塞进不同量纲的用户趋势图。
- 折线 2.5px，平滑过渡，校园蓝，面积透明度约 10%。
- X 轴显示日期，标签最多约 6 个；Y 轴从合理值开始，网格线使用 `--line`。
- `animation:false`，避免后台标签页或低帧环境停留在初始状态。
- 容器桌面高 250px，宽屏 270px，移动 220px。
- 使用 `ResizeObserver` 或现有 resize 逻辑，组件卸载时移除监听并 `dispose()`。

### 7.6 失物招领状态

- 数据：`statsPie().lostStatus`，字段 `lost`、`found`。
- 环形半径约 `48% / 70%`，不使用 3D。
- 图例在下方，只显示“寻找中”“已找回”。
- Tooltip 显示名称、数量和占比。
- 数据为空时显示空状态，不渲染一个看似有数据的空环。

### 7.7 模块发布量

- 数据：`statsModule()`。
- 原型为横向柱状图，便于阅读中文模块名称。
- 柱轴必须从 0 开始，数量右对齐，使用等宽数字。
- 主色校园蓝，不使用默认彩虹色。
- 柱条高度约 15px，圆角仅用于末端小圆角。
- Tooltip 与数值轴必须保留。

### 7.8 快捷审核

- 数据：`listIdle({ pageNum:1, pageSize:3, status:0 })`。
- 展示封面、标题、价格、时间、“去审核”。
- 点击跳闲置待办并携带记录定位参数。
- 图片懒加载；无图片时使用统一浅蓝占位，不显示破图。
- 待办为空时显示“当前没有待审闲置”，并保留进入全部闲置的入口。

### 7.9 看板请求策略

- 首屏并发请求 overview、trend、pie、module、pending、idle。
- 每个模块独立维护状态，不让一个失败接口阻塞全部内容。
- `pendingAudits` 与导航待办在 mutation 后统一调用 `useAdminPendingStore().refresh({ afterMutation:true })`。
- 保持现有请求版本控制，路由离开后忽略晚到响应。

---

## 8. 内容审核实现

### 8.1 页面结构

```text
页头：标题 + 审核说明                         [导出（仅真实支持时）]
工具栏：关键词 / 待审核或全部状态 / 全部记录 / 刷新
审核卡列表（3–5 条，接口分页）
分页
详情弹窗
驳回原因弹窗
```

### 8.2 审核卡

桌面：

- 主信息、右侧 160×108px 封面、右侧操作区。
- 标题 16px/700；发布者与时间为 12px 辅助信息。
- 摘要最多 3 行，长文进入详情。
- 操作顺序：通过、驳回、查看详情。
- 1220 以下封面 130×96；900 以下封面顶部通栏；移动端操作 2 列，详情可占满一行。

必须保留现有真实字段：

- 标题：`title / subject / content / goal / itemType` 回退。
- 正文：`description / content / goal / itemDesc / itemType`。
- 作者：`authorName / nickname / contact`。
- 时间：`createTime / time / lostTime`。
- 图片：优先真实图片接口或后端返回 URL，原型素材只能作开发 fallback。
- 图片失败显示统一占位，不重复触发无限错误请求。

### 8.3 审核类型

| 类型 key | 页面 | 特殊要求 |
|---|---|---|
| `activity` | 活动审核 | 活动详情、地点、时间等真实字段 |
| `idle` | 闲置审核 | 价格、图片、发布者 |
| `lostfound` | 失物招领审核 | 类型、地点、时间、图片 |
| `post` | 动态审核 | 正文、图片、评论定位 |
| `partner` | 学习搭子审核 | 目标、联系方式、说明 |
| `ai` | AI 内容审核 | 使用独立完整组件，不走简化卡片逻辑 |

### 8.4 筛选与 URL

继续使用：

- `view=pending|all`
- `riskOnly=true`：风险优先。
- `page`：正整数，非法值回退 1。
- `record`：正整数记录定位。
- 全局搜索返回所需的 `searchQ/searchScope/searchPages/searchTarget`。

原型中的本地关键词输入可按以下规则落地：

- `view=all`：调用 `auditAll({ type, keyword, pageNum, pageSize })`，后端已支持全文查询。
- `view=pending`：当前后端 `/audit/pending` 仅声明 `type/pageNum/pageSize/riskOnly`，**尚未声明 keyword**。若产品要求待办全文搜索，需同步扩展后端 Service 与 Controller，再进行服务端分页查询。
- 在后端未扩展前，不允许只过滤当前页并把它称为完整搜索；可暂时隐藏待办关键词框，保留全局搜索入口。

### 8.5 详情

- 复用现有 `auditRecordDetail(type,id)` 仅作为可获取详情的补充。
- 当前后端只显式提供 idle 和 activity 详情；其他类型继续使用行记录已有字段，不能假设通用详情一定返回完整对象。
- 弹窗必须包含标题、发布者、时间、状态、正文及类型特定只读字段。
- 审核决定仍应在列表或详情中明确触发，不能查看详情即自动变更状态。

### 8.6 通过流程

1. 用户点击“通过”。
2. 按钮进入 loading，禁用同卡其他审核动作。
3. 待办视图调用 `auditPass(type,id,true)`；全部视图调用 `auditPass(type,id,false)`。
4. 成功后 Toast，刷新当前列表、详情状态和导航待办。
5. 若记录已不在当前筛选中，平滑移除并校正分页。
6. 失败保留卡片和当前状态，显示后端错误，允许重试。

### 8.7 驳回流程

- 必须填写原因；去除首尾空白后为空时，在输入框下显示“请填写驳回原因”。
- 建议长度 2–500 字；实际约束以后端 DTO 为准。
- 驳回弹窗显示对象标题，避免误操作。
- 调用 `auditReject(type,id,reason,pendingOnly)`。
- 成功前不清空原因；成功后关闭弹窗、清空并刷新。
- 连续点击不得产生重复请求。

### 8.8 审核列表状态

- 待办为空：“当前没有待人工审核的内容”。
- 全部记录筛选为空：“没有找到匹配的内容”，提供重置筛选。
- 请求失败：保留工具栏，列表区域显示异常与重试。
- 页码超出：列表为空且 `page>1` 时，回到最后有效页，不显示错误。

---

## 9. 用户管理实现

### 9.1 页面结构

- 页头：用户管理 / 查询学生账号，管理启用状态与密码重置。
- 工具栏：关键词、状态、查询、新增管理员跳转（可选）、刷新、导出（仅真实支持时）。
- 用户表格、分页、操作弹窗。

### 9.2 表格字段

| 列 | 字段与显示 |
|---|---|
| 用户 | 头像、昵称；昵称回退为“未设置昵称” |
| 学号 | `studentNo`，等宽数字 |
| 手机号 | `phone`，无数据显示 `—` |
| 角色 | `role === 1 ? 普通用户 : 管理员`，实际以后端枚举为准 |
| 状态 | `status === 0 ? 正常 : 已禁用` |
| 注册时间 | `createTime` |
| 操作 | 启用/禁用、重置密码 |

头像：昵称首字符，统一浅蓝底；不要加载外部随机头像服务。

### 9.3 查询

现有后端已支持：

```text
GET /api/admin/user/list
keyword?: string
record?: number
status?: number
pageNum: number = 1
pageSize: number = 10
```

- 关键词匹配学号或昵称。
- `record` 用于搜索结果定位，定位后应显示返回链接。
- 筛选、分页写入 URL；刷新和分享链接后状态可恢复。
- 空结果保留表头并显示“没有找到用户”。
- 403 显示无权限说明；其他错误显示重试。

### 9.4 启用/禁用

- 当前 `status=0` 时操作文案为“禁用”；当前非 0 时为“启用”。
- 确认弹窗必须带昵称和学号。
- 请求：`PUT /user/{id}/status`，Body `{ "status": 0|1 }`。
- 成功 Toast 根据**变更后的状态**生成：0 为“已启用”，1 为“已禁用”。
- 失败后保留原状态，不做未回滚的乐观更新。

### 9.5 重置密码

- 使用现有 `resetPassword(id)`。
- 保留当前后端真实重置语义、结果提示和权限控制。
- 原型没有实现短信、邮件或站内通知服务，生产页面不得显示“已发送重置通知”。
- 密码等敏感信息不得写入日志、URL 或 Toast 之外的持久化存储。

---

## 10. 全局搜索、通知与主题

### 10.1 全局搜索

- 顶栏输入回车后进入 `/admin/search`，不是只弹出三条静态结果。
- 支持范围：全部、用户、校园内容、举报工单。
- 举报支持 `#123` 或纯编号定位。
- 保留 `pages=user:2,activity:1` 这类分组分页状态。
- 结果进入用户、审核、内容或举报详情时携带定位参数。
- 返回搜索结果后，按 `#result-{type}-{id}` 滚动并高亮目标。
- 每个分组独立 loading/error/retry；部分失败时仍显示其他分组。

### 10.2 通知

- 通知按钮使用真实未读数据；未读为 0 时不显示圆点。
- 原型通知数据仅作展示，生产可接现有通知接口或待办摘要。
- “全部已读”只有在真实接口成功后才能清除未读状态；没有接口时不要提供虚假动作。
- 通知项目可跳转相应待办或公告，不阻断搜索和侧栏。

### 10.3 主题

- `data-theme="light|dark"` 挂在 `document.documentElement`。
- 本地偏好键统一为 `admin-theme`，非法值回退浅色。
- 初始加载尽早设置，避免主题闪烁。
- 图表颜色随主题更新；不能只在 CSS 中换背景而保留刺眼的白色 Tooltip。
- 深蓝色侧栏在两种主题中都保持管理端识别度。

---

## 11. 动效、材质与 Three.js

### 11.1 Motion

生产可使用 Motion One：

```bash
npm install motion three
```

仅用于：

- 页面主标题和首屏统计的短暂 stagger 入场。
- 弹窗、Toast、通知浮层的打开/关闭。
- 按钮和卡片的轻量 Hover 反馈。
- 列表筛选项变化时的短过渡。

建议参数：

- 时长 160–420ms。
- 缓动 `cubic-bezier(.16,1,.3,1)`。
- 入场位移 8–12px，不能大幅飞入。
- 数据列表更新不重复播放整套首屏动画。
- `prefers-reduced-motion: reduce` 时直接呈现最终状态。

### 11.2 噪点与网格

- 噪点使用内联 SVG `feTurbulence`，透明度约 `.025–.035`，`pointer-events:none`。
- 网格仅用于欢迎横幅，低对比、固定尺寸，不覆盖正文。
- 不在所有面板重复叠加玻璃模糊；后台以读表效率优先。

### 11.3 Three.js 氛围

- 只允许出现在欢迎横幅右侧，不能遮挡文字、按钮或图表。
- 元素：低透明线框几何体、少量校园色粒子。
- 摄像机 FOV 约 42°，无自动大幅旋转。
- 像素比上限 1.5；页面隐藏时停止渲染；卸载时释放 renderer、geometry、material。
- WebGL 不可用、减少动效或 `file://` 双击模式下，直接显示静态网格/校园图。
- 不能把 Three.js 作为看板数据图表；ECharts 负责所有数据图形。

---

## 12. 接口接入清单

所有相对路径默认前缀：`/api/admin`。

| 模块 | 方法与路径 | 前端 API | 用途 |
|---|---|---|---|
| 看板 | GET `/stats/overview` | `statsOverview` | 今日统计 |
| 看板 | GET `/stats/trend` | `statsTrend` | 日期、用户趋势、AI 调用趋势 |
| 看板 | GET `/stats/module` | `statsModule` | 模块发布量 |
| 看板 | GET `/stats/pie` | `statsPie` | 失物状态 |
| 待办 | GET `/stats/pending-counts` | `statsPendingCounts` | 导航和看板待办 |
| 审核 | GET `/audit/pending` | `auditList` | 待审核分页 |
| 审核 | GET `/audit/all` | `auditAll` | 全部记录、关键词和记录定位 |
| 审核 | GET `/audit/{type}/{id}` | `auditRecordDetail` | 已支持类型的详情 |
| 审核 | PUT `/audit/{type}/{id}/pass` | `auditPass` | 通过 |
| 审核 | PUT `/audit/{type}/{id}/reject` | `auditReject` | 驳回 |
| 用户 | GET `/user/list` | `listUsers` | 用户分页、筛选、定位 |
| 用户 | PUT `/user/{id}/status` | `updateUserStatus` | 启禁用 |
| 用户 | PUT `/user/{id}/reset-password` | `resetPassword` | 重置密码 |
| 内容 | PUT `/content/{type}/{id}/off` | `contentOff` | 下架 |
| 内容 | PUT `/content/{type}/{id}/on` | `contentOn` | 上架 |
| 活动报表 | GET `/content/activity/{id}/signin-report` | `signinReport` | 签到报表 |
| 举报 | GET `/report/list` | `reportList` | 举报分页与定位 |
| 举报 | GET `/report/{id}` | `reportDetail` | 举报详情 |
| 举报 | PUT `/report/{id}/handle` | `handleReport` | 举报处置 |
| 公告 | GET/POST `/notice`、`/notice/list` | `noticeApi` | 公告列表与新建 |
| 公告 | PUT `/notice/{id}`、`/publish`、`/offline` | `noticeApi` | 编辑、发布、下线 |
| 导出 | GET `/export/activity/{id}/members` | `exportMembers` | 报名 CSV |
| 导出 | GET `/export/activity/{id}/signins` | `exportSignins` | 签到 CSV |
| 系统配置 | GET/PUT `/system/config` | `systemConfig` | 读取和批量保存 |
| 管理员 | GET/POST `/system/admin` | `listAdmins/createAdmin` | 子管理员 |
| AI 配置 | GET/PUT `/ai/config` | `getAiConfig/updateAiConfig` | AI 配置 |
| 提示词 | GET/POST `/ai/prompt` | `listPrompts/createPrompt` | 模板 |
| AI 日志 | GET `/ai/log` | `aiLogs` | 调用日志 |
| 管理助手 | `/assistant/...` | `assistant.js` | 会话、规则、诊断、审核确认 |

### 12.1 请求与响应约定

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 0,
    "pages": 0,
    "list": []
  }
}
```

- HTTP 200 不代表业务成功，必须检查 `code`。
- 分页一般为 `pageNum/pageSize`，返回 `total/pages/list`。
- 受保护接口使用 `Authorization: Bearer <admin_token>`。
- 401 由统一拦截器退出并回登录页；晚到的旧会话响应不能清除新会话。
- CSV、PDF、SSE 不走普通 Axios `res.data` 解包。
- 403 与 500 的页面表现不同：403 展示权限说明，500 提供重试。

### 12.2 不建议新增的前端假能力

在后端没有真实接口前，不实现：

- 通用全模块数据导出。
- 虚假的通知已读持久化。
- 虚假的新增学生用户。
- 虚假的密码重置通知。
- 只看当前页的“全文搜索”。
- 伪造的实时在线用户或同比增长。

---

## 13. 推荐组件与文件落点

```text
web/frontend/admin/src/
├─ layout/
│  ├─ AdminLayout.vue             # 改：外壳、顶栏、移动抽屉、主题、通知
│  └─ adminNavigation.mjs         # 保留：菜单、待办和面包屑逻辑
├─ views/
│  ├─ dashboard/Dashboard.vue     # 改：Bento 看板和真实图表
│  ├─ audit/AuditModule.vue       # 改：审核卡、筛选和弹窗视觉
│  ├─ audit/AiContentAudit.vue    # 保留业务，统一外壳
│  ├─ user/UserList.vue           # 改：工具栏、表格、状态与弹窗
│  └─ ...                         # 其他页面只渐进统一样式
├─ components/wt/
│  ├─ WtPageHeader.vue            # 保留并统一规格
│  ├─ WtPanel.vue                 # 可新增，减少重复面板样式
│  ├─ WtState.vue                 # 可新增：empty/error/loading
│  └─ WtStatusTag.vue             # 可新增：统一语义色
├─ composables/
│  ├─ useRequestState.js          # 可新增：四态与请求版本
│  └─ useChartResize.js           # 可新增：图表生命周期
├─ styles/
│  ├─ tokens.css                  # 保留原变量
│  ├─ prototype-overrides.css     # 新增：本文 Token 与公共组件覆盖
│  ├─ element-theme.css           # 调整 Element Plus 外观
│  ├─ tailwind.css                # 新增：带 wt- 前缀的工具类
│  └─ base.css                    # 保留可访问性和 reset
└─ api/                           # 保留现有接口层，不在组件里散落 axios
```

公共组件按需抽取，不要求一次性抽象所有页面。优先抽取重复出现三次以上且语义一致的结构，避免为了“组件化”制造大量只有一行的包装组件。

---

## 14. 素材与静态资源

原型素材位于 `wutong-ui-prototype/assets/`：

| 文件 | 建议用途 |
|---|---|
| `home-campus.webp` | 管理端欢迎横幅右侧 |
| `campus-v2.webp` | 备用校园场景 |
| `generated-activity-campus.webp` | 活动审核开发占位 |
| `generated-idle-items.webp` | 闲置审核开发占位 |
| `generated-lost-found.webp` | 失物审核开发占位 |
| `generated-study-partner.webp` | 学习搭子审核开发占位 |
| `generated-social-campus.webp` | 动态审核开发占位 |
| `generated-ai-study.webp` | AI 内容开发占位 |

生产规则：

- 真实内容优先使用后端图片 URL 或已有图片接口。
- 原型图片用于横幅和开发环境占位，不能冒充用户上传内容。
- 建议将横幅资源复制到 `admin/public/images/admin/home-campus.webp`，以 `/images/admin/home-campus.webp` 引用。
- 用户内容图片使用懒加载、固定容器比例、`object-fit:cover` 和错误占位。
- 不引用随机网络图片服务，避免验收时资源不稳定。
- HTML 原型直接双击时，Three.js 自动降级；业务页面由 Vite 服务运行时不受此限制。

---

## 15. 可访问性、安全与性能

### 15.1 可访问性

- 所有功能可键盘操作；`:focus-visible` 使用 2px 校园蓝焦点环。
- 图标按钮有 `aria-label`；当前导航有 `aria-current`。
- 弹窗具备标题关联、焦点锁定和关闭后的焦点恢复。
- 状态不仅依赖颜色；图表有文字摘要或数据表入口。
- 正文和关键控件对比度至少 4.5:1。
- 移动端点击目标建议至少 40×40px。
- 尊重减少动效设置。

### 15.2 安全

- 展示用户内容时使用 Vue 文本插值；不得将审核正文直接拼入 `v-html`。
- 搜索关键词、驳回原因和公告内容按后端约束校验。
- 文件下载使用现有鉴权逻辑，不能在 URL 中附加 Token。
- 不在前端日志输出管理员 Token、AI 密钥、用户密码或敏感个人信息。
- 所有高风险写操作仍由后端鉴权和记录日志，前端隐藏按钮不是安全边界。

### 15.3 性能

- 看板请求并发，模块独立失败。
- 图表按需引入，组件卸载释放实例。
- Three.js 动态导入，首屏不阻塞列表和接口。
- 长列表使用服务端分页，不一次拉取全部用户或内容。
- 图片懒加载并限制显示尺寸。
- 搜索输入在提交时查询；如增加实时搜索，需 300ms 左右防抖并取消旧请求。

---

## 16. 推荐实施顺序

### 阶段 A：基础层

1. 备份并跑通当前管理端登录、看板、审核、用户管理。
2. 新增 `prototype-overrides.css`，建立浅色/深色 Token 和旧变量别名。
3. 接入带 `wt-` 前缀、关闭 Preflight 的 Tailwind。
4. 统一按钮、输入、标签、面板、弹窗、Toast、四态。
5. 运行现有测试，确认无业务回归。

### 阶段 B：应用外壳

1. 调整 `AdminLayout.vue` 网格、侧栏滚动和 260/72/抽屉三种形态。
2. 统一面包屑、全局搜索、主题、通知与管理员区域。
3. 接入移动遮罩、Escape、焦点和滚动锁定。
4. 验证菜单权限、待办总数和页面跳转。

### 阶段 C：看板

1. 欢迎横幅接入本地时间与真实待办。
2. 统计卡接入 overview，待办卡接入 pending-counts。
3. 趋势、失物状态、模块发布量改为本文图形规范。
4. 快捷审核接入闲置待办。
5. 完成局部 loading/empty/error 和图表生命周期。

### 阶段 D：审核与用户

1. `AuditModule.vue` 落地审核卡与统一详情/驳回弹窗。
2. 保留 URL 查询、记录定位、风险筛选和分页校正。
3. 待办关键词若纳入范围，先扩展后端 `/audit/pending`。
4. `UserList.vue` 落地表格、筛选和状态操作。
5. 验证真实重置密码语义，不使用原型假通知。

### 阶段 E：其余业务与总验收

1. 逐页统一内容、公告、举报、系统、AI 和助手外壳。
2. 全局搜索、公告编辑、AI 审核完整回归。
3. 接入克制动效和可选 Three.js。
4. 按本文验收矩阵检查桌面、宽屏、移动端、暗色和异常状态。

---

## 17. 验收标准

### 17.1 视觉与布局

- [ ] 侧栏仍为深蓝分组导航，顺序和权限不变。
- [ ] 1440px：4 统计列、5 待办列、双列图表和双列快捷区。
- [ ] 1920px：内容最大宽度 1500px，留白增加但不被无限拉宽。
- [ ] 900px 附近：侧栏正确收为 72px，文字不溢出。
- [ ] 680px 以下：抽屉可打开/关闭，统计 2 列，页面无横向溢出。
- [ ] 390px：`document.documentElement.scrollWidth === 390`。
- [ ] 表格仅在自身容器内横向滚动，不撑宽整个页面。
- [ ] 浅色/深色均无刺眼白块、低对比文字或图表主题不一致。
- [ ] 无 Emoji 功能图标、廉价发光、过度渐变和大圆角堆叠。

### 17.2 看板

- [ ] 0 值正常显示，接口失败显示 `—` 或错误态，不伪造数据。
- [ ] 待办点击到正确模块和 `view=pending`。
- [ ] 趋势使用真实日期和用户数据，坐标、Tooltip、空态完整。
- [ ] 失物状态数量与占比正确，空数据不渲染假环。
- [ ] 模块发布量横轴从 0 开始。
- [ ] 快捷审核图片失败有占位，空待办有下一步入口。
- [ ] 路由切换后图表和监听器被释放。

### 17.3 审核

- [ ] 六类审核入口均进入真实页面，AI 审核功能未退化。
- [ ] 通过按钮防重复提交，成功后列表与角标同步刷新。
- [ ] 驳回原因为空时不能提交，并显示字段错误。
- [ ] 驳回失败不丢原因、不关闭弹窗、不错误移除记录。
- [ ] 全部内容搜索为服务端全文查询，不是当前页假过滤。
- [ ] 待办搜索若未扩展后端，不出现误导性入口。
- [ ] 详情只读字段与当前类型匹配，用户内容不通过 `v-html` 注入。
- [ ] 空结果、请求失败、无权限、页码超出均有正确处理。

### 17.4 用户

- [ ] 关键词、状态、分页和记录定位可通过 URL 恢复。
- [ ] 启禁用确认对象正确，Toast 与变更后状态一致。
- [ ] 请求失败后原状态不变。
- [ ] 重置密码提示与真实后端行为一致。
- [ ] 普通管理员无法执行无权限操作。

### 17.5 全局搜索与导航

- [ ] `Ctrl/Cmd + K` 可聚焦搜索，Enter 提交一次。
- [ ] 搜索结果支持范围、分页、部分失败重试。
- [ ] 进入记录后可返回原关键词、范围和分页。
- [ ] 返回后目标记录滚动到可见区域并高亮。
- [ ] 公告新建/编辑、举报处置、系统配置和 AI 助手回归通过。

### 17.6 交互与质量

- [ ] 所有异步操作有 loading，无重复提交。
- [ ] 弹窗键盘可达，Escape 和焦点恢复正常。
- [ ] 减少动效时信息完整，无持续运动。
- [ ] 控制台无 Vue 警告、未捕获异常和资源 404。
- [ ] 后端未启动时显示真实异常态，不显示伪造成功数据。
- [ ] 现有自动化测试与关键手工业务回归全部通过。

---

## 18. 完成定义（Definition of Done）

只有同时满足以下条件，才视为“按原型实现完成”：

1. 原型视觉已覆盖应用外壳、看板、审核、用户和公共组件。
2. 现有 16 项管理导航及隐藏搜索/编辑路由均可用。
3. 真实接口、权限、鉴权、日志、搜索定位、审核证据和文件下载未退化。
4. 1440、1920、390 三档布局及暗色、加载、空、异常状态通过验收。
5. 审核通过/驳回、用户启禁用、密码重置、全局搜索和公告流程完成真实联调。
6. 无虚构数据、假导出、假通知、当前页假搜索或仅前端隐藏权限。
7. 控制台无错误，资源无 404，组件卸载无遗留图表/动画监听。
8. 文档、实际代码和最终页面三者一致；后续若改业务，以代码和接口为准同步更新本文。

---

## 19. 最容易出现的偏差

- 把原型中的演示通用页当作最终业务页面，覆盖现有完整组件。
- 只改颜色和圆角，没有实现四态、请求竞态和分页恢复。
- 为了好看把审核信息压缩到无法核对证据。
- 使用原型静态统计、趋势百分比和通知数据替代真实接口。
- 待办搜索只过滤当前页，却显示完整总数。
- 全局搜索退化成无分页、无返回定位的弹窗。
- 忘记 AI 待办是子集，导致总角标重复计数。
- 暗色模式只改背景，未同步表格、弹窗、Toast 和 ECharts。
- Tailwind Preflight 覆盖 Element Plus 样式，导致全站按钮和表格错位。
- Three.js 持续渲染或遮挡内容，影响后台性能与阅读。
- 双击 HTML 的降级行为被误当成生产 Vite 页面能力缺失。

按本文实施时，应始终把管理端定位为**高频核对与处理工具**：视觉负责提升层级和反馈，业务证据、操作确定性与数据准确性始终优先。
