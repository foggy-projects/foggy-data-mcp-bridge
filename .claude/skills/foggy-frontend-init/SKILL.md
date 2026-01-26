---
name: foggy-frontend-init
description: 初始化前端项目的 Foggy 开发环境。安装依赖、创建配置文件、生成公共 API。当用户首次在前端项目中使用 Foggy 组件或查询 API 时使用。
---

# Foggy Frontend Init

初始化前端项目的 Foggy 开发环境，包括安装依赖、创建配置文件、生成公共查询 API。

## 使用场景

当用户需要以下操作时使用（通常只需执行一次）：
- 首次在前端项目中集成 Foggy 组件
- 安装 foggy-data-viewer 和相关依赖
- 创建 Semantic API 配置文件
- 生成公共 DSL 查询 API

## 执行流程

### 第一步：检查项目类型

确认是前端项目：
- 检查 `package.json` 是否存在
- 检查是否为 Vue/React/其他框架项目

### 第二步：安装 npm 依赖

检查并安装必需依赖：

```bash
# 检查 foggy-data-viewer 是否已安装
grep "foggy-data-viewer" package.json
```

**如果未安装**：

1. 从 npm 获取最新版本信息：
   ```bash
   npm view foggy-data-viewer versions --json
   ```

2. 安装依赖（推荐 beta 版）：
   ```bash
   npm install foggy-data-viewer@beta axios
   ```

**依赖列表**：

| 包名 | 说明 | 版本 |
|------|------|------|
| `foggy-data-viewer` | 数据表格组件库 | `@beta` |
| `axios` | HTTP 请求库 | `^1.x` |
| `element-plus` | UI 组件库（可选，Vue 项目） | `^2.x` |

### 第三步：创建配置文件

创建 `.claude/config/semantic-api.config.json`：

```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

询问用户：
1. **API 地址**（默认 `http://localhost:7108`）
2. **命名空间**（默认 `default`）
3. **授权信息**（可选）

### 第四步：创建组件生成器配置（可选）

如果用户计划使用 `frontend-component-generator`，创建 `.claude/config/component-generator.config.json`：

```json
{
  "commonComponentPath": "components",
  "componentAuthor": "Frontend Team"
}
```

### 第五步：生成公共 DSL 查询 API

检查并生成 `src/apis/common/dslQuery.ts`：

```typescript
// src/apis/common/dslQuery.ts
import axios from 'axios'

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

export interface CalculatedFieldDef {
  name: string
  caption?: string
  expression: string
  agg?: 'SUM' | 'AVG' | 'COUNT' | 'MAX' | 'MIN'
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

### 第六步：配置应用入口文件（⚠️ 必需）

**重要提示**: foggy-data-viewer 底层依赖 VXETable 表格引擎，必须在应用启动时全局注册，否则表格组件无法显示。

检查并配置 `src/main.js` 或 `src/main.ts`：

#### 检测步骤

1. 读取 `src/main.js` 或 `src/main.ts`
2. 检查是否包含以下关键配置：
   - `import VXETable from 'vxe-table'`
   - `import 'foggy-data-viewer/style.css'`
   - `app.use(VXETable)`

#### 如果缺失配置，询问用户是否自动添加

**检测结果示例**:
```
⚠️ 检测到缺少 VXETable 配置

foggy-data-viewer 依赖 VXETable 作为底层表格引擎，需要在应用启动时全局注册。

当前缺失：
- ❌ 未导入 VXETable
- ❌ 未导入 foggy-data-viewer 样式
- ❌ 未注册 VXETable 插件

建议操作：
是否自动修改 src/main.js 添加必要配置？[Y/n]
```

#### Vue 3 + Vite 项目配置模板

```javascript
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import VXETable from 'vxe-table'                    // ⬅️ 必需
import 'foggy-data-viewer/style.css'                // ⬅️ 必需
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 必需：注册 VXETable（foggy-data-viewer 的底层表格引擎）
app.use(VXETable)                                   // ⬅️ 必需

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
```

#### 必需的依赖包

确保 `package.json` 中包含以下依赖：

```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "element-plus": "^2.4.0",
    "foggy-data-viewer": "^1.0.1-beta.0",
    "vxe-table": "^4.7.0",                          // ⬅️ 必需
    "vxe-pc-ui": "^4.2.0",                          // ⬅️ 必需
    "xe-utils": "^3.5.0",                           // ⬅️ 必需
    "axios": "^1.6.0"
  }
}
```

**如果缺少依赖，自动安装**:
```bash
npm install vxe-table vxe-pc-ui xe-utils
```

#### 配置说明

| 配置项 | 说明 | 是否必需 |
|-------|------|---------|
| `import VXETable from 'vxe-table'` | 导入 VXETable | ✅ 必需 |
| `import 'foggy-data-viewer/style.css'` | 导入组件样式 | ✅ 必需 |
| `app.use(VXETable)` | 全局注册 VXETable | ✅ 必需 |
| `locale: zhCn` | Element Plus 中文语言包 | 推荐 |

### 第七步：添加环境变量模板（可选）

如果项目使用 Vite，建议在 `.env.example` 中添加：

```env
# Foggy Semantic API
VITE_API_BASE_URL=http://localhost:7108
VITE_NAMESPACE=default
```

### 第八步：输出总结

```
✅ Foggy 前端环境初始化完成！

📦 已安装依赖：
  - foggy-data-viewer@beta
  - vxe-table（表格引擎）
  - vxe-pc-ui、xe-utils
  - axios

📁 已创建/修改文件：
  - .claude/config/semantic-api.config.json
  - src/apis/common/dslQuery.ts
  - src/main.js（已配置 VXETable）

✅ 已完成配置：
  - VXETable 全局注册
  - foggy-data-viewer 样式导入
  - Element Plus 中文语言包

🚀 下一步：
  1. 使用 /frontend-dsl-query 生成业务查询 API
  2. 使用 /frontend-component-generator 生成数据表格组件
  3. 使用 /qm-schema-viewer 查看可用的数据模型

📖 DSL 查询示例：
  import { query } from '@/apis/common/dslQuery'

  const { items, total } = await query('UserQueryModel', {
    columns: ['userId', 'userName'],
    filters: [{ field: 'status', op: '=', value: 'active' }],
    page: 1,
    pageSize: 20,
  })
```

## 输入要求

用户需提供（或使用默认值）：
- API 地址（默认 `http://localhost:7108`）
- 命名空间（默认 `default`）
- 授权信息（可选）

## 约束条件

- 必须是前端项目（存在 `package.json`）
- 公共 API 文件位置固定：`src/apis/common/dslQuery.ts`
- 配置文件位置固定：`.claude/config/semantic-api.config.json`

## 决策规则

- 如果依赖已安装 → 跳过安装步骤
- 如果配置已存在 → 询问是否覆盖或跳过
- 如果公共 API 已存在 → 跳过生成
- 如果不是 npm 项目 → 提示用户手动安装依赖

## 与其他技能的关系

本技能是前置技能，完成后可使用：
- `frontend-dsl-query` - 生成业务查询 API
- `frontend-component-generator` - 生成数据表格组件
- `qm-schema-viewer` - 查看模型 schema

## ⚠️ 常见问题

### Q1: 表格不显示，但 API 返回数据正常，控制台无报错？

**原因**: VXETable 未全局注册或样式未导入。

**解决方法**:

检查 `src/main.js` 是否包含：

```javascript
import VXETable from 'vxe-table'
import 'foggy-data-viewer/style.css'

app.use(VXETable)
```

如果缺失，请添加上述代码并重启开发服务器。

---

### Q2: 提示 "Cannot find module 'vxe-table'"？

**原因**: 缺少必需依赖包。

**解决方法**:

```bash
npm install vxe-table vxe-pc-ui xe-utils
```

---

### Q3: 表格样式错乱或显示不正常？

**原因**: 缺少样式文件导入。

**解决方法**:

确保 `src/main.js` 中包含：

```javascript
import 'foggy-data-viewer/style.css'
```

---

### Q4: Element Plus 组件显示英文？

**原因**: 未配置中文语言包。

**解决方法**:

```javascript
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

app.use(ElementPlus, { locale: zhCn })
```

---

### Q5: 初始化后启动项目报错？

**原因**: 可能是依赖版本冲突或 Node.js 版本过低。

**解决方法**:

1. 确保 Node.js 版本 >= 16
2. 删除 `node_modules` 和 `package-lock.json`
3. 重新安装：`npm install`
4. 如仍有问题，检查控制台具体错误信息
