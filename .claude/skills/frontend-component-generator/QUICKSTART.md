# Frontend Component Generator - 快速参考

## 三行代码快速理解

1. **输入**: QM 模型名称 OR 列需求描述
2. **处理**: 拉取 schema → 生成代码 → 创建配置 → 生成文档
3. **输出**: 4 个文件（组件 + schema + API + 文档）

## 生成的组件结构

```
┌─────────────────────────────────────────────────┐
│  [toolbar插槽-按钮等]  [共xxx条] [分页组件▶◀]  │  <- 顶部工具栏
├─────────────────────────────────────────────────┤
│                                                 │
│          DataTable + SearchToolbar              │
│                                                 │
└─────────────────────────────────────────────────┘
```

**特点**：
- ✅ 顶部 toolbar（靠左，支持插槽添加按钮）
- ✅ 右侧分页控件（显示总数、页码、每页条数）
- ✅ 中间搜索+表格（支持多条件筛选、排序）

## 最常见的用法

### 场景 A：我知道模型名称

```
用户：根据 order_query_model 生成订单表格组件
输入：
- 模型名：order_query_model
- 组件名：OrderQueryTable
- 描述：订单查询表格

输出：
src/components/models/
├── OrderQueryTable.vue
├── schemas/order-query-table.schema.ts
├── apis/order-query-table.api.ts
└── README.md
```

### 场景 B：我不知道模型，只知道需要的列

```
用户：我需要一个包含【订单ID、订单号、金额、状态、创建时间】的表格
步骤1：技能调用 metadata API 搜索
步骤2：返回候选模型给用户选择（最多 5 个）
步骤3：用户选择后，继续生成流程

自动推荐模型，用户只需选择！
```

## 关键文件速查

| 文件 | 位置 | 用途 |
|------|------|------|
| 主组件 | `src/{path}/models/{Name}.vue` | 包含所有业务逻辑 |
| schema | `src/{path}/models/schemas/{name}.schema.ts` | 列定义 + 类型 |
| API | `src/{path}/models/apis/{name}.api.ts` | 后台接口封装 |
| 文档 | `src/{path}/models/README.md` | 使用说明 + 示例 |

## 配置文件

**第一次使用时**自动创建：`.claude/config/component-generator.config.json`

```json
{
  "apiBaseUrl": "http://localhost:8080",        // 后台 API 地址
  "namespace": "default",                        // 命名空间
  "commonComponentPath": "components",           // 组件存放目录
  "componentAuthor": "Frontend Team",           // 作者
  "authorization": ""                            // 授权 token（可选，留空则不使用）
}
```

后续使用自动读取，可随时编辑。

**authorization 字段说明**：
- 留空或不设置 → 不使用授权
- 设置值（如 `"Bearer xxx"`）→ 在所有 API 请求中添加 Authorization header

## 决策树

```
用户请求 → 有配置文件？
         ├─ 是 → 直接使用
         └─ 否 → 询问用户并保存

用户提供模型信息？
├─ 已知模型名称 → 调用 description-model-internal
└─ 只有列需求 → 调用 metadata 推荐模型 → 用户选择 → 调用 description-model-internal

获取 schema → 用户确认生成配置
├─ 选择显示的列
├─ 选择组件类型（默认 DataTableWithSearch）
├─ 输入组件名称
└─ 输入组件描述

生成 4 个文件 → 显示完成信息
```

## API 接口一览

| 接口 | 用途 | 何时调用 |
|------|------|--------|
| `/mcp/analyst/metadata` | 搜索匹配的模型 | 用户只提供列需求时 |
| `/mcp/analyst/description-model-internal` | 获取完整 schema | 确定模型后 |
| `/jdbc-model/query-model/v2` | 查询数据 | 生成的组件运行时 |

## 生成的组件工作流程

```
①  组件挂载 → 调用 loadData()
②  loadData() → 调用 API → 更新 data + total
③  用户分页/筛选 → 调用 handlePageChange/handleFilterChange
④  重新调用 loadData()
⑤  返回 ②

用户可以：
- 调用 ref.refresh() 手动刷新
- 调用 ref.clearFilters() 清空筛选
- 通过插槽 #toolbar 添加自定义按钮
```

## Toolbar 插槽使用

生成的组件在顶部左侧提供 `toolbar` 插槽，你可以添加自定义操作按钮：

```vue
<template>
  <OrderQueryTable>
    <template #toolbar>
      <!-- 在这里添加按钮 -->
      <el-button type="primary" size="small">新增</el-button>
      <el-button type="default" size="small">编辑</el-button>
      <el-button type="danger" size="small">删除</el-button>
    </template>
  </OrderQueryTable>
</template>
```

按钮会自动排列在顶部，分页组件自动靠右。

## 常见 TypeScript 类型

所有生成的组件都遵循这些类型：

```typescript
// 列定义（foggy-data-viewer）
interface EnhancedColumnSchema {
  name: string              // 字段名
  type: string              // 数据类型
  title: string             // 显示标题
  width?: number
  filterable?: boolean
  filterType?: 'text' | 'number' | 'date' | 'select' | 'bool'
}

// 查询请求（生成的 API 层）
interface QueryRequest {
  page: number
  pageSize: number
  filters?: SliceRequestDef[]  // foggy-data-viewer 定义
  sort?: { field: string; order: 'asc' | 'desc' }
}

// 查询响应（生成的 API 层）
interface QueryResponse {
  rows: any[]
  total: number
}
```

## 环境配置

在 `.env` 中配置接口地址和授权信息：

```env
VUE_APP_API_BASE=http://localhost:8080
VUE_APP_NAMESPACE=default
VUE_APP_AUTHORIZATION=                          # 可选，如需要授权填入 token
```

生成的 API 层会自动读取这些变量。

**环境变量说明**：
- `VUE_APP_API_BASE` - SemanticController API 地址
- `VUE_APP_NAMESPACE` - 命名空间，通过 X-NS header 传递
- `VUE_APP_AUTHORIZATION` - 授权 token（可选），如果不需要可留空或删除

## 生成的组件有什么能力？

✅ 分页 + 排序 + 筛选
✅ 搜索工具栏（独立字段快速搜索）
✅ 表头筛选器（多种类型）
✅ 数据汇总行（可选）
✅ 自定义 toolbar（左侧插槽添加按钮）
✅ **操作列**（右侧固定，通过插槽自定义 编辑/删除 等按钮，`:show-oper-column` 控制显示）
✅ 响应式加载状态
✅ 完整的 TypeScript 类型
✅ 错误处理
✅ 开箱即用

❌ 不包含：数据导出、编辑、删除等写操作（需要通过 operColumn 插槽自行实现）

## 下一步

1. **第一次使用**: 提供 API 地址等配置
2. **生成组件**: 给出模型名称或列需求
3. **使用组件**: 在页面中导入并使用
4. **自定义**: 编辑 schema 和 API 层调整

最常见的自定义：
- 修改列宽 → 编辑 schema.ts
- 修改显示列 → 编辑 schema.ts
- 修改格式化方式 → 编辑 schema.ts 的 formatter
- 修改 API 逻辑 → 编辑 api.ts

所有生成的代码都是**可完全自定义的**，不受技能约束。
