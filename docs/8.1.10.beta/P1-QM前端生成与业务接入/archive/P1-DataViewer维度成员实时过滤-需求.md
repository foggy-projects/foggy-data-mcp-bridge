> **⚠️ SUPERSEDED** — 本文档已被 [P1-QM前端组件体系-技术规范](../../P1-QM前端组件体系-技术规范.md) 及其子规范替代。保留仅供讨论历史回溯。

---

# P1-DataViewer维度成员实时过滤-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`待评审`
- 责任边界：
  - `foggy-dataset-model`：保持现有 synthetic member-QM 与维度 simple/direct DSL 能力，不在本需求中继续扩展前端契约
  - `addons/foggy-data-viewer`：封装面向前端 QM 表格组件的成员查询接口、元数据适配、组件交互

## 系列归档
本需求已归入 `QM 前端生成与业务接入` 系列，相关总览和接入文档见：

- [P1-QM前端生成与业务接入-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端生成与业务接入-需求.md)
- [P1-QM业务系统使用规范-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM业务系统使用规范-需求.md)

## 背景
维度成员查询能力已经在 model 模块内完成，内部基线见：

- [P1-维度成员内部QM映射-使用方式.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-维度成员内部QM映射/P1-维度成员内部QM映射-使用方式.md)

当前 data-viewer 侧现状：

- 前端类型里已经有 `filterType = 'dimension'`
- `SearchToolbar` 已支持 `filterOptionsLoader`
- `SelectFilter` 当前仍是“本地 options + 本地搜索”模型
- `ViewerApiController` 目前没有真正实现维度成员实时加载接口
- 现有 `fetchFilterOptions(model, queryId, columnName)` 只适合静态或一次性加载，不适合远程搜索、分页和选中值回填

对应源码位置：

- [types/index.ts](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend/src/types/index.ts)
- [SearchToolbar.vue](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend/src/components/SearchToolbar.vue)
- [SelectFilter.vue](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend/src/components/filters/SelectFilter.vue)
- [viewer.ts](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend/src/api/viewer.ts)
- [ViewerApiController.java](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/src/main/java/com/foggyframework/dataviewer/controller/ViewerApiController.java)

## 结论
本需求的推荐方案是：

- 不让前端组件直接调用 model 模块的 `/jdbc-model/...` 接口
- 不让前端组件直接感知 synthetic member-QM 名称
- 在 `addons/foggy-data-viewer` 内增加一层前端友好的成员查询封装
- 组件侧只依赖 `qmModel + fieldName`
- data-viewer 服务端负责把前端请求映射到现有的维度成员 simple/direct DSL 能力

## 目标
- 为 `foggy-data-viewer` 的 QM 表格组件提供“远程维度成员过滤/加载”能力
- 支持：
  - 打开下拉时按需加载
  - 输入关键字实时搜索
  - 服务端分页
  - 已选值回填
  - 父子维 hierarchy 查询
- 让组件最终生成正确的 DSL `slice`
- 保持前端不依赖 synthetic member-QM 内部实现细节

## 非目标
- 本需求不改 `foggy-dataset-model` 的 synthetic member-QM 设计
- 本需求不做“当前事实数据上下文裁剪成员”
- 本需求不讨论外部插件侧 DSL patch 注入
- 本需求不把“字典项”“属性 distinct 值”“维度成员”一次性统一到同一实现中
- 本需求不要求前端直接支持任意 direct DSL 维度查询

## 设计原则
- 前端契约稳定，内部实现可继续演进
- `data-viewer` 是前端消费层，不是把 dataset controller 原样透传出去
- 远程成员过滤必须按 `id` 存值，不按 `caption` 存值
- schema 只放“如何取成员”的元信息，不放大成员列表
- 兼容 data-viewer 当前组件结构，优先做增量改造

## 为什么不能直接复用当前 `filter-options` 设计
当前 data-viewer 前端预留的是：

- `filterOptionsLoader(columnName) => Promise<option[]>`

这套形式的问题很明显：

- 没有 `keyword`
- 没有 `start/limit`
- 没有 `selectedValues`
- 没有 `hierarchy`
- 无法区分 `caption` 字段与真正的 `selectionField`
- 组件无法做远程搜索和回填

因此本需求不建议继续在原始的“只传 `columnName`、只返回整包 options”模型上打补丁，而应升级为“远程成员查询请求/响应模型”。

## 推荐接口设计

### 1. data-viewer 服务端正式接口
建议在 `addons/foggy-data-viewer` 新增：

```text
POST /data-viewer/api/members/query
```

请求示例：

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

响应示例：

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
      "childrenOf",
      "descendantsOf",
      "selfAndDescendantsOf",
      "ancestorsOf",
      "selfAndAncestorsOf"
    ],
    "items": [
      {
        "value": "T003",
        "label": "华东一区",
        "parentValue": "T002",
        "depth": 3,
        "hasChildren": false
      },
      {
        "value": "T004",
        "label": "华东二区",
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
    "total": 2,
    "hasMore": false
  }
}
```

说明：

- 这不是 model 模块的新协议，而是 data-viewer 的前端适配协议
- data-viewer 服务端内部再去调用现有的维度 simple/direct DSL 能力
- 前端只拿 `value/label`

### 2. 正式接口唯一性
`8.1.10.beta` 不提供 viewer 页兼容入口，也不继续扩展旧的 `filter-options` 路径。

正式接口只保留：

```text
POST /data-viewer/api/members/query
```

原因：

- 维度成员能力本质是 `qmModel + fieldName`
- 它不依赖 `queryId` 当前缓存的分页与数据结果
- 用 `queryId` 绑定成员查询，会让接口和查询缓存生命周期耦合过深
- 继续保留兼容入口会让前端长期被旧签名拖住

## 元数据要求
前端要正确构建维度过滤器，schema 至少需要能表达：

```json
{
  "name": "team$caption",
  "title": "团队",
  "filterType": "dimension",
  "filterable": true,
  "hierarchical": true,
  "hierarchyOps": [
    "childrenOf",
    "descendantsOf",
    "selfAndDescendantsOf",
    "ancestorsOf",
    "selfAndAncestorsOf"
  ],
  "memberLookup": {
    "enabled": true,
    "selectionFieldName": "team$id",
    "displayFieldName": "team$caption",
    "searchable": true,
    "pageable": true,
    "defaultLimit": 20
  }
}
```

推荐要求：

- `filterType = "dimension"` 只表示“候选值来自远程成员查询”
- 真正生成 DSL `slice` 时必须使用 `memberLookup.selectionFieldName`
- 当前显示字段可以是 `team$caption`
- 但最终筛选字段必须是 `team$id`

这是本需求里最关键的一条。如果前端仍按当前 `SelectFilter.field = col.name` 直接产出 DSL，那么在维度成员场景下会把 `caption` 当成筛选值，语义会错。

## data-viewer 前端类型建议
建议在 `frontend/src/types/index.ts` 中增加如下结构：

```ts
export interface MemberLookupMeta {
  enabled: boolean
  selectionFieldName: string
  displayFieldName: string
  searchable?: boolean
  pageable?: boolean
  defaultLimit?: number
  hierarchical?: boolean
  hierarchyOps?: string[]
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
}

export interface MemberQueryResponse {
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

并在 `ColumnSchema` 上增加：

```ts
memberLookup?: MemberLookupMeta
```

## data-viewer 前端 API 层建议
建议在 [viewer.ts](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend/src/api/viewer.ts) 中新增：

```ts
fetchMemberOptions(request: MemberQueryRequest): Promise<MemberQueryResponse>
```

保留现有 `fetchFilterOptions(...)` 仅作为兼容或字典静态加载接口，不再作为维度实时过滤主入口。

## 组件改造建议

### 1. `SearchToolbar`
当前 `filterOptionsLoader` 签名过窄：

```ts
(columnName: string) => Promise<{ label: string; value: string | number }[]>
```

建议升级为：

```ts
(request: MemberQueryRequest) => Promise<MemberQueryResponse>
```

或至少支持第二种远程模式：

```ts
filterMemberLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
```

这样可以避免把字典静态选项和维度远程成员查询绑成一种接口。

### 2. `SelectFilter`
当前 `SelectFilter` 是本地搜索模型，需要补齐：

- 打开下拉时首次加载
- 输入关键词 debounce 后远程搜索
- 支持 loading
- 支持分页或“加载更多”
- 支持 `selectedValues` 回填
- 支持根据 `memberLookup.selectionFieldName` 生成 DSL slice

建议的行为：

- 单选：
  - 选择后立即产出 `[{ field: selectionFieldName, op: '=', value }]`
- 多选：
  - 确认后产出 `[{ field: selectionFieldName, op: 'in', value: [...] }]`
- 清空：
  - 返回 `null`

### 3. `DataTableWithSearch`
需要能够把当前 `qmModel` 透传给搜索栏或远程 loader。

如果当前页面是 `DataViewer` 场景：

- `qmModel` 可来自 `tableConfig.qmModel`

如果是 schema 模式：

- `qmModel` 需要由外层显式传入，或从 `schema` 中补充

## data-viewer 服务端适配建议
`ViewerApiController` 或其 service 层负责完成：

1. 读取 `qmModel + fieldName`
2. 调用现有 model 模块的维度成员查询能力
3. 把返回结果映射成 data-viewer 前端需要的统一 `value/label` 结构
4. 在服务端保留 header、namespace、鉴权上下文透传

注意：

- 这里是“data-viewer 封装 dataset 能力”
- 不是“把 dataset controller URL 原样暴露给浏览器”

## 与现有 synthetic member-QM 的映射关系
data-viewer 侧不需要暴露 synthetic member-QM 命名细节，但文档上应明确它的内部映射：

```text
qmModel + fieldName
-> 归一到 synthetic member-QM
-> 若有 keyword / hierarchy / selectedValues，则转成内部查询参数
-> 调用现有 simple/direct DSL 能力
-> 返回统一 MemberQueryResponse
```

## 缓存建议
data-viewer 侧建议做轻量缓存，避免前端频繁请求：

- 前端内存缓存 key：
  - `qmModel + fieldName + keyword + hierarchy + start + limit`
- 同 keyword 输入建议 `250ms` debounce
- 打开下拉时再请求，不预加载全量成员
- 同一组 `selectedValues` 的回填可以单独缓存短时间结果

## MVP 建议
`8.1.10.beta` 推荐最小版本如下：

- data-viewer 服务端新增 `POST /data-viewer/api/members/query`
- data-viewer 前端新增 `fetchMemberOptions`
- `ColumnSchema` 增加 `memberLookup`
- `SelectFilter` 支持维度远程加载
- 支持能力：
  - keyword 搜索
  - start/limit 分页
  - selectedValues 回填
  - hierarchy 的 `childrenOf`
- 树能力要求：
  - 本版本至少定义清楚树所需结构和字段规范
  - 若实现进度允许，直接基于 `vxe-table` 落一个树形筛选/树表 MVP
- 先不做：
  - 无限层懒加载 UI 的完整体验打磨
  - 字典与维度统一抽象
  - direct DSL 前端直通

## 需要避免的坑
- 不要让前端直接依赖 synthetic member-QM 名称
- 不要继续把 `caption` 当筛选值写入 DSL
- 不要把大成员列表塞进 schema
- 不要把 `queryId` 当成成员能力的唯一锚点
- 不要让远程维度过滤退化成“一次性拉全量 options 再本地搜索”

## 建议交付给 data-viewer 的实现任务
- 服务端：
  - 新增 data-viewer 成员查询 controller/service 封装
  - 适配现有 dataset 维度成员查询能力
- 前端：
  - 扩展 `types/index.ts`
  - 在 `viewer.ts` 增加成员查询 API
  - 改造 `SearchToolbar`
  - 改造 `SelectFilter`
  - 为 `DataViewer` / `DataTableWithSearch` 打通 `qmModel` 透传

## 最终建议
这项需求应被视为：

- “data-viewer 对现有维度成员能力的前端消费封装”

而不是：

- “继续在 model 模块里设计新的前端接口”

先把这一层 adapter 做稳，后续如果字典、属性 distinct、外部 patch 也要统一，再在 data-viewer 层继续收口会更合适。

## 设计收口说明
本需求的最后一轮设计收口已单独整理为：

- [P1-DataViewer维度成员实时过滤-设计收口.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-设计收口.md)

本次收口重点锁定：

- `data-viewer` 适配层的正式接口
- `memberLookup` 的 schema 映射规则
- 前后端 DTO 结构
- `8.1.10.beta` 的实施顺序与明确非目标

后续实现以设计收口文档为准；如需调整协议，先更新 `docs/8.1.10.beta/` 下对应文档，再进入代码实现。
