---
acceptance_scope: feature
version: 8.6.0.beta
target: foggy-data-viewer-custom-list
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-05-24
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 5
doc_role: feature_acceptance
doc_purpose: Sign off foggy-data-viewer custom list feature with recorded residual risks.
---

# Feature Acceptance

## Background

本验收记录用于签收 `addons/foggy-data-viewer` 自定义列表能力。目标是在新版组件中补齐旧版“自定义列表”常用能力，并利用新版 DSL/状态能力支持用户自定义列、默认过滤条件、排序、分页大小与默认方案。

## Acceptance Basis

- 设计文档：`docs/8.6.0.beta/foggy-data-viewer-custom-list-preset-design.md`
- 执行计划：`docs/8.6.0.beta/foggy-data-viewer-custom-list-next-step-plan.md`
- 验收清单：`docs/8.6.0.beta/foggy-data-viewer-custom-list-acceptance-checklist.md`
- 实现质量记录：`docs/8.6.0.beta/quality/foggy-data-viewer-custom-list-implementation-quality.md`
- 测试覆盖审计：`docs/8.6.0.beta/coverage/foggy-data-viewer-custom-list-coverage-audit.md`

## Checklist

| 验收项 | 状态 | 说明 |
| --- | --- | --- |
| 自定义列表入口与弹窗 | 通过 | 已提供 `ListPresetManager`，支持管理用户列表方案。 |
| 列配置保存与应用 | 通过 | 支持显隐、顺序、宽度、固定列，并能应用到 DataTableWithSearch。 |
| 默认过滤条件与排序 | 通过 | 支持保存当前筛选、排序和分页大小；也可选择只保存列配置。 |
| 默认方案 | 通过 | 支持设置默认方案，默认唯一性由后端保证。 |
| 用户标识传入 | 通过 | 前端按 `userId` 调用 `/users/{userId}` API，满足文件系统按用户目录降级。 |
| Mongo 优先与文件系统降级 | 通过，有管理风险 | 实现已完成；真实 Mongo 读写测试已作为 opt-in 集成测试保留，默认环境跳过。 |
| 后端字段校验扩展 | 通过 | 已提供 `ListPresetFieldValidator` Bean 扩展点。 |
| 兼容既有 DataTableWithSearch | 通过 | 未配置 `listPreset` 时不启用自定义列表能力。 |
| 文档与使用说明 | 通过 | 已补充使用示例、存储说明和扩展点说明。 |

## Evidence

| 类型 | 证据 |
| --- | --- |
| 前端测试 | `addons/foggy-data-viewer/frontend`: `npm test -- --run`，271 个用例通过。 |
| 前端构建 | `addons/foggy-data-viewer/frontend`: `npm run build:lib` 通过。 |
| 后端测试 | 根目录：`mvn test -pl addons/foggy-data-viewer`，76 个用例通过，Mongo opt-in 用例默认跳过 3 个。 |
| 集成演示构建 | `addons/foggy-data-viewer/verification-app`: `npm run build` 通过。 |
| 浏览器 e2e | `addons/foggy-data-viewer/verification-app`: `npm run test:e2e -- --project=chromium`，1 个用例通过。 |

## Failed Items

无阻塞失败项。

## Risks / Open Items

- Mongo 真实读写测试默认不跑，需要接入方或专用 CI 提供 Mongo 后设置 `FOGGY_DATA_VIEWER_MONGO_IT=true` 执行。
- 共享查询权限、后台取用户标识、审计日志属于后续增强。
- 字段强校验默认不启用，需要接入方按自身模型服务注册 `ListPresetFieldValidator`。

## Final Decision

验收结论：`accepted-with-risks`。

功能主链路已经具备实现、测试、构建和文档证据，可以作为 8.6.0.beta 自定义列表能力进入后续联调；上述风险项不阻塞当前阶段签收，但需要在后续版本或接入项目中继续补齐。

## Signoff Marker

`foggy-data-viewer-custom-list@8.6.0.beta` signed off on 2026-05-24 with recorded risks.
