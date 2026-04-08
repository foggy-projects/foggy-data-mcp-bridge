> **⚠️ SUPERSEDED** — 本文档已被 [P1-QM前端组件体系-技术规范](../../P1-QM前端组件体系-技术规范.md) 及其子规范替代。保留仅供讨论历史回溯。

---

# P1-QM查询条件区与列筛选并存-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`讨论中`
- 所属系列：
  - [P1-QM前端生成与业务接入-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端生成与业务接入-需求.md)

## 背景
当前表格查询条件基本都“挂在列上”，前端更多是从列定义里推导 `filterType`、再在表格上方生成对应的筛选控件。

这种方式在简单列表里够用，但在业务系统里会遇到明显问题：

- 有些用户更习惯传统的“表格上方查询区”
- 有些查询条件并不适合作为列筛选表达
- 有些查询条件并不对应实际展示列
- 同一个字段有时既需要在查询区出现，也需要作为快捷列筛选出现

因此，`8.1.10.beta` 需要把“查询条件区”和“列筛选”从“只能二选一”升级为“可并存、可共享状态”的设计。

## 目标
- 支持传统查询区和列筛选并存
- 让两套 UI 最终汇聚到同一套查询状态和 DSL `slice`
- 让查询条件不再只能依附于列定义
- 为生成器和业务接入层提供稳定的 query schema

## 非目标
- 本文档不要求本版本实现复杂可视化查询设计器
- 本文档不要求支持任意自定义表达式拼装器
- 本文档不要求把所有查询条件都做成列头内联筛选
- 本文档不要求在 `8.1.10.beta` 引入查询字段分组或高级查询能力

说明：

- 查询字段分组
- 高级查询

统一放入 `8.1.11.beta` 讨论和规划

## 核心结论

### 1. 查询条件应成为一等模型，不再只从列派生
`ColumnSchema` 仍可表达“这个列可筛选”，但不应再承担全部查询条件定义职责。

需要新增独立的 `QueryFieldSchema` / `QuerySchema` 概念，用于描述：

- 哪些字段进入传统查询区
- 哪些字段进入列筛选
- 哪些字段同时出现在两处
- 哪些字段只作为隐藏默认条件存在

### 2. UI 可以有两种承载方式，但状态必须统一
本版本支持两条渲染通道：

- `QueryPanel`
  - 传统表格上方查询区
- `ColumnFilters`
  - 轻量快捷筛选区

两者最终必须共享同一份 `QueryState`，而不是各自维护一套筛选结果。

### 3. 最终查询协议仍然统一落到 DSL slice
无论条件来自：

- 查询区
- 列筛选
- 默认隐藏条件

最终都应统一编译为 `SliceRequestDef[]`，进入同一条查询链路。

## 查询条件模型

### QueryFieldSchema
建议新增独立结构：

```ts
interface QueryFieldSchema {
  key: string
  label: string
  sourceField?: string
  placement: 'form' | 'column' | 'both' | 'hidden'
  component:
    | 'text'
    | 'numberRange'
    | 'dateRange'
    | 'dictSelect'
    | 'qmLookupSelect'
    | 'memberSelect'
    | 'bool'
    | 'custom'
  defaultOperator?: string
  defaultValue?: unknown
  columnRef?: string
  dictId?: string
  lookupRef?: string
  order?: number
  span?: number
  visible?: boolean
}
```

说明：

- `key`：查询条件唯一键
- `sourceField`：最终生成 slice 使用的字段
- `placement`：决定出现在查询区、列筛选还是两者同时出现
- `columnRef`：若与某列关联，可显式引用列名

### QuerySchema
```ts
interface QuerySchema {
  fields: QueryFieldSchema[]
  submitMode?: 'manual'
  collapsible?: boolean
  defaultExpanded?: boolean
  layout?: QueryPanelLayoutSchema
}
```

含义：

- `fields`：查询条件定义列表
- `submitMode`：
  - 本版本默认固定为 `manual`
  - 服务端查询以“显式提交”生效，不采用任意输入即自动查库
- `collapsible` / `defaultExpanded`：用于传统查询区的折叠交互
- `layout`：用于传统查询区的布局规则

### QueryPanelLayoutSchema
```ts
interface QueryPanelLayoutSchema {
  mode?: 'grid'
  columns?: {
    xs?: 1
    sm?: 2
    md?: 3
    lg?: 4
    xl?: 4
  }
  labelWidth?: number
  actionAlign?: 'left' | 'right'
  collapsedRows?: number
  gutter?: number
}
```

含义：

- `mode`：
  - `8.1.10.beta` 先固定为 `grid`
  - 不在本版本引入复杂自由布局编辑器
- `columns`：响应式列数
- `labelWidth`：传统查询区标签宽度
- `actionAlign`：查询/重置按钮区域对齐方式
- `collapsedRows`：折叠时默认展示的行数
- `gutter`：字段间距

## placement 规则

### `form`
- 只出现在表格上方查询区
- 适合复杂、低频、传统业务条件

### `column`
- 只出现在快捷列筛选区
- 适合高频轻量筛选

### `both`
- 同时出现在查询区和列筛选
- 但共享同一份状态
- 任意一处更新后，另一处应同步显示
- 只建议用于少量高频字段，不应滥用

### `hidden`
- 不直接展示给用户
- 只作为默认条件或包装层注入条件存在

## 统一状态模型
建议新增两段状态：

```ts
type QueryDraftState = Record<string, unknown>
type QueryAppliedState = Record<string, unknown>
```

规则：

- `QueryPanel` 读写 `QueryDraftState`
- `ColumnFilters` 读写同一份 `QueryDraftState`
- 显式触发“查询/确认/回车”后，`QueryDraftState` 提交为 `QueryAppliedState`
- 服务端查询始终基于 `QueryAppliedState`
- 本地预览过滤可基于 `QueryDraftState`

这样可以避免：

- 查询区一套状态
- 列筛选一套状态
- 两边互相覆盖或相互看不见
- 输入过程中频繁触发服务端查询

## 交互提交规则

### 1. 表格上方查询区
- 修改查询区字段时，只更新 `QueryDraftState`
- 不立即影响表格主体服务端数据
- 只有点击“查询”按钮时，才把当前 draft 提交为 applied，并触发表格主查询

### 2. 列筛选
- 列筛选的修改也写入同一份 `QueryDraftState`
- 变更后应同步反映到表格上方查询区对应字段
- 对当前已加载表格数据可立即做本地预览过滤
- 但不应因为一次输入变更就立刻触发服务端查询
- 列筛选触发服务端查询时，应提交“整份当前 draft”，而不是只提交当前字段

### 3. 服务端查询的显式提交
列筛选触发服务端查询时，按以下规则：

- 文本输入类：
  - 只有用户在列筛选组件中按回车，才提交服务端查询
- 单选 / 多选下拉：
  - 组件上应提供“查询”或“确认后查询”动作
  - 用户只是切换选项时，只更新 draft，不立即查库
  - 用户主动点击查询/确认后，才提交服务端查询
- 日期范围 / 数值范围：
  - 建议与查询区一致，走显式确认

### 4. 本地预览过滤
列筛选在 draft 变化后，可以对当前已加载的表格数据做本地过滤预览。

约束：

- 这只是对当前前端数据集的即时过滤
- 不等同于服务端查询结果
- 一旦用户执行显式提交，应以服务端返回结果为准刷新表格主体
- 查询区字段修改默认不触发本地预览，避免表单编辑过程影响主体表格

## `both` 模式的联动规则

### 1. 定位
`both` 模式不是“双份独立条件”，而是：

- 查询区作为完整表达面
- 列筛选作为快捷入口
- 两者操作同一份条件状态

### 2. 适用范围
推荐只用于高频、简单、用户经常来回调的条件，例如：

- 关键词
- 状态
- 少量高频枚举

不建议默认进入 `both` 的条件：

- 复杂 lookup
- 复杂日期范围
- 多层级树形条件
- 占用空间较大的表单组件

### 3. 状态同步规则
- 在查询区修改，更新 `QueryDraftState`，列筛选同步显示
- 在列筛选修改，更新 `QueryDraftState`，查询区同步显示
- 同一字段以最后一次用户修改结果为当前 draft 值

### 4. 提交规则
- 查询区点击“查询”：
  - 提交整份 `QueryDraftState`
  - 刷新表格主体服务端数据
- 列筛选按回车 / 点查询 / 点确认：
  - 同样提交整份 `QueryDraftState`
  - 不是只提交当前字段

原因：

- `both` 模式下列筛选已经同步了查询区条件
- 如果列筛选只提交当前字段，会导致查询区其他草稿条件被忽略，交互语义不一致

### 5. 重置规则
- 列筛选内清空某字段：
  - 只清该字段的 draft
  - 查询区同步清空对应字段
  - 不自动触发服务端查询
- 查询区执行全局重置：
  - 清空整份 `QueryDraftState`
  - 列筛选同步清空
  - 仍需显式查询后才刷新服务端结果

## 查询区布局规范

### 1. 本版本只定义稳定的响应式网格布局
`8.1.10.beta` 不做自由拖拽布局，不做设计器式行列编排，先统一为传统查询区最常用的响应式网格：

- `xs = 1`
- `sm = 2`
- `md = 3`
- `lg = 4`
- `xl = 4`

业务包装层可以覆盖，但生成器默认按以上值产出。

### 2. 字段宽度只允许轻量控制
单个 `QueryFieldSchema` 继续只保留轻量布局属性：

- `order`
- `span`
- `visible`

约束：

- `span` 只对 `QueryPanel` 生效
- `span` 的语义是“占用几个 grid 单元”，而不是绝对像素宽度
- 本版本不引入复杂的 `rowStart`、`colStart`、自由区域命名

### 3. 组件默认占位规则
若生成器未显式指定 `span`，建议默认值如下：

- `text` / `bool` / `dictSelect`：`span = 1`
- `memberSelect` / `qmLookupSelect`：`span = 1`
- `numberRange` / `dateRange`：`span = 2`
- `custom`：默认 `span = 2`，由业务包装层再覆盖

### 4. 操作区布局规则
查询区默认应保留统一操作区，用于：

- 查询
- 重置
- 展开/收起

规则：

- 操作区默认右对齐
- 折叠状态下，操作区仍保持可见
- 业务系统如需额外按钮，应通过包装层扩展，不直接塞入生成 schema

### 5. 折叠规则
建议默认：

- `collapsible = true`
- `defaultExpanded = true`
- `collapsedRows = 1`

原因：

- 大多数业务页面都需要传统查询区，但并不希望首屏占用过高
- 先给出统一折叠策略，比每页自行发明一套更稳

## Query Schema 生成规则

### 1. 生成来源
`query.schema.ts` 不应凭空手写拼装，建议按以下优先级生成：

1. QM 前端元数据
2. 列 schema 中可复用的筛选语义
3. 生成器默认映射规则
4. 模型级生成配置覆盖

结论：

- Query schema 的主来源是 QM 元数据
- Column schema 只作为补充，不再反向主导查询模型

### 2. 字段纳入规则
默认只考虑满足以下条件的字段生成 query field：

- `filterable = true`
- 不是纯展示型计算字段
- 不是明显仅用于表格展示的衍生 label 字段

默认排除：

- `measure = true` 且无明确筛选语义的聚合指标
- 仅用于展示格式化的冗余 caption 字段
- 后端已声明不适合前端筛选的字段

### 3. 组件类型默认映射
建议默认映射如下：

- 有 `dictId`：`dictSelect`
- 维度成员字段或 `filterType = dimension`：`memberSelect`
- 远程 QM lookup 字段：`qmLookupSelect`
- 日期 / 日期时间：`dateRange`
- 数值型字段：`numberRange`
- 布尔型字段：`bool`
- 其他普通可搜索文本：`text`

若同一字段存在更明确的后端提示，以后端元数据为准。

### 4. sourceField 归一规则
查询条件最终应尽量落到“值字段”而不是“展示字段”：

- 字典字段：使用业务值字段
- member / lookup 字段：优先使用 `id/value` 字段
- `caption/label` 字段若仅用于展示，不直接作为默认 `sourceField`

例如：

- `team$caption` 在查询层应优先归一到 `team$id`
- `statusLabel` 若只是展示标签，不应默认作为 query field 主字段

### 5. placement 默认映射
本版本建议的默认策略：

- `text`：
  - 默认 `both`
  - 仅限高频关键词类字段
- `dictSelect` / 简单 `bool`：
  - 默认 `both`
- `dateRange` / `numberRange`：
  - 默认 `form`
- `memberSelect` / `qmLookupSelect`：
  - 默认 `form`
- 明显只适合快捷筛选的轻量字段：
  - 可显式生成为 `column`

约束：

- `both` 只能给少量高频字段
- 生成器不应因为字段可筛选就大量默认塞进 `both`

### 6. 顺序与布局默认规则
若后端未显式提供 query 字段顺序，建议生成器按以下顺序推导：

1. 关键词类字段
2. 高频状态/枚举字段
3. 时间范围字段
4. 维度 / lookup 字段
5. 其他低频条件

布局默认规则：

- `both` 字段优先靠前
- `form` 字段按业务常见使用顺序排列
- 范围类字段优先给 `span = 2`

### 7. hidden 条件生成规则
以下条件不进入用户可见 query fields，但可进入 `hidden`：

- 宿主固定上下文条件
- 模块级默认业务条件
- 只允许包装层注入、不允许页面随意修改的约束

这些条件：

- 可以出现在生成的 query schema 中
- 但默认 `placement = hidden`
- 最终仍参与 slice 编译

### 8. 允许覆盖，但禁止破坏主语义
业务包装层可以覆盖：

- `placement`
- `order`
- `span`
- `visible`
- `layout`

但不建议随意破坏：

- `sourceField` 的值字段归一规则
- lookup / member 类型到值字段的映射
- draft/applied 的统一状态模型

## 编译规则

### 1. 默认规则
若 `QueryFieldSchema` 只提供：

- `sourceField`
- `defaultOperator`

则按默认规则直接生成 slice。

### 2. 范围类组件
例如：

- `numberRange`
- `dateRange`

允许一个 query field 生成多个 slice。

### 3. lookup 类组件
例如：

- `dictSelect`
- `qmLookupSelect`
- `memberSelect`

应始终以对应的值字段生成 slice，而不是展示 label 字段。

### 4. hidden 条件
`hidden` 字段虽然不展示，但仍参与最终 slice 编译。

## 与列定义的关系
`ColumnSchema` 继续保留列展示职责，但不再承担全部查询语义。

推荐关系如下：

- 列是“展示模型”
- `QueryFieldSchema` 是“查询模型”
- 两者可以通过 `columnRef` 建立关联

结论：

- 不是每个可筛选列都必须进入查询区
- 不是每个查询条件都必须对应一个展示列

## 对组件设计的影响

### 1. 需要独立 QueryPanel
建议新增或抽象独立的传统查询区组件，例如：

- `QueryPanel`

职责：

- 根据 `QuerySchema` 渲染传统查询区
- 支持折叠/展开
- 支持查询/重置

### 2. SearchToolbar 不应再是唯一查询入口
当前 `SearchToolbar` 更多是列筛选驱动模型。

后续应调整为：

- 要么继续承担 `ColumnFilters`
- 要么被重构为同时支持 query schema

但无论哪种，都不应继续假设“查询条件只来自列”。

进一步要求：

- 需要支持 draft 状态和 applied 状态
- 需要区分“本地预览过滤”和“提交服务端查询”

### 3. DataTableWithSearch 需要同时支持两类查询 UI
建议最终支持如下能力开关：

- `querySchema`
- `showQueryPanel`
- `showColumnFilters`

这样业务系统可以自由选择：

- 只用传统查询区
- 只用列筛选
- 两者并存

同时需要支持：

- `queryDraftState`
- `queryAppliedState`
- 列筛选本地预览开关

## 对生成器设计的影响
代码生成不应只产出 table schema，还应产出 query schema。

建议生成器至少补出：

- `*.table.schema.ts`
- `*.query.schema.ts`

这样：

- 列展示配置和查询条件配置可以独立演进
- 业务包装层更容易做覆盖和布局调整

## 对业务系统使用规范的影响
业务包装层需要能够覆盖：

- query panel 是否显示
- query field 的 placement
- query field 的顺序、span、折叠策略
- 默认隐藏条件

这意味着包装层不只覆盖列，也要覆盖 query schema。

## 当前建议
`8.1.10.beta` 按以下顺序推进：

1. 先把 `QueryFieldSchema` / `QuerySchema` 模型定住
2. 再让生成器产出 `query.schema.ts`
3. 再在前端组件层实现 `QueryPanel + ColumnFilters` 并存，以及 draft/applied 双态
4. 最后补业务包装层覆盖规则

## 后续讨论项
- `QueryPanel` 是否作为独立组件暴露
- query schema 的后端元数据显式扩展位
- `8.1.11.beta` 是否补充 query field 分组能力
- `8.1.11.beta` 是否补充高级查询能力
