---
quality_scope: feature
quality_mode: pre-coverage-audit
version: 8.6.0.beta
target: foggy-data-viewer-custom-list
status: reviewed
decision: ready-for-coverage-audit
reviewed_by: Codex
reviewed_at: 2026-05-24
follow_up_required: yes
---

# Implementation Quality Gate

## Background

本记录用于复核 `addons/foggy-data-viewer` 自定义列表能力的实现质量，范围覆盖用户自定义列、默认筛选条件、排序、分页大小、默认方案、Mongo 优先存储、文件系统降级、前端用户标识传入，以及后端字段校验扩展点。

## Check Basis

- 需求与方案：`docs/8.6.0.beta/foggy-data-viewer-custom-list-preset-design.md`
- 执行计划：`docs/8.6.0.beta/foggy-data-viewer-custom-list-next-step-plan.md`
- 验收清单：`docs/8.6.0.beta/foggy-data-viewer-custom-list-acceptance-checklist.md`

## Changed Surface

- 后端：新增 ListPreset domain、controller、service、store、Mongo/FileSystem/Fallback 存储实现、Repository、AutoConfiguration 接入。
- 后端扩展：新增 `ListPresetFieldValidator`，允许接入方按 QM schema、字段权限、业务规则二次校验保存内容。
- 前端：新增 list preset API/types、DataTable toolbar/pager 扩展、DataTableWithSearch 状态导出与应用、ListPresetManager 管理弹窗。
- 验证应用：新增自定义列表 demo 配置与类型 shim。
- 文档：新增设计、执行计划、使用说明、验收清单。

## Quality Checklist

| 检查项 | 结论 | 说明 |
| --- | --- | --- |
| 职责边界 | 通过 | 前端只负责状态采集、编辑和应用；后端负责持久化、隔离、默认唯一性与基础校验。 |
| 存储策略 | 通过 | Mongo 可用时走 Mongo；未提供 Mongo 时使用文件系统目录，路径按用户与模型隔离。 |
| 用户隔离 | 通过 | API 路径显式使用 `/users/{userId}`，文件系统可据此建目录；后台取身份标识留作后续增强。 |
| 默认方案 | 通过 | Service 层保证同一 user/model/businessKey 下默认方案唯一。 |
| 字段校验扩展 | 通过 | 默认不阻断；接入方可注册 Bean 覆盖校验逻辑，避免组件库强依赖具体模型服务。 |
| 前端可用性 | 通过 | 支持列显隐、顺序、宽度、固定列、筛选/排序/分页保存、默认方案、编辑、覆盖、删除、应用。 |
| 兼容性 | 通过 | 未配置 `listPreset` 时原 DataTableWithSearch 使用路径不变。 |
| 错误处理 | 通过 | 前端提示保存/加载/应用异常；后端非法请求返回失败响应。 |
| 文档同步 | 通过 | 已补充设计文档、使用说明、计划与验收材料。 |

## Findings

未发现阻塞发布的实现质量问题。

当前实现采用保守集成方式：自定义列表作为可选能力接入，不改变现有查询 DSL 与 DataTableWithSearch 的默认使用方式；后端字段强校验通过扩展点开放，避免组件库在当前阶段引入过重的 QM/权限耦合。

## 2026-05-24 Final Self Check

本轮推进 1~5 后重新执行了完整验证矩阵：

| 验证项 | 结论 |
| --- | --- |
| 前端单元测试 | `npm test -- --run` 通过，11 个测试文件、273 个测试。 |
| 前端组件库构建 | `npm run build:lib` 通过。 |
| 后端默认测试 | `mvn test -pl addons/foggy-data-viewer` 通过，79 个测试，Mongo opt-in 用例默认跳过 3 个。 |
| Mongo 真实存储测试 | 显式开启 `FOGGY_DATA_VIEWER_MONGO_IT=true` 后，`MongoListPresetStoreIntegrationTest` 通过，3 个测试。 |
| verification-app 构建 | `npm run build` 通过，保留 Vite 大 chunk warning。 |
| verification-app 浏览器流程 | `npm run test:e2e -- --project=chromium` 通过，覆盖保存、应用、设默认、删除和刷新恢复默认方案。 |
| 接入文档 | 已新增最小接入指南，并在 8.6.0.beta README 中挂载。 |

自检结论：实现边界、存储策略、用户配置隔离、前端默认方案应用顺序、测试证据和接入说明均已收口。当前未发现需要在进入接入方试用前阻塞的问题。

## Risks / Follow-ups

- UI 浏览器级交互已补 Playwright 自动化，覆盖保存、设默认、应用、删除和刷新后默认加载顺序；后续可继续扩展列拖拽等更细粒度体验用例。
- Mongo 真实实例读写测试已保留为 opt-in 集成测试，默认测试跳过；后续可在提供 Mongo 的专用 CI job 中启用。
- 共享查询当前为结构预留，强权限、组织范围与审计后续再做。
- 后端从登录态解析用户标识暂缓，现阶段依赖接入方前端传入 `userId`。

## Recommended Next Skills

- `foggy-test-coverage-audit`
- `foggy-acceptance-signoff`

## Decision

实现质量结论：`ready-for-coverage-audit`。

该功能可以进入测试覆盖审计与验收签收阶段，以上风险作为非阻塞后续项记录。
