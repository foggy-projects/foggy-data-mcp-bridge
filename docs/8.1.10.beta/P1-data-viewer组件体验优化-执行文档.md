# P1-data-viewer 组件体验优化 — 执行文档

## 文档作用

- doc_type: `requirement+implementation-plan`
- intended_for: `reviewer` / `execution-agent`
- purpose: 将上游对 `foggy-data-viewer` 的组件层反馈整理为可评审、可开工的单文档执行包，明确本轮优化范围、责任边界、代码触点和完成标准

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`执行中`
- 责任项目：`foggy-data-mcp-bridge`
- 责任模块：`addons/foggy-data-viewer/frontend`
- 验证模块：`addons/foggy-data-viewer/verification-app`
- 对齐文档：
  - `docs/8.1.10.beta/P1-QM前端组件体系-技术规范.md`
  - `docs/8.1.10.beta/P1-QM前端组件体系/S3-业务接入规范.md`
  - `docs/8.1.10.beta/P1-QM前端组件体系/S4-代码生成器规范.md`

## 背景

上游业务在接入 `foggy-data-viewer` 后，集中反馈了几类问题：

1. `toolbar` slot 在开发时新增后，HMR 热更新不稳定，往往需要重启 Vite 并清理 `.vite` 缓存。
2. 规范与示例中已经把 `row-actions` 作为标准扩展点，但组件库当前真正落地的是 `column-*` 型 cell slot，导致 `row-actions` 契约与实际实现脱节。
3. `DataTable` 内部维护了选中行状态，但上层没有直接 `getSelectedRows()` 便捷 API。
4. 表格默认使用 `height: '100%'`，业务方在“填满剩余空间”场景下需要自行补完整的高度链路，现有文档说明不足。

这些问题的共同点不是“核心查询能力错误”，而是组件层契约、开发体验和接入可用性还没有完全收口。如果继续保持现状：

- 业务方会把“规范已支持”的扩展点误判为“运行时可直接使用”
- 批量操作、列表页工具栏、行级动作等高频场景仍需要业务端绕路
- 组件库的真实能力边界和文档边界会继续漂移

## 问题定义

本轮要解决的不是重新设计整个 QM 前端组件体系，而是收口 `data-viewer` 在列表页场景下最直接的几个体验缺口：

- 已承诺的扩展点要么真正可用，要么明确降级为文档约束，不允许继续处于“规范说有、实现里没有”的状态
- 高概率复用的交互能力要给出一跳可达 API，而不是要求业务方先拿内部实例再自行探测
- 高度链路这类容易踩坑的接入要求，要由组件文档明确说清，而不是默认让业务方自己试
- 开发时新增标准 slot 不应依赖完整重启才可见

## 目标

- 修复 `toolbar` slot 相关开发态 HMR 体验问题，至少让“新增标准 slot 后可见”成为可复现、可验证的默认行为
- 收口 `row-actions` 扩展点，使其在组件层和规范层表达一致
- 为 `DataTable` / `DataTableWithSearch` 暴露直接的选中行获取 API
- 补齐“表格自适应高度”的推荐 CSS 使用手册
- 为本轮优化补足测试和 verification evidence，避免再次出现“文档已写、运行时未兑现”的情况

## 非目标

- 本轮不重做 `DataTable` 的整体渲染架构
- 本轮不新增 `QueryTable` 抽象层；如上游存在该包装层，由上游在消费侧自行代理本轮新增 API
- 本轮不默认向行数据自动注入 `_rowIndex` 或伪 `_rowId`
- 本轮不把“业务缺少稳定主键”问题转嫁为组件库自动兜底
- 本轮不扩展新的查询能力、排序能力或过滤 DSL 语义

## 范围决策

### 本轮纳入

- `toolbar` slot HMR 问题复现与修复
- `row-actions` 扩展点契约收口
- `getSelectedRows()` / `getSelectedCount()` 便捷 API 暴露
- 自适应高度文档补充
- 对应测试、verification 验证和规范文档同步

### 本轮暂不纳入

- 行双击事件自动注入行标识
- 基于 `ResizeObserver` 的全自动高度计算新机制
- 更高层业务脚手架或代码生成器以外的新组件抽象

## 方案约束

- 保持 `DataTable` 作为通用表格组件的定位，不把过多业务约定直接塞进裸表格模板
- `row-actions` 如需作为标准能力落地，应优先在 `DataTableWithSearch` 或生成层做“约定映射”，而不是仅在模板中孤立增加一个无上下文的 `<slot name="row-actions" />`
- API 补齐必须同步更新单元测试和使用文档
- 所有改动优先保持向后兼容：已有 `column-actions` 用法不能被破坏

## 验收标准

### 功能验收

- 在 verification 或等价最小复现场景中，新增 `#toolbar` slot 后无需手工清 `.vite` 缓存即可看到更新
- 在组件标准用法中，传入 `#row-actions` 后可以稳定渲染行级操作内容
- `DataTable` 可直接获取当前选中行数组；`DataTableWithSearch` 可直接代理该能力
- 现有 `#column-actions` 使用方式仍然可用，不产生重复操作列
- 文档中明确给出“固定高度”和“填满剩余空间”两类推荐写法

### 质量验收

- `addons/foggy-data-viewer/frontend` 相关单元测试更新并通过
- `npm run build:lib` 通过
- verification-app 至少覆盖：
  - 工具栏 slot
  - 行操作 slot
  - 批量选择并读取选中行
  - 高度自适应示例

### 文档验收

- 规范文档不再继续宣称未兑现的扩展点
- 组件使用文档补齐接入要求与兼容说明

## Module Responsibility

### Workspace / Root

- 在 `docs/8.1.10.beta/` 维护本轮执行文档
- 明确本轮优化属于 `foggy-data-viewer` 组件库收口，不扩散为跨仓大改

### foggy-data-viewer frontend

- 负责组件实现、对外 expose、slot 契约和文档收口
- 负责补齐单元测试和构建验证

### verification-app

- 负责提供最小可视化验证场景
- 用于复现 HMR 和确认 `row-actions` / 选中行 API 的使用体验

### 规范文档

- 负责把 `S3` / `S4` 中关于 `row-actions` 的表述与实际实现对齐
- 明确业务侧应直接使用的扩展点名称和边界

## Code Inventory

### 组件实现

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/src/components/DataTable.vue`
- role: 通用表格核心实现，维护 grid 配置、列组装、selection 状态和 expose
- expected change: `update`
- notes: 补齐选中行 API；保持对 `column-*` slot 的兼容；如需配合 `row-actions`，仅承担底层 cell slot 消费，不承担业务语义漂移

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/src/components/DataTableWithSearch.vue`
- role: 组合组件，负责标准 slot 透传、schema 模式和对外常用 API
- expected change: `update`
- notes: 优先在这里收口 `row-actions` 标准能力和 HMR 相关 slot 透传实现

### 测试

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/src/components/DataTable.test.ts`
- role: DataTable 单测
- expected change: `update`
- notes: 补选中行 expose、操作列兼容性或相关回归测试

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/src/components/DataTableWithSearch.test.ts`
- role: DataTableWithSearch 单测
- expected change: `update`
- notes: 补 `row-actions`、slot 透传和 API 代理相关测试

### 文档

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/USAGE.md`
- role: 组件使用说明
- expected change: `update`
- notes: 补表格高度链路推荐写法和选中行 API 示例

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/frontend/README.md`
- role: 组件库总览
- expected change: `update`
- notes: 如 README 已承接主要入口，需同步补一段“常见接入问题”

- repo: `foggy-data-mcp-bridge`
- path: `docs/8.1.10.beta/P1-QM前端组件体系/S3-业务接入规范.md`
- role: 业务接入规范
- expected change: `update`
- notes: 明确 `row-actions` 的最终约定和使用边界

- repo: `foggy-data-mcp-bridge`
- path: `docs/8.1.10.beta/P1-QM前端组件体系/S4-代码生成器规范.md`
- role: 代码生成器规范
- expected change: `update`
- notes: 如果生成层承担 `row-actions -> actions 列` 的约定映射，需要同步写清

### 验证场景

- repo: `foggy-data-mcp-bridge`
- path: `addons/foggy-data-viewer/verification-app/src/App.vue`
- role: verification 场景页
- expected change: `update`
- notes: 增加或调整最小验证场景，确保文档和真实行为一致

## 实施步骤

### Step 1. 复现并修复标准 slot 的开发态 HMR 问题

目标：先把问题从“反馈现象”变成“组件库可稳定复现的问题”，再收口实现。

执行要求：

1. 在 verification 或最小复现场景中验证“运行中新增 `#toolbar` slot”是否需要完整重启才可见。
2. 排查 `DataTableWithSearch` 当前动态 slot 透传方式是否为根因。
3. 如确认与动态透传有关，优先改为“标准 slot 显式透传 + 动态前缀 slot 保留兼容”的实现方式。

完成定义：

- 问题有明确复现记录
- 修复后可在开发态直接验证生效
- 不破坏现有 `column-*` / `filter-*` 自定义能力

### Step 2. 收口 `row-actions` 契约

目标：让规范、生成层和运行时行为一致。

推荐落地方式：

1. 保持 `DataTable` 的底层通用表格定位。
2. 在 `DataTableWithSearch` 或生成层识别 `row-actions` 标准扩展点。
3. 通过注入约定操作列或映射到 `column-actions` 的方式渲染行操作。
4. 若用户已显式提供 `actions` 列，不重复注入。

必须避免：

- 只在模板里机械增加 `<slot name="row-actions" />`
- 让 `row-actions` 和 `column-actions` 同时生成两套操作列

完成定义：

- `row-actions` 在标准示例中可直接使用
- 旧的 `column-actions` 用法继续兼容
- `S3` / `S4` 文档表述同步收口

### Step 3. 补齐选中行便捷 API

目标：把批量操作所需的选中行读取能力变成标准公开接口。

执行要求：

1. 在 `DataTable` expose 中增加 `getSelectedRows()`。
2. 如已有 `getSelectedCount()` 能力基础，可一并公开。
3. 在 `DataTableWithSearch` 对外代理同名方法，避免业务方必须先取内部实例。

完成定义：

- 业务侧可直接通过组件 ref 拿到选中行数组
- 单测覆盖 API 存在性和基本返回值

### Step 4. 补齐高度自适应文档

目标：把当前隐式接入约束转成显式说明。

执行要求：

1. 在 `USAGE.md` 或等价主文档中补两类示例：
   - 固定像素高度
   - 填满剩余空间的 flex 高度链路
2. 写清楚百分比高度依赖父级高度收敛，不承诺“无父容器约束自动铺满”。
3. 如 verification 已有成熟样式，优先复用示例。

完成定义：

- 文档中能直接找到推荐 CSS 方案
- 业务方不需要再从 verification 样式里倒推用法

### Step 5. 回归验证与构建

执行要求：

1. 运行 `npm test -- --run`
2. 运行 `npm run build:lib`
3. 在 verification-app 手工验证：
   - `toolbar` slot
   - `row-actions`
   - 批量选择并读取选中行
   - 高度自适应

完成定义：

- 测试通过
- 构建通过
- verification 结果可截图或形成简短验证记录

## 不做的事

- 不把“缺少业务主键”解释成组件自动补主键
- 不在本轮引入新的表格布局模式或额外第三方依赖
- 不为尚不存在的 `QueryTable` 预先设计接口层级

## 风险与注意事项

- `row-actions` 若落在错误层级，后续会继续出现“规范有、运行时无”的漂移
- HMR 问题若只靠文档规避而不复现，后续容易再次回归
- 高度问题本质上仍涉及父级布局约束，文档必须把边界写清，避免过度承诺

## 建议评审关注点

- `row-actions` 的最终归属层级是否接受“DataTable 保持通用，DataTableWithSearch/生成层承接约定映射”
- 是否接受本轮先做“文档明确 + API 补齐 + HMR 修复”，暂不处理 `_rowIndex/_rowId`
- verification 场景是否足够覆盖后续业务接入的高频路径

## 开工前置结论

满足以下条件后即可开工：

- 本文档评审通过
- `row-actions` 的最终落地层级不再摇摆
- 同意本轮不纳入自动行标识注入
