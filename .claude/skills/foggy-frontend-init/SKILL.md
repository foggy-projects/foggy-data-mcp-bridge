---
name: foggy-frontend-init
description: 初始化前端项目的 Foggy 开发环境。安装依赖、创建配置文件、生成公共 API。当用户首次在前端项目中使用 Foggy 组件或查询 API 时使用。
---

# Foggy Frontend Init

初始化前端项目的 Foggy 开发环境（仅需执行一次）。

## 执行流程

### 1. 检查项目类型
确认 `package.json` 存在。

### 2. 安装依赖

```bash
npm install foggy-data-viewer@beta axios vxe-table vxe-pc-ui xe-utils element-plus
```

### 3. 创建配置文件

**`.claude/config/semantic-api.config.json`**（询问用户）：
```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

### 4. 生成公共 DSL 查询 API

**`src/apis/common/dslQuery.ts`**：
```typescript
import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7108'
const DEFAULT_NAMESPACE = import.meta.env.VITE_NAMESPACE || 'default'

export interface SliceRequestDef {
  field: string
  op: '=' | '!=' | '>' | '>=' | '<' | '<=' | 'in' | 'not in' | 'like' | 'is null' | 'is not null' | '[]' | '[)'
  value?: any
}

export interface OrderRequestDef {
  field: string
  dir?: 'asc' | 'desc'
}

export interface DslQueryRequest {
  page?: number
  pageSize?: number
  param: {
    columns?: string[]
    slice?: SliceRequestDef[]
    orderBy?: (string | OrderRequestDef)[]
  }
}

export async function dslQuery<T = any>(
  modelName: string,
  request: DslQueryRequest,
  options?: { namespace?: string; authorization?: string }
) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-NS': options?.namespace || DEFAULT_NAMESPACE,
  }
  if (options?.authorization) headers['Authorization'] = options.authorization

  const response = await axios.post(
    `${API_BASE_URL}/jdbc-model/query-model/v2/${modelName}`,
    request,
    { headers }
  )
  if (response.data.code !== 200 && response.data.code !== 0) {
    throw new Error(response.data.msg || '查询失败')
  }
  return response.data
}

export async function query<T = any>(
  modelName: string,
  options: {
    columns?: string[]
    filters?: SliceRequestDef[]
    orderBy?: (string | OrderRequestDef)[]
    page?: number
    pageSize?: number
  }
): Promise<{ items: T[]; total: number }> {
  const result = await dslQuery<T>(modelName, {
    page: options.page || 1,
    pageSize: options.pageSize || 20,
    param: { columns: options.columns, slice: options.filters, orderBy: options.orderBy }
  })
  return { items: result.data.items, total: result.data.total }
}
```

### 5. 配置应用入口（⚠️ 必需）

检查并修改 `src/main.js` 或 `src/main.ts`：

```javascript
import { createApp } from 'vue'
import VxeUI from 'vxe-pc-ui'        // ⬅️ 必需 (vxe-table v4.7+)
import VXETable from 'vxe-table'     // ⬅️ 必需
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'foggy-data-viewer/style.css' // ⬅️ 必需
import App from './App.vue'

const app = createApp(App)
app.use(VxeUI)      // ⬅️ 必须在 VXETable 之前
app.use(VXETable)   // ⬅️ 必需
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
```

**检测要点**：
- `import VxeUI from 'vxe-pc-ui'`
- `import VXETable from 'vxe-table'`
- `import 'foggy-data-viewer/style.css'`
- `app.use(VxeUI)` 在 `app.use(VXETable)` 之前

### 6. 输出总结

```
✅ Foggy 前端环境初始化完成！

📦 已安装: foggy-data-viewer, vxe-table, vxe-pc-ui, xe-utils, axios
📁 已创建: .claude/config/semantic-api.config.json, src/apis/common/dslQuery.ts
✅ 已配置: VxeUI + VXETable 全局注册

🚀 下一步:
  - /frontend-component-generator 生成数据表格组件
  - /qm-schema-viewer 查看可用的数据模型
```

## 决策规则

- 依赖已安装 → 跳过安装
- 配置已存在 → 询问是否覆盖
- VxeUI/VXETable 未注册 → 显示警告并提供代码
