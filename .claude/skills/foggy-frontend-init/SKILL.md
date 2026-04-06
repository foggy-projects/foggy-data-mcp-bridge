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

默认安装运行依赖，不默认强绑 `@beta` 版本，除非用户明确要求：

```bash
npm install foggy-data-viewer axios vxe-table vxe-pc-ui xe-utils element-plus
```

### 3. 配置应用入口

确保入口文件包含：

```typescript
import VxeUI from 'vxe-pc-ui'
import VXETable from 'vxe-table'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'
import 'foggy-data-viewer/style.css'
```

并按顺序注册：

```typescript
app.use(VxeUI)
app.use(VXETable)
app.use(ElementPlus, { locale: zhCn })
```

`VxeUI` 必须在 `VXETable` 之前。

### 4. 配置服务地址

至少补一份供项目和后续 agent 使用的配置。优先环境变量，其次本地配置文件。

推荐环境变量：

```env
VITE_FOGGY_SERVER_URL=http://localhost:7108
```

可选补充：

```json
{
  "serverUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

### 5. 确认生成链路

如果项目要自己生成 `generated/` 代码，再补这一项：

- 项目内已有 `npm run gen`
- 或可直接调用本地 `foggy-gen.mjs`

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

## 初始化完成标准

- 依赖已安装
- 入口注册正确
- 样式已引入
- 服务地址可配置
- 生成链路或运行链路至少一条可用

## 下一步

- 生成代码：`frontend-component-generator`
- 查看模型：`qm-schema-viewer`
- 联调页面：按 `frontend-meta / members/query / query/create` 跑 smoke
