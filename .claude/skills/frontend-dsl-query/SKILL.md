---
name: frontend-dsl-query
description: 引导前端开发使用 DSL 直接查询数据。生成公共查询 API 和业务封装 API。当用户需要在前端查询数据、生成查询接口时使用。
---

# Frontend DSL Query

引导前端开发人员使用语义层 DSL 查询数据，生成可复用的查询 API。

## 使用场景

当用户需要以下操作时使用：
- 根据业务需求封装查询 API（如 `getUserById`、`getOrderList`）
- 了解 DSL 查询语法和使用方式

## 前置条件

使用本技能前，请确保已运行 `foggy-frontend-init` 完成环境初始化：
- 已安装 `foggy-data-viewer@beta` 和 `axios`
- 已创建 `.claude/config/semantic-api.config.json`
- 已生成 `src/apis/common/dslQuery.ts`

如果环境未就绪，请先运行 `/foggy-frontend-init`。

## 生成的文件结构

```
src/apis/
├── common/
│   └── dslQuery.ts       # 公共 DSL 查询 API（由 foggy-frontend-init 生成）
└── query/                # 业务封装 API 目录（本技能生成）
    ├── userQuery.ts      # 用户相关查询
    └── orderQuery.ts     # 订单相关查询
```

## 执行流程

### 第一步：检查环境

快速检查（不安装，仅验证）：
- 检查 `src/apis/common/dslQuery.ts` 是否存在
- 检查 `.claude/config/semantic-api.config.json` 是否存在

**如果缺失** → 提示用户先运行 `/foggy-frontend-init`

### 第二步：确定 API 目录

1. 默认使用 `src/apis/query/`
2. 如果项目使用其他目录结构（如 `src/services`、`src/api`），询问用户确认

## 公共 API 参考

公共 API 由 `foggy-frontend-init` 生成，位于 `src/apis/common/dslQuery.ts`：

```typescript
// src/apis/common/dslQuery.ts
import axios from 'axios'

// 配置（可从环境变量或配置文件读取）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7108'
const DEFAULT_NAMESPACE = import.meta.env.VITE_NAMESPACE || 'default'

// DSL 查询请求类型
export interface SliceRequestDef {
  field: string
  op: '=' | '!=' | '>' | '>=' | '<' | '<=' | 'in' | 'not in' | 'like' | 'left_like' | 'right_like' | 'is null' | 'is not null' | '[]' | '[)' | '(]' | '()'
  value?: any
}

export interface OrderRequestDef {
  field: string
  dir?: 'asc' | 'desc'
}

export interface WindowOrderDef {
  field: string
  dir?: 'asc' | 'desc'
}

export interface CalculatedFieldDef {
  name: string
  caption?: string
  expression: string
  agg?: 'SUM' | 'AVG' | 'COUNT' | 'COUNT_DISTINCT' | 'COUNTD' | 'MAX' | 'MIN'
  partitionBy?: string[]
  windowOrderBy?: WindowOrderDef[]
  windowFrame?: string
}

export interface DslQueryParam {
  columns?: string[]
  slice?: (SliceRequestDef | { $or: SliceRequestDef[] } | { $and: SliceRequestDef[] })[]
  groupBy?: (string | { field: string; agg?: string })[]
  orderBy?: (string | OrderRequestDef)[]
  calculatedFields?: CalculatedFieldDef[]
  returnTotal?: boolean
}

export interface DslQueryRequest {
  page?: number
  pageSize?: number
  start?: number
  limit?: number
  param: DslQueryParam
}

export interface DslQueryResponse<T = any> {
  code: number
  msg: string
  data: {
    items: T[]
    total: number
    totalData?: Record<string, any>
  }
}

/**
 * 执行 DSL 查询
 * @param modelName QM 模型名称
 * @param request DSL 查询请求
 * @param options 可选配置
 */
export async function dslQuery<T = any>(
  modelName: string,
  request: DslQueryRequest,
  options?: {
    namespace?: string
    authorization?: string
  }
): Promise<DslQueryResponse<T>> {
  const namespace = options?.namespace || DEFAULT_NAMESPACE

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-NS': namespace,
  }

  if (options?.authorization) {
    headers['Authorization'] = options.authorization
  }

  const response = await axios.post<DslQueryResponse<T>>(
    `${API_BASE_URL}/jdbc-model/query-model/v2/${modelName}`,
    request,
    { headers }
  )

  if (response.data.code !== 200 && response.data.code !== 0) {
    throw new Error(response.data.msg || '查询失败')
  }

  return response.data
}

/**
 * 简化的查询方法
 */
export async function query<T = any>(
  modelName: string,
  options: {
    columns?: string[]
    filters?: SliceRequestDef[]
    orderBy?: (string | OrderRequestDef)[]
    page?: number
    pageSize?: number
    namespace?: string
  }
): Promise<{ items: T[]; total: number }> {
  const result = await dslQuery<T>(modelName, {
    page: options.page || 1,
    pageSize: options.pageSize || 20,
    param: {
      columns: options.columns,
      slice: options.filters,
      orderBy: options.orderBy,
    }
  }, { namespace: options.namespace })

  return {
    items: result.data.items,
    total: result.data.total,
  }
}
```

### 第三步：了解用户需求

询问用户要生成的业务 API：
- 查询哪个模型？（使用 qm-schema-viewer 查看可用模型）
- 接收哪些参数？（如 userId、startDate、endDate）
- 返回哪些字段？
- 是否需要分页？
- API 函数名称？

### 第四步：生成业务封装 API

根据用户需求生成业务 API 文件。

#### 示例：根据 userId 查询用户信息

用户需求：根据 userId 查询用户基本信息

生成文件 `src/apis/query/userQuery.ts`：

```typescript
// src/apis/query/userQuery.ts
import { dslQuery, query, type SliceRequestDef } from '../common/dslQuery'

// 用户信息类型（根据 schema 生成）
export interface UserInfo {
  userId: number
  userName: string
  email: string
  phone: string
  createTime: string
  status: string
}

/**
 * 根据用户ID查询用户信息
 * @param userId 用户ID
 */
export async function getUserById(userId: number): Promise<UserInfo | null> {
  const result = await query<UserInfo>('UserQueryModel', {
    columns: ['userId', 'userName', 'email', 'phone', 'createTime', 'status'],
    filters: [
      { field: 'userId', op: '=', value: userId }
    ],
    pageSize: 1,
  })

  return result.items[0] || null
}

/**
 * 查询用户列表
 * @param params 查询参数
 */
export async function getUserList(params: {
  userName?: string
  status?: string
  page?: number
  pageSize?: number
}): Promise<{ items: UserInfo[]; total: number }> {
  const filters: SliceRequestDef[] = []

  if (params.userName) {
    filters.push({ field: 'userName', op: 'like', value: params.userName })
  }

  if (params.status) {
    filters.push({ field: 'status', op: '=', value: params.status })
  }

  return query<UserInfo>('UserQueryModel', {
    columns: ['userId', 'userName', 'email', 'phone', 'createTime', 'status'],
    filters,
    orderBy: ['-createTime'],
    page: params.page || 1,
    pageSize: params.pageSize || 20,
  })
}
```

### 第五步：输出使用示例

```typescript
// 使用示例

// 1. 查询单个用户
const user = await getUserById(12345)
console.log(user?.userName)

// 2. 查询用户列表
const { items, total } = await getUserList({
  userName: '张',
  status: 'active',
  page: 1,
  pageSize: 20,
})

// 3. 直接使用 DSL 查询（高级用法）
import { dslQuery } from '@/apis/common/dslQuery'

const result = await dslQuery('UserQueryModel', {
  page: 1,
  pageSize: 50,
  param: {
    columns: ['userId', 'userName', 'totalAmount'],
    slice: [
      { field: 'status', op: '=', value: 'active' },
      { field: 'createTime', op: '[)', value: ['2024-01-01', '2024-12-31'] }
    ],
    groupBy: ['status'],
    orderBy: ['-totalAmount'],
  }
})
```

## 输入要求

**必需输入**：
- 业务需求描述（如"根据 userId 查询用户信息"）
- 或指定模型名称和查询参数

**可选输入**：
- API 存放目录（默认 `src/apis/query`）
- 函数命名风格
- 是否生成 TypeScript 类型

## 输出格式

```
✅ 查询 API 生成完成！

📁 生成的文件：
  - src/apis/common/dslQuery.ts (公共 API，已存在则跳过)
  - src/apis/query/{businessName}Query.ts

🚀 使用示例：
  import { getUserById, getUserList } from '@/apis/query/userQuery'

  const user = await getUserById(12345)
  const { items, total } = await getUserList({ status: 'active' })

📖 DSL 语法参考：
  详见 docs-site/zh/dataset-model/tm-qm/query-dsl.md
```

## ⚠️ 重要提示

### 请求参数结构

Foggy Dataset Model API 要求的请求参数结构：

```json
{
  "param": {              // ⬅️ 查询条件必须包裹在 param 对象内
    "columns": [...],
    "slice": [...],
    "orderBy": [...]
  },
  "page": 1,             // ⬅️ 分页参数在外层
  "pageSize": 50
}
```

**常见错误** ❌:
```json
{
  "columns": [...],      // ❌ 错误：直接平铺参数
  "slice": [...],
  "page": 1,
  "pageSize": 50
}
```

### API 端点

- **正确**: `/jdbc-model/query-model/v2/{QueryModelName}`
- **错误**: `/mcp/analyst/query-model/v2/{QueryModelName}`

## DSL 语法快速参考

### 过滤条件操作符

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `=` | 等于 | `{ field: 'status', op: '=', value: 'active' }` |
| `!=` | 不等于 | `{ field: 'status', op: '!=', value: 'deleted' }` |
| `in` | 包含于 | `{ field: 'status', op: 'in', value: ['a', 'b'] }` |
| `like` | 模糊匹配 | `{ field: 'name', op: 'like', value: '张' }` |
| `[)` | 左闭右开区间 | `{ field: 'date', op: '[)', value: ['2024-01-01', '2024-12-31'] }` |
| `is null` | 为空 | `{ field: 'email', op: 'is null' }` |

### 字段引用格式

| 格式 | 说明 |
|------|------|
| `fieldName` | 直接属性 |
| `dimension$id` | 维度ID |
| `dimension$caption` | 维度显示值 |
| `dimension$property` | 维度属性 |

### 排序简写

| 格式 | 说明 |
|------|------|
| `'fieldName'` | 升序 |
| `'-fieldName'` | 降序 |
| `{ field: 'name', dir: 'desc' }` | 完整格式 |

## 约束条件

- 公共 API 文件位置固定：`src/apis/common/dslQuery.ts`
- 业务 API 目录可配置（默认 `src/apis/query`）
- 需要先通过 qm-schema-viewer 了解模型 schema
- 生成的代码需要项目已安装 axios

## 决策规则

- 如果公共 API 已存在 → 跳过生成，直接复用
- 如果业务 API 已存在 → 询问用户是否覆盖或追加函数
- 如果用户未指定模型 → 使用 qm-schema-viewer 搜索合适的模型
- 如果用户未指定字段 → 查询 schema 后推荐常用字段
- 如果项目使用 fetch 而非 axios → 提供 fetch 版本的公共 API
- 如果用户需要去重计数/窗口函数/移动平均 → 读取 `references/advanced-query-patterns.md`

## 依赖技能

- `qm-schema-viewer` - 获取模型 schema 信息
- `dsl-syntax-guide` - DSL 查询语法参考

## 配置文件

复用 `.claude/config/semantic-api.config.json`：

```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": "",
  "queryApiPath": "src/apis/query"
}
```
