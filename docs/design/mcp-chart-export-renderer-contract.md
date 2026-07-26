---
doc_type: delivery-spec
delivery_type: feature
version: next-unassigned
ticket: mcp-chart-export-renderer-contract
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: repository-owner-via-user-request
approved_at: 2026-07-26
open_questions: []
---

# Delivery Spec: MCP 引擎原生图表配置与双渲染导出工具

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 固定 XChart/ECharts 引擎原生优先的图表合同、双工具边界、查询结果归一化规则、
  验收标准与实施范围。
- canonical_path: `docs/design/mcp-chart-export-renderer-contract.md`
- release_assignment: 本事项尚未绑定具体发布版本；版本归属不阻塞实现，也不由本事项决定。

## Goal

- version_goal: 让图表模块默认依赖 JVM 内 XChart 即可工作，同时保留显式 ECharts 外部渲染能力。
- target_outcome:
  - LLM 面对两个描述独立、引擎固定的查询导出工具。
  - XChart 工具直接接受 XChart builder/styler/series 语义的 Adapter JSON。
  - ECharts 工具直接接受原生 ECharts Option。
  - 不要求 LLM 学习 Foggy 自定义通用图表 DSL，也不在两个引擎之间转换配置。
  - 普通 DSL、DSL_CTE、timeWindow 和可图表化的 Pivot 只改变数据结果与字段绑定，不改变
    各引擎配置语言。
- critical_outcomes:
  - 默认 XChart 路径不依赖浏览器、Node.js 或额外服务。
  - MCP 工具描述按引擎拆分，避免同一描述混入两套配置词汇。
  - Foggy 只增加查询数据绑定、图片封装和安全约束；样式、坐标轴、系列等尽量使用引擎原生表达。
  - 查询权限和语义查询边界保持不变，图表层只消费最终查询结果。
  - Pivot、timeWindow 和 DSL_CTE 不因结果形态差异产生错误图表或静默重复统计。
- success_is_sufficient_when: AC-1 至 AC-10 均有实际测试证据，且受影响模块测试通过。

## Scope

- in_scope:
  - 将查询图表导出拆为 `dataset.export_with_xchart` 和 `dataset.export_with_echarts`。
  - 移除旧 `dataset.export_with_chart` 的注册、描述、Schema 和内部引用，不提供兼容别名。
  - 让 XChart 工具直接使用 Foggy XChart Adapter Config，不增加通用图表转换层。
  - 让 ECharts 工具直接使用原生 ECharts Option，不转换 XChart 配置。
  - 不提供公共 `options` 层；引擎私有配置直接写入各工具自己的 `chart.config`。
  - 保留 `chart.generate` 作为直接数据生成入口，默认 `xchart`，并按 `engine` 接受对应配置。
  - 对普通 DSL、DSL_CTE、timeWindow 和 Pivot flat 查询结果做图表前归一化。
  - 更新工具配置、角色过滤、审计默认值、AI 结果校验、描述、Schema 和测试。
- affected_modules:
  - `foggy-dataset-mcp`
  - `foggy-mcp-launcher`
  - `foggy-dataset-demo`（审计工具字典）
  - `addons/foggy-benchmark-spider2`（MCP 测试配置）
  - `addons/foggy-dataset-model-mongo`（审计测试夹具）
  - `docs/design`
- external_dependencies:
  - XChart Maven 依赖，用于默认 JVM 内渲染。
  - 现有 `chart-render-service`，仅供显式 ECharts 工具或直接生成时的 ECharts 路径使用。

## Non-Goals

- out_of_scope:
  - 不定义 Foggy 通用图表 config。
  - 不实现 XChart↔ECharts 配置转换，也不保证两份 config 可移植。
  - 不通过公共 `options` 对配置做二次 merge。
  - 不自动推断图表类型、X/Y 字段、ECharts encode 或业务含义。
  - 不通过反射开放任意 XChart Java setter；Adapter Config 只支持 Schema 明确声明的能力。
  - 不支持 Pivot `tree`、`grid`、`hierarchyMode=tree` 的自动展开。
  - 不自动拼接多层 Pivot 轴标签，也不为 ECharts 自动生成未知枚举值对应的动态多 series。
  - 不支持 `dataset.compose_script` 多 plan 输出直接生成图表。
  - 不改变 QueryModel、Pivot、timeWindow 或 DSL_CTE 引擎语义。
  - 不把 ECharts 改造成 JVM 内渲染，也不新增浏览器运行时。
  - 本事项不统一修正全局 `PivotRequest` 默认值与 MCP 文档默认值的历史差异。
- do_not_touch:
  - 语义查询的权限注入、namespace、datasource、缓存和预聚合路由。
  - DSL_CTE 沙箱、受控 stage contract 与禁止 raw SQL 的边界。
  - 图表存储适配器的既有 URL/Base64 降级契约，除非测试证明与本功能冲突。
- non_blocking_or_waivable_items:
  - 非核心 XChart builder/styler/series 映射可以由 owner 延后。
  - ECharts 真实外部服务 E2E 可以用 WireMock 合同测试替代。

## Terminology and Design Principle

- `XChart Adapter Config`：
  - 这是 Foggy 提供的 JSON 表达，用于映射明确开放的 XChart builder、styler 和 series 能力。
  - 它沿用 XChart 的类型、属性和枚举词汇，但不是上游 XChart 发布的标准 JSON 协议。
  - `xField`、`yField`、`seriesField`、`nameField`、`valueField` 是 Foggy 为对象行数据增加的
    最小绑定扩展。
- `ECharts Option`：
  - 指 ECharts 原生 Option 对象。
  - Foggy 只负责注入受治理的数据源和图片输出参数，不把 Option 改写成另一套图表 DSL。
- engine-native-first：
  - 能由目标引擎直接表达的图表能力，不再先经过 Foggy 通用配置。
  - Foggy 特有字段只用于工具编排、查询数据绑定、安全保护和图片结果封装。

## Public Tool Contract

### XChart query export

工具名固定为：

```text
dataset.export_with_xchart
```

请求示例：

```json
{
  "model": "SalesQueryModel",
  "payload": {
    "columns": ["month", "amount"]
  },
  "chart": {
    "config": {
      "chartType": "CategoryChart",
      "title": "月度销售额",
      "xAxisTitle": "月份",
      "yAxisTitle": "销售额",
      "theme": "GGPlot2",
      "styler": {
        "legendVisible": true,
        "labelsVisible": true,
        "defaultSeriesRenderStyle": "Bar"
      },
      "series": [
        {
          "name": "销售额",
          "xField": "month",
          "yField": "amount",
          "renderStyle": "Bar"
        }
      ]
    },
    "image": {
      "width": 1200,
      "height": 700,
      "format": "png"
    }
  }
}
```

- 工具没有 `engine` 参数。
- `chart.config` 直接使用 XChart Adapter Config。
- XChart 的 chart type、theme、styler、series 样式和枚举直接写在 `config` 中。
- export 数据只能来自查询最终 `items`；series 使用字段绑定，不接受 `xData`、`yData` 或
  Pie series `value` 注入另一份数据。
- XChart Adapter Config 使用封闭 Schema，未知字段必须拒绝并返回可诊断错误。

### ECharts query export

工具名固定为：

```text
dataset.export_with_echarts
```

请求示例：

```json
{
  "model": "SalesQueryModel",
  "payload": {
    "columns": ["month", "amount"]
  },
  "chart": {
    "config": {
      "title": {"text": "月度销售额"},
      "tooltip": {"trigger": "axis"},
      "dataset": {
        "dimensions": ["month", "amount"]
      },
      "xAxis": {"type": "category"},
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "销售额",
          "type": "bar",
          "encode": {"x": "month", "y": "amount"}
        }
      ]
    },
    "image": {
      "width": 1200,
      "height": 700,
      "format": "png"
    }
  }
}
```

- 工具没有 `engine` 参数。
- `chart.config` 就是原生 ECharts Option，保持开放对象语义。
- exporter 将最终查询行注入单个 `dataset.source`，Option 使用 ECharts 原生 `dataset`、
  `dimensions`、`encode` 等机制描述图表。
- export 配置不得用 `dataset.source`、`series[*].data` 或 `xAxis.data` 替换查询结果。
- 首期继续只支持单个 dataset 对象；ECharts dataset 数组以及依赖数据集链的 `transform`
  不在本事项范围内。

### Shared export behavior

- `model`、`payload` 与 `dataset.query_model` 保持同一语义。
- `chart.image` 是 Foggy 通用图片输出封装；格式仍受对应渲染器能力约束。
- 查询 `items` 自动成为图表数据，调用方不得在 export 工具中另传 `data`。
- 面向 LLM 的描述必须明确：常规请求优先选择 XChart；只有用户明确要求 ECharts，或确实需要
  ECharts 私有能力时才选择 ECharts。
- 两个工具之间不做自动 fallback；显式 ECharts 失败时返回其错误和已取得的查询结果，不静默改画
  XChart。
- MCP/应用启动不检查 ECharts 服务可达性，只有调用 ECharts 路径时才访问外部服务。

### Direct data tool

`chart.generate` 保留为直接数据入口：

```json
{
  "engine": "xchart",
  "data": [
    {"month": "1月", "amount": 12000},
    {"month": "2月", "amount": 15000}
  ],
  "config": {
    "chartType": "CategoryChart",
    "title": "月度销售额",
    "styler": {"legendVisible": true},
    "series": [
      {
        "name": "销售额",
        "xField": "month",
        "yField": "amount",
        "renderStyle": "Bar"
      }
    ]
  },
  "image": {"width": 1000, "height": 600, "format": "png"}
}
```

- `engine` 缺省为 `xchart`。
- `engine=xchart` 时 `config` 使用 XChart Adapter Config。
- `engine=echarts` 时 `config` 使用原生 ECharts Option。
- `data` 必填且至少包含一行；对应 adapter 将其绑定到 XChart series 或
  ECharts `dataset.source`。
- 不提供 `options`；直接数据工具同样不做跨引擎配置转换。
- `config` 内不得嵌入第二份数据；XChart 使用字段绑定，ECharts 使用 dataset/encode。

## Engine-specific Config Boundaries

### XChart Adapter Config

- 首期至少保留当前已实现的 `CategoryChart`、`XYChart`、`PieChart`。
- builder 能力直接使用 `title`、`xAxisTitle`、`yAxisTitle`、`theme` 等 XChart 词汇。
- styler 能力直接放入 `styler`，字段与 Schema 明确开放的 XChart Styler setter 语义一致。
- CategoryChart/XYChart series 使用 `xField`、`yField` 和可选 `seriesField` 绑定对象行。
- PieChart 使用 `nameField`、`valueField` 绑定对象行。
- render style、smooth、颜色、线宽、marker、Y 轴分组等使用 XChart series 语义，不映射为
  Foggy 公共枚举。
- Adapter 必须显式解析和校验支持的字段、类型与枚举；不得对任意输入执行 Java 反射调用。
- 字段不存在是稳定错误；CategoryChart/XYChart 数值 Y 为 `null` 时表达为数据缺口，不补零。

### ECharts Option

- Option 中的 title、tooltip、legend、grid、axis、series、visualMap、dataZoom、dataset、
  dimensions、encode 等保持 ECharts 原生语义。
- Foggy 不解析这些属性的业务含义，也不尝试生成等价 XChart 配置。
- renderer 在发送请求前注入规范化后的单一 `dataset.source`。
- Option 中已有的 `dataset.source`、`series[*].data` 或 `xAxis.data` 必须被拒绝，不能绕过
  查询结果；renderer 只在不存在这些路径时创建或补充单个 dataset 对象的 `source`。
- 首期不接受 dataset 数组，也不接受需要上游/下游 dataset 链的 `transform`。
- 图片格式由外部渲染服务能力决定；当前合同请求 `png` 或 `svg`。

## Query Result Contract

| 查询模式 | XChart config | ECharts config | 图表前处理 |
|---|---|---|---|
| 普通 QueryModel DSL | 绑定最终字段 | Option encode 最终字段 | 直接使用最终 `items` |
| DSL_CTE | 只绑定 `cte_plan.output` 别名 | encode 最终 output 别名 | 只使用实际执行后的最终 `items` |
| timeWindow | 绑定派生字段 | encode 派生字段 | 合法 null 表达为缺口 |
| Pivot flat | 使用字段绑定 | 使用 dataset/dimensions/encode | 使用 flat 行并过滤汇总元数据行 |
| Pivot tree/grid | 不适用 | 不适用 | 在查询前拒绝 |
| ComposeScript | 不适用 | 不适用 | 本事项不支持 |

### timeWindow

- `pivot` 与 `timeWindow` 的互斥继续由查询 Schema/引擎负责。
- `__prior`、`__diff`、`__ratio`、`__ytd`、`__mtd`、`__rolling_*` 都是最终结果字段，
  可直接用于 XChart 字段绑定或 ECharts encode。
- timeWindow 派生值允许为 `null`；XChart 使用 `NaN` 或版本支持的等价缺口表示，
  ECharts 保持 `null`。
- 两个渲染器都必须保持 X/Y 对齐，不能把缺口改为零，也不能因首期 prior/ratio 为空而使整图失败。

### DSL_CTE

- export 工具继续使用 `dataset.query_model` 的 `payload.route=DSL_CTE` 与
  `executable_plan.cte_plan` 契约。
- 禁止 raw SQL `WITH`、数据库函数旁路和未治理脚本。
- XChart 字段绑定和 ECharts encode 只能引用 `cte_plan.output` 最终别名，不引用中间 stage。
- 只有实际执行并返回 `items` 的结果才进入渲染；计划未执行、查询失败或空结果均不渲染。

### Pivot

- 当 `payload.pivot` 存在且未声明 `outputFormat` 时，export 工具在查询请求副本中显式注入
  `outputFormat: "flat"`；不得依赖 Java DTO 当前的 `tree` 默认值。
- 显式请求 `tree` 或 `grid` 时，在执行查询前返回稳定的“不支持图表导出”错误。
- `hierarchyMode=tree` 因要求 `outputFormat=tree`，首期不支持图表导出。
- flat 结果检查每行嵌套 `_sys_meta` 对象；其中 `isRowSubtotal`、`isColSubtotal` 或
  `isGrandTotal` 任一为 `true` 时默认过滤该行。
- XChart 常用绑定：
  - row axis → `xField`
  - column axis → `seriesField`
  - metric → `yField`
- ECharts 使用原生 dataset/dimensions/encode 表达 Pivot；exporter 不把 Pivot 自动转换成
  Option，也不根据运行后才出现的 column 成员自动生成动态 series。
- ECharts 需要未知动态 column series 的场景，首期优先选择支持 `seriesField` 的 XChart，
  或由调用方提供能够原生处理该数据形态的 Option。
- 多层轴不自动拼接，多 metric 由各引擎 config 显式表达。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 拆成两个固定引擎的 export 工具 | 分离 MCP 描述和 Schema，避免 LLM 混用配置词汇 | 不保留旧工具兼容别名 |
| engine-native-first | LLM 更熟悉 XChart/ECharts 词汇，减少 Foggy 私有 DSL 的解释和示例成本 | 两份 config 不可移植 |
| 不提供通用 config/translator/options | 避免无业务价值的中间抽象和 merge 规则 | 引擎能力直接写入各自 config |
| XChart 使用 Adapter Config | XChart Java API 需要 JSON 到 builder/styler/series 的受控映射 | 不是上游官方 JSON；仅开放 Schema 声明能力 |
| ECharts 使用原生 Option | 保留 ECharts 完整表达力 | 查询数据源由 exporter 注入 |
| XChart 为默认实现 | Java 进程内可生成图片，不要求额外服务 | XChart 路径必须在 sidecar 不可用时独立工作 |
| LLM 默认路由到 XChart | 避免常规请求依赖可选 sidecar | 显式 ECharts 失败不自动切换引擎 |
| `chart.generate` 不拆分 | 保留直接传数据入口，并以 XChart 为默认 | 由 engine 决定 config 语言 |
| 图表请求只有一个数据源 | 防止 config 与查询结果互相覆盖 | export 使用最终 items；direct tool 使用顶层 data |
| export 只接 QueryModel payload | 复用现有治理、权限和结果契约 | ComposeScript 多 plan 另行设计 |
| Pivot export 强制 flat | tree/grid 响应不是普通 row list | 缺省注入 flat；显式 tree/grid 拒绝 |
| Pivot 汇总行默认过滤 | 避免重复统计和异常分类项 | 首期不提供 includeTotals 开关 |
| timeWindow null 表示数据缺口 | prior/ratio 首期为空是合法结果 | 不允许因 null 使整张图失败 |
| DSL_CTE 只绑定最终 output | 保持受控 CTE 与 schema 闭环 | 不访问中间 stage 或 raw SQL |
| 查询凭据不转发给外部渲染服务 | 防止数据面身份泄露给图表 sidecar | ECharts 使用部署配置的服务凭据 |

## Acceptance Criteria

- [x] AC-1: MCP 注册并公开 `dataset.export_with_xchart` 和
  `dataset.export_with_echarts`，二者没有 `engine` 参数，旧
  `dataset.export_with_chart` 不再注册或出现在默认配置中。
- [x] AC-2: XChart export 直接接受包含 builder/styler/series 语义的 Adapter Config，
  生成有效 png/jpg 图片，且不依赖外部 ECharts 服务。
- [x] AC-3: ECharts export 直接接受原生 Option，通过单个 `dataset.source` 注入查询结果，
  并满足现有 native render HTTP 合同。
- [x] AC-4: 两个 export Schema/描述没有公共 `options` 或通用 chart config；XChart 与 ECharts
  的 `config` 定义有意不同，且没有 translator/merge 代码路径。
- [x] AC-5: `chart.generate` 支持顶层 `data + engine-specific config`，缺省使用 XChart；
  XChart 和 ECharts 的直接数据路径均有测试。
- [x] AC-6: 普通 DSL 与 DSL_CTE 只使用最终 `items`；DSL_CTE 可绑定最终 output 别名，
  失败、未执行或空结果不会调用渲染器。
- [x] AC-7: timeWindow 派生字段可用于 XChart binding 或 ECharts encode；合法 `null`
  不导致整图失败，且 X/Y 数据不会错位或补零。
- [x] AC-8: Pivot 缺省格式在 export 请求副本中固定为 flat；显式 tree/grid 和
  hierarchy tree 被稳定拒绝；subtotal/grand-total 元数据行默认被过滤。
- [x] AC-9: 工具描述、JSON Schema、默认工具配置、launcher 配置、角色过滤、审计默认值、
  AI 结果校验、mock 和 controller/tool tests 全部使用新工具名和新合同。
- [x] AC-10: 查询 Authorization 只进入 QueryModel 执行；ECharts sidecar 仅使用部署配置的
  chart-render 凭据；日志不输出 token、完整 data URI 或查询数据。

## Contract / Data / Security Constraints

- API or event contract:
  - 这是有意的破坏性 MCP 工具变更；不保留 `dataset.export_with_chart`。
  - XChart/ECharts `chart.config` 不再声明为相同结构。
  - 请求中不存在公共 `chart.options`。
  - 图表返回继续包含 URL、engine、type、title、format、width、height 和 fileSize。
  - 图表失败时保留已经成功取得的查询结果，并在 exports 中返回可诊断错误。
- data and migration:
  - 无数据库迁移。
  - export 渲染的数据必须等于经过归一化后的查询 `items`，不得绕过 query limit、权限或治理链
    重新取数。
  - chart layer 不做业务聚合、排序、去重或补零；允许的形态操作仅包括 XChart
    `seriesField` 分组、Pivot 汇总行过滤和 null-gap 表达。
  - Pivot 注入 `outputFormat=flat` 必须操作请求副本，不修改调用方传入 Map。
- compatibility and rollback:
  - 旧工具名不兼容，符合 owner 已确认的“不考虑原兼容”决策。
  - XChart/ECharts 当前 engine-native config 可按新工具 Schema 拆分复用，但不承诺旧
    `dataset.export_with_chart` 请求继续工作。
  - 可通过回退本次 MCP/launcher 变更恢复旧工具；不涉及持久化数据回滚。
- permissions and secrets:
  - QueryModel 继续使用调用方 Authorization。
  - 外部 ECharts 渲染服务只使用 `CHART_RENDER_TOKEN` 等部署凭据。
  - 不把用户 Authorization、模型内部信息或查询调试 SQL 传给图表服务。
  - ECharts 会接收图表所需查询行，部署方必须把 sidecar 视为受信任的数据处理组件。

## Current Implementation Review

reviewed_at: 2026-07-26

| Severity | Finding | Disposition |
|---|---|---|
| blocker | 当前只有 `dataset.export_with_chart`，通过 `engine` 选择渲染器 | 拆为两个固定引擎工具并移除旧注册 |
| major | 当前一个 export 描述/Schema 同时解释 XChart 和 ECharts | 拆为两个面向 LLM 的独立描述和 Schema |
| reusable | 当前 XChartRenderer 已按 builder/styler/series Adapter Config 工作 | 直接复用并收紧 export 数据绑定与 null-gap，不新增 translator |
| reusable | 当前 EChartsRenderer 已转发原生 Option 并注入 `dataset.source` | 直接复用 WebClient/WireMock 合同，不新增配置转换 |
| major | 当前 XChart config 可用 `xData/yData/value` 绕过 export 查询结果 | export Schema/运行时固定使用字段绑定 |
| major | 当前 `chart.generate` 的 data 可为空，数据来源规则不够明确 | 固定 direct tool 顶层 data 为唯一数据源 |
| major | 当前 Pivot 查询结果直接把 `items` 送入渲染器；DTO 默认 tree，而 MCP 文档写 flat | export 层复制 payload 并固定 flat，显式非 flat 预先拒绝 |
| major | 当前 XChart `requiredField` 拒绝 `null` y 值，timeWindow 首期 prior/ratio 可能失败 | 增加 null-gap 数据绑定与回归测试 |
| major | 当前扫描到生产配置、文档和测试仍有多处引用旧工具名 | 全量更新并增加残留名称扫描 |
| minor | 当前 ECharts adapter 只支持单 dataset 对象 | 保持该边界；dataset 数组继续作为非目标 |
| reusable | `ChartRendererRegistry`、图片编码、存储适配和 WebClient 启动不探测 sidecar | 保留现有能力，调整工具与数据边界 |

Review conclusion:

- 修订后的方案边界完整，`open_questions` 为零，可以进入实施。
- 引擎原生优先使当前 renderer 和既有测试更可复用，实施不需要新增通用 translator 或 options merger。
- 不需要修改语义查询引擎或外部 chart-render-service 才能完成首期交付。

Review evidence:

```text
mvn -pl foggy-dataset-mcp -Dtest=ChartToolTest,ExportWithChartToolTest,XChartRendererTest,EChartsRendererTest,ToolConfigLoaderTest test
```

- 上述命令退出码 0，35 tests，0 failures，0 errors，0 skipped。
- 当前聚焦测试证明 engine-native renderer 基线可运行，但不证明双工具拆分、数据源保护、
  Pivot/timeWindow/DSL_CTE 新合同。
- 全工作区 `git diff --check` 退出码 0；仅报告既有 CRLF→LF 提示，没有 whitespace error。

## Next-step Implementation Plan

实施按以下顺序推进；每个批次先通过自己的 gate，再进入下一批次。

1. Batch 0 — 开工基线与状态
   - 实施会话将本文件状态改为 `ULTRA_EXECUTING`，记录当前 dirty worktree，不重置或覆盖已有
     图表实现。
   - 复跑现有 35 项聚焦测试，确认 XChart/ECharts renderer 基线仍成立。
   - gate: 基线失败必须先判断是已有改动还是本事项问题；不能在未知基线上继续迁移。
2. Batch 1 — 公共合同先行
   - 新增 `dataset.export_with_xchart`、`dataset.export_with_echarts` 的独立描述、Schema 和
     工具发现合同测试。
   - XChart Schema 只暴露受支持的 Adapter Config；ECharts Schema 直接描述开放 Option，
     两个 export 均不包含 `engine` 或公共 `options`。
   - `chart.generate` 保留单工具，但把顶层 `data` 固定为 required、non-empty。
   - gate: Schema 可解析；两个新工具在 `tools/list` 中独立可见；合同测试能阻止旧合同继续通过。
3. Batch 2 — 双工具 façade 与共享编排
   - 建立两个固定 renderer 的公共 MCP 工具；共享内部编排复用 QueryModel 调用、结果提取、
     ChartTool/renderer 调用、存储和响应组装。
   - 旧 `dataset.export_with_chart` 不再作为 MCP bean 或默认工具注册；共享编排本身不得暴露
     成第三个公共工具。
   - 同步与流式路径都只执行一次查询；查询失败或空结果不调用 renderer。
   - gate: 普通 DSL 下 XChart/ECharts 各自命中固定 renderer；请求中的任意 config 字段都不能
     改变工具所选引擎。
4. Batch 3 — 单一数据源与 renderer 收紧
   - XChart 禁止 `xData`、`yData` 和 Pie `series.value` 内嵌第二份数据，未知字段/枚举
     fail closed；`chart.generate` 与 export 均通过顶层 data/最终 items 绑定字段。
   - XChart 数值 Y 的合法 `null` 转为 gap 表达并保持 X/Y 对齐。
   - ECharts 拒绝已有 `dataset.source`、`series[*].data`、`xAxis.data`、dataset 数组和
     dataset-chain `transform`，随后确定性注入单一 `dataset.source`。
   - ECharts 错误日志不输出响应体中的 token、查询行或完整 data URI。
   - gate: XChart 真实 png/jpg 字节测试、ECharts WireMock 请求捕获和全部负向数据源测试通过。
5. Batch 4 — QueryModel 结果归一化
   - 复制 query payload；Pivot 未指定格式时在副本内注入 `flat`，显式 tree/grid 或
     `hierarchyMode=tree` 在查询前拒绝。
   - 过滤 flat 行中 `_sys_meta` 标记的 subtotal/grand-total；不修改普通 DSL、DSL_CTE 或
     timeWindow 的业务结果。
   - 普通 DSL、DSL_CTE 只消费最终 `items`；timeWindow `null` 保持 gap，不补零。
   - gate: payload 原对象不变；Pivot、DSL_CTE、timeWindow 以及 query failure/empty
     回归测试通过。
6. Batch 5 — 工具面迁移与残留清理
   - 更新默认工具配置、launcher、角色过滤、审计默认值、AI 结果校验、mock/controller tests、
     MCP 多角色文档和模块测试说明。
   - 同步 demo 审计字典、benchmark 测试配置和 Mongo 审计测试夹具中的两个新工具名。
   - 删除旧 export 描述/Schema；版本化历史 evidence 保持不可改写。
   - gate: 生产代码、活动配置、当前文档和测试中不再引用旧工具名；允许只在 canonical
     work item 的迁移说明和不可改写历史 evidence 中出现。
7. Batch 6 — 受影响面验证与交接
   - 先运行 renderer/tool/schema/config focused tests，再运行
     `foggy-dataset-mcp` affected-module lane 和 launcher 配置验证。
   - 对 addon/demo 只运行与改名直接相关的 test-compile、focused test 或静态合同检查，
     不启动外部数据库矩阵。
   - 将实际命令、退出码、测试计数、未运行原因和 residual risks 写回
     `Implementation Result`，状态改为 `READY_FOR_SIGNOFF`。

Recommended implementation checkpoints:

| Checkpoint | Required outcome |
|---|---|
| C1 Public contract green | 双工具描述/Schema/发现测试通过，旧工具不再作为目标合同 |
| C2 Rendering contract green | 固定 renderer、单一数据源、XChart bytes、ECharts HTTP 合同通过 |
| C3 Query modes green | ordinary DSL、DSL_CTE、timeWindow、Pivot 归一化测试通过 |
| C4 Surface migration green | launcher、role/audit/validator/mock/addon/demo 引用全部完成迁移 |
| C5 Ready for signoff | focused + affected 验证充分，work item 回写完整 |

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/AC-4/AC-9 | must-pass | major | tool config/schema/controller/validator tests + addon/demo config scan | 现有工具加载测试结构 | 双工具可见、Schema 分离、旧工具/公共 options 无活动引用 |
| AC-2 | must-pass | major | XChart real byte tests + 无 ECharts 服务启动测试 | 现有 XChart renderer tests | direct adapter config 生成图片且无 sidecar |
| AC-3 | must-pass | major | ECharts WireMock HTTP/data injection tests | 现有 ECharts renderer tests | Option 原样保留、query rows 成为唯一 source |
| AC-5 | must-pass | major | ChartTool direct-data tests | 现有 storage/image tests | required data、default XChart、explicit ECharts |
| AC-6 | must-pass | major | Export tool ordinary/DSL_CTE tests | 现有 QueryModel mock tests | final fields、failure/empty no render |
| AC-7 | must-pass | major | renderer/export tests with null derived values | none | no error、aligned data、no zero fill |
| AC-8 | must-pass | major | payload normalization and Pivot result tests | Pivot engine contract | flat injection、rejection、meta filtering |
| AC-10 | must-pass | major | WebClient/auth/logging captor tests | configured service token behavior | no caller Authorization forwarded |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation:
  - `git diff --check`
  - focused JUnit classes for renderers、chart tools、query normalization and tool config loading
  - JSON Schema parse/consistency tests
  - 生产代码和配置的旧工具名、公共 `options`、common translator 残留扫描
  - expected duration: each command under 5 minutes
- medium_validation:
  - `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`
  - launcher/config focused test or compile lane when launcher resources change
  - addon Java test fixture `test-compile` only when its source changes
  - expected duration: 5-30 minutes
- expensive_validation: none required
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none; change is localized to MCP chart tooling and launcher configuration
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: only if focused and affected-module evidence cannot determine a must-pass result
- maximum_expensive_attempts: 0 without replan/user approval
- reusable_evidence:
  - existing engine-native XChart byte encoding、storage fallback and ECharts WireMock tests may be adapted
  - existing single-export tests do not prove the split public contract
- implementation_evidence_record:
  - `Implementation Result.tests_and_results` 必须记录实际命令、退出码和测试计数。
  - 失败或跳过项必须记录原因；仅新增测试文件但未运行不构成证据。
  - 对人工检查记录所查路径、关键断言和结果，不能只写“已 review”。
- minimum_revalidation_radius:
  - XChart Adapter Schema/renderer 变化使 XChart renderer、XChart export 和 direct tool 证据失效。
  - ECharts Option/data injection 变化使 ECharts renderer、ECharts export 和 direct tool 证据失效。
  - 查询归一化变化使 ordinary DSL、DSL_CTE、timeWindow、Pivot focused evidence 失效。
  - 仅描述或 Schema 文案变化只重跑 schema/config/description consistency lane。
  - launcher 默认配置变化只重验工具装配与启动配置，不要求重跑语义查询引擎测试。
  - addon/demo 仅工具名或测试夹具变化，只重验相应配置扫描或 test-compile，不使 renderer
    和 QueryModel 证据失效。
- stop_when_evidence_is_sufficient:
  - AC-1 through AC-10 map to passing focused/affected tests
  - no old export tool、公共 options 或 common translator remains in production configuration or code paths
  - diff check passes and no unreviewed query-engine change exists
- validation_not_required:
  - root reactor full test
  - external database matrix
  - real browser/Node ECharts E2E
  - release authority、replay、source seal、artifact promotion or remote CI

## Waiver Policy

- waivable_items:
  - 非核心 XChart builder/styler/series 映射。
  - ECharts 真实外部服务体验测试，前提是 WireMock 合同通过。
- authorized_role: repository owner
- non_waivable_guards:
  - 双工具命名与旧工具移除。
  - engine-specific config，不引入 Foggy 通用图表 DSL。
  - QueryModel 权限不旁路，查询数据不能被 config 内第二份数据替换。
  - Pivot 不误绘 tree/grid 包装数据或汇总重复行。
  - timeWindow 合法 null 不导致整图失败或被补零。
- required_risk_record: 被豁免能力、影响范围、检测方式、回滚方式和后续 owner。

## Risks and Open Questions

- known_risks:
  - XChart Adapter Config 不是上游标准 JSON；工具 Schema 和描述必须与实际支持的 Java 映射同步，
    并对 LLM 生成的未知 setter/枚举 fail closed。
  - 两个引擎的配置不可移植；这是 engine-native-first 的明确取舍。
  - Java `PivotRequest` 默认 `tree` 与 MCP 文档/Schema 所述默认 `flat` 不一致；本事项通过 export
    请求显式注入 flat 隔离风险，但不会修正全局合同。
  - ECharts 对动态 Pivot column series 不做自动生成；需要动态分组时首期优先使用 XChart
    `seriesField`，或由调用方提供原生可执行的 Option。
  - XChart 与 ECharts 对 null 和部分视觉能力表现不同；视觉像素级一致性不是验收目标。
  - ECharts 会把查询结果发送到配置的外部渲染服务，部署方必须把它视为受信任的数据处理组件。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目与 `foggy-dataset-mcp` 的持久化规范，以及 `foggy-semantic-query` Skill。
- 在 scope 内自主决定具体类、包和复用结构；不得重新引入 Foggy 通用 chart config、
  cross-engine translator 或公共 options merge。
- 优先复用当前 engine-native renderers 和测试，不机械推倒已有 XChart/ECharts 实现。
- 保留工作区中用户已有修改；只有能确认属于本事项的图表改动才可调整。
- 如需改变工具名、engine-native-first、数据源规则、Pivot/timeWindow/DSL_CTE 或安全决策，
  设置 `NEEDS_REPLAN` 并停止扩展。
- 先用测试冻结新公共合同，再完成实现并运行与改动面匹配的验证。
- 不得声称未实际运行的测试通过。
- 未经用户明确批准，不得主动运行预计超过 30 分钟或包含
  authority/replay/rehearsal/source-seal 的大型链路。
- 完成后填写 `Implementation Result`，并将状态改为 `READY_FOR_SIGNOFF`；不得自行设置
  `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - 将原 `dataset.export_with_chart` 拆为固定引擎的
    `dataset.export_with_xchart` 与 `dataset.export_with_echarts`，共享非 MCP
    `ExportWithChartExecutor`，不保留旧工具别名。
  - `chart.generate` 改为 renderer registry 架构，默认 XChart；XChart 在 JVM 内生成
    png/jpg，ECharts 显式调用既有 native render 服务。
  - XChart 接受封闭的 builder/styler/series Adapter JSON，并对未知字段、非法枚举和内嵌
    数据 fail closed；ECharts 接受开放 Option，但由 renderer 确定性注入唯一
    `dataset.source` 并拒绝第二份数据。
  - export 查询继续走 `dataset.query_model` 权限与治理链；普通 DSL、DSL_CTE、timeWindow
    使用最终 `items`，Pivot 在请求副本中固定 flat、拒绝 tree/grid 并过滤汇总元数据行。
  - 已同步 Schema、工具描述、默认/launcher 配置、角色过滤、审计默认值、AI 校验、mock、
    benchmark 与 Mongo 测试夹具。
- changed_paths:
  - `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/chart/**`
  - `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ChartTool.java`
  - `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ExportWith*`
  - `foggy-dataset-mcp/src/main/resources/schemas/**`
  - `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/{chart,tools}/**`
  - `foggy-dataset-mcp` 配置、controller、审计、AI 校验、mock 与说明文档
  - `foggy-mcp-launcher/src/main/resources/application.yml`
  - `foggy-dataset-demo`、`addons/foggy-benchmark-spider2`、
    `addons/foggy-dataset-model-mongo` 的工具名迁移
- tests_and_results:
  - `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`：
    15 个 Reactor 模块全部 SUCCESS；`foggy-dataset-mcp` 520 tests，
    0 failures / 0 errors / 0 skipped。
  - 聚焦合同测试：
    `ChartToolTest,ExportWithChartToolsTest,XChartRendererTest,EChartsRendererTest,`
    `ToolConfigLoaderTest,ToolFilterServiceTest,AnalystMcpControllerTest,ResultValidatorTest`：
    96 tests，0 failures / 0 errors / 0 skipped。
  - `mvn -B -ntp -pl foggy-mcp-launcher -am -DskipTests compile`：
    27 个 Reactor 模块全部 SUCCESS。
  - `mvn -B -ntp -pl addons/foggy-dataset-model-mongo -DskipTests test-compile`：
    Mongo addon 主代码与 9 个测试源编译成功。
  - 三份图表 JSON Schema 通过 `jq empty`；`git diff --check` 通过。
- manual_or_experience_evidence:
  - 生产源码、launcher、demo 与 Mongo addon 中已无旧公开标识
    `dataset.export_with_chart` / `dataset_export_with_chart`。
  - 两份 export Schema/描述未声明公共 `options`，且 XChart 与 ECharts config 结构独立。
  - ECharts WireMock 请求捕获确认查询行成为唯一 `dataset.source`，使用部署配置的
    Authorization，并且仅透传 trace id，不接收查询调用方凭据。
  - 代码复核未发现跨引擎转换、自动 fallback、raw SQL/ComposeScript 旁路或查询数据日志；
    XChart Category/XY/Pie 数据类型错误只保留字段级诊断，不再拼接实际查询值。
- deviations: none
- residual_risks:
  - ECharts 路径仍依赖部署方提供并信任的外部渲染服务；默认 XChart 路径不受此影响。
  - XChart Adapter 仅覆盖 Schema 明确开放的能力，不等同于完整 XChart Java API。
  - ECharts 动态 Pivot column series、多层 Pivot 轴自动拼接与跨引擎像素一致性仍不支持，
    符合本事项 Non-Goals。
- reused_evidence:
  - 复用现有 ChartStorageAdapter URL/Base64 降级合同及存储失败测试。
  - 复用既有 ECharts native render HTTP 路径与 WireMock 合同测试方式。
- omitted_validation_and_reason:
  - 未调用真实 ECharts 外部服务；按验证预算使用 WireMock 覆盖请求体、header、格式和错误边界。
  - 未运行数据库依赖的 Mongo 集成测试、远程 CI、发布验收或 authority/replay 链路；
    本事项仅修改 Mongo 审计测试夹具，已完成 test-compile。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex-signoff-reviewer
- signed_off_at: 2026-07-26
- acceptance_record:
  `docs/design/acceptance/mcp-chart-export-renderer-contract-signoff-20260726-r2.md`
- prior_acceptance_record:
  `docs/design/acceptance/mcp-chart-export-renderer-contract-signoff-20260726.md`
- blocking_items: none
- follow_up_required: no

## Remediation Submission

- remediation_status: READY_FOR_SIGNOFF
- submitted_at: 2026-07-26
- remediated_item:
  - AC-10: XChart validation errors can place query row values in application logs
- implementation:
  - `XChartRenderer` 的 Category/XY Y、Pie value 与 XY X 类型错误不再包含实际输入值，
    仍保留字段名、期望类型和修复建议等可诊断信息。
  - `ExportWithChartToolsTest` 新增 Category/XY/Pie 三条完整 export 路径日志捕获回归测试，
    经过真实 `ChartTool` 与 `XChartRenderer`，验证 `chartError` 和应用日志均不含敏感查询值，
    且日志不含调用方 Authorization。
- verification:
  - 完整图表合同聚焦集合
    `ChartToolTest,ExportWithChartToolsTest,XChartRendererTest,EChartsRendererTest,`
    `ToolConfigLoaderTest,ToolFilterServiceTest,AnalystMcpControllerTest,ResultValidatorTest`：
    96 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
  - `mvn -B -ntp -pl foggy-dataset-mcp -Dtest=ExportWithChartToolsTest,XChartRendererTest,ChartToolTest test`：
    29 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
  - `mvn -B -ntp -pl foggy-dataset-mcp -am test -DskipITs`：
    15 个 Reactor 模块全部 SUCCESS；`foggy-dataset-mcp` 520 tests，
    0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
  - 生产 chart/ChartTool 源码扫描未发现 `实际值` 错误拼接；Surefire 报告未发现
    `SECRET-CATEGORY-AC10`、`SECRET-XY-AC10` 或 `SECRET-PIE-AC10`。
  - `git diff --check` 通过。
- remediation_record:
  - `docs/design/acceptance/mcp-chart-export-renderer-contract-remediation-20260726.md`
- requested_action: 重新执行独立签收；不得复用原 rejected verdict 作为接受结论。

## References

- requirement / issue:
  - 2026-07-26 owner conversation confirming split export tools、XChart default、
    Pivot/timeWindow/DSL_CTE behavior and no legacy compatibility requirement.
  - 2026-07-26 owner clarification confirming engine-native-first because LLM understands
    XChart/ECharts vocabulary better than a Foggy-specific chart DSL.
- architecture / glossary:
  - `docs/architecture/README.md`
  - `foggy-dataset-mcp/CLAUDE.md`
  - `foggy-dataset-mcp/src/main/resources/schemas/descriptions/query_model_v3.md`
- related work items:
  - `docs/8.2.0.beta/P0-ComposeQuery-CTE使用参考手册.md`
  - `docs/9.0.0.beta/detailed_design/01_pivot_dsl_and_result_contract.md`
