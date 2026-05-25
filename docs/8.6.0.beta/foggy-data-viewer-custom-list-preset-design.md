# foggy-data-viewer 自定义列表能力设计

## 文档信息

- version: 8.6.0.beta
- status: draft
- scope: `addons/foggy-data-viewer`
- target: 在 DataTable / DataTableWithSearch 的工具栏区域提供类似旧版“自定义列表”的能力，支持用户保存、加载、设为默认、共享常用列表配置

## 背景

业务方现在使用 `addons/foggy-data-viewer` 下的表格组件。截图红框处希望增加一个用户可点击的“自定义列表”入口，目标能力类似旧版：

- 用户自己选择显示列和列顺序。
- 用户保存默认过滤条件。
- 用户可以把某个配置设为默认。
- 可以查看“我的查询”和“共享查询”。

当前新版已经具备比旧版更强的 DSL 能力，因此新能力不能只复刻旧版的列选择和简单筛选。设计上应把高频、稳定、容易可视化的 DSL 能力纳入第一版，把表达复杂、容易误配、需要高级编排 UI 的能力放到后续版本。

## 当前代码基线

### 已有基础

- `DataTable.vue`
  - 已有 `toolbar` 插槽，位置正好对应截图红框左侧区域。
  - 已支持表头筛选、排序、分页、汇总、列格式化、自定义列渲染。
  - 已支持 `initialSlice` 回填到表头筛选器。

- `DataTableWithSearch.vue`
  - Schema 模式下统一管理 `page/pageSize/slice/orderBy/loading/data`。
  - 已暴露 `getQueryState()`，返回 `columns/slice/orderBy`。
  - 已暴露 `applyQueryState()`，但当前只应用筛选和排序，列配置需要父层重建 schema。
  - 已有 `enableSavedQuery?: boolean` prop，但模板未使用，属于未落地声明。

- saved-query 模块
  - 前端已有 `SavedQueryManager`、`SaveQueryDialog`、`QueryListDialog`。
  - 后端已有 `SavedQueryDef`，可保存 `columns/slice/orderBy/groupBy/calculatedFields/visibility/owner`。
  - 共享范围支持 `PRIVATE/DEPARTMENT/TENANT`。

- 查询 DSL 类型
  - `SliceRequestDef` 已支持 `field/op/value/link/children`。
  - `OrderRequestDef` 已支持字段排序。
  - QueryPayload 已包含 `columns/slice/groupBy/orderBy/calculatedFields`。

### 主要缺口

- `enableSavedQuery` 没有自动渲染 UI。
- saved-query 的定位偏“保存查询”，还不是用户理解的“自定义列表”。
- 应用保存配置时不能在组件内部直接切换可见列和列顺序。
- 现有保存弹窗会重新配置筛选条件，但没有直接承接当前表格已输入的筛选 UI 状态。
- `businessId` 前端已有参数，但当前后端 `SavedQueryDef/Service/Controller` 未真正保存和过滤。
- 后端 `applySavedQuery` 创建 cached query 时未带 authorization，作为 viewer queryId 使用时需要复核权限上下文。

## 产品目标

第一版目标是把“自定义列表”做成标准组件能力，而不是让每个业务页面手写一套：

1. DataTableWithSearch 开启后自动在工具栏显示“自定义列表”按钮。
2. 弹窗内提供“我的列表 / 共享列表”。
3. 用户可以新建、编辑、删除自己的列表。
4. 用户可以选择字段、调整顺序、设置列宽、固定列、设置默认排序。
5. 用户可以保存当前筛选条件为默认条件。
6. 用户可以将某个列表设为个人默认。
7. 页面初始化时自动应用个人默认列表；没有个人默认时使用页面 schema 默认。
8. 对外保留插槽能力，业务方仍可在 toolbar 插入其他按钮。

## 非目标

8.6.0.beta 第一版不做以下能力：

- 不做完整 BI 查询设计器。
- 不做任意嵌套 `children` 条件的可视化编辑器。
- 不做跨模型 compose_script 编排。
- 不做透视表 / pivot 配置。
- 不做用户自定义表达式编辑器的完整校验与联想。
- 不让前端绕过 QM 字段权限选择未授权字段。

## 命名建议

旧版入口叫“自定义列表”。新版内部建议使用更准确的 `ListPreset`，UI 文案仍叫“自定义列表”。

原因：

- “SavedQuery” 更像一次查询。
- 业务用户理解的是一个列表视图方案，包含列、筛选、排序、默认值和共享范围。
- `ListPreset` 可以自然承载后续的展示偏好，例如 density、汇总显示、导出字段等。

## 总体架构

```text
DataTableWithSearch
  ├─ toolbar-left
  │   ├─ 用户 toolbar slot
  │   └─ ListPresetButton / 自定义列表
  ├─ QueryPanel / SearchToolbar
  └─ DataTable

ListPresetButton
  ├─ ListPresetDialog
  │   ├─ MyPresets
  │   ├─ SharedPresets
  │   └─ PresetEditor
  └─ useListPreset

useListPreset
  ├─ list/create/update/delete/setDefault/apply
  ├─ 当前表格状态读取
  ├─ schema 派生与列重排
  └─ 默认 preset 自动应用
```

## 前端 API 设计

### DataTableWithSearch props

```ts
interface ListPresetConfig {
  enabled?: boolean
  model: string
  businessKey?: string
  /**
   * v1 必填：前端显式传入的用户标识。
   * 后端用它隔离配置存储命名空间；它不是安全边界。
   */
  userId: string
  autoLoadDefault?: boolean
  allowShared?: boolean
  allowTenantShared?: boolean
  buttonText?: string
  placement?: 'toolbar-left' | 'toolbar-right' | 'external'
}

interface Props {
  listPreset?: boolean | ListPresetConfig
}
```

兼容策略：

- 保留 `enableSavedQuery`，但标记为 deprecated。
- `enableSavedQuery=true` 时等价于 `listPreset.enabled=true`，但需要传 `qmModel/model`，否则只显示禁用态并给开发期 warning。
- 推荐业务方新接入使用 `:list-preset="{ enabled: true, model: 'FactOrderQueryModel', userId: currentUser.id, businessKey: 'work-order-list' }"`。

### DataTableWithSearch expose

```ts
interface ListViewState {
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
  pageSize?: number
}

interface DataTableWithSearchExpose {
  getListViewState(): ListViewState
  applyListViewState(state: ListViewState, options?: { reload?: boolean }): void
  resetListViewState(): void
}
```

关键变化：

- `applyListViewState` 必须能在组件内部应用列顺序和可见列。
- Schema 模式下，组件内部维护 `effectiveSchema`，由 `baseSchema + activePreset` 派生。
- 受控模式下，列变更不能擅自改父组件 props，应通过 `preset-apply` 事件把状态交给父层。

### 事件

```ts
defineEmits<{
  (e: 'preset-apply', preset: ListPresetDef, state: ListViewState): void
  (e: 'preset-save', preset: ListPresetDef): void
  (e: 'preset-delete', presetId: string): void
  (e: 'preset-default-change', presetId: string | null): void
}>()
```

## ListPreset 数据契约

建议新增契约，不直接复用 `SavedQueryDef` 名称。后端可以第一阶段复用同一 collection，也可以迁移成新 collection，但 API 语义要面向列表配置。

```ts
export type ListPresetVisibility = 'PRIVATE' | 'DEPARTMENT' | 'TENANT'

export interface ColumnViewSetting {
  name: string
  visible: boolean
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  order: number
}

export interface QueryConditionPreset {
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
}

export interface ListPresetDef {
  id: string
  model: string
  businessKey?: string
  title: string
  description?: string
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  query: QueryConditionPreset
  pageSize?: number
  visibility: ListPresetVisibility
  ownerId: string
  ownerDeptId?: string
  ownerTenantId?: string
  isDefault?: boolean
  version: 1
  createdAt: string
  updatedAt: string
}
```

### 为什么保留 `columns`

`columns` 是最小可执行 DSL 投影，后端和现有 saved-query 已经使用。`columnSettings` 承载 UI 层偏好。应用时：

- DSL 查询使用 `columns`。
- 表格 UI 使用 `columnSettings` 补充宽度、固定列和顺序。
- 当 `columnSettings` 缺失时，按 `columns` 自动生成。

## 用户标识与存储策略

8.6.0.beta 先采用前端显式传 `userId` 的方案。自定义列表配置按用户隔离，文件系统降级存储时也需要稳定目录，例如 `/list-preset/users/${userId}/...`。这一阶段 `userId` 是配置命名空间，不作为权限边界；真实数据查询仍走现有后端数据权限。

后续如果接入方愿意做二次开发，可以补一个后端身份解析扩展，例如 `ListPresetIdentityResolver`，从登录态、网关 header 或 session 中解析用户，再逐步废弃前端传 `userId`。

存储策略：

1. Mongo 仍是推荐存储；默认 `AUTO` 模式下，只有配置了 `spring.data.mongodb.uri` 才使用 Mongo。
2. 如果运行环境没有提供 Mongo URI，则直接使用文件系统；显式 `storage=MONGO` 时仍保持 Mongo 优先并在运行时不可用时降级。
3. 文件系统目录按 `userId/model/businessKey` 隔离，所有路径片段必须做白名单清洗，避免把前端传入值直接拼成路径。
4. 查询配置泄漏暂时不是 P0 风险，因为配置只影响前端视图和默认条件；数据结果仍受后端数据权限控制。后续共享能力增强时再补更严格的配置可见性和后台身份校验。

## 后端 API 设计

建议新增 API 前缀：

```text
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}?businessKey=xxx
GET    /data-viewer/api/list-preset/users/{userId}/models/{model}/default?businessKey=xxx
POST   /data-viewer/api/list-preset/users/{userId}/models/{model}
GET    /data-viewer/api/list-preset/users/{userId}/presets/{id}
PUT    /data-viewer/api/list-preset/users/{userId}/presets/{id}
DELETE /data-viewer/api/list-preset/users/{userId}/presets/{id}
POST   /data-viewer/api/list-preset/users/{userId}/presets/{id}/default
DELETE /data-viewer/api/list-preset/users/{userId}/models/{model}/default?businessKey=xxx
```

第一版不建议继续把“应用”设计成创建临时 `queryId` 的后端动作。对嵌入式组件来说，应用 preset 应是前端状态切换：

1. 前端拿到 preset。
2. 校验字段仍在当前 schema 中。
3. 派生 columns/schema。
4. 更新 slice/orderBy。
5. 调用现有 `fetchData` 或 direct query。

保留 `saved-query/{id}/apply` 给老 viewer/queryId 链接场景，不作为新自定义列表主路径。

### 后端必须补齐的点

- `businessKey` 全链路落库、查询过滤、索引。
- `ownerId` 从 path 中的 `userId` 写入，v1 不额外做强身份校验。
- 每个 `userId + model + businessKey` 只能有一个默认 preset。
- 新增 `ListPresetStore` 抽象，按 `foggy.data-viewer.list-preset.storage` 选择 Mongo 或文件系统；`AUTO` 模式未提供 Mongo URI 时使用文件系统。
- 文件系统存储必须按 `userId/model/businessKey` 分目录，并对路径片段做白名单清洗。
- 保存时校验字段必须属于 QM schema 的可见字段，不能只信前端。
- 保存 slice/orderBy 时校验字段权限和操作符合法性。

## UI 设计

### 工具栏入口

按钮文案：`自定义列表`

位置：

- 默认放在 `DataTable` 顶部工具栏左侧，位于业务 toolbar slot 后面。
- 若业务方指定 `placement='toolbar-right'`，放到分页左侧。
- 若指定 `external`，组件不渲染按钮，只提供 `ListPresetManager` 给外部使用。

### 弹窗结构

使用旧版用户熟悉的两栏结构，但重命名：

- 左侧：我的列表
- 右侧：共享列表

列表项展示：

- 名称
- 字段数
- 筛选数
- 排序数
- 默认标记
- 更新时间
- 可见范围

操作：

- 应用
- 设为默认
- 编辑
- 复制为我的
- 删除

### 编辑器步骤

第一版建议 3 步：

1. 字段选择
   - 搜索字段
   - 全选 / 全不选
   - 勾选显示字段
   - 右侧已选字段拖拽排序
   - 固定左 / 固定右
   - 列宽设置

2. 查询条件
   - “保存当前筛选条件”一键带入。
   - 支持追加常用条件。
   - 支持清空默认条件。
   - 支持默认排序设置。

3. 基本设置
   - 名称
   - 描述
   - 可见范围
   - 是否设为默认

## 高频 DSL 能力分级

### 8.6.0.beta 第一版必须支持

这些能力高频、易 UI 化、风险可控：

- 投影列：`columns`
- 字段顺序：`columns` 顺序 + `columnSettings.order`
- 字段显示/隐藏：`columnSettings.visible`
- 列宽：`width/minWidth`
- 固定列：`fixed`
- 分页大小：`pageSize`
- 排序：`orderBy`
- 基础比较过滤：`=`, `!=`, `>`, `>=`, `<`, `<=`
- 文本过滤：`like`, `right_like`
- 集合过滤：`in`, `not in`
- 范围过滤：`[]`, `[)`
- 空值过滤：`is null`, `is not null`
- 字典 / bool / 维度成员筛选
- 多条件 AND：slice 数组默认 AND 语义

### 8.6.0.beta 可选支持

这些能力可以先作为只读回显或高级开关，若实现成本可控再纳入：

- OR 条件：`link=2`
- 一层 `children` 条件组
- `groupBy`
- 简单 `calculatedFields` 的只读保存和应用
- 汇总字段展示开关

建议第一版不要做 OR/children 的完整拖拽条件树编辑器。可以允许“高级模式”展示 JSON，只保存已有合法 DSL，不鼓励普通用户手写。

### 后续版本再做

- 任意嵌套条件树可视化。
- calculatedFields 表达式编辑器。
- 复杂 groupBy 聚合设计器。
- timeWindow / baselineRatio / parentShare 等分析型 DSL。
- pivot rows/columns/metrics。
- compose_script 多步查询。
- Drillthrough / 单元格坐标反查。

## 与现有 saved-query 的关系

短期策略：

- 保留 `SavedQueryManager`，避免破坏已有使用方。
- 新增 `ListPresetManager`，面向红框“自定义列表”场景。
- 后端可以先复用 `SavedQueryDef` 的字段模型，但 API、类型和 UI 不再叫 saved-query。

中期策略：

- 把 `SavedQueryManager` 标记为兼容组件。
- 文档推荐使用 `ListPresetManager`。
- 如果需要从旧 saved-query 迁移，提供一次性转换：
  - `columns -> columns`
  - `slice/orderBy -> query`
  - `visibility/owner -> visibility/owner`
  - `groupBy/calculatedFields -> advancedDsl`

## 实施计划

### S1 前端状态模型与列应用

- 新增 `ListViewState` / `ColumnViewSetting` / `ListPresetDef` 类型。
- `DataTableWithSearch` 内部维护 `activeListViewState`。
- Schema 模式下 `effectiveColumns` 从 `base columns + active state` 派生。
- 补齐 `applyListViewState()` 对列的应用。
- 单测覆盖：
  - 应用 preset 后列顺序变化。
  - 隐藏列不进入表格。
  - 固定列和宽度合并正确。
  - unknown columns 被忽略并 warning。

### S2 自定义列表 UI

- 新增 `components/list-preset/ListPresetManager.vue`。
- 新增 `ListPresetDialog.vue`、`ListPresetEditor.vue`。
- 在 `DataTableWithSearch` 中消费 `listPreset` prop 自动挂载按钮。
- 保留 toolbar slot 透传。
- verification-app 增加“自定义列表”场景。

### S3 后端 ListPreset API

- 新增 domain / repository / service / controller。
- 新增 `ListPresetStore` 抽象，提供 Mongo 和文件系统两套实现。
- 支持 `businessKey`。
- 支持默认 preset。
- API 路径带 `users/{userId}`，后端用该值作为配置命名空间。
- 保存与更新时做模型字段校验。
- 单测覆盖 Mongo store、文件 store、默认唯一性、路径清洗、字段校验。

### S4 DSL 高频条件编辑

- 复用现有 filter 组件生成 `SliceRequestDef`。
- 编辑器支持从当前筛选条件带入。
- 支持默认排序编辑。
- 对 OR/children 先做只读回显或高级 JSON 入口。

### S5 文档与迁移

- 更新 `addons/foggy-data-viewer/docs/frontend-usage-and-capabilities.md`。
- 更新 `frontend/SAVED_QUERY_USAGE.md`，说明 saved-query 与 list-preset 的边界。
- 给业务方提供最小接入示例。

## 兼容性与迁移策略

- 不删除 `SavedQueryManager`。
- 不改变现有 `saveQuery/listSavedQueries` API。
- `enableSavedQuery` 保留一个版本周期，内部映射到 `listPreset` 或输出 warning。
- 旧业务如果已经外部放置 `SavedQueryManager`，不受影响。
- 新业务直接使用 `listPreset`。

## 风险点

- 列状态如果只在子组件内部维护，受控模式用户可能以为父层 columns 已改变；需要事件和文档说清楚。
- v1 的 `userId` 由前端提供，只用于隔离配置存储；如果接入方把配置本身视为敏感信息，需要二次开发后台身份解析。
- 保存 preset 时仍要做后端字段权限校验，否则用户可构造非法 DSL。
- 默认 preset 自动加载可能改变页面首次请求参数；需要 `autoLoadDefault` 可关闭。
- 共享列表被应用后不能修改原列表，建议提供“复制为我的”。
- `businessKey` 必须真正落库，否则不同业务页面会互相污染列表。

## 验收标准

- 在 `DataTableWithSearch` 上配置 `listPreset.enabled=true` 后，工具栏出现“自定义列表”按钮。
- 用户新建列表后，刷新页面仍能在“我的列表”看到。
- 应用列表后，表格列顺序、显示字段、固定列、列宽、筛选、排序都生效。
- 设置默认列表后，重新进入页面自动应用。
- Mongo 可用时配置写入 Mongo；没有 Mongo 时配置写入文件系统。
- 不同 `userId`、不同 `businessKey` 的方案互不覆盖。
- unknown / unauthorized 字段不能保存；已保存列表中的失效字段应用时被忽略并提示。
- 原 `SavedQueryManager` 使用方式仍可运行。
