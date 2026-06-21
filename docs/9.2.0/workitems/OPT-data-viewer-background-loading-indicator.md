---
type: optimization
source_type: user-experience-followup
version: 9.2.0
ticket: OPT-data-viewer-background-loading-indicator
priority: P1
status: implemented
owner: foggy-data-viewer-frontend
created_at: 2026-06-21
updated_at: 2026-06-21
delivery_mode: single-root-delivery
automation_decision: required
---

# Data Viewer Background Query Loading Indicator

## 文档作用

- doc_type: workitem
- intended_for: execution-agent, reviewer, signoff-owner
- purpose: 记录 Data Viewer 表格在后台查询、排序、分页时的非阻塞 loading 体验方案、实现和验证结果。

## 背景

当前 `DataTableWithSearch` 已将表头输入拆为两类行为：

- 输入中只做当前页前端过滤，不触发后台查询。
- 用户按 Enter、清空过滤、选择过滤操作符、点击排序或分页时，才触发后台查询。

为了避免已有数据刷新时整表遮罩一闪一闪，组件暂时在已有数据场景下不再把 `query.loading` 直接传给 VXE 表格遮罩。这样解决了闪烁问题，但也带来新的体验缺口：后台查询、排序、分页正在执行时，用户缺少可感知的 loading 提示。

## 已确认体验原则

1. 首次加载或当前无数据时，可以使用表格级 loading 遮罩。
2. 已有数据时触发后台查询、排序、分页，不清空旧数据，不使用整表遮罩。
3. 后台刷新提示优先放在表格顶部 toolbar 右侧、分页器左侧。
4. 同时提供 2px 顶部进度线，放在 toolbar 下方、表头上沿，作为非阻塞刷新反馈。
5. loading 显示需要延迟和最小显示时长，避免请求很快时出现视觉闪烁。
6. 中文输入法组合输入期间不触发后台查询，也不出现后台 loading。
7. 绝大部分后台查询场景都带分页，因此分页器是后台 loading 的主承载位置；无分页表格通常是特殊用途，不能默认假定会触发后台查询。

## 参考方案来源

- Ant Design `Spin` 的 `delay` 机制用于避免快速请求闪烁。
- TanStack Query 区分初始 loading 和 background fetching，已有数据时保留旧数据并显示后台刷新状态。
- Element Plus、AG Grid、VXE 类表格的容器级 loading 更适合首次加载或无数据加载。
- Material Design 的 indeterminate progress 适合未知耗时的非阻塞进度反馈。
- NN/g 响应时间原则：极短操作无需反馈，超过用户感知阈值后再提供明确状态。

## 目标

1. 表格首次加载或无数据加载时，保留遮罩型 loading，但加入延迟显示和淡入淡出。
2. 已有数据上的后台查询、排序、分页，展示轻量状态提示和顶部进度线，不遮挡表格。
3. loading 文案能反映触发来源：
   - filter: `正在筛选...`
   - sort: `正在排序...`
   - page: `正在加载第 N 页...`
   - refresh / reload: `正在刷新...`
4. 快速请求不闪烁；慢请求有清晰状态；失败时保留旧数据并提供轻量失败提示。
5. 方案优先覆盖带分页的 schema 模式，同时不破坏受控模式和无分页特殊表格既有用法。

## 非目标

1. 不改变表头过滤的提交规则；输入仍然只做当前页过滤，显式提交才查后端。
2. 不在本轮引入请求取消、请求合并或竞态覆盖治理。
3. 不重做分页布局，不替换 `vxe-pager`。
4. 不把 loading 状态做成全局页面级 loading。
5. 不记录或触碰 npm token、私有账号、生产地址凭证。

## UX 设计

### 首次加载 / 无数据加载

- 使用表格级 loading overlay。
- 建议延迟显示：150ms。
- 建议最小显示时长：250ms。
- 建议淡入淡出：160ms。
- 只有请求超过延迟阈值才显示，避免 100ms 以内请求闪现。

### 已有数据后台刷新

- 保留当前表格数据。
- 在 `.data-table-toolbar .toolbar-right` 中、分页器左侧显示状态：
  - 小 spinner
  - 文案
  - 可选失败态 `查询失败，重试`
- 在 `.table-wrapper` 顶部显示 2px indeterminate progress bar。
- 状态提示和进度线共享延迟显示与最小显示时长。
- 不阻塞单元格查看、复制和横向滚动。

### 分页场景

- 分页触发后文案显示 `正在加载第 N 页...`。
- 分页控件可以在后台请求期间短暂禁用重复点击，避免连续触发多个页码请求。
- 新页数据返回前保留旧页数据，成功后一次性替换。

### 无分页场景

- `showPager=false` 时，不默认渲染分页旁 loading，因为这类表格很可能是特殊展示、局部工具或前端内存数据。
- 如果无分页表格仍处于 schema 模式并实际触发了 `fetchData`，只使用低干扰反馈：
  - 优先显示顶部 2px 进度线。
  - 仅在存在 toolbar-right 或外部刷新入口时显示短文案 `正在刷新...`。
- 如果无分页表格没有后台查询动作，不额外显示后台 loading UI。
- 无分页场景不因为本优化而新增自动后台查询行为。

### 失败场景

- 不清空表格。
- 不弹出强干扰 toast。
- 在分页旁边显示 `查询失败，重试`。
- 后续是否暴露 retry 回调可作为二阶段优化，本轮至少保留旧数据和错误状态。

## Module Responsibility

| Owner | Scope | Responsibility |
|---|---|---|
| workspace docs | `docs/9.2.0/workitems` | 记录需求、规划、测试和验收边界 |
| frontend component library | `addons/foggy-data-viewer/frontend` | 实现 DataTable / DataTableWithSearch 的 loading 体验 |
| demo app | `addons/foggy-data-viewer/frontend/src/App.vue`, `vite.config.ts` | 提供可观察的慢请求验证场景 |
| tests | `addons/foggy-data-viewer/frontend/src/components/*.test.ts` | 覆盖状态传递、延迟显示、后台刷新不遮罩 |

## Code Inventory

| Repo / Module | Path | Role | Expected Change | Notes |
|---|---|---|---|---|
| root | `docs/9.2.0/workitems/OPT-data-viewer-background-loading-indicator.md` | workitem | create | 本文档 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/composables/useTableQuery.ts` | query state | update | 增加 active trigger / error state 以支持文案和失败态 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/composables/useDeferredVisibility.ts` | UI state helper | create | 统一实现 show delay、min duration、fade 状态 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/DataTable.vue` | table shell | update | 渲染 toolbar 状态、顶部进度线、可选分页禁用 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/DataTableWithSearch.vue` | schema orchestration | update | 区分 initial loading 和 background loading，并传递 trigger 文案 |
| frontend | `addons/foggy-data-viewer/frontend/src/types/index.ts` | public contract | update | 必要时补充 loading 配置类型，保持向后兼容 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/DataTable.test.ts` | unit tests | update | 覆盖 UI 状态和延迟显示 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/DataTableWithSearch.test.ts` | unit tests | update | 覆盖 schema 模式已有数据后台查询不遮罩 |
| frontend | `addons/foggy-data-viewer/frontend/src/components/composables/useTableQuery.test.ts` | unit tests | update | 覆盖 active trigger 和错误状态 |
| frontend demo | `addons/foggy-data-viewer/frontend/vite.config.ts` | demo API | update | 支持可配置慢请求或固定延迟验证 |
| frontend demo | `addons/foggy-data-viewer/frontend/src/App.vue` | demo route | update | compact demo 展示后台刷新 loading |

## Implementation Plan

### Step 1: 查询状态分层

1. 在 `useTableQuery` 增加当前请求触发来源，例如 `activeTrigger`。
2. 记录最近一次查询错误状态，供 DataTableWithSearch 传递轻量失败提示。
3. 保持 `loading` 语义不变，避免破坏已有调用方。

完成标准：

- `loadData('sort')` 执行期间能读到 `activeTrigger = 'sort'`。
- 请求完成或失败后 active trigger 被清理。
- 现有 query hooks 行为不变。

### Step 2: 延迟显示工具

1. 新增 `useDeferredVisibility`，输入原始 busy 状态。
2. 支持 `delayMs = 150`、`minVisibleMs = 250`、`fadeMs = 160` 默认配置。
3. 请求在 delay 之前结束时，不显示状态。
4. 请求已经显示后，即使很快结束，也至少保留最小显示时长。

完成标准：

- fake timers 单测覆盖快请求不显示、慢请求显示、最小展示时长。
- 组件卸载时清理 timeout。

### Step 3: DataTable 轻量刷新 UI

1. 新增非阻塞后台 loading 展示入口。
2. 带分页时，在 toolbar-right 中、pager 左侧渲染 `data-table-query-status`。
3. 在 table-wrapper 顶部渲染 `data-table-progress-line`。
4. 使用 CSS transition 实现淡入淡出。
5. 保留 VXE overlay 仅用于首次或无数据 loading。
6. 无分页时不强制渲染状态文案；只有实际存在后台 loading 且有合适工具栏承载位时才展示轻量文案，否则只保留顶部进度线。

完成标准：

- 有数据后台刷新时，表格数据仍可见。
- 状态出现在分页器左侧。
- 顶部进度线不影响表头筛选、排序图标、滚动同步。

### Step 4: DataTableWithSearch 状态编排

1. schema 模式下：
   - `initialLoading = query.loading && query.data.length === 0`
   - `backgroundLoading = query.loading && query.data.length > 0`
2. 根据 `activeTrigger` 映射文案。
3. filter/sort/page/refresh/reload 触发后台查询时传递正确状态。
4. `showPager=false` 时，不生成分页类 loading 文案；只有实际后台请求中的 refresh/reload/filter/sort 才允许产生低干扰刷新提示。
5. 受控模式保持现有 `loading` 行为，必要时允许调用方传入后台 loading 配置。

完成标准：

- 表头输入未提交时不出现后台 loading。
- Enter 提交过滤时显示 `正在筛选...`。
- 点击排序时显示 `正在排序...`。
- 翻页时显示 `正在加载第 N 页...`。
- `showPager=false` 且无后台查询时不出现后台 loading UI。

### Step 5: Demo 和体验验证

1. compact demo 的 mock API 增加延迟参数或固定延迟，便于观察状态。
2. 用浏览器验证：
   - 初次进入页面加载态合理。
   - 输入未提交不触发后台 loading。
   - Enter、排序、分页触发轻量 loading。
   - 快速请求不闪烁。
   - 慢请求可感知。

完成标准：

- 提供本地预览 URL。
- Playwright 脚本验证核心交互。

## Testing Plan

| Layer | Test | Expected Result |
|---|---|---|
| unit | `useDeferredVisibility.test.ts` | 延迟显示、最小显示时长、卸载清理通过 |
| unit | `useTableQuery.test.ts` | active trigger、error state、loading 生命周期通过 |
| component | `DataTable.test.ts` | 后台状态和进度线渲染位置正确 |
| component | `DataTableWithSearch.test.ts` | schema 模式背景刷新不传 VXE overlay，仍传后台状态 |
| regression | existing DataTable / DataTableWithSearch suite | 既有筛选、排序、分页测试不回退 |
| build | `npm run build:lib` | 组件库构建通过 |
| browser | compact demo Playwright verification | 输入不查后端，提交/排序/分页有轻量 loading |
| browser | no-pager demo or component case | 无分页且无后台查询时不出现后台 loading；无分页但真实后台刷新时只显示低干扰提示 |

## Experience Progress

本任务涉及 UI 交互和数据展示，体验验证不可标记为 N/A。

| Dimension | Planned Check | Status |
|---|---|---|
| 页面可达性 | compact demo 能正常打开 | local-verified |
| 核心交互流程 | 输入、Enter 筛选、排序、分页触发正确 loading | local-verified |
| 表单验证 | 输入法组合输入不误触发后台查询 | covered-by-component-behavior |
| 异常状态 | 后台查询失败保留旧数据并显示轻量失败提示 | component-tested |
| 权限可见性 | 不涉及权限 UI，记录为 not-applicable | not-applicable |
| 数据一致性 | 后台返回后表格数据、total、分页一致 | local-verified |

## Acceptance Criteria

1. 已有数据时后台查询不出现整表遮罩。
2. 后台查询超过延迟阈值后，在分页器左侧显示轻量 loading。
3. 后台查询超过延迟阈值后，表格上沿显示 2px 进度线。
4. 快速请求不出现可感知闪烁。
5. 表头输入过程中不触发后台 loading；显式提交才触发。
6. 排序、分页、过滤提交都能显示对应文案。
7. 请求失败不清空旧数据，并显示轻量失败状态。
8. `showPager=false` 且无后台查询动作时，不额外显示后台 loading UI。
9. `showPager=false` 但 schema 模式真实后台刷新时，不显示分页类文案，最多显示低干扰刷新提示和顶部进度线。
10. 组件库测试和 `npm run build:lib` 通过。
11. compact demo 浏览器验证通过并提供截图或脚本输出作为 evidence。

## Constraints

1. 遵守根目录 `CLAUDE.md` 的敏感信息边界，不记录 npm token、账号密码或私有凭证。
2. 不引入新的 UI 库，优先使用现有 Element Plus / CSS / VXE 结构。
3. 不破坏 `vxe-pager` 当前布局。
4. 不改变现有 `fetchData` API 签名。
5. 不为了 loading 体验引入全局状态管理。

## Progress Tracking

### Development Progress

| Step | Status | Notes |
|---|---|---|
| Step 1 查询状态分层 | implemented | `useTableQuery` 记录 active trigger 和错误状态 |
| Step 2 延迟显示工具 | implemented | 新增 `useDeferredVisibility`，覆盖 delay / min duration / cleanup |
| Step 3 DataTable 轻量刷新 UI | implemented | toolbar 状态、顶部进度线、延迟遮罩已接入 |
| Step 4 DataTableWithSearch 状态编排 | implemented | 区分 initial loading 与 background loading |
| Step 5 Demo 和体验验证 | local-verified | compact demo 已用于用户预览确认 |

### Testing Progress

| Command / Evidence | Status | Notes |
|---|---|---|
| `npm test -- --run src/components/composables/useTableQuery.test.ts` | passed | active trigger 和错误状态测试通过 |
| `npm test -- --run src/components/DataTable.test.ts src/components/DataTableWithSearch.test.ts` | passed | 表格状态、排序、过滤、复制行为测试通过 |
| `npm test -- --run` | passed | 前端组件库测试通过 |
| `npm run build:lib` | passed | 组件库产物校验通过 |
| compact demo browser verification | passed | 用户已确认最终视觉效果可接受 |

### Implementation Self-Check

| Item | Status |
|---|---|
| requirement scope implemented | passed |
| non-goals preserved | passed |
| touched code paths recorded | passed |
| no token or credential recorded | passed |
| tests executed and results recorded | passed |
| acceptance readiness assessed | user-confirmed |

## Review And Acceptance Workflow

1. 编码完成后先执行轻量实现自检，并回写本文件 `Progress Tracking`。
2. 因本任务涉及共享组件和 UI 体验，完成后需要执行 `foggy-implementation-quality-gate`。
3. 测试通过后执行测试覆盖审计，确认单元、组件、浏览器体验证据覆盖验收标准。
4. 用户确认预览效果后，再进入 npm 发版流程。
