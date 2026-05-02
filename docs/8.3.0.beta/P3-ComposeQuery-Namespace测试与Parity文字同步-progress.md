---
type: progress
version: 8.3.0.beta
req_id: P3-ComposeQuery-Namespace-Test-And-Parity-Sync
priority: P3
status: accepted
last_updated: 2026-04-26
---

# Compose Query · Namespace 测试与 Parity 文字同步 — Progress

> 状态口径：`not-started` / `in-design` / `in-progress` / `blocked` / `ready-for-review` / `accepted` / `rejected`

## 关联规范文档

- 需求：`P3-ComposeQuery-Namespace测试与Parity文字同步-需求.md`
- 上游 P2：`P2-ComposeQuery-Columns-API-收口-progress.md`
- spec 落点：`../8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`

## 当前阶段判断

- 当前阶段：`accepted`
- 当前目标：完成 Item A 测试改写 + Item B spec 文字补充，sqlite lane 归零
- 当前范围：仅 Java 主仓 8.2 / 8.3 两份 doc + 2 个 namespace 测试

## 前置条件检查表

| item | 说明 | 状态 |
|---|---|---|
| P2 已 accepted | columns API 收口完成 | `done` (2026-04-26) |
| sandbox NPE 5 个 follow-up 已修 | A-10 / B-05 / B-06 / C-06 / C-07 五个测试转 passed | `done` (2026-04-26) |
| Item A 决策 | A1：接受 `null/empty namespace → ""` 为合法默认 | `done` (2026-04-26) |

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S0 | 创建需求 + progress 文档 | `completed` | `2026-04-26` |
| S1 | Item A 决策点拍板（A1） | `completed` | 需求文档 §决策点 已标明 A1 选定 |
| S2 | Item A 测试改写：`ComposeQueryContextTest.namespaceRequiredNonBlank` / `AuthorityRequestTest.namespaceRequired` | `completed` | 各改写为 `namespaceFallsBackToEmptyOnNullOrBlank`，断言 `null` / `""` 输入构造成功且 `namespace() == ""`；15 / 15 passed |
| S3 | Item B 在 8.2 P0 §核心语义 加 §7 columns 元素类型（heterogeneous wildcard） | `completed` | 落点：`8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`；同时声明跨仓 invariant + 引用 8.3 P2/P3 文档 |
| S4 | sqlite lane 0-failure 验收 | `completed` | **1702 passed / 0 failures / 1 skipped**；compose subtree 净增 +0 个新测试（既有 2 个改写后仍 passed）|
| S5 | 文档回写：本 progress 转 `accepted`；需求 §决策点 标明 A1；新增 acceptance 记录 | `completed` | 见 §文档回写；acceptance 记录 `acceptance/P3-ComposeQuery-Namespace-Parity-acceptance.md` |

## 验收对照

| AC | 验收点 | 状态 | 证据 |
|---|---|---|---|
| AC-A1 | sqlite lane 2 个 namespace failure 归零 | ✅ `passed` | sqlite lane **1702 passed / 0 failures / 1 skipped** |
| AC-A2 | Item A 决策有显式落点 | ✅ `passed` | 需求文档 §决策点 已标 A1 选定，附决策日期 + 理由 |
| AC-A3 | 测试断言与运行时行为一致 | ✅ `passed` | 两个测试已重命名为 `namespaceFallsBackToEmptyOnNullOrBlank`，断言 `namespace() == ""` |
| AC-B1 | spec 文字补 heterogeneous wildcard 段 | ✅ `passed` | 8.2 P0 §核心语义 §7 已落 |
| AC-B2 | Python parity 提交不需要代码改动 | ✅ `passed` | 0 行 Python diff（仅 Java 仓 spec 文字 + 测试改写） |

## 文档回写

- 需求文档：本目录下，§决策点 已选定 A1。
- 进度文档：本文件 — `accepted`。
- 验收记录：`acceptance/P3-ComposeQuery-Namespace-Parity-acceptance.md`（decision: `accepted` · 2026-04-26）。
- 上游 8.2 P0 spec：`§核心语义 §7 columns 元素类型（heterogeneous wildcard）` 已补，含跨仓 invariant + 落地引用。
- Python parity：0 行 Python 代码改动；spec 文字层面已对等声明。
- 8.2.0.beta P0 M10 解锁：sqlite lane 0 failure 基线达成，可启动 M10 签收。

## 当前测试基线

- 全仓 sqlite lane：**1702 passed / 0 failures / 1 skipped**
- 改写测试复跑：`mvn -pl foggy-dataset-model "-Dtest=ComposeQueryContextTest,AuthorityRequestTest" -P!multi-db test` → **15 passed / 0 failures**
- compose subtree 基线：581 passed / 0 failures（P2 + 5 sandbox follow-up + 2 namespace 改写后稳定）

## 改动文件清单

### Item A · 测试改写

1. `foggy-dataset-model/src/test/java/.../compose/context/ComposeQueryContextTest.java`
   - `namespaceRequiredNonBlank` → `namespaceFallsBackToEmptyOnNullOrBlank`，断言 `null` / `""` 输入构造成功且 `namespace()` 返回 `""`，`@DisplayName` 同步更新
2. `foggy-dataset-model/src/test/java/.../compose/security/AuthorityRequestTest.java`
   - `namespaceRequired` → `namespaceFallsBackToEmptyOnNullOrBlank`，同上

### Item B · spec 文字

3. `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - §核心语义 新增 §7 `columns 元素类型（heterogeneous wildcard）` 子节，覆盖：
     - 元素类型枚举（String / 8 种 PlanExpression 子类型）
     - 构造期 fail-closed 校验规则（4 条）
     - 跨仓 invariant（Java wildcard 单 setter ↔ Python `List[Union[str, ...]]`）
     - 落地参考链接（P2 / P3）

### 文档（meta）

4. `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-需求.md` — §决策点 标 A1 选定
5. `docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-progress.md`（本文件）— `ready-for-review`

## 遗留与跟踪

- 不在本 P3 范围：8.2.0.beta P0 M10 整体签收（独立工作）
- 后续如新增 PlanExpression 子类型，须在 §7 列表里同步增项 + Python 端对应类型 + sandbox Layer-B 白名单分支三处一起改

## 签收记录

- ✅ 5 项 AC 全部 `passed`
- ✅ sqlite lane 0 failure（1702 passed / 0 failures / 1 skipped）
- ✅ 文档双向回写完成
- ✅ Acceptance 记录已落 `acceptance/P3-ComposeQuery-Namespace-Parity-acceptance.md`
- ✅ Decision: `accepted` · 2026-04-26 · Codex reviewer
