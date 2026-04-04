> **⚠️ SUPERSEDED** — 本文档已被 [P1-QM前端组件体系-技术规范](../../P1-QM前端组件体系-技术规范.md) 及其子规范替代。保留仅供讨论历史回溯。

---

# P1-DataViewer维度成员实时过滤-设计收口

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 设计状态：`待评审`
- 对应主需求：
  - [P1-DataViewer维度成员实时过滤-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-需求.md)
- 内部能力基线：
  - [P1-维度成员内部QM映射-使用方式.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-维度成员内部QM映射/P1-维度成员内部QM映射-使用方式.md)

## 本轮收口结论
`8.1.10.beta` 的实现边界明确如下：

- `foggy-dataset-model` 保持现有 synthetic member-QM 与维度成员检索能力，不新增新的前端协议。
- `addons/foggy-data-viewer` 新增前端友好的适配接口和组件改造，作为正式消费层。
- 前端组件只依赖 `qmModel + fieldName`，不直接依赖 synthetic member-QM 名称，也不直接调用 `/jdbc-model/...`。
- 本版本只解决“维度成员远程过滤/回填”，不顺带解决字典统一接口、属性 distinct 值统一接口、外部 patch 注入。

## 最终目标
- 让 `SearchToolbar` / `SelectFilter` 在 `filterType = 'dimension'` 时支持：
  - 打开下拉按需加载
  - keyword 远程搜索
  - `start/limit` 分页
  - `selectedValues` 回填
  - 层级维的 `childrenOf`
- 让最终生成的 DSL `slice` 始终落在正确的 `selectionFieldName` 上，而不是当前显示列名上。
- 保持前端契约稳定，后端内部仍可继续演进 synthetic member-QM 与 simple/direct DSL 的实现。

## 明确非目标
- 不改 `foggy-dataset-model` 现有成员检索底层协议。
- 不把“字典项”“维度成员”“属性 distinct 值”统一成一个接口。
- 不在本版本处理外部业务控制参数的通用协议。
- 不在本版本处理基于当前事实数据结果集的动态成员裁剪。
- 不在本版本让浏览器直接访问内部 synthetic member-QM 能力。

## 正式服务端契约

### 接口路径
```text
POST /data-viewer/api/members/query
```

### 请求体
```json
{
  "qmModel": "FactTeamSalesQueryModel",
  "fieldName": "team$caption",
  "keyword": "华东",
  "start": 0,
  "limit": 20,
  "selectedValues": ["T002", "T005"],
  "hierarchy": {
    "op": "childrenOf",
    "value": "T002"
  }
}
```

### 请求字段约束
- `qmModel`：必填，外层业务 QM 名称。
- `fieldName`：必填，前端 schema 暴露出来的字段名，例如 `team$caption`。
- `keyword`：可选，成员搜索关键字。
- `start`：可选，默认 `0`。
- `limit`：可选，默认 `20`，建议上限由服务端限制。
- `selectedValues`：可选，用于回填已选项。
- `hierarchy`：可选，本版本只要求正式支持 `childrenOf`。

### 响应体
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "qmModel": "FactTeamSalesQueryModel",
    "fieldName": "team$caption",
    "selectionFieldName": "team$id",
    "displayFieldName": "team$caption",
    "hierarchical": true,
    "hierarchyOps": [
      "childrenOf"
    ],
    "items": [
      {
        "value": "T003",
        "label": "华东一区",
        "parentValue": "T002",
        "depth": 3,
        "hasChildren": false
      }
    ],
    "selectedItems": [
      {
        "value": "T002",
        "label": "华东大区"
      }
    ],
    "total": 1,
    "hasMore": false
  }
}
```

### 响应字段约束
- `selectionFieldName`：前端最终生成 DSL `slice` 时必须使用的字段名。
- `displayFieldName`：前端当前过滤器展示字段名。
- `hierarchical`：是否层级维。
- `hierarchyOps`：当前字段允许的 hierarchy 操作集合。
- `items`：当前分页查询结果。
- `selectedItems`：已选值回填结果；允许与 `items` 无交集。
- `total`：总条数。
- `hasMore`：是否仍有下一页。

### 接口唯一性
`8.1.10.beta` 不再定义 viewer 页兼容入口，也不保留基于 `queryId` 的成员查询别名接口。

原因：

- 成员查询的主锚点是 `qmModel + fieldName`
- `queryId` 会把成员查询和 viewer 页缓存生命周期错误耦合
- 正式契约应尽早切到新接口，避免旧签名继续渗透到组件层

## 正式前端类型

### TypeScript
```ts
export interface MemberLookupMeta {
  enabled: boolean
  selectionFieldName: string
  displayFieldName: string
  searchable?: boolean
  pageable?: boolean
  defaultLimit?: number
}

export interface MemberQueryRequest {
  qmModel: string
  fieldName: string
  keyword?: string
  start?: number
  limit?: number
  selectedValues?: Array<string | number>
  hierarchy?: {
    op: string
    value: string | number
  }
}

export interface MemberOption {
  value: string | number
  label: string
  parentValue?: string | number | null
  depth?: number
  hasChildren?: boolean
  pathValues?: Array<string | number>
  pathLabels?: string[]
  disabled?: boolean
}

export interface MemberQueryResponse {
  qmModel?: string
  fieldName?: string
  selectionFieldName: string
  displayFieldName: string
  hierarchical?: boolean
  hierarchyOps?: string[]
  items: MemberOption[]
  selectedItems?: MemberOption[]
  total: number
  hasMore?: boolean
}
```

### Java
建议在 `data-viewer` 侧新增独立 DTO，而不是复用旧的 `filter-options` 结构：

- `MemberQueryRequest`
- `MemberQueryResponse`
- `MemberOption`
- `MemberHierarchyRequest`

这样可以把远程成员查询与静态字典选项明确区分，避免继续挤压旧接口。

## Schema 收口规则

### ColumnSchema 扩展
`frontend/src/types/index.ts` 需要增加：

```ts
export interface ColumnSchema {
  // 已有字段...
  hierarchical?: boolean
  hierarchyOps?: string[]
  memberLookup?: MemberLookupMeta
}
```

### memberLookup 的最终职责
- `memberLookup` 只描述“这个字段如何查远程成员”。
- `memberLookup` 不承载成员数据本身。
- `memberLookup` 不负责表达页面级 UI 细节。
- 最终 DSL `slice` 始终以 `memberLookup.selectionFieldName` 为准。

### 树能力契约
考虑到当前前端基于 `vxe-table` 封装，树表实现成本可控，因此本版本对树能力做如下收口：

- 树能力不再视为完全后置议题
- 本版本至少要把树所需的返回结构和字段规范定死
- 若实现进度允许，可直接基于 `vxe-table` 的树表能力实现 MVP

本版本树结构最小字段集：

- `value`
- `label`
- `parentValue`
- `depth`
- `hasChildren`

可选扩展字段：

- `pathValues`
- `pathLabels`
- `disabled`

约束：

- 服务端返回仍以扁平结构为主，不强制嵌套 `children`
- 前端可基于 `parentValue` 和 `hasChildren` 构建树表或懒加载树
- hierarchy 请求本版本正式支持 `childrenOf`，其余操作后续扩展

### 自动映射规则
基于现有 QM schema 和命名约定，`data-viewer` 在处理 `filterType = 'dimension'` 时按以下规则推导：

1. 当前字段名以 `$caption` 结尾，且存在同基名的 `$id` 字段：
   - `selectionFieldName = {base}$id`
   - `displayFieldName = {base}$caption`
   - 为当前列补充 `memberLookup`

2. 当前字段名以 `$id` 结尾，且存在同基名的 `$caption` 字段：
   - `selectionFieldName = {base}$id`
   - `displayFieldName = {base}$caption`
   - 允许补充 `memberLookup`
   - 但前端默认应优先把过滤器挂在 `$caption` 字段上，而不是 `$id`

3. 当前字段无法推导出成对的 `$id/$caption`：
   - 不自动开启 `memberLookup`
   - 继续保留普通字段行为，避免错误地产生 DSL

4. `hierarchical` 和 `hierarchyOps`：
   - 优先透传后端 metadata 中已有值
   - 本版本前端只保证 `childrenOf` 进入正式交互流程

### 为什么要这样收口
- `team$caption` 是展示字段，不是最终筛选字段。
- 当前 `SelectFilter` 直接用 `col.name` 产出 slice，会把 `caption` 误当成真实取值字段。
- 只有把 `selectionFieldName` 明确写进 schema，前端组件才能稳定生成正确 DSL。

## 前端组件改造收口

### SearchToolbar
- 保留现有静态 `filterOptionsLoader` 兼容路径，不作为维度主入口。
- 新增独立远程成员加载能力，例如：

```ts
filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
```

- 当列包含 `memberLookup` 时，优先走 `filterMemberLoader`。

### SelectFilter
`SelectFilter` 在本版本需升级为同时支持：

- 静态 options 模式
- 远程 member 模式
- 树形 member 模式

远程 member 模式最小要求：

- 打开下拉首次加载
- keyword debounce 搜索
- `selectedValues` 回填
- `start/limit` 分页
- 基于 `selectionFieldName` 输出 DSL
- 单选立即提交，多选确认提交
- 对 `hierarchical = true` 的字段，能够消费树结构字段并接入 `vxe-table` 树能力

### DataTableWithSearch
- 需要保证 `qmModel` 能传到 `SearchToolbar`。
- schema 模式下可优先从 `tableConfig.qmModel` 读取。
- 若调用方未提供 `qmModel`，则 dimension 远程过滤能力不自动启用。

## 服务端适配收口
`data-viewer` 服务端 adapter 的职责固定为：

1. 接收前端的 `qmModel + fieldName + 查询参数`
2. 根据当前字段映射到内部 synthetic member-QM 能力
3. 调用既有 simple/direct DSL 查询
4. 归一化成 `MemberQueryResponse`
5. 保留调用链中的 header、namespace、鉴权上下文

约束：

- 不把 synthetic member-QM 名称直接暴露给前端
- 不把 `/jdbc-model/...` URL 直接暴露给前端
- 不在本版本新建第二套成员查询底层协议

## 与字典接口的边界
这份需求只处理 `filterType = 'dimension'` 的远程成员过滤。

以下内容明确不在本次收口内：

- 开发期代码生成使用的统一字典接口
- 运行时字典 preload / batch 获取策略
- 字典与维度成员是否统一成单一 lookup 契约

这些能力后续如果进入 `8.1.10.beta`，应单独建文档再讨论。

## 实施顺序
建议按以下顺序进入实现：

1. 先改 `types/index.ts`，补齐 `MemberLookupMeta`、`MemberQueryRequest`、`MemberQueryResponse`、`ColumnSchema.memberLookup`
2. 再改 `viewer.ts`，新增 `fetchMemberOptions`
3. 再做 `data-viewer` 服务端 `members/query` adapter
4. 再改 `SearchToolbar`
5. 再改 `SelectFilter`
6. 最后打通 `DataTableWithSearch` / `DataViewer`

原因：

- 先锁类型和接口，避免组件层边写边改协议
- 先做 adapter，前端调试时才有稳定入口
- `SelectFilter` 是行为变化最大的部分，应放在接口稳定后改

## 本版本验收标准
- 前端 schema 中可稳定得到 `memberLookup`
- 前端通过 `fetchMemberOptions` 能按 `qmModel + fieldName` 远程加载成员
- `dimension` 过滤器支持 keyword、分页、selectedValues 回填
- 最终生成的 DSL `slice.field` 为 `selectionFieldName`
- `childrenOf` 能贯通到服务端并返回正确成员集合
- 树场景下字段结构规范已锁定；若进入实现，前端可基于 `vxe-table` 消费 `parentValue/depth/hasChildren`
- 前端不直接依赖 synthetic member-QM 名称或 `/jdbc-model/...` 路径

## 待后续单独建档的议题
- 统一字典接口
- 外部业务控制参数如何进入成员查询
- `Authorization` / `X-NS` 之外的权限扩展方案
- 更完整的 hierarchy 操作集
- 字典、维度成员、属性 distinct 值的统一 lookup 契约
