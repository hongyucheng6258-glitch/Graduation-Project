# 梧桐校园学生端 UI 原型开发文档

> 文档版本：v1.0  
> 编写日期：2026-11-13  
> 适用项目：`Ai-campus_副本`  
> 实现目标：以已验收的学生端高保真原型为视觉目标，在现有 Vue 3 工程中完成渐进升级，并保留活动、闲置、失物、学习、动态、AI、消息和三维校园的真实业务能力。

---

## 1. 基准文件与实现边界

### 1.1 交付物

- 学生端高保真原型：`wutong-ui-prototype/梧桐校园-UI原型.html`
- 本开发文档：`wutong-ui-prototype/梧桐校园-学生端UI原型-开发文档.md`
- 原型素材：`wutong-ui-prototype/assets/`
- 学生端工程：`web/frontend/student/`
- 后端工程：`web/backend/`

### 1.2 最终原型的首页结构

实现时以以下顺序为准，不恢复为另一套 Dashboard，也不改回旧的左右侧栏首页：

1. 水平顶栏导航。
2. 左侧文案、右侧校园图的首页横幅。
3. 六项快捷校园入口。
4. 校园活动推荐。
5. 动态广场横幅。
6. 闲置互换内容区。
7. 学习搭子与学习互助区。
8. 失物招领内容区。
9. AI 学习互动区。
10. 页脚。

### 1.3 冲突时的优先级

1. 登录鉴权、真实数据、业务状态、安全和已有测试。
2. 现有 Vue Router 路由与 API 合同。
3. 本文和最终原型规定的布局、视觉和组件反馈。
4. Motion、噪点、渐变网格和 Three.js 氛围。

### 1.4 原型演示与生产业务的差异

| 原型表现 | 生产实现要求 |
|---|---|
| 单页内切换 `home/activity/idle/study` 等视图 | 使用现有 Vue Router 页面，首页只是聚合入口，不复制完整业务逻辑 |
| 点击活动卡片打开简要弹窗 | 可保留快速预览，但完整信息、报名冲突、候补和签到必须进入 `/activity/detail/:id` |
| 发布活动使用一个简化弹窗 | 使用现有 `/activity/publish` 完整发布页，保留校验、图片、AI 辅助和审核流程 |
| 报名按钮延迟后显示成功 | 调用真实报名接口，处理登录、时间冲突、人数、审核、取消报名和候补状态 |
| 收藏只切换图标颜色 | 调用统一收藏 API，并恢复真实收藏状态 |
| 预约、交接、认领只显示 Toast | 进入现有闲置预约/交接与失物认领流程，提交真实表单 |
| 学习搭子和问答合并为一个展示视图 | 首页可合并宣传，业务仍分别进入 `/partner` 和 `/qa` |
| AI 返回固定文本 | 使用真实 AI 向导/Agent/会话接口，失败可重试，不伪造“在线”与答案 |
| 全局搜索只过滤活动 | 使用 `/search` 综合搜索及原有分类搜索能力 |
| 消息“全部已读”只隐藏角标 | 调用 `markAllRead()`，成功后同步消息 Store 和未读数 |
| 三维校园弹窗列出四个房间 | 入口进入现有 `/campus-3d`，保留完整校园场景、服务空间与降级列表 |
| 静态统计和“在线”标签 | 有真实字段才显示；无数据时隐藏，不编造今日数量或预计分钟数 |

---

## 2. 现有工程与改造原则

### 2.1 已确认技术栈

- Vue 3.4、Vue Router 4、Pinia 2
- Vite 6
- Element Plus 2.7、@element-plus/icons-vue
- Axios、Day.js
- Markdown It、Highlight.js、Monaco、WangEditor
- 开发端口：`5173`
- `/api` 代理到 `http://localhost:8080`
- `/ws` 代理到 `ws://localhost:8080`

### 2.2 改造原则

- 保留 `MainLayout.vue` 的水平顶栏，不改成管理端式深蓝侧栏。
- 保留首页横幅与六项快捷入口，升级其比例、留白和反馈。
- 首页数据优先复用 `/home/aggregate`，不要在首页重复实现各业务列表的完整逻辑。
- 真实写操作必须调用 `src/api/`，不得只修改本地数组或显示假成功。
- 公共视觉收敛到 Token、`WtHero`、按钮、卡片、输入、弹窗、Toast 和状态组件。
- 保留登录守卫、消息轮询、私信未读、主题和移动端导航能力。
- 不使用外部随机图片，不引入另一套模板。

### 2.3 Tailwind 接入

原型使用 Tailwind 辅助响应式布局。现有工程没有 Tailwind，为避免 Preflight 与 Element Plus 冲突，采用前缀方式接入：

```bash
cd web/frontend/student
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

已有 SFC 类名继续保留，新布局工具类使用 `wt-` 前缀。不要求为了 Tailwind 批量重写已有业务组件。

### 2.4 Motion 与 Three.js

```bash
npm install motion three
```

- Motion One 用于首屏编排、区块 reveal、弹窗与反馈。
- Three.js 只用于横幅右侧低对比氛围；完整三维校园继续使用现有场景。
- 两者均不得阻塞首屏文字、图片和接口请求。

---

## 3. 路由与信息架构

### 3.1 公共页面

| 页面 | 路径 | 说明 |
|---|---|---|
| 首页 | `/` | 原型重点改造页 |
| 综合搜索 | `/search` | 保留分类搜索和结果定位 |
| 活动列表/详情 | `/activity`、`/activity/detail/:id` | 公开浏览 |
| 闲置列表/详情 | `/idle`、`/idle/detail/:id` | 公开浏览 |
| 失物列表/详情 | `/lostfound`、`/lostfound/detail/:id` | 公开浏览 |
| 学习搭子 | `/partner` | 公开列表 |
| 互助问答 | `/qa`、`/qa/detail/:id` | 公开浏览 |
| 动态广场 | `/social` | 公开浏览 |
| 校园公告 | `/notice`、`/notice/detail/:id` | 公开浏览 |
| 3D 校园 | `/campus-3d` | 独立于主布局的完整场景 |

### 3.2 登录后页面

- 发布：`/activity/publish`、`/idle/publish`、`/lostfound/publish`、`/partner/publish`、`/qa/publish`。
- 我的活动：`/activity/my-signup`。
- 我的预约：`/idle/appointments`。
- AI：`/ai/chat`、`/ai/code`、`/ai/wrong`。
- 消息与私信：`/message`、`/chat`、`/chat/:conversationId`。
- 个人中心：`/profile`、`/user/:id`。
- 你画我猜：`/draw-guess`、`/draw-guess/room/:roomId`。

路由守卫保持：非 `meta.public` 页面未登录时跳转 `/login?redirect=<原路径>`。登录成功后应回到原目标页。

### 3.3 顶栏导航取舍

原型桌面顶栏展示：首页、校园活动、闲置互换、失物招领、学习搭子、互助问答、动态广场。

现有 AI 学习、私信、发布、主题和个人中心能力不能删除：

- AI 学习通过第六个快捷入口、首页 AI 区和移动导航进入。
- 私信通过消息中心、个人中心和移动导航进入。
- 发布通过头像菜单或顶栏发布按钮进入各真实发布页。
- 主题可收纳进个人菜单，避免顶栏过满。
- 移动端继续提供完整功能导航，而非只保留桌面七项。

---

## 4. 设计 Token

### 4.1 原型精确色板

```css
:root {
  --page: #f1f4f6;
  --bg: #fbfdfd;
  --panel-2: #f2f5f7;
  --panel-3: #e7edf0;

  --border: rgba(23,35,49,.085);
  --border-strong: rgba(23,35,49,.16);

  --ink-1: #172331;
  --ink-2: #405063;
  --ink-3: #6c7b8b;

  --accent: #315fdb;
  --accent-hover: #244ab4;
  --accent-soft: rgba(49,95,219,.09);
  --on-accent: #f6f8ff;

  --up: #2c7a8c;
  --down: #b84940;
  --warn: #9a6818;

  --shadow-card:
    0 1px 2px rgba(25,47,68,.03),
    0 12px 30px -18px rgba(25,47,68,.14),
    0 32px 70px -45px rgba(25,47,68,.15);
  --shadow-float:
    0 2px 5px rgba(25,47,68,.05),
    0 20px 50px -16px rgba(25,47,68,.22),
    0 50px 100px -45px rgba(25,47,68,.22);

  --r-shell: 18px;
  --r-card: 12px;
  --r-control: 8px;
  --r-pill: 999px;
  --ease: cubic-bezier(.16,1,.3,1);
}
```

### 4.2 与现有 Token 的映射

不要一次性删除现有变量，也不要让变量引用自身。先将原型变量命名为独立的 `--wt-*`，再映射到项目已有变量：

```css
:root {
  --wt-page:#f1f4f6; --wt-bg:#fbfdfd;
  --wt-panel-2:#f2f5f7; --wt-panel-3:#e7edf0;
  --wt-ink-1:#172331; --wt-ink-2:#405063; --wt-ink-3:#6c7b8b;
  --wt-border:rgba(23,35,49,.085);
  --wt-border-strong:rgba(23,35,49,.16);
  --wt-accent:#315fdb; --wt-accent-hover:#244ab4;
  --wt-accent-soft:rgba(49,95,219,.09); --wt-on-accent:#f6f8ff;
  --wt-r-card:12px; --wt-r-control:8px;

  --paper:var(--wt-page); --surface:var(--wt-bg);
  --surface-2:var(--wt-panel-2); --surface-3:var(--wt-panel-3);
  --ink:var(--wt-ink-1); --ink-2:var(--wt-ink-2); --ink-3:var(--wt-ink-3);
  --line:var(--wt-border); --line-strong:var(--wt-border-strong);
  --brand:var(--wt-accent); --brand-strong:var(--wt-accent-hover);
  --brand-soft:var(--wt-accent-soft); --brand-ink:var(--wt-on-accent);
  --r-md:var(--wt-r-card); --r-sm:var(--wt-r-control);
  --shadow-sm:var(--shadow-card); --shadow-lg:var(--shadow-float);
  --ease-out:var(--ease);
}
```

其中 `--shadow-card`、`--shadow-float` 和 `--ease` 直接使用第 4.1 节定义的原型变量，不与现有变量同名。暗色主题同时覆盖 `--wt-*` 源变量和必要的组件色，不要通过自引用覆盖旧变量。

### 4.3 暗色主题

原型重点验收浅色，但现有工程已有暗色能力，不得删除。暗色按语义映射：

- `--page`：深墨蓝灰，建议 `#101720`。
- `--bg`：建议 `#17212c`。
- `--panel-2`：建议 `#1e2a37`。
- `--ink-1`：建议 `#edf2f7`。
- `--ink-2`：建议 `#c0cbd7`。
- `--ink-3`：建议 `#94a3b3`。
- 边框使用白色低透明度，阴影降低扩散。
- 校园蓝适当提亮，保证按钮和文字对比度。

暗色最终实现需单独截图验收，不可直接反色图片或简单降低全页亮度。

### 4.4 字体与数字

```css
font-family: "PingFang SC", "Microsoft YaHei", "Segoe UI", system-ui, sans-serif;
```

| 用途 | 规格 |
|---|---|
| 首页主标题 | `clamp(34px,3.3vw,48px)`，行高 1.18，字重 700–750 |
| 区块标题 | 24px，移动端 22px |
| 展示卡标题 | 28px，移动端 24px |
| 页面标题 | 34px，移动端 27px |
| 卡片标题 | 14px / 1.45，字重 600–650 |
| 正文 | 13–14px / 1.6–1.85 |
| 辅助信息 | 10–12px / 1.5 |
| 数据 | 使用 `font-variant-numeric:tabular-nums` |

不使用全大写英文眉标，不给标题中的某一个词随意换色，不使用 Emoji 作为图标。

---

## 5. 应用外壳

### 5.1 顶栏

- 高度 70px，`position:sticky; top:0; z-index:40`。
- 背景：`rgba(241,244,246,.82)`，`backdrop-filter:blur(20px)`。
- 底边：1px `--border`。
- 内容最大宽度 1240px，水平 padding 20px。
- 品牌：四格蓝色标志 + “梧桐校园”，英文副标仅作辅助，移动端隐藏。
- 导航项高 40px，13px，激活项校园蓝并带底部 2px 指示线。
- 搜索框宽 230px，高 40px；`Ctrl/Cmd + K` 聚焦。
- 右侧保留消息、头像和必要的主题/发布入口，间距不小于 7px。

### 5.2 内容容器

```css
.content {
  max-width: 1240px;
  margin: 0 auto;
  padding: 26px 20px 44px;
}
```

宽屏 `>=1600px` 时顶部 padding 增至 32px，横幅高度增至约 390px。不要无限增加内容宽度。

### 5.3 移动导航

- `<=900px` 隐藏桌面导航，显示菜单按钮。
- 菜单位于顶栏下方，左右 12px，三列网格。
- 使用细边框、表面色和浮层阴影，不改成全屏深色菜单。
- 菜单打开时 `aria-expanded=true`，选择后关闭。
- Escape、点击外部和路由变化后关闭。
- 完整功能较多时使用现有抽屉承载全部入口，视觉仍沿用浅色面板与校园蓝。

### 5.4 页脚

- 顶部 1px 分隔线，品牌与一句项目说明。
- 桌面左右排列，移动端纵向排列。
- 可保留你画我猜、帮助与页面导航，不添加无效社交图标。

---

## 6. 通用组件规范

### 6.1 卡片与 Bento 区块

- 大容器圆角 18px，仅用于 Hero 和大弹窗。
- 普通卡片圆角 12px，控件 8px，状态标签胶囊。
- 卡片背景 `--bg`，1px 细边框，轻阴影。
- 内容卡 Hover 最多上移 3px，封面图缩放最多 1.025。
- 不让每个文本块都成为卡片，不使用厚重渐变边框。

### 6.2 按钮

- 默认高 42px，小按钮 34px，水平 padding 12–16px。
- Primary：校园蓝底、`--on-accent` 字。
- Default：表面色、细边框。
- Ghost/Text：无厚重底色，用于查看全部和次级操作。
- Active 可缩放至 `.97`，不使用弹跳。
- 异步操作必须 loading、禁用并防重复提交。

### 6.3 输入与筛选 Chip

- 搜索和表单输入高 40–44px，8px 圆角。
- Focus：校园蓝边框和 3px `--accent-soft` 外环。
- Chip 高 32px，激活时浅蓝底与校园蓝文字。
- 分类和关键词变化应调用服务端列表接口或明确的前端已加载数据筛选，不伪造完整总数。
- 筛选状态建议写入 URL，便于分享和返回。

### 6.4 内容卡

统一四部分：

1. 16:9 封面。
2. 状态标签和可选收藏按钮。
3. 标题、分类、地点或摘要。
4. 时间、人数/价格/互动量与进入动作。

- 标题最多两行，溢出省略。
- 封面使用真实图片，失败时用项目插画或图标占位。
- 收藏按钮是独立按钮，不与卡片跳转发生事件冲突。
- 状态标签同时有文字和颜色。

### 6.5 弹窗

生产优先使用 Element Plus Dialog 或原生 `<dialog>`，不要复刻一个无焦点锁定的普通 `div`。

- 默认宽 `min(560px,calc(100vw - 32px))`，宽详情 760px。
- 顶部安全区布局，最大高度 `calc(100vh - 100px)`，内容可滚动。
- 打开保存原焦点，关闭恢复；Tab 不离开弹窗。
- 移动端按钮纵向排列并占满宽度。
- 表单错误靠近字段，提交失败保留输入。
- 复杂发布和报名流程使用真实路由页，不把所有业务强塞进弹窗。

### 6.6 Toast、加载、空和异常

- Toast 位于右下，移动端宽度 `calc(100vw - 32px)`。
- 成功文案必须来自真实操作结果；AI 和业务失败不使用成功图标。
- Loading 使用骨架屏，保持卡片宽高。
- Empty 提供换分类、清关键词或发布入口。
- Error 提供重试，并保留用户已填内容。
- 首页聚合接口局部失败时，其他分区继续可用，不能显示整页空白。

---

## 7. 首页详细实现

### 7.1 首页横幅

桌面：

```text
┌──────────────────────────── 横幅卡片 ────────────────────────────┐
│ 问候 / 主标题 / 描述 / 两个按钮        │ 校园图 + Three 氛围     │
│                                      │ 底部“探索 3D 校园”        │
└──────────────────────────────────────┴───────────────────────────┘
```

- 整体双列约 `1.02fr .98fr`，最小高 360px。
- 左侧 padding 42px，右侧图片容器 margin 12px。
- 主按钮“发现校园活动”进入 `/activity`。
- 次按钮“找 AI 帮忙”：登录后进入 `/ai/chat` 或首页 AI 区；未登录按现有守卫处理。
- 校园图：`home-campus.webp`，`fetchpriority=high`。
- “探索 3D 校园”进入 `/campus-3d`，不是只显示演示弹窗。
- 问候使用本地时间和昵称；未登录显示“同学”。
- 移动端变为单列，图片位于文案下方，最小高 240px。

可复用并升级 `components/wt/WtHero.vue`，不要在 `Home.vue` 再复制一套不相关 Hero。

### 7.2 六项快捷入口

| 入口 | 路由 |
|---|---|
| 校园活动 | `/activity` |
| 闲置互换 | `/idle` |
| 失物招领 | `/lostfound` |
| 学习搭子 | `/partner` |
| 互助问答 | `/qa` |
| AI 学习 | `/ai/chat` |

- 桌面六列，1120 以下三列，600 以下两列。
- 卡片最小高 116px，移动端 106px。
- 图标 34px 容器，功能 SVG，不用 Emoji。
- Hover 位移和边框反馈克制，键盘焦点完整。

### 7.3 活动推荐

- 标题：“把课余时间，留给热爱”。
- 桌面三列，900 以下两列，600 以下单列。
- 首页筛选：精选推荐、文化艺术、体育运动。
- 数据：`homeAggregate().activities`，或必要时 `listActivity({pageNum:1,pageSize:3,...})`。
- 分类筛选如果只对首页已加载的三条数据过滤，文案必须是“首页推荐筛选”；完整活动筛选在 `/activity` 服务端分页完成。
- 卡片进入真实详情，不用静态文案替代时间、地点、人数和状态。

### 7.4 动态广场横幅

- 使用左右分栏展示卡，图片 `generated-social-campus.webp`。
- 主文案：“有些小美好，值得一起分享。”
- CTA 进入 `/social`。
- 你画我猜入口可以放在动态区或移动导航，进入 `/draw-guess`。
- 原型中的“今日新动态/正在进行的话题”只有在有真实统计字段时显示；否则隐藏指标区，不把总动态数改名为今日数。

### 7.5 闲置互换

- 标题：“好物不闲置”。
- 首页展示最多三件，来自 `homeAggregate().idleItems`。
- 卡片显示标题、分类、地点、期望交换物、状态和封面。
- CTA 进入 `/idle`，单卡进入 `/idle/detail/:id`。
- 预约与交接进入真实详情/预约页，调用 `appoint()`、交接确认等现有接口。
- 未登录点击预约时跳登录并保留返回路径。

### 7.6 学习搭子与学习互助

- 使用反向分栏展示卡，图片 `generated-study-partner.webp`。
- 文案覆盖学习搭子、互助问答、代码纠错与错题复习。
- 主 CTA 进入 `/partner`；问答入口进入 `/qa`，AI 入口进入 `/ai/chat`。
- 错题数量可调用 `wrongStats()`，未登录显示“登录后查看错题”。
- 没有真实预计时长时，不显示“预计 12 分钟”。

### 7.7 失物招领

- 标题：“每一份遗失，都有回应”。
- 使用简洁列表而非过满卡片。
- 数据：`homeAggregate().lostFounds` 或 `listLostFound()`。
- 显示寻物/拾到、标题、地点、时间和图片。
- “我有线索/申请认领”进入 `/lostfound/detail/:id`，提交真实认领信息。
- AI 候选匹配使用 `lostMatch()`，必须允许用户核对候选，不自动确认认领。

### 7.8 AI 学习互动区

组成：

- 左侧 AI 学习图片。
- 右侧标题、状态、对话预览、两个推荐问题、输入和发送按钮。
- 快捷问题：“推荐本周活动”“查找学习搭子”。

生产建议：

- 首页简单校园问题可使用 `agentAsk(question, sessionId, signal)` 或 `aiGuideAsk(question)`。
- 需要持续会话、PDF、代码和错题时进入 `/ai/chat` 等完整页面。
- 未登录时先进入登录页，不显示伪造答案。
- “在线”只在 capabilities 或健康检查成功后显示；未配置/失败时显示“暂不可用”并提供重试。
- 发送期间禁用重复提交，路由离开时通过 `AbortController` 取消。
- Agent 返回活动草稿、闲置预约等动作时，只展示确认卡和跳转，未经用户确认不得执行写操作。
- AI 文本使用文本插值或项目既有安全 Markdown 渲染，不直接注入未消毒 HTML。

---

## 8. 活动完整流程

### 8.1 列表

生产页面：`/activity`。

- 页头：标题、说明、发布活动。
- 工具栏：关键词、分类、刷新。
- 分类：全部、文化艺术、体育运动、学术科技、志愿公益；实际分类以接口字典为准。
- 数据：`listActivity({ keyword, category, pageNum, pageSize })`。
- 筛选变化页码回 1，URL 保留查询条件。
- 使用服务端分页，不用首页六条演示数据代替完整列表。
- 加载、空、异常三种状态保持工具栏可用。

### 8.2 详情

生产页面：`/activity/detail/:id`。

必须保留：

- 活动标题、封面、分类、地点、开始/结束时间、说明。
- 报名状态和人数。
- 报名、取消报名、报名名单和发布者处理成员。
- `signupConflicts()` 时间冲突提示。
- 满员后的 `activityWaitlist()`、加入/退出/接受候补等真实能力。
- 签到二维码（发布者/有权限用户）。
- 收藏状态与收藏/取消收藏。

原型的简要活动弹窗可以作为快速预览，但不能替代上述详情。

### 8.3 报名

1. 检查登录与活动当前状态。
2. 查询并展示日程冲突。
3. 用户填写真实报名所需信息。
4. 调用 `signupActivity(id,data)`。
5. 成功后刷新详情、人数、我的报名与消息状态。
6. 满员时按业务进入候补，不能仍提示“报名成功”。
7. 失败保留表单并展示后端错误。

### 8.4 发布

生产页面：`/activity/publish`。

原型字段最低要求：

- 活动名称（必填）。
- 活动分类。
- 开始时间（必填）。
- 活动地点（必填）。
- 活动说明。
- 现有发布页中的结束时间、人数、图片和其他真实必填字段继续保留。

校验：

- 去除首尾空白。
- 结束时间晚于开始时间。
- 人数和图片约束按现有 DTO。
- 提交中禁用按钮。
- 成功后提示真实审核状态并进入详情或我的活动。
- `aiAssistCompose()` 和 `aiAssistPolish()` 只能辅助生成草稿，用户确认后才提交。

---

## 9. 其他业务页面落地

### 9.1 闲置互换

- 列表/详情沿用 `/idle` 与 `/idle/detail/:id`。
- 视觉使用原型内容卡、状态标签和分栏服务介绍。
- 保留发布、编辑、下架、重新上架、我的闲置。
- 预约：`appoint(id,data)`。
- 预约处理：`handleAppoint(id,accept)`。
- 完成与评价：`finishAppoint()`、`reviewAppoint()`。
- 交接：`getIdleHandoff/proposeIdleHandoff/confirmIdleHandoff`，保留版本确认，避免双方状态覆盖。
- AI 估价和 Agent 草稿只作辅助，不自动发布或预约。

### 9.2 失物招领

- 保留寻物与拾到两类发布。
- 详情展示真实物品特征、地点、时间和图片。
- 认领：`claimLostFound()`；发布者处理：`handleClaim()`；双方确认：`confirmClaim()`。
- 认领表单要求提供可核对特征，不能点击一次就直接完成认领。
- AI 匹配：`lostMatch()`，结果只作为候选。

### 9.3 学习搭子与问答

学习搭子：

- 列表、发布、我的搭子、结束搭子关系。
- AI 匹配 `partnerMatch()` 只生成推荐，由用户选择。

互助问答：

- 列表、发布、详情、回答、采纳。
- AI 参考回答 `aiAnswer()` 必须标明 AI 属性，不能自动采纳。

### 9.4 动态广场与你画我猜

- 动态发布、详情、点赞/取消、评论列表与发表评论。
- 列表使用 `listPost()`，保留 `compactImages=true` 与已通过审核的图片地址。
- 图片失败显示占位，不使用外部随机图。
- 你画我猜进入真实房间流程，保留登录、房间码、实时连接和断线反馈。

### 9.5 AI 学习中心

- `/ai/chat`：完整会话与流式回答。
- `/ai/code`：代码纠错。
- `/ai/wrong`：错题、提纲和同类题。
- SSE 必须处理 `delta/done/error`、跨 chunk、截断流、取消和非 SSE JSON 错误。
- EOF 不等于成功，只有收到 done 才按完整回答结束。
- AI 长请求使用现有 silent 与长超时策略，页面内显示错误与重试。

---

## 10. 全局搜索、消息与个人中心

### 10.1 全局搜索

- 顶栏支持全部、活动、闲置、失物、动态范围。
- 全部进入 `/search?q=...`，单类进入对应列表并携带 `q`。
- 空关键词给出轻提示，不发请求。
- 保留现有 `SearchResults.vue` 的结果分类、详情链接和状态。
- 移动端点击搜索图标进入完整搜索页，不显示一个无法使用的小输入框。

### 10.2 消息中心

- 未读数为 `messageStore.unread + chatStore.unreadTotal`。
- 点击铃铛进入 `/message`，未登录先登录。
- 全部已读调用 `PUT /message/read-all`，成功后更新 Store。
- 轮询在登录后启动，退出登录和组件卸载时停止。
- 私信未读与系统消息不得重复计数。

### 10.3 个人中心

头像菜单至少保留：

- 个人中心。
- 我的报名/预约/收藏入口。
- 错题本。
- 主题设置。
- 退出登录。

退出登录停止消息轮询、销毁聊天连接并返回登录页。

---

## 11. 动效、材质与三维校园

### 11.1 Motion 编排

首屏：

- Hero 和快捷区作为一次编排，时长约 600ms。
- 每项 stagger 45–80ms。
- 位移 12px 内，透明度到 1 后保持稳定。

滚动：

- 使用 `IntersectionObserver`，阈值约 `.08`。
- 每个区块只 reveal 一次，离开后不重复消耗动画。
- 卡片 Hover 与滚动 reveal 不同时控制同一 transform 属性。

交互：

- 弹窗、Toast、菜单反馈 160–320ms。
- 加载骨架 shimmer 在减少动效时停止。
- 不使用数字从 0 滚动、打字机、无限漂浮粒子或全站统一淡入模板。

### 11.2 噪点与渐变网格

- `body::before` 固定噪点透明度约 `.026`，`pointer-events:none`。
- 渐变网格仅两处低对比径向渐变，不能变成紫蓝大面积背景。
- 玻璃模糊仅用于顶栏、消息浮层和必要浮层，不能全站滥用。

### 11.3 首页 Three.js 氛围

原型场景规格：

- 摄像机 FOV 38°，位置约 `(5,4.2,7)`。
- 三组低多边形建筑体块，材质粗糙度约 `.58`，非金属。
- 半球光 + 一盏方向光，低对比校园蓝灰。
- 一个低透明网格和最多 30 个粒子。
- Canvas 透明度约 `.38`，不接收指针事件。
- 像素比上限 1.5，标签页隐藏时停止绘制。
- WebGL 初始化失败时隐藏 Canvas，保留校园图与按钮。

生产必须额外做：

- 组件卸载时取消 RAF、移除 resize/visibility 监听并释放资源。
- `prefers-reduced-motion` 时不运动或完全隐藏。
- 低性能设备优先静态图，不为了氛围降低页面帧率。

### 11.4 完整 3D 校园

`/campus-3d` 继续使用 `src/features/campus3d/campus-data.js` 的数据驱动结构：

- 服务分组：校园总览、校园服务、社交信息、AI 学习。
- 服务空间：活动、闲置、搭子、失物、问答、动态、游戏、公告、消息、AI、代码、错题等。
- 保留 `ROOM_PROFILES` 的房间类型、设施、提示和色彩。
- WebGL 不可用时提供服务列表，不阻断业务入口。
- 首页的简化“房间列表”只能作为降级，不替代完整三维校园。

---

## 12. 接口接入清单

默认前缀：`/api`。

| 模块 | 方法与路径 | 前端 API | 用途 |
|---|---|---|---|
| 首页 | GET `/home/aggregate` | `homeAggregate` | 活动、闲置、失物首页数据 |
| 公告 | GET `/notice/list` | `listNotice` | 公告列表 |
| 活动 | GET `/activity/list` | `listActivity` | 服务端列表与筛选 |
| 活动 | GET `/activity/{id}` | `activityDetail` | 详情 |
| 活动 | POST `/activity` | `publishActivity` | 发布 |
| 活动 | POST `/activity/{id}/signup` | `signupActivity` | 报名 |
| 活动 | DELETE `/activity/{id}/signup` | `cancelActivitySignup` | 取消报名 |
| 活动 | GET `/activity/{id}/signup-conflicts` | `signupConflicts` | 日程冲突 |
| 活动 | `/activity/{id}/waitlist` | `activityWaitlist` 等 | 候补 |
| 闲置 | GET `/idle/list` | `listIdle` | 列表 |
| 闲置 | POST `/idle/{id}/appoint` | `appoint` | 预约 |
| 闲置 | `/idle/appoint/{id}/handoff` | handoff API | 交接协商与确认 |
| 失物 | GET `/lostfound/list` | `listLostFound` | 列表 |
| 失物 | POST `/lostfound/{id}/claim` | `claimLostFound` | 认领 |
| 失物 | POST `/lostfound/match` | `lostMatch` | AI 候选匹配 |
| 搭子 | GET `/partner/list` | `listPartner` | 列表 |
| 搭子 | POST `/partner/match` | `partnerMatch` | AI 匹配 |
| 问答 | GET `/qa/list` | `listQuestion` | 列表 |
| 问答 | POST `/qa/{id}/answer` | `answerQuestion` | 回答 |
| 动态 | GET `/post/list` | `listPost` | 动态列表 |
| 动态 | POST/DELETE `/post/{id}/like` | `likePost/unlikePost` | 点赞 |
| 动态 | POST `/post/{id}/comment` | `commentPost` | 评论 |
| 收藏 | POST/DELETE `/favorite/{type}/{id}` | `favorite/unfavorite` | 收藏 |
| 消息 | PUT `/message/read-all` | `markAllRead` | 全部已读 |
| AI 向导 | POST `/ai/guide/ask` | `aiGuideAsk` | 校园数据问答 |
| AI Agent | POST `/ai/agent/ask` | `agentAsk` | 活动/闲置等 Agent 结果 |
| AI 会话 | POST `/ai/chat/stream` | `chatStream` | SSE 答疑 |
| AI 辅助 | `/ai/assist/compose`、`/polish` | assist API | 草稿与润色 |

### 12.1 请求约定

```json
{ "code": 200, "message": "success", "data": {} }
```

- `code!==200` 按业务失败处理，不能仅看 HTTP 200。
- 学生 Token 来自 `localStorage.token`，由现有 Axios 拦截器附加。
- 401 跳登录并保留 redirect；503 按维护策略处理。
- `silent:true` 的请求由页面局部展示错误，不重复弹全局错误。
- SSE、文件和流式响应不走普通 JSON 解包。
- 所有列表筛选和分页必须与服务端总数一致。

---

## 13. 素材清单与使用规则

原型共引用 14 个本地图片：

| 文件 | 用途 |
|---|---|
| `home-campus.webp` | 首页主横幅 |
| `campus-v2.webp` | 摄影活动、校园导览 |
| `generated-activity-campus.webp` | 活动封面 |
| `activity-card.jpg` | 体育活动 |
| `generated-social-campus.webp` | 动态、夜跑与社交场景 |
| `social-card.jpg` | 动态内容 |
| `generated-idle-items.webp` | 闲置服务横幅与物品占位 |
| `idle-calculator.jpg` | 计算器 |
| `idle-badminton.jpg` | 羽毛球拍 |
| `idle-schematic-book.jpg` | 教材 |
| `generated-study-partner.webp` | 学习搭子与技术活动 |
| `generated-lost-found.webp` | 失物服务横幅 |
| `lost-card.jpg` | 卡包失物 |
| `generated-ai-study.webp` | AI 学习与工作坊 |

生产规则：

- 首页横幅和服务宣传图放入 `student/public/images/`，沿用已有文件。
- 真实用户内容优先使用后端图片地址和 `content-assets.mjs`。
- 原型图片只能作为宣传图或开发 fallback，不能冒充用户上传内容。
- 首屏主图高优先级，其余图片懒加载。
- 图片容器固定宽高比，失败显示项目插画/图标。
- HTML 原型还引用 Tailwind、Motion、Three CDN；生产必须安装本地依赖，不依赖 CDN 才能运行。

---

## 14. 推荐文件落点

```text
web/frontend/student/src/
├─ layout/MainLayout.vue             # 顶栏、搜索、消息、移动导航、个人菜单
├─ views/home/Home.vue              # 按最终原型重排首页内容流
├─ components/wt/
│  ├─ WtHero.vue                    # 双列横幅、校园图、可选氛围插槽
│  ├─ WtContentCard.vue             # 可新增：统一首页内容卡
│  ├─ WtFeatureSplit.vue            # 可新增：动态/学习等分栏卡
│  ├─ WtState.vue                   # 可新增：loading/empty/error
│  └─ WtThemeToggle.vue             # 保留
├─ components/campus/HeroAmbient.vue # 可新增：Three 氛围与资源释放
├─ composables/
│  ├─ useRequestState.js            # 可新增：请求四态和版本
│  └─ useReducedMotion.js           # 可新增：统一动效偏好
├─ styles/
│  ├─ tokens.css                    # 保留现有变量
│  ├─ prototype-overrides.css       # 新增：原型 Token 与公共覆盖
│  ├─ tailwind.css                  # 新增：wt- 前缀工具类
│  └─ base.css                      # 可访问性和基础样式
└─ api/                              # 保留真实接口，不在组件中伪造成功
```

公共组件抽取以复用价值为准。首页可以保留清晰的分区 SFC，不要求为了组件化而拆成大量难维护的小文件。

---

## 15. 可访问性、安全与性能

### 15.1 可访问性

- 所有按钮、卡片和菜单可键盘操作，焦点环为 2px 校园蓝。
- 收藏使用 `aria-pressed`，菜单使用 `aria-expanded`，当前导航使用 `aria-current`。
- 弹窗焦点锁定、Escape 关闭、关闭后恢复焦点。
- 图片替代文本说明内容；纯装饰 Canvas `aria-hidden=true`。
- 状态、错误和 AI 生成内容不只靠颜色区分。
- 减少动效时直接呈现最终状态。

### 15.2 安全

- 用户动态、问答、活动说明、AI 输出不得未经消毒进入 `v-html`。
- 发布、报名、预约、认领、确认动作均由后端鉴权和校验。
- Agent 草稿与确认卡必须由用户明确确认，不能自动执行写操作。
- 不在日志或 URL 中写入 Token、密码和敏感个人信息。
- 文件上传沿用类型、大小和后端扫描约束。

### 15.3 性能

- 首页使用聚合接口，避免每个分区重复请求同一列表。
- 非首屏图片懒加载，Three 动态导入。
- 路由组件继续懒加载，保留 Vite manualChunks。
- 取消离开页面后的搜索、AI 和冲突查询。
- 消息轮询和三维渲染在后台标签页停止。
- 长列表服务端分页，首页每区只取必要条数。

---

## 16. 推荐实施顺序

### 阶段 A：基础与应用外壳

1. 跑通当前登录、首页、活动、闲置、AI 和消息。
2. 新增原型 Token 覆盖层与旧变量别名，避免自引用。
3. 接入带前缀、无 Preflight 的 Tailwind。
4. 升级顶栏、搜索、移动导航、消息和个人菜单。
5. 统一按钮、输入、卡片、Chip、Dialog、Toast 和四态。

### 阶段 B：首页

1. 升级 `WtHero`，实现桌面双列和移动单列。
2. 实现六项快捷入口及响应式 6/3/2 列。
3. 按最终顺序接入活动、动态、闲置、学习、失物、AI 分区。
4. 使用真实聚合数据，移除无依据的静态指标。
5. 完成局部加载、空、异常与图片 fallback。

### 阶段 C：关键流程

1. 活动列表、详情、收藏、报名冲突、候补和发布联调。
2. 闲置预约/交接、失物认领、搭子/问答联调。
3. 动态点赞评论、你画我猜入口联调。
4. AI 首页向导、完整会话、草稿确认和错误重试联调。
5. 消息全部已读与未读数同步。

### 阶段 D：动效与验收

1. 加入首屏编排、单次 scroll reveal 和组件反馈。
2. 加入可降级的 Three 氛围，验证完整 `/campus-3d` 不受影响。
3. 验收 1440、390、900/600 断点、暗色和键盘操作。
4. 运行项目测试与构建，完成真实后端联调。

---

## 17. 验收标准

### 17.1 首页视觉

- [ ] 顶栏仍为水平导航，未改成左侧 Dashboard。
- [ ] 1440px：Hero 左右两列，快捷入口六列，活动卡三列。
- [ ] 390px：Hero 单列，快捷入口两列，活动卡单列。
- [ ] 首页分区顺序与本文第 1.2 节一致。
- [ ] 页面 `scrollWidth` 与 viewport 一致，无横向溢出。
- [ ] 视觉使用细边框、轻阴影、克制噪点与渐变，无 Emoji、发光和信息过满。
- [ ] 真实数据为空或失败时，不出现伪造的卡片和指标。

### 17.2 活动

- [ ] 分类、关键词、分页、刷新和空态使用真实接口。
- [ ] 活动详情包含真实时间、地点、人数、说明与状态。
- [ ] 收藏状态刷新后保持，按钮事件不触发卡片跳转。
- [ ] 发布必填校验、提交 loading、失败保留输入、成功状态正确。
- [ ] 报名处理登录、冲突、满员、取消和候补，不只显示固定 Toast。
- [ ] 我的报名和消息状态在操作后同步。

### 17.3 其他业务

- [ ] 闲置预约、交接双方确认、完成与评价未退化。
- [ ] 失物认领需提交核对信息，AI 匹配不自动确认。
- [ ] 学习搭子和问答分别进入真实路由。
- [ ] 动态点赞、取消点赞和评论与接口一致。
- [ ] 你画我猜保留房间与实时连接流程。
- [ ] `/campus-3d` 服务空间和降级列表可用。

### 17.4 AI 与消息

- [ ] AI 未配置、失败、取消和截断流均有正确反馈。
- [ ] 首页无真实健康状态时，不显示“在线”。
- [ ] Agent 写操作必须经用户确认。
- [ ] SSE 收到 done 才按完整回答结束。
- [ ] 消息全部已读后角标与 Store 同步，退出登录停止轮询。

### 17.5 交互和质量

- [ ] 所有异步按钮防重复提交。
- [ ] 弹窗焦点、Escape、关闭后焦点恢复正常。
- [ ] 移动菜单支持外部点击和 Escape。
- [ ] 减少动效时无持续运动，信息完整。
- [ ] Three 失败不影响校园图、导航和业务按钮。
- [ ] 控制台无未捕获异常、Vue 警告和资源 404。
- [ ] `npm test` 与 `npm run build` 通过，关键真实业务回归通过。

---

## 18. 完成定义（Definition of Done）

只有同时满足以下条件，才视为学生端按原型实现完成：

1. 首页布局、视觉、响应式和组件反馈与最终原型一致。
2. 顶栏、横幅、六项快捷入口和最终内容流没有被另一套模板替代。
3. 活动发布/报名、闲置预约/交接、失物认领、学习互助、动态和 AI 使用真实接口。
4. 登录、权限、消息、收藏、搜索、三维校园和你画我猜能力未退化。
5. 1440 与 390 无横向溢出，快捷入口桌面六列、移动两列。
6. 加载、空、异常、无权限、AI 未配置和网络中断均有可用反馈。
7. 控制台无错误，资源无 404，动画和三维资源在卸载时释放。
8. 测试、构建和关键业务验收通过，文档与最终实现同步更新。

---

## 19. 最容易出现的偏差

- 只看原型静态效果，把真实列表、详情和发布页替换成演示弹窗。
- 首页改回旧双栏侧栏，或套成与管理端相同的左侧导航。
- 用首页三条数据的前端筛选冒充完整服务端搜索。
- 用固定“报名成功、预约成功、AI 在线”掩盖接口失败。
- 保留原型里的静态今日数量、预计分钟和互动量。
- 收藏按钮与卡片跳转嵌套不当，导致点击收藏同时进入详情。
- Motion 与 CSS Hover 同时写 transform，造成抖动或动画停在中途。
- Three.js 覆盖正文、持续后台渲染或未释放资源。
- Tailwind Preflight 影响 Element Plus 和现有编辑器样式。
- 暗色只改背景，未检查输入、弹窗、Toast、图片和 AI 气泡。

最终实现应让学生端首先保持**清晰、温暖、可用**：首页负责发现和连接，完整业务页负责真实操作，氛围效果始终服从信息与任务。
