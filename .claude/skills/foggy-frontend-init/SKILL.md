---
name: foggy-frontend-init
description: 初始化前端项目的 Foggy 组件接入环境。安装 foggy-data-viewer 运行依赖、注册 VxeUI/VXETable、补齐基础配置，并在需要时代码生成链路接好。当用户首次在前端项目中使用 Foggy 前端组件体系时使用。
---

# Foggy Frontend Init

初始化业务前端项目，使其具备接入 `foggy-data-viewer` 和生成组件代码的条件。

## 适用边界

适用：

- Vue 3 项目首次接入 Foggy 前端组件体系
- 需要使用 `DataTableWithSearch`、`QueryPanel`、生成器产物
- 需要跑 `frontend-meta v1 / members/query / query/create` 相关链路

不适用：

- 非 Vue 项目
- 只想直接调老的 `/jdbc-model/query-model/v2/*`

## 初始化目标

完成后至少具备：

- `foggy-data-viewer` 运行依赖
- `VxeUI`、`VXETable`、`ElementPlus` 注册完成
- 样式正确引入
- 可配置服务地址
- 可以承接 `frontend-component-generator` 生成的代码

## 工作流

### 1. 检查项目

确认：

- 存在 `package.json`
- 项目是 Vue 3
- 入口文件是 `src/main.ts` 或 `src/main.js`

### 2. 安装依赖

根据项目所处阶段选择安装方式。**优先询问用户当前阶段**，如未明确则默认联调阶段。

#### 联调阶段（推荐）— npm beta 或本地 link

```bash
# 方式 A：npm beta 版（推荐）
npm install foggy-data-viewer@beta

# 方式 B：本地 link（频繁迭代时推荐，改动即时生效）
cd /path/to/foggy-data-mcp-bridge/addons/foggy-data-viewer/frontend
npm link
cd /path/to/your-business-project
npm link foggy-data-viewer

# 安装 peer 依赖
npm install vxe-pc-ui vxe-table xe-utils element-plus axios
```

> **⚠️ 不推荐 GitHub 整库安装**：`foggy-data-viewer` 是 Java monorepo (`foggy-data-mcp-bridge`) 的子目录，
> `npm install github:foggy-projects/foggy-data-mcp-bridge#branch` 会下载整个 Java 项目（>100MB），
> 且 `dist/` 不在源码中，需要额外配置 Vite alias。请使用 npm 包或本地 link。

#### 稳定阶段 — npm 正式版

```bash
# 正式版
npm install foggy-data-viewer
```

切换时只需改 `package.json` 中的 `foggy-data-viewer` 版本号，其余代码无需调整。

### 3. 配置应用入口

确保入口文件包含：

```typescript
import VxeUI from 'vxe-pc-ui'
import 'vxe-pc-ui/lib/style.css'
import VxeTable from 'vxe-table'
import 'vxe-table/lib/style.css'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
```

并按顺序注册：

```typescript
app.use(VxeUI)      // 必须在 VxeTable 之前，否则分页器/工具提示不可用
app.use(VxeTable)
app.use(ElementPlus, { locale: zhCn })
```

### 4. 配置服务地址

推荐环境变量：

```env
VITE_FOGGY_SERVER_URL=http://localhost:7108
```

Vite 代理配置（开发环境跨域）：

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/data-viewer/api': {
        target: process.env.VITE_FOGGY_SERVER_URL || 'http://localhost:7108',
        changeOrigin: true,
      },
    },
  },
})
```

### 5. 确认后端具备前端组件所需端点

前端组件体系需要后端提供以下端点：

| 端点 | 来源 | 说明 |
|------|------|------|
| `GET /data-viewer/api/frontend-meta/{qmModel}` | `foggy-data-viewer` addon | 元数据 |
| `POST /data-viewer/api/query/direct/{qmModel}` | `foggy-data-viewer` addon | 直连查询 |
| `POST /data-viewer/api/members/query` | `foggy-data-viewer` addon | 维度成员 |

> **注意**：`@EnableFoggyFramework` + `@EnableDatasetClient` 只暴露 `/jdbc-model/` 端点，
> 不包含 `frontend-meta`。需要额外引入 `foggy-data-viewer` JAR（含 `ViewerApiController`），
> 并确保 MongoDB 可用（data-viewer 需要 MongoDB 缓存查询上下文）。
>
> 如果后端尚未具备这些端点，使用 `foggy-java-integration` skill 完成 Java 侧集成，
> 或使用 `foggy-mcp-integration` skill 部署独立 MCP Server。

### 6. 确认生成链路

如果项目要自己生成 `generated/` 代码：

```bash
# 在线模式（需后端运行）
node node_modules/foggy-data-viewer/scripts/foggy-gen.mjs \
  --model FactOrderQueryModel \
  --server http://localhost:7108 \
  --output src/generated/qm/order

# 离线模式（从 JSON 文件）
node node_modules/foggy-data-viewer/scripts/foggy-gen.mjs \
  --file path/to/model.frontend-meta.json \
  --output src/generated/qm/order
```

可在 `package.json` 中添加快捷脚本：

```json
{
  "scripts": {
    "gen:order": "node node_modules/foggy-data-viewer/scripts/foggy-gen.mjs --model FactOrderQueryModel --server http://localhost:7108 --output src/generated/qm/order"
  }
}
```

如果业务仓只是消费现成 `generated/` 产物，可以跳过。

### 6. 做最小 smoke test

至少验证一项：

- 页面启动后无 `vxe-pager` / `vxe-tooltip` 未注册错误
- 能成功打开一个使用 `foggy-data-viewer` 的页面
- 能访问 `frontend-meta` 或跑通一次生成命令

## 决策规则

- 业务目标是新前端组件体系：走 `data-viewer/api`，不要回退到老 `dslQuery.ts`
- 用户只要运行时接入：只做依赖和入口注册，不强行生成模板代码
- 用户要可持续生成：再接 `frontend-component-generator`
- 发现入口注册顺序不对：优先修正，再排查其他问题
- **联调阶段优先 GitHub 安装**，稳定后再切 npm 包
- 用户未指定阶段时默认走 GitHub 方式

## 安装方式切换指引

| 阶段 | 安装方式 | package.json 示例 |
|------|---------|-------------------|
| 联调 / 频繁迭代 | npm beta 或本地 link | `"foggy-data-viewer": "^1.0.1-beta.10"` 或 link 模式 |
| 正式上线 | npm latest | `"foggy-data-viewer": "^1.1.0"` |

> ⚠️ 不推荐 `github:foggy-projects/foggy-data-mcp-bridge#branch`——会下载整个 Java monorepo。

切换时只改 `package.json` 依赖声明，业务代码无需变动。

## 初始化完成标准

- 依赖已安装
- 入口注册正确（VxeUI 在 VxeTable 之前）
- 样式已引入（vxe-pc-ui + vxe-table + element-plus）
- 服务地址可配置
- 生成链路或运行链路至少一条可用

## 下一步

- 生成代码：`frontend-component-generator`
- 查看模型：`qm-schema-viewer`
- 联调页面：按 `frontend-meta / members/query / query/create` 跑 smoke

## 常见问题

**Q: vxe-pager 不显示？**
A: 确保 `app.use(VxeUI)` 在 `app.use(VxeTable)` 之前，且引入了 `vxe-pc-ui/lib/style.css`。

**Q: GitHub 安装后 node_modules 里没有 dist？**
A: GitHub 安装的是源码。如果项目直接引用 `dist/`，需要先在 foggy-data-viewer 目录 `npm run build:lib`。推荐 import 路径指向 `src/index.ts`（需项目 Vite 配置 alias）。

**Q: 切换到 npm 包后行为不一致？**
A: npm 包是 build:lib 产物，GitHub 是源码。如有差异检查 Vite external 配置。
