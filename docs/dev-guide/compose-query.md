# Compose Query — 多查询编排引擎

> 功能名称：**Compose Query**
> 包路径：`foggy-dataset-model/.../engine/compose/`
> MCP 工具：`dataset.compose_script`
> 分支：`dev-compose`
> 最后更新：2026-05-03

## 一、功能定位

Compose Query 解决的核心问题：**单个 QM 查询无法满足的多步分析场景**。

传统 QM 查询是"单模型 → 单 SQL → 单结果"。Compose Query 通过 fsscript 脚本编排多个 `dsl()` / `QueryPlan` 查询节点，支持：

- **ID 下推**：A 查询的结果作为 B 查询的过滤条件
- **CTE / 子查询组合**（同库）：多个 QM 的 SQL 拼接为 CTE 或 derived table，在数据库层 JOIN / UNION / 二段查询
- **内存 JOIN**（跨库）：各自执行后在 Java 内存中 Hash JOIN
- **二次计算**：对结果集做内存 filter / sort / compute

```
┌─────────────────────────────────────────────────────────┐
│                   fsscript 沙箱                          │
│                                                         │
│   dsl({model: 'A', ...})  ──→  DataSetResult            │
│   dsl({model: 'B', ...})  ──→  DataSetResult            │
│                                                         │
│   ┌─ 同库 ─→ .withJoin()  ──→ CTE/子查询 ──→ DB 执行   │
│   │                                                     │
│   ├─ 跨库 ─→ .joinInMemory() ──→ Hash JOIN ──→ 内存合并 │
│   │                                                     │
│   └─ 链式 ─→ .filter().sort().compute() ──→ 内存变换    │
│                                                         │
│   return result                                         │
└─────────────────────────────────────────────────────────┘
```

## 二、架构分层

```
MCP 层（foggy-dataset-mcp）
  └─ ComposeScriptTool       ← MCP 工具入口，接收 fsscript 脚本
       └─ DslQueryFunction   ← fsscript 内置函数 dsl()
            └─ SemanticQueryServiceV3.queryModel()    ← 执行查询
            └─ SemanticQueryServiceV3.generateSql()   ← 仅生成 SQL（CTE 用）

引擎层（foggy-dataset-model / engine/compose/）
  ├─ DataSetResult            ← 轻量 DataFrame，查询结果包装
  │   ├─ .column() / .toList() / .first() / .value()   ← 数据访问
  │   ├─ .filter() / .sort() / .compute()              ← 内存变换（P3）
  │   ├─ .withJoin()          ← 创建 CTE 组合（同库，P2）
  │   └─ .joinInMemory()     ← 内存 Hash JOIN（跨库，P4）
  │
  ├─ ComposedDataSetResult    ← withJoin 的延迟执行容器
  │   └─ .execute()           ← generateSql() → CteComposer → DB 执行
  │
  ├─ CteComposer              ← 旧 withJoin 路径的 SQL 拼接（WITH ... AS / 子查询回退）
  ├─ CteUnit                  ← 一个 CTE 子句（alias, sql, params）
  ├─ JoinSpec                 ← JOIN 描述（type, leftKey, rightKey）
  ├─ plan/QueryPlan           ← base / derived / union / join 关系节点对象模型
  ├─ compilation/ComposePlanner ← QueryPlan → SQL lowering（CTE / 子查询 fallback）
  ├─ ComposedSql              ← 拼接后的最终 SQL + 合并参数
  └─ SqlGenerationResult      ← generateSql() 的返回值

方言层（foggy-dataset）
  ├─ FDialect.supportsCte()   ← 旧 withJoin 路径使用
  └─ ComposePlanner.dialectSupportsCte()
       ├─ mysql8 / postgres / postgresql / sqlite → CTE
       └─ mysql / mysql57 / mssql / sqlserver     → 子查询 fallback
```

## 三、核心类详解

### 3.1 DataSetResult — 轻量 DataFrame

持有 `List<Map<String, Object>>`，所有方法返回**新实例**（不可变语义）。

| 方法 | 类型 | 说明 |
|---|---|---|
| `column(field)` | 数据访问 | 提取单列去重值（ID 下推用） |
| `toList()` | 数据访问 | 返回不可修改的行列表 |
| `first()` / `value(field)` | 数据访问 | 首行 / 首行单值 |
| `size()` / `isEmpty()` | 数据访问 | 行数 / 空判断 |
| `filter(expr)` | 内存变换 | fsscript 表达式过滤（truthy 语义） |
| `sort(field)` | 内存变换 | 排序，`-field` 降序 |
| `compute(name, expr)` | 内存变换 | 添加计算列 |
| `withJoin(right, type, key)` | SQL 组合 | CTE JOIN（同库，延迟执行） |
| `joinInMemory(right, type, key)` | 内存合并 | Hash JOIN（跨库，立即执行） |
| `joinInMemory(right, type, lk, rk)` | 内存合并 | 左右不同 key 名 |

实现 `PropertyFunction` 接口，fsscript 中 `ds.method()` 自动分发。

### 3.2 CteComposer — SQL 拼接器

纯静态工具类，无状态。两种模式：

**CTE 模式**（PostgreSQL、MySQL 8+、SQLite 3.35+）：
```sql
WITH cte_0 AS (sql₁), cte_1 AS (sql₂)
SELECT cte_0.*, cte_1.*
FROM cte_0 LEFT JOIN cte_1 ON cte_0.key = cte_1.key
```

**子查询模式**（MySQL 5.7、SQL Server 回退）：
```sql
SELECT t0.*, t1.*
FROM (sql₁) AS t0 LEFT JOIN (sql₂) AS t1 ON t0.key = t1.key
```

支持二元组合和多表链式 JOIN（N 个 CteUnit + N-1 个 JoinSpec）。

SQL Server 支持顶层 CTE，但当前 `QueryPlan` lowering 可能把一个编排节点嵌入到另一个 derived table 中。SQL Server 不接受 `FROM (WITH ... SELECT ...)` 这种嵌套形态，因此 Java 引擎对 `mssql/sqlserver` 统一使用子查询 fallback。

### 3.3 QueryPlan / ComposePlanner — 关系节点编排

`QueryPlan` 是当前 CTE 编排能力的主语义层，支持四类节点：

| 节点 | 说明 | 常见 SQL 形态 |
|---|---|---|
| `BaseModelPlan` | 一个 QM 基础查询 | `SELECT ... FROM model_sql` |
| `DerivedQueryPlan` | 基于前一层结果再过滤 / 分组 / 排序 / 分页 | `SELECT ... FROM (<inner>)` |
| `UnionPlan` | 两个同结构节点 `UNION` / `UNION ALL` | `(<left>) UNION ALL (<right>)` |
| `JoinPlan` | 两个节点按 `JoinOn` 连接 | `left JOIN right ON ...` |

实际 SQL 由 `ComposePlanner.compile(...)` 生成。方言策略是：

| 方言 | 策略 | 说明 |
|---|---|---|
| `postgres/postgresql` | CTE | 真实 SQL parity 已覆盖 |
| `sqlite` | CTE | 默认本地 lane |
| `mysql8` | CTE | 待 MySQL 8 lane 补验证 |
| `mysql/mysql57` | 子查询 fallback | MySQL 5.7 无 CTE / 无窗口函数 |
| `mssql/sqlserver` | 子查询 fallback | 避免嵌套 CTE 非法 |
| 未知方言 | fail-closed | 抛 `UNSUPPORTED_PLAN_SHAPE` |

### 3.4 joinInMemory — 内存 Hash JOIN

算法（与 `ResultSetQueryImpl.LeftJoin` 相同策略）：

```
1. 右表建 HashMap<joinKey, List<Row>>   — O(m)
2. 遍历左表，逐行探测 HashMap           — O(n)
3. 匹配 → 合并行（左表列优先 putIfAbsent）
4. 1:N → 自动展开为多行
5. 无匹配 → LEFT 保留 / INNER 丢弃
```

支持 `LEFT` 和 `INNER` 两种 JOIN 类型。

### 3.5 withJoin / QueryPlan vs joinInMemory 选择策略

| | `withJoin` | `joinInMemory` |
|---|---|---|
| **执行位置** | 数据库（CTE/子查询） | Java 内存 |
| **跨库** | ❌ 同一 DataSource | ✅ 任意 DataSource |
| **数据量** | 无限制（DB 处理） | 受 JVM 内存限制 |
| **选择依据** | 同 `dataSourceGroup` | 不同 `dataSourceGroup` |
| **执行时机** | 延迟（首次调用方法时） | 立即 |

当前新能力优先使用 `QueryPlan` 路径表达 derived / union / join / timeWindow；`withJoin` 保留给旧脚本兼容与轻量二元 join 场景。

## 四、实现阶段

| Phase | 状态 | 内容 |
|---|---|---|
| **P1 — MVP** | ✅ | `dsl()` 桥接、`DataSetResult` 基础包装、`ComposeScriptTool` MCP 工具 |
| **P2 — CTE 组合** | ✅ | `generateSql()` 模式、`CteComposer`、`withJoin` 延迟执行、`FDialect.supportsCte()` |
| **P2.5 — QueryPlan 编排** | ✅ | `BaseModelPlan / DerivedQueryPlan / UnionPlan / JoinPlan`、`ComposePlanner`、方言 CTE / 子查询 fallback |
| **P3 — 二次计算** | ✅ | `filter()` / `sort()` / `compute()` 内存操作 |
| **P4 — 跨库 JOIN** | ✅ | `joinInMemory()` Hash JOIN，支持 LEFT/INNER，不同 key 名 |

## 五、文件清单

### 新建文件

| 文件 | 模块 | 职责 |
|---|---|---|
| `DataSetResult.java` | dataset-model | 轻量 DataFrame + 内存变换 + joinInMemory |
| `DslQueryFunction.java` | dataset-model | fsscript `dsl()` 内置函数 |
| `ComposedDataSetResult.java` | dataset-model | withJoin 延迟执行容器 |
| `CteComposer.java` | dataset-model | CTE / 子查询 SQL 拼接 |
| `CteUnit.java` | dataset-model | CTE 单元 |
| `JoinSpec.java` | dataset-model | JOIN 规格 |
| `ComposedSql.java` | dataset-model | 拼接后的 SQL + 参数 |
| `SqlGenerationResult.java` | dataset-model | generateSql() 返回值 |
| `plan/QueryPlan.java` 等 | dataset-model | QueryPlan 关系节点对象模型 |
| `compilation/ComposePlanner.java` | dataset-model | QueryPlan SQL lowering |
| `ComposeScriptTool.java` | dataset-mcp | MCP 工具入口 |
| `compose_script_m2.md` | dataset-mcp | AI 工具说明文档 |

### 修改文件

| 文件 | 改动 |
|---|---|
| `SemanticQueryServiceV3.java` | 新增 `generateSql()` 接口 |
| `SemanticQueryServiceV3Impl.java` | 实现 `generateSql()` |
| `QueryFacade.java` / `Impl` | 新增 `buildSqlOnly()` |
| `FDialect.java` | 新增 `supportsCte()` 默认 true |
| `MysqlDialect.java` | 覆写 `supportsCte()` → false |

### 测试文件

| 文件 | Tests | 覆盖 |
|---|---|---|
| `CteComposerTest.java` | 13 | CTE/子查询拼接、多表链式、参数合并、边界 |
| `DataSetResultTest.java` | 46 | 数据访问、filter/sort/compute、joinInMemory、链式调用、空集边界 |
| `ComposedDataSetResultIT.java` | 1 | withJoin 延迟组合真实 DB/QM 执行、execute 缓存、手写 SQL parity |
| `ComposeRealSqlParityTest.java` | 3 | derived/filter、join aggregate、union all 与手写 SQL 逐行比较 |
| `ScriptResourceRealSqlParityTest.java` | 3 | 脚本资源真实执行，结果与手写 SQL 逐行比较 |
| `ComposeScriptToolIT.java` | 2 | `dataset.compose_script` MCP 工具注册、embedded-mode 真实脚本执行、手写 SQL parity |
| `DialectFallbackTest.java` | 16 | CTE / 子查询 fallback 方言策略 |
| `ScriptRuntimeTest.java` | 18 | 脚本 preview、返回值解耦、`timeWindow` 请求映射 |

## 六、测试覆盖

### 已覆盖

| 组件 | 测试数 | 关键场景 |
|---|---|---|
| CteComposer | 13 | CTE/子查询二元 JOIN、三表链式、参数合并顺序、边界校验 |
| DataSetResult 基础 | 12 | toList/first/size/isEmpty/value/column 去重与 null 过滤 |
| filter | 4 | 数值比较、字符串相等、不可变性、空表达式异常 |
| sort | 6 | 升序/降序、字符串、null 值、不可变性 |
| compute | 3 | 添加计算列、不可变性、空参数异常 |
| 链式调用 | 2 | filter→sort→compute、sort→filter→column |
| joinInMemory | 15 | LEFT/INNER、1:N 展开、N:1 展开、不同 key、左表优先、链式、空集边界 |
| 空集边界 | 4 | 空结果 filter/sort/compute/null items |
| ComposedDataSetResult | 1 | `withJoin` 延迟执行容器经真实 DB/QM 查询、`execute()` 缓存、手写 SQL parity |
| QueryPlan 真实 SQL parity | 3 | derived/filter、join aggregate、union all 与手写 SQL 对比 |
| 脚本资源真实 SQL parity | 3 | `real_sql_derived_query_scenario.js` / `real_sql_join_scenario.js` / `real_sql_union_scenario.js` 真实执行并与手写 SQL 对比 |
| ComposeScriptTool MCP 端到端 | 2 | Spring 工具注册、embedded runtime bundle、真实 join 编排脚本执行并与手写 SQL 对比 |
| QueryPlan 方言 fallback | 16 | MySQL 5.7 / MySQL 8 / PostgreSQL / SQLite / SQL Server 策略 |
| CTE 场景脚本 preview | 3 scripts | `derived_query_scenario.js` / `join_scenario.js` / `union_scenario.js` 通过 `ScriptRuntime` preview |

### 跨方言验证基线（2026-04-26）

| Lane | 结果 | 说明 |
|---|---|---|
| SQLite | 8 passed / 0 skipped | `ComposeRealSqlParityTest + ComparativeExecutionIT + TimeWindowExecutionIT` |
| PostgreSQL | 8 passed / 0 skipped | 真实 SQL parity 已通过 |
| MySQL 5.7 | 8 passed / 0 skipped | 非窗口 compose / comparative 通过；timeWindow 因无窗口函数记录 info log 后 no-op 返回 |
| MySQL 5.7 脚本 parity | 3 passed / 0 skipped | 脚本资源真实执行，不依赖窗口函数 |
| MySQL 5.7 ComposedDataSetResult | 1 passed / 0 skipped | `withJoin` legacy 延迟执行通道真实 DB/QM parity |
| MCP compose_script integration | 2 passed / 0 skipped | MySQL integration profile；工具注册 + embedded-mode join script parity |
| SQL Server | 8 passed / 0 skipped | 子查询 fallback 通过 |
| 本地 compose 目标套件 | 71 passed / 0 skipped | `ComposedDataSetResultIT + DataSetResultTest + ScriptRuntimeTest + ComposeRealSqlParityTest + ScriptResourceRealSqlParityTest` |

### 待覆盖

| 组件 | 类型 | 优先级 |
|---|---|---|
| 多方言 CTE/子查询 | MySQL 8 lane | P2 |

## 七、下一步工作

1. **MySQL 8 lane**：补齐窗口函数环境下的 CTE + timeWindow 真实 SQL parity
2. **元数据 dataSourceGroup**：在模型元数据中暴露 dataSource 归属，供 LLM 判断 withJoin 或 joinInMemory
3. **工具描述维护**：`dataset.compose_script` 使用 `compose_script_m2.md` 作为唯一 AI-facing 工具描述

执行拆分见 `docs/8.2.0.beta/M11-CTE-TimeWindow-Coverage-Closure-Plan.md`；Python 对齐提示词见 `docs/8.2.0.beta/M10-Python-CTE-TimeWindow-Coverage-Alignment-Prompt.md`。
