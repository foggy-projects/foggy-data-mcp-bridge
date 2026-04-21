# P1-Compose Query 多查询编排能力收口 — Progress

## 基本信息

- 目标版本：`8.1.11`
- 当前状态：`待开始`
- 上游需求：`docs/8.1.11/P1-Compose Query多查询编排能力收口-需求.md`
- 模块职责：`docs/8.1.11/P1-Compose Query多查询编排能力收口-module-responsibility.md`
- 代码清单：`docs/8.1.11/P1-Compose Query多查询编排能力收口-code-inventory.md`
- 实施计划：`docs/8.1.11/P1-Compose Query多查询编排能力收口-implementation-plan.md`
- 执行提示词：`docs/8.1.11/P1-Compose Query多查询编排能力收口-execution-prompt.md`

## 前置条件检查

| 检查项 | 状态 | 备注 |
|------|------|------|
| 本版本范围已评审 | `[ ]` | |
| `withJoin()` 正式边界已确认 | `[ ]` | |
| 组合结果返回契约已确认 | `[ ]` | |
| 同库判断方案已确认 | `[ ]` | |
| 数据库验证环境可用 | `[ ]` | |

## Development Progress

| Step | 状态 | 说明 |
|------|------|------|
| Step 1. 收口正式能力边界 | `todo` | |
| Step 2. 统一 compose 请求语义 | `todo` | |
| Step 3. 收口 planner 与运行保护 | `todo` | |
| Step 4. 补数据库集成测试与 MCP 端到端测试 | `todo` | |
| Step 5. 收口开发文档与验收材料 | `todo` | |

## 计划外变更

- 暂无

## Testing Progress

| 测试项 | 状态 | 结果 / 备注 |
|------|------|------|
| `DataSetResultTest` | `pending` | |
| `CteComposerTest` | `pending` | |
| `ComposedDataSetResult` 集成测试 | `pending` | |
| `ComposeQueryTool` 端到端测试 | `pending` | |
| PostgreSQL CTE 验证 | `pending` | |
| MySQL 8 CTE 验证 | `pending` | |
| MySQL 5.7 子查询回退验证 | `pending` | |

## Experience Progress

- `N/A`

## 需求验收标准对照

| 验收标准 | 状态 |
|------|------|
| 文档、schema 描述、实现、测试对 `withJoin()` / `joinInMemory()` 的行为描述一致 | `pending` |
| `Compose Query` 继续复用现有语义查询主链 | `pending` |
| `withJoin()` 路径请求语义与 `dsl()` 路径一致或差异已显式约束 | `pending` |
| `withJoin()` 的同库判断依据有可执行方案 | `pending` |
| `joinInMemory()` 的适用边界、限制和失败提示明确 | `pending` |
| 已新增数据库集成测试覆盖 CTE 与子查询回退 | `pending` |
| 已新增 MCP 端到端验证覆盖主要成功 / 失败路径 | `pending` |
| 版本文档可独立回答当前支持项、限制项和验证方式 | `pending` |

## 阻塞项

- 暂无

## 后续衔接

- 当前文档用于评审 `8.1.11` 的正式推进范围
- 评审通过后，按 execution prompt 开始开发与测试
