---
type: progress
version: 8.2.0.beta
req_id: P1-ComposeQuery-TimeAnalytics
status: in-design
priority: P1
last_updated: 2026-04-25
---

# 8.2.0.beta Compose Query 时间分析能力增强 — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 需求：`P1-ComposeQuery-时间分析能力增强-需求.md`
- 评估：`P1-ComposeQuery-时间分析能力增强评估.md`
- 上游基线能力：`P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
- 能力对比参考：`P0-ComposeQuery-固定Schema下业务分析能力对比评估.md`

## 当前阶段判断

- 当前阶段：`in-design`
- 当前目标：把时间分析增强从“评估草案”推进为“可进一步拆实现规划的正式需求”
- 当前范围：仅记录需求与设计方向，不进入代码实现

## 前置条件检查表

| item | 说明 | 状态 |
|---|---|---|
| `QueryPlan` 主语义稳定 | 时间分析基于既有派生查询 / 阶段切断 / `JOIN` / `UNION` 能力展开 | `assumed-ready` |
| 时间分析评估稿完成 | 作为正式需求的上游输入 | `done` |
| 时间粒度建模基线明确 | 默认为 TM / QM 可补齐年月日等属性 | `assumed-ready` |
| 是否引入完整财务日历 | 当前未纳入第一批范围 | `out-of-scope` |

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S1 | 补写时间分析能力评估稿 | `completed` | `P1-ComposeQuery-时间分析能力增强评估.md` 已创建 |
| S2 | 上升为正式需求文档 | `completed` | `P1-ComposeQuery-时间分析能力增强-需求.md` 已创建 |
| S3 | 明确优先级与非目标 | `completed` | 已在需求稿中冻结为窗口 / 偏移比较 / 区间累计 / 补桶四层 |
| S4 | 决定是否继续拆实现规划 | `pending` | 待设计评审 |
| S5 | 决定是否同步补参考手册章节 | `pending` | 待需求收口后决定 |

## 开发进度

- 当前无代码实现
- 当前已完成文档化收口：
  - 评估稿
  - 正式需求稿
  - 本 progress 骨架

## 测试进度

- 自动化测试：`N/A`
- 原因：当前尚未进入实现阶段，暂无代码或测试用例落地
- 预期后续测试方向：
  - 窗口函数集成测试
  - 同比 / 环比语义测试
  - `MTD` / `QTD` / `YTD` 测试
  - 连续时间桶补齐测试

## 体验进度

- experience: `N/A`
- 原因：当前为后端 DSL / 查询语义需求文档，不涉及 UI 体验与页面交互

## 需求验收标准对照

| 验收项 | 当前状态 | 说明 |
|---|---|---|
| 需求目标清晰 | `done` | 已说明为什么要补时间分析以及补哪些能力 |
| 非目标明确 | `done` | 已排除完整 SQL、复杂 cohort、完整财务日历等范围 |
| 优先级有序 | `done` | 已明确 1~4 优先顺序 |
| 与既有 QueryPlan 语义兼容 | `done` | 需求稿已要求继续服从阶段切断与字段可见性 |
| 可继续拆规划 | `ready` | 具备进入实现规划阶段的基础 |

## 计划外变更

- 当前无

## 阻塞项

- 当前无硬阻塞
- 待确认项：
  - 时间粒度字段主要依赖 TM 预建，还是 DSL 增加轻量封装
  - 同比 / 环比是做显式高层语义，还是以 `lag/lead` 为核心再包一层
  - 连续时间轴补齐是否进入第一批范围

## 后续衔接

- 下一步建议：如设计方向认可，继续产出实现规划文档
- 下一步文档候选：
  - `P1-ComposeQuery-时间分析能力增强-实现规划.md`
  - `P1-ComposeQuery-时间分析能力增强-代码清单.md`

## 后置评审要求

- 当前不需要 `foggy-implementation-quality-gate`
- 当前不需要 `foggy-test-coverage-audit`
- 当前不需要 `foggy-acceptance-signoff`
- 原因：尚未进入实现与验收阶段

## 自检结论

- 当前交付类型：`record`
- 当前结论：`self-check-only`
- 已完成：
  - 文档路径落在正确版本目录
  - 命名与现有 `docs/8.2.0.beta/` 约定一致
  - development / testing / experience 三个维度均已记录
  - 正式需求与评估稿已交叉链接
