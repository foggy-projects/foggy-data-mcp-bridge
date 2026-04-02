# P1-QM业务系统使用规范-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P1`
- 状态：`讨论中`
- 所属系列：
  - [P1-QM前端生成与业务接入-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端生成与业务接入-需求.md)

## 背景
[P1-QM前端代码生成-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端代码生成-需求.md) 当前主要定义了“QM 可以生成什么前端产物”，但还没有定义“业务系统应该如何接入这些产物”。

同时，生成产物的范围也不应只限于表格组件，相关扩展见：

- [P1-QM前端下拉组件生成-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM前端下拉组件生成-需求.md)
- [P1-QM查询条件区与列筛选并存-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-QM查询条件区与列筛选并存-需求.md)

如果这一层不先讨论清楚，会直接影响组件和生成器设计：

- 生成组件是否允许直接修改
- 业务系统如何挂接工具栏按钮、页面动作、权限按钮
- 页面如何注入路由参数、全局参数、模型级参数
- 页面如何替换列展示、字典展示、远程成员查询策略
- 页面如何封装查询 hooks、埋点、错误处理、缓存策略

因此，这份文档虽然面向业务系统使用规范，但本质上也是前端组件设计输入。

## 目标
- 明确生成产物在业务系统中的推荐接入方式
- 明确业务系统可扩展点和禁止修改点
- 倒推出生成器和组件必须预留的扩展能力
- 让“生成产物可用”变成“生成产物可稳定接入”

## 非目标
- 本文档不定义前端元数据 JSON 契约细节
- 本文档不定义 `data-viewer` 维度成员接口细节
- 本文档不替代具体页面视觉规范或设计系统规范
- 本文档不要求生成器直接生成完整业务页面

## 推荐接入模型
业务系统不应直接在页面里到处散用底层 schema 和 API，而应采用“三层接入”：

1. `generated/`
   - 生成器输出
   - 只允许覆盖式重生成
   - 不允许业务直接手改

2. `modules/` 或 `adapters/`
   - 面向业务域的薄封装层
   - 负责把生成产物和业务参数、权限、页面动作拼起来

3. `pages/`
   - 业务页面层
   - 负责布局、页面级交互、组合多个组件

## 推荐目录结构
```text
src/
  generated/
    qm/
      order/
        OrderTable.vue
        order.schema.ts
        order.types.ts
        order.api.ts
  modules/
    order/
      OrderListModule.vue
      order-table-config.ts
      order-query-hooks.ts
  pages/
    order/
      OrderListPage.vue
```

## 业务系统如何使用生成产物

### 1. 不直接修改 generated 文件
原则：

- `generated/` 只由生成器覆盖
- 业务改动放在 `modules/`、`pages/`、`custom/`
- 若需要改变生成结果，应回到模板、元数据或扩展参数

这是最核心的使用规范。如果业务系统直接改 generated 文件，后续模型迭代时一定失控。

### 2. 通过包装层接入业务参数
业务系统推荐通过包装层统一注入：

- 路由参数
- 全局查询参数
- 租户/组织/场景参数
- 权限上下文
- 默认筛选条件
- 页面级 query hooks

不建议页面直接把这些参数塞进生成组件内部实现，否则生成组件会被业务细节反向污染。

包装层还应负责承接：

- 是否显示传统查询区
- query field 的 placement 覆盖
- query panel 的折叠与布局策略

### 3. 通过扩展位承接业务动作
生成组件至少要支持以下扩展位：

- toolbar 插槽
- 行级 action 插槽
- 列覆盖或列附加配置
- query hooks
- 全局参数入口
- 模型级 custom 参数入口

如果这些扩展位没有被预留，业务系统最终还是会去 fork 生成组件。

## 反向约束到组件设计
为了让业务系统能按上面的方式接入，组件和生成器设计必须满足：

### 1. 生成产物要“薄”
- 生成的 Vue 组件只负责组装 `DataTableWithSearch` 与基础 schema
- 不把页面级动作、按钮、弹窗、路由逻辑写进生成组件

### 2. Schema 要允许二次合并
- 业务系统应能在外层追加列配置、隐藏列、改标题、加 formatter
- 不能要求业务系统只能全量复制一份 schema 再改

进一步要求：

- 列 schema 与 query schema 应分别可覆盖
- 不应把“查询条件布局”继续塞回列定义里

### 3. Query 层要允许 hook
- 业务系统需要接入埋点、鉴权头、错误统一处理、结果后处理
- 因此组件或 API 包装层必须有 query hooks 或 adapter 扩展点

### 4. 参数入口要显式区分
- 全局参数：跨模型、跨页面一致的上下文
- custom 参数：当前模型或当前页面专属
- 不应把所有参数都混在一个匿名对象里

### 5. 运行时能力要由适配层提供
- 维度成员远程过滤
- 字典批量加载
- 统一 API 调用封装

这些能力不应散落在业务页面中各自实现，否则“生成组件可复用”会失去意义。

## 分层设计建议
考虑到当前 `addons/foggy-data-viewer` 中的前端组件后续可能独立立项，并服务于 Python 版、Odoo 插件和未来更多宿主，本系列建议按三层思路设计：

### 1. 协议层
负责定义与具体渲染实现无关的稳定契约，例如：

- `TableSchema`
- `ColumnSchema`
- `MemberQueryRequest/Response`
- 字典与 lookup 契约
- query hooks
- 全局参数 / custom 参数约定

原则：

- 业务系统优先依赖这一层
- 这一层不直接绑定 `vxe-table`

### 2. 渲染层
负责把协议层落到具体前端技术栈。

当前版本的默认渲染层：

- Vue 3
- `vxe-table`

职责：

- 表格/树表渲染
- 过滤器组件
- `DataTableWithSearch`
- schema 到 UI 的转换

原则：

- 可以基于 `vxe-table` 做深度封装
- 可以内部吸收 plugin 思路
- 但不应把 `vxe-table` plugin 直接作为业务系统的主 public API

### 3. 宿主层
负责把渲染层接到具体运行环境里，例如：

- `data-viewer`
- Python 版插件
- Odoo 插件
- 未来其他宿主

职责：

- 提供运行时 adapter
- 承接宿主特有的路由、权限、配置和上下文
- 组合协议层与渲染层

### 为什么主路线仍然选择“封装”而不是“纯 plugin”
`vxe-table` plugin 的优点是轻，但它更适合增强 `vxe-table`，不适合作为我们这套 QM 组件体系的主架构。

主路线继续走封装的原因：

- 我们的核心价值是 QM 协议、lookup 能力、query hooks 和生成器产物，而不是 `vxe-table` 本身
- 业务系统如果直接围绕 `vxe-table` plugin 写，会更容易被 `vxe-table` API 绑死
- 封装层可以让业务系统优先依赖 Foggy 自己的协议和组件，而不是依赖某个具体表格库
- 后续如果更换底层实现，协议层和宿主层更容易保住

结论：

- 对外：优先暴露 Foggy 自己的协议与封装组件
- 对内：允许在渲染层内部采用 `vxe-table` plugin 化思路优化实现

## 推荐的业务接入方式

### 模式 A：业务模块包装生成组件
这是推荐模式。

做法：

- 生成器输出 `OrderTable.vue`
- 业务模块创建 `OrderListModule.vue`
- 在 `OrderListModule.vue` 中注入权限、工具栏、默认参数、hooks、列覆盖
- 页面只使用 `OrderListModule.vue`

优点：

- 生成产物和业务逻辑隔离清楚
- 便于后续重生成
- 页面层更薄

### 模式 B：页面直接消费生成组件
只适合 PoC 或很轻的场景。

问题：

- 页面容易堆满参数拼装逻辑
- 多页面复用时会复制粘贴
- 后期接权限、埋点、动作会失控

## 推荐包装层 API 形态
为了避免每个业务模块都随意设计一套接入方式，建议 `8.1.10.beta` 先收口一个统一的包装层 API 形态。

### 1. 生成组件的职责
生成组件保持“薄组件”定位，建议只暴露与通用表格能力直接相关的输入：

- `globalParams`
- `customParams`
- `initialSlices`
- `tableOverrides`
- `columnOverrides`
- `queryHooks`
- `lookupAdapters`

生成组件不直接暴露业务按钮、业务弹窗、业务状态机这类页面能力。

### 2. 包装层的职责
业务包装层负责：

- 读取路由、store、宿主上下文
- 解析权限与场景参数
- 组装 `globalParams` / `customParams`
- 合并列覆盖和 table 覆盖
- 挂接 toolbar、row actions、query hooks
- 对页面暴露更稳定、更贴近业务的 props

### 3. 推荐的包装层 props
建议业务包装层对页面暴露类似如下接口：

```ts
interface QmBusinessModuleProps {
  globalParams?: Record<string, unknown>
  customParams?: Record<string, unknown>
  initialSlices?: SliceRequestDef[]
  tableOverrides?: Partial<TableConfig>
  columnOverrides?: Record<string, BusinessColumnOverride>
  queryHooks?: QueryHooks
}

interface BusinessColumnOverride {
  title?: string
  width?: number | string
  hidden?: boolean
  order?: number
  formatter?: unknown
  uiConfig?: Record<string, unknown>
}
```

说明：

- 页面层只传“业务意图”，不直接改 generated schema 文件
- 包装层内部再把这些 props 适配成生成组件可识别的最终输入

### 4. 推荐的包装层文件形态
每个业务模块建议至少拆成：

- `XxxListModule.vue`
- `xxx-module.config.ts`
- `xxx-query-hooks.ts`

其中：

- `XxxListModule.vue` 负责组合 generated 组件和业务插槽
- `xxx-module.config.ts` 负责静态列覆盖、默认参数、动作声明
- `xxx-query-hooks.ts` 负责查询前后处理、埋点、错误处理

## 参数合并规则
为了避免后续每个模块都自行决定“谁覆盖谁”，这里先定统一规则。

### 1. globalParams
推荐合并顺序：

1. 宿主层提供的基础全局参数
2. 业务模块默认全局参数
3. 页面传入的 `globalParams`
4. `beforeQuery` hook 最终 patch

规则：

- 后者覆盖前者的同名 key
- 普通对象按浅合并处理
- 数组直接替换，不做拼接合并
- 权限、租户、命名空间这类安全上下文字段不应允许页面层随意覆盖

### 2. customParams
推荐合并顺序：

1. 生成组件默认 custom 参数
2. 业务模块默认 custom 参数
3. 页面传入的 `customParams`
4. `beforeQuery` hook 最终 patch

规则：

- 后者覆盖前者
- 普通对象浅合并
- 数组替换
- `customParams` 只承载模型或页面专属参数，不与 `globalParams` 混用

### 3. initialSlices
`initialSlices` 不与 generated 默认筛选做深度自动合并，推荐规则是：

- 生成组件如有默认 slices，先作为 base
- 页面传入的 `initialSlices` 按 `field + op` 维度覆盖同类条件
- 无法判定等价关系的 slice 直接追加

原因：

- slice 是 DSL 结构，不适合做隐式深合并
- 明确覆盖维度比“猜测如何合并”更可控

## 列覆盖规则

### 1. 基本原则
- 列覆盖按 `column.name` 为主键
- 不按数组下标覆盖
- 不要求业务系统复制整份 schema 再修改

### 2. 推荐合并顺序
1. generated schema 列定义
2. 业务模块默认列覆盖
3. 页面传入的 `columnOverrides`

### 3. 字段级覆盖规则
对于同名列，按以下规则处理：

- 标量字段：后者覆盖前者
- `uiConfig`：浅合并
- `hidden = true`：该列最终不展示，但原始 schema 仍保留
- `order`：仅用于最终展示排序，不回写 generated schema

### 4. 新增列与附加列
如业务需要附加非 QM 原生列，建议通过包装层追加，而不是修改 generated schema。

规则：

- 附加列默认追加到末尾
- 如指定 `order`，则参与最终排序
- 附加列应显式标识为业务列，避免与 QM 原生列混淆

### 5. 禁止方式
以下方式不建议进入正式规范：

- 页面直接复制 generated schema 数组后随意改
- 基于数组下标做列覆盖
- 在多个页面各自维护一份几乎相同的 schema 副本

## tableOverrides 规则
`tableOverrides` 用于承接表格级别的轻量覆盖，例如：

- 分页大小
- 默认高度
- 是否显示工具栏
- 局部展示配置

规则：

- 只做浅合并
- 不承载列级覆盖
- 不承载业务权限逻辑
- 如果某配置会影响 query 协议，应优先回到生成器或 schema 契约层讨论

## 组件必须预留的最小扩展点
`8.1.10.beta` 建议至少保证以下扩展点存在：

- `toolbar` 插槽
- 行动作渲染扩展
- 列配置覆盖入口
- 全局参数入口
- custom 参数入口
- query hooks
- `qmModel` 透传
- 远程 lookup adapter 注入入口
- 树表/树形过滤所需结构的消费能力

## 与运行时 adapter 的关系
业务系统不应直接访问底层成员检索接口。

相关运行时能力由下列文档承接：

- [P1-DataViewer维度成员实时过滤-需求.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-需求.md)
- [P1-DataViewer维度成员实时过滤-设计收口.md](/D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/docs/8.1.10.beta/P1-QM前端生成与业务接入/P1-DataViewer维度成员实时过滤-设计收口.md)

这意味着业务系统使用规范会反向影响组件设计，但不会直接定义底层 adapter 协议。

## 当前讨论结论
这份文档当前先锁三件事：

- 生成组件不能作为业务最终层，必须有业务包装层
- 组件和生成器必须围绕“可包装、可扩展、可重生成”设计
- 业务系统使用规范必须参与前端组件设计讨论，不能等生成器做完再补
- 包装层 API、参数合并规则、列覆盖规则应优先标准化

## 待继续讨论项
- 全局参数与 custom 参数的最终字段结构
- 字典与维度成员 adapter 在业务系统中的注入方式
- query hooks 的声明式和运行时注入边界
