# P0 - JDBC 条件聚合 IF 降级兼容 — Progress

## 文档作用

- doc_type: `progress`
- intended_for: `execution-agent | reviewer | signoff-owner`
- purpose: 记录本特性的开发、测试与收口状态，作为后续执行和验收的进度模板

## 基本信息

- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`已完成`
- 责任项目：`foggy-data-mcp-bridge`
- 上游需求：`docs/8.1.10.beta/P0-JDBC条件聚合IF降级兼容-需求.md`
- 实施计划：`docs/8.1.10.beta/P0-JDBC条件聚合IF降级兼容-implementation-plan.md`
- 完成日期：`待回填`

## 前置条件检查

| 前置条件 | 状态 |
|----------|------|
| 已完成方案收口：JDBC 端优先补 `IF(...) -> CASE WHEN` lowering | ✅ |
| 已明确本阶段不引入 `count_if / sum_if / avg_if` 正式 DSL 契约 | ✅ |
| 已确认 parser / semantic / SQL builder 主逻辑不在本次改动范围 | ✅ |
| 已确认版本目录与文档命名规则 | ✅ |

## Development Progress

### Step 1. 统一 `IF(...)` lowering

- 状态：`已完成`
- 目标：在 `SqlFunctionExp` 中将 `IF(cond, a, b)` 统一降级为 `CASE WHEN cond THEN a ELSE b END`
- 输出：
  - `SqlFunctionExp` 新增 `IF/IIF(...) -> CASE WHEN ... END` lowering
  - `SqlFragment` 补 `IIF` 返回类型推断
  - `AllowedFunctions` 补 `IIF` 白名单

完成说明：

- JDBC SQL 片段层已不再依赖底层数据库原生 `IF(...)`
- 降级结果统一为标准 SQL `CASE WHEN ... THEN ... ELSE ... END`

### Step 2. 聚合函数包裹 `IF(...)` 组合验证

- 状态：`已完成`
- 目标：确认 `SUM / AVG / COUNT` 包裹 `IF(...)` 时的 aggregate 识别、类型与引用列传播正确

完成说明：

- 发现真实阻塞点不在 `SqlFunctionExp`，而在 FSScript parser 将 `if` 视为保留关键字
- 为遵守“不改 parser 主语法”，在 `CalculatedFieldService.compileExpression` 外围新增预处理：
  - 函数式 `if(...)` 在编译前归一化为 `IIF(...)`
  - 字符串字面量内容保持原样，不误改 `'if('`
- 现有 `sum(if(...))` 写法可继续对外暴露，内部编译链路实际走 `IIF(...)`

### Step 3. 单元测试补齐

- 状态：`已完成`
- 目标：补 `IF` lowering 与聚合组合的表达式层单测

完成说明：

- 新增 `SqlExpFactoryTest.testParseFunctionIif`
- 新增 `CalculatedFieldServiceTest.extractRefs_ifFunctionNormalized`

### Step 4. 集成测试补齐

- 状态：`进行中`
- 目标：补真实查询结果比对，至少覆盖 SQLite 基线

完成说明：

- 在 `CalculatedFieldTest` 新增两条真实查询回归：
  - `SUM(IF(orderStatus == 'COMPLETED', 1, 0))`
  - `SUM(IF(orderStatus == 'COMPLETED' && salesAmount > 100, salesAmount, 0))`
- 已验证 SQL 中包含 `CASE WHEN`
- 已在 SQLite profile 下实际执行查询成功
- 已补充并通过两条 `NULL` 分支回归：
  - `AVG(IF(orderStatus == 'COMPLETED', salesAmount, null))`
  - `COUNT(IF(orderStatus == 'COMPLETED', 1, null))`
- 修复点：`SqlFunctionExp` 在参数收集阶段显式保留 `NullExp -> SQL NULL`

### Step 5. 文档回写

- 状态：`已完成`
- 目标：如代码落地成功，回写 docs-site 计算字段说明

完成说明：

- `docs/8.1.10.beta` 需求、代码清单、实施计划、进度文档已建立并回填
- 已回写 docs-site 中文、英文与 downloads 计算字段文档
- 已补条件聚合推荐写法、`==` / 多条件说明、SQL lowering 规则与边界说明

## 计划外变更

- 当前无

## Testing Progress

| 用例 / 维度 | 状态 | 备注 |
|-------------|------|------|
| `IF(...)` lowering SQL 断言 | `已完成` | 通过 `SqlFunctionExp` + `CalculatedFieldTest` 间接覆盖 |
| `SUM(IF(...))` SQL 断言 | `已完成` | `CalculatedFieldTest` |
| `AVG(IF(...))` SQL 断言 | `已完成` | `CalculatedFieldTest` |
| `COUNT(IF(...))` SQL 断言 | `已完成` | `CalculatedFieldTest` |
| SQLite 真实查询结果比对 | `已完成` | 集成测试通过 |
| MySQL / PostgreSQL / SQL Server 多方言验证 | `未执行` | 本地仅跑 SQLite profile |
| 现有表达式 / 聚合回归 | `已完成` | 定向回归测试通过 |

## Experience Progress

- 当前状态：`N/A`
- 原因：本特性为后端 JDBC 表达式引擎能力，不涉及 UI 交互

## 需求验收标准对照

| 验收标准 | 状态 |
|----------|------|
| `sum(if(cond, amount, 0))` 跨方言可生成并执行 | `部分完成` |
| `avg(if(cond, amount, null))` 跨方言可生成并执行 | `已完成` |
| `count(if(cond, 1, null))` 跨方言可生成并执行 | `已完成` |
| 不修改 parser / semantic / SQL builder 主逻辑 | `已完成` |
| 不影响现有聚合表达式链路 | `已完成` |
| 文档与测试证据齐全 | `已完成` |

## Implementation Self-Check

| 检查项 | 状态 |
|--------|------|
| requirement scope 已收口为 JDBC `IF` lowering | `已完成` |
| 未擅自扩展为正式 `count_if / sum_if / avg_if` 契约 | `已完成` |
| 代码改动路径与 code inventory 一致 | `已完成` |
| 关键测试已运行并记录结果 | `已完成` |
| 文档回写已完成 | `已完成` |
| 自检结论（`self-check-only` / `needs-formal-quality-gate`）已填写 | `已完成` |

## 阻塞项

- 当前无硬阻塞
- 已识别边界：
  - `if(...)` 作为关键字冲突，不能只靠 `SqlFunctionExp` 解决
  - `NullExp` 需要在 SQL 函数参数收集阶段显式映射为 `NULL`

## 后续衔接

| 后续项 | 状态 |
|--------|------|
| 若 JDBC 方案稳定，评估 `count_if / sum_if / avg_if` 正式 DSL 化 | `待评估` |
| planner / prompt 是否要同步收敛到 `IF(...)` 兼容写法 | `建议跟进` |
| 是否需要同步 docs-site 英文文档 | `已完成` |

## 执行完成后回填

- 完成代码路径：
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/AllowedFunctions.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/CalculatedFieldService.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/SqlFragment.java`
  - `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/expression/sql/SqlFunctionExp.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/expression/SqlExpFactoryTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/expression/CalculatedFieldServiceTest.java`
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/CalculatedFieldTest.java`
- 实际测试命令：
  - `mvn -pl foggy-dataset-model "-Dtest=SqlExpFactoryTest,CalculatedFieldServiceTest,CalculatedFieldTest" test`
- 测试结果：
  - 通过，`Tests run: 83, Failures: 0, Errors: 0, Skipped: 0`
- 风险 / 例外说明：
  - 本次方案虽然不改 parser 主语法，但新增了编译前预处理，不属于“只改 SqlFunctionExp 即可”
  - 多数据库 profile 尚未执行
- 自检结论：
  - `needs-formal-quality-gate`
- 是否进入正式 `foggy-implementation-quality-gate`：
  - `建议进入`
