---
type: execution-prompt
version: 8.2.0.beta
milestone: M3
target_repo: foggy-data-mcp-bridge (worktree: foggy-data-mcp-bridge-wt-dev-compose)
target_modules: [foggy-fsscript, foggy-dataset-model]
req_id: M3-Dialect-and-SandboxErrors-Java
parent_req: P0-ComposeQuery-QueryPlan派生查询与关系复用规范
status: done
python_reference_landed_at: 2026-04-21
python_baseline: 2591 passed / 1 skipped
java_landed_at: 2026-04-21
java_baseline: foggy-fsscript +8 tests · foggy-dataset-model sqlite lane 1348 passed / 0 failures
---

# Java M3 · `ComposeQueryDialect` + Sandbox 错误契约 开工提示词

## 执行位置（读在最前）

- **实际工作目录**：`D:\foggy-projects\foggy-data-mcp\foggy-data-mcp-bridge-wt-dev-compose`
- **逻辑仓**：`foggy-data-mcp-bridge`（Compose Query 分支最终会合回 mainline）
- **当前阶段**：8.2.0.beta 所有改动都只在 worktree 里；mainline `foggy-data-mcp-bridge/` HEAD 还**没有** `engine/compose/` 新包 / `foggy-fsscript` 的 `ComposeQueryDialect` / `docs/8.2.0.beta/`
- **本文档里所有 `foggy-data-mcp-bridge/...` 形式的路径**，物理上都定位到 `foggy-data-mcp-bridge-wt-dev-compose/...`
- **Maven 命令**在 worktree 根目录执行：`mvn test -pl foggy-fsscript,foggy-dataset-model ...`

Python 侧参考实现位于 `foggy-data-mcp-bridge-python`（独立仓，非 worktree，路径字面有效）。

## 角色与语境设定

你是 `foggy-data-mcp-bridge` worktree 下 `foggy-fsscript` + `foggy-dataset-model` 两个模块的维护者。你要在 Java 侧镜像 Python 已落地的 M3：

1. 在 foggy-fsscript 添加 `ComposeQueryDialect`，让 `from(...)` 作为函数名可被 parser 解析为普通函数调用
2. 在 foggy-dataset-model 新建 `engine.compose.sandbox` 子包，落地 14 个三层 sandbox violation 错误码 + `ComposeSandboxViolationException` 结构化异常 + layer/kind 辅助

本步**不做**以下事（都是 M9 真正落地的范围）：
- Layer A/B/C 的静态 AST validator
- 任何针对具体用例（A-01 ~ C-07）的断言测试
- 任何 sandbox 的运行时拦截

M9 会基于本步交付的错误码/异常类型填充 validator 与 24 条用例测试。

## 必读前置

严格按顺序读完再动手：

1. **主需求**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md`
   - 重点章节：`§命名约定`、`§白名单与隔离`
2. **实现规划**：`foggy-data-mcp-bridge/docs/8.2.0.beta/P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md`
   - 重点：`§白名单与隔离实现规划`
3. **沙箱脚手架（用例清单）**：`foggy-data-mcp-bridge/docs/8.2.0.beta/M9-三层沙箱防护测试脚手架.md`
   - 重点：错误码命名表、phase 枚举 —— 你要镜像到 Java 的错误码必须与这份文档字面对齐
4. **Python 对等实现**（**事实来源**）：
   - `foggy-data-mcp-bridge-python/src/foggy/fsscript/parser/dialect.py`（`COMPOSE_QUERY_DIALECT` 定义）
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/sandbox/error_codes.py`（14 个 code + 7 个 phase + `layer_of` / `kind_of`）
   - `foggy-data-mcp-bridge-python/src/foggy/dataset_model/engine/compose/sandbox/exceptions.py`（`ComposeSandboxViolationError`）
   - `foggy-data-mcp-bridge-python/tests/compose/sandbox/test_compose_query_dialect.py`（12 tests）
   - `foggy-data-mcp-bridge-python/tests/compose/sandbox/test_sandbox_error_codes.py`（15 tests）
5. **M1/M2 Java 落地范本**（同模块、同风格）：
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityErrorCodes.java`（错误码常量类范本）
   - `foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/security/AuthorityResolutionException.java`（结构化异常范本）
6. **foggy-fsscript M1 Dialect 现状**（Java 侧 dialect 基类）：
   - `foggy-data-mcp-bridge/foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/dialect/FsscriptDialect.java`
   - `foggy-data-mcp-bridge/foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/dialect/SqlExpressionDialect.java`（`isKeywordAsIdentifier(IF, '(')` → true 的范本）
   - `foggy-data-mcp-bridge/foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ElExpScanner.java`（Scanner 层已在 v1.4 咨询 `dialect.isKeywordAsIdentifier`，M3 不改）

## 对齐原则（硬要求）

1. **Python 是本期事实来源** —— 14 个 code 字符串 / 7 个 phase 字符串 / `layer_of / kind_of` 返回值都必须与 Python 逐字符对齐
2. **延续 M1/M2 显式 Builder + final 风格** —— 错误码用 `public static final String` 常量；异常类用 explicit 构造 + final 字段；不用 Lombok / Record
3. **Dialect 用现有 `FsscriptDialect` 子类模式** —— 参考 `SqlExpressionDialect` 的写法，别新起一套 class hierarchy
4. **不实装 validator** —— M3 只交付"错误契约"本身；Layer A/B/C 的扫描 / 执行期拦截由 M9 完成
5. **JDK 17 可用** —— `Set.of` / `Map.of` / `record` 等都可以用（但常量类本身继续 final class + private ctor）

## 交付清单

### 源码

#### `foggy-fsscript` 新增类

路径：`foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/dialect/ComposeQueryDialect.java`

```java
package com.foggyframework.fsscript.parser.dialect;

import com.foggyframework.fsscript.parser.ExpSymbols;

/**
 * Compose Query 方言：8.2.0.beta 的脚本入口需要把 `from` 作为普通函数名
 * 使用（对齐 JS 宿主 `from({model: 'X'})` 形态）。
 *
 * <p>唯一差异：{@code from(} 序列在 Scanner 层被解除保留，作为 IDENTIFIER
 * token 进入 parser，被识别为 FunctionCall(id=from, ...)。其他保留字
 * （if / return / const / ...）保持默认。</p>
 *
 * <p>Scanner 钩子：{@link #isKeywordAsIdentifier(int, int)} 仅在
 * ``keywordSymbol == ExpSymbols.FROM && nextChar == '('`` 时返回 true。
 * 这让 `from` 本身仍然作为 import 语法保留字（如果未来 fsscript 添加
 * ES Module 风格 import），只有 `from(` 的函数调用形态被降级。</p>
 */
public final class ComposeQueryDialect extends FsscriptDialect {

    public static final ComposeQueryDialect INSTANCE = new ComposeQueryDialect();

    private ComposeQueryDialect() { /* singleton */ }

    @Override
    public boolean isKeywordAsIdentifier(int keywordSymbol, int nextChar) {
        return keywordSymbol == ExpSymbols.FROM && nextChar == '(';
    }

    @Override
    public String name() {
        return "compose-query";
    }
}
```

> 注意：Python 的 dialect 是把 `from` 整体解除保留字；Java 的 scanner 钩子更精细，只降级 `from(` 二字节序列。两种都合法地达成同一目标：`from(...)` 被当成函数调用。**不要**在 Java 也"整体解保留" —— Scanner 层的精细钩子是 v1.4 M3 已冻结的契约。

#### `foggy-dataset-model` 新增 sandbox 子包

路径：`foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/sandbox/`

1. `ComposeSandboxErrorCodes.java` —— 14 常量 + phase 枚举 + `layerOf` / `kindOf` 工具方法。参考 Python `error_codes.py` 对齐；参考 Java `AuthorityErrorCodes.java` 风格。

2. `ComposeSandboxViolationException.java` —— `extends RuntimeException` · final 字段 `code / layer / kind / phase / scriptLocation` · 构造期校验 `code ∈ ALL_CODES && phase ∈ VALID_PHASES` · 参考 Python `exceptions.py` 与 Java `AuthorityResolutionException.java`。

### 测试（JUnit5 + `@DisplayName` 中文）

路径：`foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/sandbox/`

镜像 Python 27 tests 至少一一对应（合理合并允许）：

```
ComposeSandboxErrorCodesTest.java   ~12 tests
  · 14 个 code 常量值正确
  · namespace 前缀
  · 分层计数 (A:8 / B:3 / C:3)
  · VALID_PHASES 完整
  · layerOf / kindOf 正确 + unknown 抛异常

ComposeSandboxViolationExceptionTest.java   ~8 tests
  · 合法构造保留 code/layer/kind/phase/message/scriptLocation
  · 非法 code 被拒绝
  · 非法 phase 被拒绝
  · cause 通过构造函数附加
  · 14 个 code 都可成功构造
  · toString 携带诊断字段
```

路径：`foggy-fsscript/src/test/java/com/foggyframework/fsscript/parser/dialect/ComposeQueryDialectTest.java` (~8 tests)

```
· INSTANCE 单例
· name() 返回 "compose-query"
· isKeywordAsIdentifier(FROM, '(') == true
· isKeywordAsIdentifier(FROM, ' ') == false — 非 ` ( ` 后续仍保留
· isKeywordAsIdentifier(IF, '(') == false — 只处理 FROM
· 默认方言下 `from x` 作为 FROM 关键字 parse
· ComposeQueryDialect 下 `from(` 作为 IDENTIFIER parse
· end-to-end: ComposeQueryDialect 解析 `from({model: 'X'})` 成功，默认方言抛 parse 错
```

## 跨仓错误码 parity 硬对齐表

| Layer | Python 常量 | Java 常量 | 字符串 |
|-------|------------|-----------|--------|
| A | `LAYER_A_EVAL_DENIED` | `LAYER_A_EVAL_DENIED` | `compose-sandbox-violation/A/eval-denied` |
| A | `LAYER_A_ASYNC_DENIED` | `LAYER_A_ASYNC_DENIED` | `compose-sandbox-violation/A/async-denied` |
| A | `LAYER_A_NETWORK_DENIED` | `LAYER_A_NETWORK_DENIED` | `compose-sandbox-violation/A/network-denied` |
| A | `LAYER_A_IO_DENIED` | `LAYER_A_IO_DENIED` | `compose-sandbox-violation/A/io-denied` |
| A | `LAYER_A_GLOBAL_DENIED` | `LAYER_A_GLOBAL_DENIED` | `compose-sandbox-violation/A/global-denied` |
| A | `LAYER_A_TIME_DENIED` | `LAYER_A_TIME_DENIED` | `compose-sandbox-violation/A/time-denied` |
| A | `LAYER_A_SECURITY_PARAM` | `LAYER_A_SECURITY_PARAM` | `compose-sandbox-violation/A/security-param-denied` |
| A | `LAYER_A_CONTEXT_ACCESS` | `LAYER_A_CONTEXT_ACCESS` | `compose-sandbox-violation/A/context-access-denied` |
| B | `LAYER_B_FUNCTION_DENIED` | `LAYER_B_FUNCTION_DENIED` | `compose-sandbox-violation/B/function-denied` |
| B | `LAYER_B_DERIVED_FN_DENIED` | `LAYER_B_DERIVED_FN_DENIED` | `compose-sandbox-violation/B/derived-plan-function-denied` |
| B | `LAYER_B_INJECTION_SUSPECTED` | `LAYER_B_INJECTION_SUSPECTED` | `compose-sandbox-violation/B/injection-suspected` |
| C | `LAYER_C_METHOD_DENIED` | `LAYER_C_METHOD_DENIED` | `compose-sandbox-violation/C/method-denied` |
| C | `LAYER_C_RESULT_ITERATION` | `LAYER_C_RESULT_ITERATION` | `compose-sandbox-violation/C/result-iteration-denied` |
| C | `LAYER_C_CROSS_DS` | `LAYER_C_CROSS_DS` | `compose-sandbox-violation/C/cross-datasource-denied` |

| Phase | Python 常量 | Java 常量 | 字符串 |
|-------|------------|-----------|--------|
| — | `PHASE_SCRIPT_PARSE` | `PHASE_SCRIPT_PARSE` | `script-parse` |
| — | `PHASE_SCRIPT_EVAL` | `PHASE_SCRIPT_EVAL` | `script-eval` |
| — | `PHASE_PLAN_BUILD` | `PHASE_PLAN_BUILD` | `plan-build` |
| — | `PHASE_SCHEMA_DERIVE` | `PHASE_SCHEMA_DERIVE` | `schema-derive` |
| — | `PHASE_AUTHORITY_RESOLVE` | `PHASE_AUTHORITY_RESOLVE` | `authority-resolve` |
| — | `PHASE_COMPILE` | `PHASE_COMPILE` | `compile` |
| — | `PHASE_EXECUTE` | `PHASE_EXECUTE` | `execute` |

## 非目标（禁止做）

- 不做 Layer A static AST validator（M9）
- 不做 Layer B function whitelist runtime check（M9 合入 `AllowedFunctions`）
- 不做 Layer C method whitelist reflection（M9 在 `QueryPlan` class 上做）
- 不写 A-01..C-07 的用例测试（都在 M9 scaffold doc，M9 落地）
- 不修改 `ElExpScanner` / `FsscriptDialect` 基类 —— 这些是 v1.4 冻结面

## 验收硬门槛

1. `mvn test -pl foggy-fsscript -Dtest='ComposeQueryDialectTest' -Dspring.profiles.active=sqlite -P!multi-db` 全绿
2. `mvn test -pl foggy-dataset-model -Dtest='ComposeSandboxErrorCodesTest,ComposeSandboxViolationExceptionTest' -Dspring.profiles.active=sqlite -P!multi-db` 全绿
3. `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite -P!multi-db` 全回归，从 1246 基线推进到 1246+N（N ≥ ~20），**0 failures**
4. 14 个 code 字符串 / 7 个 phase 字符串全部与 Python 字面对齐（parity 表）
5. `ComposeQueryDialect.INSTANCE.isKeywordAsIdentifier(ExpSymbols.FROM, '(')` == true 且对其他 (keyword, nextChar) 组合都 false
6. 完成后把 8.2.0.beta progress.md 的 M3 行 `python-ready-for-review / java-pending` 更新为 `ready-for-review`，并追加 Java 测试基线数字

## 停止条件

- Python 常量字符串与提示词中给出的表不符 → 以 Python 源码为准，不要直接接受差异，联系 prompt 维护者同步
- 任何既有测试从绿变红 → 立即停 · 不提交 PR
- 反射到 Scanner 钩子逻辑冲突（例如发现 `FROM` 还被某个历史 SQL 方言覆盖）→ 立即停，贴现场到 progress.md

## 预估规模

- 源码：3 类 · ~300 LOC（常量类 + 异常类 + dialect）
- 测试：~400 LOC · 28+ tests
- 总量：0.5 人日

## 完成后需要更新的文档

1. 8.2.0.beta progress.md 的 M3 行：`ready-for-review`，追加 Java 基线数字
2. 本提示词 `status: ready-to-execute` → `status: done` + 填写完成日期和基线
3. root CLAUDE.md 的 "Compose Query M2 QueryPlan 对象模型" 段之后新增 "Compose Query M3 Dialect + Sandbox 错误契约" 段
