---
type: execution-prompt
version: 8.2.0.beta
milestone: M4
target_repo: foggy-data-mcp-bridge (worktree: foggy-data-mcp-bridge-wt-dev-compose)
target_module: foggy-dataset-model
req_id: M4-SchemaDerivation-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: done
completed_at: 2026-04-21
python_reference_landed_at: 2026-04-21
python_baseline: 2658 passed / 1 skipped
java_baseline: 1324 passed / 0 failures (M2 baseline 1246 + 78 new M4 tests)
java_new_tests: 78 (AliasExtractorTest 20 + OutputSchemaTest 16 + SchemaDerivationTest 31 + ComposeSchemaExceptionTest 11)
java_new_source_files: 7 (ColumnAliasParts + AliasExtractor + ColumnSpec + OutputSchema + ComposeSchemaErrorCodes + ComposeSchemaException + SchemaDerivation)
---

# Java M4 · Schema 推导与结构性校验 开工提示词

## 执行位置（读在最前）

- **实际工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- **逻辑仓**：`foggy-data-mcp-bridge`（Compose Query 分支最终会合回 mainline）
- **目前阶段**：8.2.0.beta 所有改动都只在 worktree 里；mainline `foggy-data-mcp-bridge/` 目录 HEAD 还**没有** `engine/compose/schema/` 新包或本提示词
- **本文档里所有 `foggy-data-mcp-bridge/...` 形式的路径**，物理上都定位到 `foggy-data-mcp-bridge-wt-dev-compose/...`
- **Maven 命令**在 worktree 根目录执行：`mvn test -pl foggy-dataset-model ...`

Python 侧参考实现位于 `foggy-data-mcp-bridge-python`（独立仓，非 worktree，路径字面有效）。

## 角色与语境设定

你是 `foggy-data-mcp-bridge` worktree 下 `foggy-dataset-model` 模块的维护者。你要在 Java 侧镜像 Python 已落地的 M4 —— 在 `QueryPlan` 基础上实现"声明侧 output schema 推导"，覆盖 4 种 plan 类型、别名解析、以及 7 种结构性校验错误码。

**重要边界**：本步**不做** authority 绑定（M5 scope）或 SQL 类型推断（M6 scope）。只做"用户写了什么 + 层间引用是否合法"的纯结构性校验。

## 必读前置

严格按顺序读完再动手：

1. **主需求**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - 重点章节：`§核心语义 §2 派生查询字段可见性`、`§union 规范`、`§join 规范`、`§典型示例`
2. **实现规划**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
   - 重点：`§Schema 与别名规则`
3. **Python 对等实现（事实来源）**：
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/schema/alias.py` —— 别名提取算法
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/schema/output_schema.py` —— `ColumnSpec` / `OutputSchema`
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/schema/derive.py` —— 核心分派 + 校验规则
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/schema/error_codes.py` —— 7 个 code + 2 个 phase
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/schema/errors.py` —— `ComposeSchemaError`
4. **Python 对等测试**（4 个文件 / 67 tests · 你的行为事实来源）：
   - `foggy-data-mcp-bridge-python/tests/compose/schema/test_alias_extraction.py`
   - `foggy-data-mcp-bridge-python/tests/compose/schema/test_output_schema.py`
   - `foggy-data-mcp-bridge-python/tests/compose/schema/test_schema_derivation.py`
   - `foggy-data-mcp-bridge-python/tests/compose/schema/test_schema_errors.py`
5. **M1-M3 Java 落地范本**（同模块、同风格）：
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/plan/*.java`（M2 · `QueryPlan` 家族）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityErrorCodes.java`（错误码常量类范本）

## 对齐原则（硬要求）

1. **Python 是事实来源**：7 个 code 字符串 / 2 个 phase 字符串 / 别名正则规则 / 4 种 plan 的校验行为必须与 Python 逐字符对齐
2. **延续 M1/M2 显式 Builder + final 字段风格**：不用 Lombok / Record；错误码用 `public static final String` 常量类
3. **只做结构性校验**：
   - 派生层 columns 里的标识符是否在源 output names 中（ID-level，不做 SQL 语法分析）
   - union 列数一致
   - join on 字段两侧都可见
   - join 两侧 output 无重名冲突
   - column 输出名无重复
   - group_by / order_by 引用的必须是 **当前 plan 的** output 名（post-alias）
4. **不做 authority 绑定**：不调 `AuthorityResolver`；不读 `fieldAccess` / `deniedColumns`。这些是 M5 的事
5. **不做 SQL 类型推断**：`ColumnSpec.dataType` 字段保留但 M4 全部返回 null
6. **Reserved token 列表与 Python 严格对齐**：`SUM / COUNT / AVG / MIN / MAX / IIF / IF / CASE / WHEN / THEN / ELSE / END / COALESCE / NULLIF / IS_NULL / IS_NOT_NULL / BETWEEN / IN / NOT / DATE_DIFF / DATE_ADD / NOW / AND / OR / TRUE / FALSE / NULL / DISTINCT`（共 27 项）

## 交付清单

### 源码（6 个类，全部 `public final`）

包 `com.foggyframework.dataset.db.model.engine.compose.schema`：

```
schema/
├── ColumnAliasParts.java         record-like 值对象 · {expression, outputName, hasAlias}
├── AliasExtractor.java           静态工具：extract(columnSpec) -> ColumnAliasParts
├── ColumnSpec.java               {name, expression, sourceModel?, dataType?, hasExplicitAlias} · 不可变
├── OutputSchema.java             ordered + 不可变 list of ColumnSpec · 构造期去重校验
├── ComposeSchemaErrorCodes.java  7 个 frozen code + 2 个 phase + ALL_CODES / VALID_PHASES
├── ComposeSchemaException.java   extends RuntimeException · 含 code / phase / planPath / offendingField / cause
└── SchemaDerivation.java         静态方法 derive(QueryPlan) -> OutputSchema · 按 4 种 plan 分派
```

**命名差异**：Python 用 `ComposeSchemaError`（Python 惯例）；Java 用 `ComposeSchemaException`（继承 `RuntimeException`，与 M1 `AuthorityResolutionException` 风格一致）。

### 测试（JUnit5 + `@DisplayName` 中文，镜像 Python 67 tests 至少一一对应）

包 `com.foggyframework.dataset.db.model.engine.compose.schema`（test 源码根）：

```
AliasExtractorTest.java          ~18 tests · 裸名 / 函数表达式 / 大小写 AS / 嵌套 IIF / AS-in-identifier 负例 / 字符串字面量 / 异常输入
OutputSchemaTest.java            ~12 tests · ColumnSpec 不变量 / 构造 / 重复名拒绝 / 访问器 / 值相等
SchemaDerivationTest.java        ~25 tests · 4 种 plan 的派生 / spec 典型示例 1 + 3 / group_by / order_by desc 前缀
ComposeSchemaExceptionTest.java  ~10 tests · 7 code 常量 / 非法 code / 非法 phase / cause 链 / toString
```

**硬指标**：Java 测试集合 ≥ 60 tests 全绿；可合并（例如 Java 不需要把 `frozen attribute` 单独测，因为 final 字段天然不可变）。

### 反射校验（推荐加一条）

在 `OutputSchemaTest.java` 里写一条反射测试：

```java
@Test
@DisplayName("OutputSchema 只暴露 read-only 访问器，禁止暴露 mutator")
void outputSchemaReadOnlySurface() {
    Set<String> forbidden = Set.of("setColumns", "addColumn", "remove");
    for (Method m : OutputSchema.class.getMethods()) {
        assertFalse(forbidden.contains(m.getName()),
                () -> "OutputSchema 不得暴露 " + m.getName());
    }
}
```

## 跨仓错误码 parity 硬对齐表

| Python 常量 | Java 常量 | 字符串 |
|------------|-----------|--------|
| `DERIVED_QUERY_UNKNOWN_FIELD` | `DERIVED_QUERY_UNKNOWN_FIELD` | `compose-schema-error/derived-query/unknown-field` |
| `COLUMN_SPEC_MALFORMED` | `COLUMN_SPEC_MALFORMED` | `compose-schema-error/column-spec/malformed` |
| `DUPLICATE_OUTPUT_COLUMN` | `DUPLICATE_OUTPUT_COLUMN` | `compose-schema-error/duplicate-output-column` |
| `UNION_COLUMN_COUNT_MISMATCH` | `UNION_COLUMN_COUNT_MISMATCH` | `compose-schema-error/union/column-count-mismatch` |
| `JOIN_ON_LEFT_UNKNOWN_FIELD` | `JOIN_ON_LEFT_UNKNOWN_FIELD` | `compose-schema-error/join/on-left-unknown-field` |
| `JOIN_ON_RIGHT_UNKNOWN_FIELD` | `JOIN_ON_RIGHT_UNKNOWN_FIELD` | `compose-schema-error/join/on-right-unknown-field` |
| `JOIN_OUTPUT_COLUMN_CONFLICT` | `JOIN_OUTPUT_COLUMN_CONFLICT` | `compose-schema-error/join/output-column-conflict` |

| Phase | Python 常量 | Java 常量 | 字符串 |
|-------|------------|-----------|--------|
| — | `PHASE_PLAN_BUILD` | `PHASE_PLAN_BUILD` | `plan-build` |
| — | `PHASE_SCHEMA_DERIVE` | `PHASE_SCHEMA_DERIVE` | `schema-derive` |

## 行为对齐速查

### 别名提取规则

- `\s+AS\s+` 大小写不敏感；取最后一次匹配（outermost alias wins）
- 别名侧必须是 `[A-Za-z_][A-Za-z0-9_$]*`
- 非法别名 → 回退为"整个字符串当作 expression"（safe-failure）
- 字符串字面量内的 `AS` 用字符级 mask 掩盖（单/双引号，支持 `\\` 转义）

### 派生层字段可见性

- `DerivedQueryPlan.columns[i]` 的 expression 内每一个"裸标识符"都必须是
  - 源 schema 的 output name **之一**，或
  - 保留 token 之一（`SUM / COUNT / ... / DISTINCT`）
- 识别器是 regex-loose 的（`[A-Za-z_][A-Za-z0-9_$]*`），不做完整 SQL 语法解析；这是 lint-quality 的启发式，M6 SQL 编译会做精确绑定

### group_by / order_by

- 两者都必须引用**当前 plan 的** output 名（不是源的）
- `order_by` 支持 `-fieldName` 的 desc 前缀

### union

- 仅验证左右两侧 **列数** 一致；类型兼容性推迟到 M6
- 输出名以左侧为准；右侧名字被忽略（但必须存在）

### join

- `on[i].left` 必须在 left.output 中
- `on[i].right` 必须在 right.output 中
- 两侧 output name 重名 → `JOIN_OUTPUT_COLUMN_CONFLICT`，用户必须在下一个 `.query()` 里显式别名消歧

## 非目标（禁止做）

- 不做 authority 绑定（`fieldAccess` / `deniedColumns` 是 M5）
- 不做 SQL 类型推断（`dataType` 字段保留但 M4 全部 null）
- 不做 union 类型兼容性（M6）
- 不做完整 SQL 表达式解析 —— 派生层字段可见性只检查"裸标识符"
- 不改动 `QueryPlan` 类家族（M2 已冻结）
- 不改动 `foggy-fsscript`

## 验收硬门槛

1. `mvn test -pl foggy-dataset-model -Dtest='AliasExtractorTest,OutputSchemaTest,SchemaDerivationTest,ComposeSchemaExceptionTest' -Dspring.profiles.active=sqlite -P!multi-db` 全绿
2. `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db` 全回归，从 1246 基线推进到 1246+N（N ≥ 60），**0 failures**
3. 7 个 code 字符串 / 2 个 phase 字符串全部与 Python 字面对齐
4. 覆盖 spec §典型示例 1（两段聚合）+ §典型示例 3（join 后派生）端到端 derive 成功
5. 完成后把 8.2.0.beta progress.md 的 M4 行 `python-ready-for-review / java-pending` 更新为 `ready-for-review`，追加 Java 测试基线数字

## 停止条件

- Python 常量 / 正则 / 校验规则与提示词表不符 → 以 Python 源码为准
- 任何既有测试从绿变红 → 立即停 · 不提交 PR
- 派生层"裸标识符扫描"在真实 spec 样例上产出 false-positive → 停下反推到本提示词维护者加保留词

## 预估规模

- 源码：6 类 · ~600 LOC（含 Builder + static 方法样板）
- 测试：~700 LOC · 60+ tests
- 总量：0.75 人日

## 完成后需要更新的文档

1. 8.2.0.beta progress.md 的 M4 行：`ready-for-review`，追加 Java 基线数字
2. 本提示词 `status: ready-to-execute` → `status: done` 并填写完成日期与基线
3. root CLAUDE.md 的 "Compose Query M3" 段之后新增 "Compose Query M4 Schema 推导" 段
