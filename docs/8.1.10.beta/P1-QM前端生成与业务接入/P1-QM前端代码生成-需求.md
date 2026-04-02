# P1-QM前端代码生成-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`规划中`
- 责任边界：
  - 后端维护 TM/QM 与元数据输出
  - 前端消费 `foggy-data-viewer` 组件并承接 UI 定制

## 系列归档
本需求已归入 `QM 前端生成与业务接入` 系列，相关总览和运行时/接入文档见：

- [P1-QM前端生成与业务接入-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端生成与业务接入-需求.md)
- [P1-QM业务系统使用规范-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM业务系统使用规范-需求.md)
- [P1-QM前端下拉组件生成-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端下拉组件生成-需求.md)
- [P1-QM查询条件区与列筛选并存-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM查询条件区与列筛选并存-需求.md)
- [P1-DataViewer维度成员实时过滤-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-需求.md)

## 背景
公司重构项目计划在前端侧复用 `addons/foggy-data-viewer` 中的 Vue 3 组件能力。

TM/QM 继续由后端相关技术维护，希望在版本 `8.1.10.beta` 中规划一套能力，使 QM 可以输出为前端 TypeScript 与 Vue 3 可直接使用的代码产物，类似当前业界常见的 “Controller/接口定义 -> TypeScript API” 生成方式。

## 当前共识
- 本阶段暂不处理 `groupBy` 交互能力，后续版本再讨论。
- `uiConfig` 不由后端强控，前端自行扩展；后端仅提供全局参数入口与定制参数入口。
- 当前能力默认依赖后端服务启动后提供元数据。
- 本需求只规范“如何生成前端产物”，不替代“业务系统如何接入这些产物”的规范；后者在独立文档中讨论。
- 主推荐方案：
  - 后端提供稳定、标准、版本化的 JSON 元数据。
  - 前端通过 Node/CLI/构建插件拉取 JSON 并生成代码。
- 备选方案：
  - 后端启动后直接提供“生成代码”接口，供前端调用获取代码。
  - 该方案可作为 PoC 或内部调试能力，不作为默认主方案。

## 目标
- 建立面向前端消费的 QM 标准元数据输出格式。
- 支持从 QM 生成前端基础代码资产，优先包括表格组件，并为 lookup / 下拉组件生成保留扩展位。
- 表格相关生成产物至少包括：
  - `types.ts`
  - `schema.ts`
  - `query.schema.ts`
  - `api.ts`
  - 薄封装的 Vue 3 表格组件
  - `index.ts` 导出入口
- 让生成产物可以直接复用 `foggy-data-viewer` 现有组件能力，而不是重复实现表格、筛选、分页逻辑。

## 非目标
- 本阶段不生成复杂业务页面。
- 本阶段不承诺通用 `groupBy`、透视、分析型页面生成。
- 本阶段不由后端定义具体页面级 `uiConfig` 细节。
- 本文档不展开定义 lookup / 下拉组件的细节契约，由独立子文档跟踪。

## 方案选择
本需求在 `8.1.10.beta` 中按方案 A 推进：

- 后端输出标准、稳定、版本化的 JSON 元数据。
- 前端使用 Node/CLI/构建插件消费 JSON，并生成 TypeScript 与 Vue 3 代码。
- 方案 B 不作为本版本默认实现，仅保留为后续 PoC 或调试型扩展思路。

## 方案 A 设计原则
- 后端只负责“模型语义”和“标准元数据”，不直接负责 Vue 页面模板。
- 前端负责“代码模板”和“UI 定制策略”，避免后端与具体前端工程结构耦合。
- 生成产物必须优先复用 `foggy-data-viewer` 现有能力，而不是再造一套表格查询层。
- 生成目录与手工目录严格隔离，避免生成器覆盖人工代码。
- 元数据格式必须显式带版本号，保证前后端可演进、可兼容。

## 现有 V3 JSON 样本观察
已基于当前项目测试环境实际生成并检查以下模型的 JSON 元数据样本：

- `FactOrderQueryModel`
- `DimProductQueryModel`
- `OdooSaleOrderQueryModel`

### 当前 V3 JSON 已具备的可复用信息
- 顶层包含 `version`、`fields`、`models`
- 字段级已输出以下关键信息：
  - `fieldName`
  - `name`
  - `type`
  - `filterType`
  - `filterable`
  - `measure`
  - `aggregatable`
  - `sourceColumn`
  - `dictId`
  - `aggregation`
  - `calculated`
  - `predefined`
  - `hierarchical`
  - `hierarchyOps`
- 模型级已输出以下基础信息：
  - `name`
  - `factTable`
  - `purpose`
  - `scenarios`

### 当前 V3 JSON 不适合直接作为前端生成契约的点
- 顶层存在 `prompt`，明显偏向 LLM/说明性用途，不适合前端生成器直接依赖
- 当前 `version = v3` 表示语义元数据版本，不等同于前端元数据契约版本
- `fields` 当前为对象映射而非字段数组，不适合作为前端稳定列顺序来源
- 字段显示名称使用 `name`，缺少前端更直观的 `title`/`caption` 约定
- `meta` 是拼接后的说明字符串，适合阅读，不适合作为稳定机器契约
- 字段内嵌 `models.{model}.description/usage` 偏说明文档，不应成为前端生成主依赖
- 单模型场景下，模型信息仍放在顶层 `models` 映射中，前端读取不够直接
- 缺少前端生成所需的默认配置，如：
  - 默认显示列
  - 默认搜索字段
  - 默认分页大小
  - 默认排序
  - 默认过滤策略
- 缺少字段级前端约束和 UI 提示，如：
  - `sortable`
  - `required`
  - `nullable`
  - `visible`
  - `uiHints`
- 缺少模型级能力声明，如：
  - 是否支持分页
  - 是否支持排序
  - 是否支持过滤
  - 是否支持字典懒加载
- 缺少全局参数入口和模型级定制参数入口定义

### 基于样本的结论
- 现有 V3 JSON 可以作为“前端元数据构建原料”
- 现有 V3 JSON 不建议直接作为“前端代码生成契约”
- `8.1.10.beta` 应新增独立的前端元数据契约，例如 `frontend-meta v1`
- 后端实现上可以复用当前 V3 生成逻辑，但对外应输出收敛后的前端专用结构

## 标准 JSON 元数据要求
后端需提供面向前端消费的标准 JSON 格式，建议独立于通用语义描述响应，形成更稳定的“前端元数据”契约。

### 顶层结构建议
- `metaVersion`：元数据契约版本，例如 `v1`
- `model`：QM 模型名称
- `caption`：模型显示名称
- `description`：模型说明
- `fields`：字段数组
- `defaults`：默认列、默认分页、默认搜索字段等
- `params`：
  - `global`：全局参数入口
  - `custom`：前端定制参数入口
- `capabilities`：能力声明，如分页、排序、过滤

### 字段结构建议
每个字段至少包含：
- `name`
- `title`
- `type`
- `filterType`
- `filterable`
- `sortable`
- `measure`
- `aggregatable`
- `dictId`
- `sourceColumn`
- `required`
- `nullable`
- `uiHints`

### 与现有 V3 字段的映射建议
- `fieldName` -> `name`
- `name` -> `title`
- `models.{model}.description` -> `description`
- `version` 不直接透传，改为新的 `metaVersion`
- `meta` 不直接透传，必要时拆分为结构化字段或放入 `description`
- `models` 顶层映射改为单模型直接字段：
  - `model`
  - `caption`
  - `description`
- `fields` 从对象映射调整为数组，同时保留稳定输出顺序
- `dictId`、`hierarchical`、`hierarchyOps`、`aggregation`、`calculated`、`predefined` 继续保留

### 当前范围
- 本阶段不要求后端输出页面级 `uiConfig`
- 本阶段不要求后端输出复杂布局元数据
- 本阶段不要求输出 `groupBy` 交互定义

## 前端生成器职责
前端生成器负责把标准 JSON 转换为可直接落地的代码文件。

### 建议生成产物
- `generated/models/{ModelName}.types.ts`
- `generated/models/{ModelName}.schema.ts`
- `generated/models/{ModelName}.api.ts`
- `generated/models/{ModelName}Table.vue`
- `generated/models/index.ts`

### 文件职责
- `types.ts`：QM 行类型、查询参数类型、可复用字典类型
- `schema.ts`：面向 `foggy-data-viewer` 的 `TableSchema`、列配置、默认搜索字段
- `query.schema.ts`：独立的 `QuerySchema`、`QueryFieldSchema`、查询区布局默认值
- `api.ts`：调用项目公共查询 API 的薄封装
- `Table.vue`：基于 `DataTableWithSearch` 的薄组件，不承载复杂业务逻辑
- `index.ts`：统一导出生成结果

### `query.schema.ts` 的生成口径
`query.schema.ts` 应按独立查询模型生成，不再简单等同于“从列里挑可筛选字段”。

建议生成来源：

1. QM 前端元数据中的字段语义
2. 字段的 `filterType`、`dictId`、lookup / member 信息
3. 生成器默认映射规则
4. 模型级生成配置覆盖

最小要求：

- 生成 `QueryFieldSchema[]`
- 生成传统查询区的默认 `layout`
- 生成 `placement` 默认值
- 生成 `sourceField` 到值字段的归一结果

约束：

- `query.schema.ts` 与 `table.schema.ts` 可以关联，但必须允许独立演进
- 不能再把“查询条件布局”硬塞回列 schema

## 生成流程
### 开发态
1. 后端启动并暴露前端元数据接口
2. 前端执行生成命令，拉取单模型或多模型 JSON
3. 生成器输出到 `src/generated/` 或约定目录
4. 前端业务代码引用生成产物并在本地补充定制逻辑

### CI / 构建态
1. 准备可访问的后端服务或元数据快照
2. 执行生成器
3. 校验生成结果是否有未提交变更或契约破坏
4. 进入前端编译和测试阶段

## 定制与覆盖规则
- 生成器只能写入 `generated/` 目录
- 业务手工代码放在 `custom/`、`modules/` 或业务目录中
- 生成组件应预留：
  - 全局参数入口
  - 模型级定制参数入口
  - 插槽
  - hook/adapter 扩展位
- 人工定制不得直接修改生成文件；如需变更，优先调整模板、参数或扩展文件

## 后端职责边界
- 提供标准 JSON 接口或下载能力
- 维护 JSON 契约版本
- 保证 QM 变更后元数据输出稳定、可追踪
- 不承担前端工程目录适配、业务模板分层和页面视觉方案

## 前端职责边界
- 维护生成器模板
- 维护生成目录结构
- 决定如何将全局参数和定制参数合并到组件
- 承担页面级 `uiConfig`、业务动作按钮、页面布局、主题样式

## 验收标准
- 能基于一个 QM 标准元数据 JSON 成功生成 `types.ts`、`schema.ts`、`api.ts`、薄 Vue 组件和导出入口
- 生成后的 Vue 组件可直接复用 `foggy-data-viewer`
- QM 字段新增、删除、重命名后，生成结果能稳定反映差异
- 生成流程可在前端本地和 CI 中重复执行
- 手工业务代码不会被生成器覆盖

## 风险与注意事项
- 如果直接复用现有通用语义响应而不做前端契约收敛，后续字段演进容易破坏前端生成器
- 如果生成组件承载过多业务逻辑，模板会快速失控
- 如果没有生成目录与定制目录隔离，后期维护成本会明显上升
- 如果后端必须在线可用，前端 CI 需补充快照或联调环境策略

## 待确认项
- 标准 JSON 是否新增独立接口，还是在现有接口基础上裁剪为前端专用响应
- 生成器以 CLI、npm 包、Vite 插件还是 monorepo 工具方式落地
- 生成目录最终使用 `src/generated/` 还是业务模块内独立生成目录
- 单模型生成、批量生成、增量生成三种模式的支持范围
- 全局参数入口与模型级定制参数入口的最终字段结构
- 是否需要输出字典值快照，还是仅输出 `dictId`

## 后续拆分建议
- `P1-QM前端元数据JSON契约-需求.md`
- `P1-前端代码生成器CLI-需求.md`
- `P2-模型级定制参数合并机制-需求.md`
- `P2-生成产物覆盖保护与校验-需求.md`

## 跟踪说明
- 后续围绕该能力的讨论、设计、拆分与实现，统一追加到 `docs/8.1.10.beta/` 目录下。
- 如果该能力拆成多个子需求，继续按 `docs/{版本号}/{需求等级}-${功能名称}-需求.md` 规则拆分建档。
- 本需求涉及的 QM 元数据样本统一归档在 `docs/8.1.10.beta/P1-QM前端生成与业务接入/qm-metadata-samples/` 目录下，作为前端元数据契约设计的参考输入。
