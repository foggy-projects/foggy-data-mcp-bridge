# P1-QM前端下拉组件生成-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`讨论中`
- 所属系列：
  - [P1-QM前端生成与业务接入-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端生成与业务接入-需求.md)

## 背景
当前系列讨论主要聚焦在“QM 生成表格组件”，但在实际业务系统里，前端可生成的组件不应只限于表格。

除了表格组件，至少还有一类高频组件同样适合由 QM/元数据驱动生成：

- 下拉框组件

并且当前已经可以明确拆成两类：

1. 字典下拉组件
2. QM 查询下拉组件

这两类组件都属于“前端生成与业务接入”系列的一部分，应在 `8.1.10.beta` 中先纳入设计边界。

## 目标
- 把“可生成组件”的范围从表格扩展到下拉组件
- 明确两类下拉组件的职责边界和最小契约
- 让后续生成器和业务系统使用规范能同时覆盖表格和 lookup 组件

## 非目标
- 本文档暂不定义最终的字典接口协议细节
- 本文档暂不定义最终的 QM lookup 查询协议细节
- 本文档不要求 `8.1.10.beta` 一次性生成完整表单页面

## 组件分类

### 1. 字典下拉组件
适用场景：

- 状态
- 类型
- 枚举值
- 相对稳定的小规模选项集

核心特点：

- 以 `dictId` 为主锚点
- 选项来源是字典
- 更适合单选/多选表单控件

建议生成产物示意：

- `OrderStatusSelect.vue`
- `order-status.options.ts`
- `order-status.types.ts`

### 2. QM 查询下拉组件
适用场景：

- 客户选择
- 商品选择
- 组织/团队选择
- 任意需要通过 QM 查询候选项的 lookup 场景

核心特点：

- 以 `qmModel + valueField + labelField` 为主锚点
- 支持远程搜索、分页、回填
- 可用于普通平铺下拉，也可扩展到树形 lookup

建议生成产物示意：

- `CustomerLookupSelect.vue`
- `customer-lookup.api.ts`
- `customer-lookup.types.ts`

## 与表格组件的关系
这类组件不应被视为“表格的附属功能”，而应与表格组件并列看待：

- 表格组件：面向列表展示和筛选
- 下拉组件：面向表单输入、筛选输入、引用选择

两者共享的底层能力包括：

- lookup 协议
- query hooks
- 全局参数 / custom 参数
- dict / member / qm query adapter

因此，生成器和协议设计都不应只围绕表格展开。

## 对生成器设计的影响
当前代码生成需求需要同步扩大范围：

- 不仅生成 `Table.vue`
- 也要支持生成 `DictSelect.vue` / `QmLookupSelect.vue`

推荐生成目录可演进为：

```text
src/generated/
  qm/
    order/
      OrderTable.vue
      OrderStatusSelect.vue
      CustomerLookupSelect.vue
```

## 对业务系统使用规范的影响
业务系统接入规范需要从“生成表格如何使用”扩展为“生成组件如何使用”。

这意味着：

- 下拉组件同样不能鼓励直接修改 generated 文件
- 下拉组件同样需要包装层和 adapter 注入能力
- 下拉组件同样要支持全局参数 / custom 参数
- 下拉组件同样要遵守协议层 / 渲染层 / 宿主层的分层设计

## 当前建议
`8.1.10.beta` 先完成以下收口：

1. 在系列范围内承认“下拉组件生成”是正式能力
2. 先把组件分类和边界定清楚
3. 表格组件继续优先推进实现
4. 下拉组件至少把设计和生成边界先锁住

## QM 查询下拉的最小查询契约
`8.1.10.beta` 先锁“组件级最小契约”，不在本轮把最终服务端路径完全定死。

### 1. 配置锚点
QM 查询下拉组件至少需要以下配置：

```ts
interface QmLookupConfig {
  qmModel: string
  valueField: string
  labelField: string
  searchFields?: string[]
  extraFields?: string[]
  multiple?: boolean
  pageable?: boolean
  hierarchical?: boolean
  defaultLimit?: number
}
```

含义：

- `qmModel`：候选项来自哪个 QM
- `valueField`：真正回填给表单值的字段
- `labelField`：前端展示文本字段
- `searchFields`：关键字命中哪些字段
- `extraFields`：返回后可供渲染或二次逻辑使用的附加字段

### 2. 最小请求结构
```ts
interface QmLookupQueryRequest {
  qmModel: string
  valueField: string
  labelField: string
  keyword?: string
  start?: number
  limit?: number
  selectedValues?: Array<string | number>
  globalParams?: Record<string, unknown>
  customParams?: Record<string, unknown>
  filters?: SliceRequestDef[]
  orderBy?: OrderRequestDef[]
  hierarchy?: {
    op: string
    value: string | number
  }
}
```

本版本最小要求：

- 支持 `keyword`
- 支持 `start/limit`
- 支持 `selectedValues` 回填
- 支持全局参数与 custom 参数注入
- 若 `hierarchical = true`，可复用树结构字段

### 3. 最小响应结构
```ts
interface QmLookupOption {
  value: string | number
  label: string
  extra?: Record<string, unknown>
  parentValue?: string | number | null
  depth?: number
  hasChildren?: boolean
}

interface QmLookupQueryResponse {
  items: QmLookupOption[]
  selectedItems?: QmLookupOption[]
  total: number
  hasMore?: boolean
  hierarchical?: boolean
  hierarchyOps?: string[]
}
```

约束：

- 组件只依赖标准化后的 `value/label`
- 附加字段统一落在 `extra`
- 树场景优先沿用 `parentValue/depth/hasChildren`

### 4. 与维度成员查询的关系
维度成员查询可以视为 QM 查询下拉的一种特化场景，但 `8.1.10.beta` 暂不强行合并成同一份运行时文档。

当前收口方式：

- 通用 QM 查询下拉：以本节最小契约为准
- 维度成员查询：继续按 [P1-DataViewer维度成员实时过滤-设计收口.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-设计收口.md) 推进

这样做的好处是：

- 先把通用 lookup 组件边界定住
- 不强迫维度成员场景立刻重构到统一协议
- 后续如果两者收敛，再单独建统一 lookup 契约文档

## 字典下拉的选项来源策略
在字典接口尚未完全定稿前，`8.1.10.beta` 建议先按“双模式”设计。

### 1. 静态快照模式
适用：

- 小规模稳定字典
- 开发期即可确定的枚举值

策略：

- 生成器在开发期拉取字典数据
- 生成 `*.options.ts`
- 组件默认直接消费本地 options

优点：

- 接入简单
- 运行时零请求
- 对业务系统最友好

### 2. 远程适配模式
适用：

- 大字典
- 动态字典
- 受权限影响的字典

策略：

- 生成组件仍以 `dictId` 为锚点
- 不内联完整 options
- 通过 adapter 注入远程字典加载能力

### 3. 当前建议
`8.1.10.beta` 先按以下规则落地：

- 字典下拉默认优先静态快照模式
- 若后续判断某类字典不能快照化，再切到远程适配模式
- 统一字典接口本身继续单独建档，不在本文档中展开

这样可以先让生成器和业务系统用起来，再逐步收敛更复杂的字典契约。

## 后续讨论项
- 表单组件与表格筛选器是否复用同一套 lookup 协议
- 生成器如何描述 `valueField` / `labelField` / 多选 / 树形能力
