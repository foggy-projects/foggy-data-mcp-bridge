---
name: frontend-component-generator
description: 自动生成基于 foggy-data-viewer 的 Vue 业务组件。根据 frontend-meta v1 自动拉取或读取 QM 元数据，生成 generated 层代码，并在需要时补 modules/pages 包装层。当用户需要快速创建数据表格业务组件时使用。
---

# Frontend Component Generator

根据 `frontend-meta v1` 生成可直接接入业务系统的前端代码。

## 适用边界

适用：

- 已有可用 QM 模型，需要生成前端表格/查询代码
- 已有 `frontend-meta` 接口或离线快照
- 需要落地 `generated → modules → pages` 三层接入

不适用：

- 只想手写一个临时表格
- 后端 `frontend-meta` 契约还没稳定
- 还没完成前端环境初始化

## 前置条件

先检查：

- 项目已完成 `foggy-frontend-init`
- 已注册 `VxeUI` 和 `VXETable`
- 可用的元数据来源二选一：
  - 在线模式：`http://<server>/data-viewer/api/frontend-meta/{qmModel}`
  - 离线模式：`*.frontend-meta.json`

## 标准输出

生成器默认只写 `generated/` 目录，至少生成 6 个文件：

- `*.types.ts`
- `*.table.schema.ts`
- `*.query.schema.ts`
- `*.api.ts`
- `*Table.vue`
- `index.ts`

业务定制放在手工维护层：

- `modules/`
- `pages/`

不要把业务逻辑写回 `generated/`。

## 工作流

### 1. 确认输入模式

优先级：

1. 用户给了 `--file` 或元数据快照 → 离线模式
2. 用户给了 `qmModel + server` → 在线模式
3. 两者都没给 → 先问清楚

### 2. 确认输出范围

至少确认：

- QM 模型名
- 服务地址或快照路径
- `generated/` 输出目录
- 是否还需要创建 `modules/pages` 包装层骨架

默认建议：

- `generated` 输出到 `src/generated/qm/<domain>/`
- 包装层输出到 `src/modules/<domain>/`、`src/pages/<domain>/`

### 3. 运行生成器

优先使用项目已有命令；没有时再直接调用脚本。

在线模式：

```bash
npm run gen -- --model FactOrderQueryModel --server http://localhost:7108 --output src/generated/qm/order
```

离线模式：

```bash
npm run gen -- --file ./meta/FactOrder.frontend-meta.json --output src/generated/qm/order
```

如果项目没有 `npm run gen`，再查找本地 `scripts/foggy-gen.mjs`。

### 4. 验证生成结果

至少检查：

- 6 个标准文件已生成
- `query.schema.ts` 和 `table.schema.ts` 都存在
- `generated/` 目录之外没有被误改
- 在线模式下能成功拉到 `frontend-meta`

### 5. 需要业务接入时，补包装层

若用户要“可直接挂页面”的业务组件，按三层结构继续补手工文件：

- `modules/<domain>/<XxxListModule>.vue`
- `modules/<domain>/<xxx>-module.config.ts`
- `modules/<domain>/<xxx>-query-hooks.ts`
- `pages/<domain>/<XxxListPage>.vue`

规则：

- `generated/` 只负责标准能力
- `modules/` 负责工具栏、权限、默认参数、query hooks
- `pages/` 负责路由入口和页面布局

### 6. 做最小验收

至少完成一项：

- 构建通过
- 页面能打开并看到表格
- `members/query`、筛选、分页至少跑通一条链路

## 决策规则

- 需要长期维护的业务页面：一定使用三层结构，不让页面直接依赖 `generated` 组件
- 只是 PoC：允许只生成 `generated` 层，但要明确这是临时方案
- 模型字段多：默认全量生成，再由包装层裁剪显示列
- 用户想手改 `generated`：直接阻止，改模板、参数或包装层
- 后端不可访问：切换离线快照模式，不阻塞生成

## 关联能力

- 元数据来源：`frontend-meta v1`
- 查询接口：`/data-viewer/api/members/query`
- 页面模式：`/data-viewer/api/query/create` + `/data-viewer/view/{model}/{queryId}`

## 交付口径

生成成功不等于业务可用。最终要看：

- 生成代码是否能编译
- 业务包装层是否与 `generated` 隔离
- 页面筛选、排序、分页是否在真实环境可跑通
