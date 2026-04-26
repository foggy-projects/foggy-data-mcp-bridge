---
type: requirement
version: 8.3.0.beta
req_id: P3-ComposeQuery-Namespace-Test-And-Parity-Sync
priority: P3
status: in-design
last_updated: 2026-04-26
---

# Compose Query · Namespace 测试与 Parity 文字同步

## 背景

8.3.0.beta P2（`columns` API 收口）签收后剩下两条独立的小尾巴：

1. **Namespace 校验测试与 in-flight 行为不一致**
   `ComposeQueryContext.java` / `AuthorityRequest.java` 的 working-tree 改动里把 `namespace == null/empty` 的 fail-closed 校验放宽为 `b.namespace == null ? "" : b.namespace`，但下面两个测试还在断言旧的 `IllegalArgumentException` 抛出行为：
   - `ComposeQueryContextTest.namespaceRequiredNonBlank`
   - `AuthorityRequestTest.namespaceRequired`
   导致 sqlite lane 持续报 2 个 failure。

2. **Python parity 文字未同步**
   8.2.0.beta P0 `P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` 的 §QueryOptions / §Builder 段没有显式声明 columns 是 heterogeneous（`String | PlanExpression`）+ wildcard 单字段；P2 收口后两端语义已对等，spec 文字应补一行约束以避免下次审计再被翻出。

合并跟踪是因为两件事都是 8.2.0.beta P0 M10 签收前的清扫项，单独立 P3 太碎。

## 目标

收口本批次 sqlite lane failure，让 8.2.0.beta P0 进入 M10 签收时 0 failure；同时把 Java↔Python parity 在 spec 文字层面对齐。

### 必要交付项

#### Item A · Namespace 校验测试同步

1. 决策：当前 working-tree 行为（`null/empty namespace → fallback to ""`）作为正式契约 ⇒ 删除两条旧测试或改写为相反断言（`null` 仍可 build 通过且 `namespace()` 返回 `""`）
2. 如果决策反转——把 fail-closed 校验改回——则恢复 `ComposeQueryContext` / `AuthorityRequest` 的 IAE 抛出，测试不变；同时审视依赖路径里有没有意外允许空 namespace 的调用方
3. 任一路径都要求 sqlite lane 这 2 个 failure 归零

#### Item B · Python parity spec 文字同步

1. 在 `docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §QueryOptions / §Builder 段添加一行：「`columns` 接受 heterogeneous list（元素为 `String` 或 `PlanExpression`），构造时 fail-closed 校验非法元素类型；Java 端通过 `Builder.columns(List<?>)` wildcard 接收，与 Python `columns: List[Union[str, ColumnExpr, ProjectedColumn, AggregateColumn, WindowColumn, PlanColumnRef]]` 语义对等」
2. 不动代码，只动 spec 文字
3. 不需要 Python 仓改动

### 非目标

- 不重新审视 namespace 在 `SemanticRequestContext` / `QueryFacade` 等下游链路的语义
- 不扩展 PlanExpression 子类型
- 不动跨仓 parity 测试基础设施

## 决策点（Item A 的前置）

| 选项 | 行为 | 优点 | 缺点 |
|---|---|---|---|
| **A1 · 已选定** ✅ | `null/empty namespace → ""` 是合法默认 | 与既有"匿名访问 = 默认 namespace"语义一致 | 删除 fail-closed，下游若依赖非空 namespace 需再校验 |
| A2 · 回滚 working-tree 放宽 | 维持 `IllegalArgumentException` | 与 v1.3 namespace 治理强契约对齐 | 需找到放宽改动的真实需求出处 |
| A3 · 改放宽但加 trace | 内部允许 `""`，但记 warning | 折中 | 多一处可观测性代码，不优雅 |

**决策（2026-04-26）**：**A1**。working-tree 改动事实存在且与 v1.3 `SemanticRequestContext` 默认行为对齐；`""` 表示匿名/默认 namespace；下游若需限制非空必须自行校验（已在测试和 spec 文字中显式声明）。

## 跨仓影响

| 仓 | 是否需要改动 | 说明 |
|---|---|---|
| `foggy-data-mcp-bridge-wt-dev-compose` | ✅ 主改动面 | Item A 测试 + Item B spec 文字 |
| `foggy-data-mcp-bridge-python` | ❌ 0 改动 | namespace 默认行为本来就是 `None/""` 容忍；spec 文字同步只在 Java 仓做 |
| 其他仓 | ❌ 无影响 | |

## 阻断与依赖

- **不阻断**任何当前迭代
- **解锁**：8.2.0.beta P0 M10 签收（要求 0 failure 基线）

## 验收标准

| # | 验收项 | 度量 |
|---|---|---|
| AC-A1 | sqlite lane 2 个 namespace failure 归零 | `mvn -pl foggy-dataset-model test -Dspring.profiles.active=sqlite -P!multi-db` 0 failure |
| AC-A2 | Item A 决策有显式落点 | 需求文档 §决策点 选定一条路并写明理由 |
| AC-A3 | 测试断言与运行时行为一致 | 选 A1：测试断言 `null` 接受 + `namespace() == ""`；选 A2：测试断言 IAE 抛出 |
| AC-B1 | spec 文字补 heterogeneous wildcard 段 | grep `heterogeneous` / `wildcard` 在 P0 需求文档命中 |
| AC-B2 | Python parity 提交不需要代码改动 | 0 行 Python diff |

## 工作量预估

| Item | 预估 |
|---|---|
| Item A 决策 + 测试改写 + 回归 | ~1 hour |
| Item B spec 文字段 | ~15 min |
| 总计 | **~1.5 hours** |

## 关联文档

- 上游：`docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-需求.md`
- 上游 progress：`docs/8.3.0.beta/P2-ComposeQuery-Columns-API-收口-progress.md`
- spec 文字落点：`docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §QueryOptions / §Builder
- 进度跟踪：`docs/8.3.0.beta/P3-ComposeQuery-Namespace测试与Parity文字同步-progress.md`
