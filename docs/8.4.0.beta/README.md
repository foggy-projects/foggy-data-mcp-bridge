# 8.4.0.beta — Java 端治理项与下一步交付

## 文档作用

- doc_type: workitem-group
- intended_for: execution-agent / reviewer
- purpose: 跟踪 8.4.0.beta 阶段的 Java 端治理项与同步改造进度

## 进度总览

| 功能 | 状态 | 备注 |
|------|------|------|
| `P0-v13引擎收紧裸dimension引用`（Java 端 · 同步 Python `v1.7`） | `in-design` | 需求文档已落盘 · M0 立项 · M1 跨端审计待启动 |

## 功能清单

| 文件 | 用途 |
|------|------|
| `P0-v13引擎收紧裸dimension引用-需求.md` | Java 端改造路径 / 列循环改造方案 / 验收标准 / 测试计划 |
| `P0-v13引擎收紧裸dimension引用-progress.md` | M0-M12 里程碑 + Self-Check + 决策记录 |

## 关联文档

- backlog 起源：`foggy-data-mcp-bridge-python/docs/backlog/B-03-v13-engine-bare-dimension-tightening.md`
- Python 端镜像：`foggy-data-mcp-bridge-python/docs/v1.7/P0-v13引擎收紧裸dimension引用-{需求,progress}.md`
- 上游触发：G5 PR-P2 调试期复盘（commit `cf2ba9b` → `352a8bb`）
- 受影响 spec：
  - `docs/8.3.0.beta/P0-SemanticDSL-列项对象语法-后置消歧设计.md` §4.2 用户级开放门
  - `docs/8.3.0.beta/G10-flag-flip-rollout-playbook.md` C1-C4 决策门

## 当前优先级判断

P0 — Java 引擎与 Python `v1.7` 同步落地，确保 Foggy QM 公开契约在跨端 SQL 引擎上一致 fail-loud。
