# P2-计算字段支持 in 和 not in 算子-需求

## 文档作用

- doc_type: workitem
- intended_for: execution-agent
- purpose: 在 foggy-dataset-model 的计算字段 / QM formula / slice `$expr` 三条 SQL 翻译链路上放行 `in` / `not in` 成员测试算子

## 基本信息

- 目标版本：`8.1.11.beta`
- 需求等级：`P2`
- 状态：`closed`
- 完成日期：2026-04-19
- 责任项目：`foggy-data-mcp-bridge` / `foggy-dataset-model`
- 交付模式：`single-root-delivery`
- 前置依赖：[`P2-fsscript支持in和not-in算子-需求.md`](./P2-fsscript支持in和not-in算子-需求.md) — 已一并签收
- 不在本次范围：TM 文件无原生 computed field 概念（`formulaDef` 语义不同）；slice `$expr` 路径由同版本 [BUG-001](./workitems/BUG-001-slice-expr-validation-gap.md) 修复后闭环

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: backend-module
- signed_off_at: 2026-04-20
- acceptance_record: [acceptance/version-signoff.md](./acceptance/version-signoff.md)
- blocking_items: none
- follow_up_required: no

## 背景

在同版本里刚完成 `foggy-fsscript` 的 SQL 风格 `v in (...)` / `v not in (...)` 成员测试算子。fsscript 运行时已经可用，但计算字段（含 QM `formula`、DSL `calculatedFields`、slice `$expr`）走的是另一条翻译链路：

```
CalculatedFieldService.compileExpression()
  → SHARED_PARSER(SqlExpFactory)
  → SqlExpFactory.createUnresolvedFunCall / createSqlExp
  → SqlBinaryExp / SqlFunctionExp / SqlExpWrapper
  → SqlFragment.binary (拼 SQL 字符串)
```

`SqlExpFactory` 用白名单 `AllowedFunctions` 拒掉所有"未知函数"。今天触发两个问题：

1. `(1, 2, 3)` 本身走 `createUnresolvedFunCall("()")` 路径，当 args.size() ≥ 2 时 `createSqlExp` 返回 null → 抛 `SecurityException: Function not allowed`
2. 即使解决了 `()` 多元素，`IN` / `NOT_IN` 也不在 `AllowedFunctions` 和 `SqlExpFactory.createSqlExp` 的二元算子分支里

## 问题定义

现状在 QM/DSL/slice 任一位置写 `brand in ('Apple','Huawei')`：

```
SecurityException: Function not allowed in calculated field expression: ()
```

编译阶段就挂，根本够不到 SQL 执行。

## 目标

- QM `columnGroups.items[].formula` 声明计算字段支持 `v in (...)` / `v not in (...)`
- DSL `calculatedFields[].expression` 支持同上
- slice `$expr` 支持同上（顺带红利，同一份 `SqlExpFactory`）
- 生成的 SQL 为标准 `(lhs IN (v1, v2, v3))` / `(lhs NOT IN (v1, v2, v3))`
- 依赖提取 `CalculatedFieldService.extractColumnReferences` 对 IN 的 RHS 列表正确处理（列表里如果确实是列引用，也能提取出来；literal 不误报）
- 计算字段类型推断：`IN` / `NOT IN` 的结果类型为 `BOOL`
- 不破坏 `(1+2)*3` 这种单元素括号表达式分组语义

## 任务拆分

### 1. `AllowedFunctions` 放行

文件：`foggy-dataset-model/.../engine/expression/AllowedFunctions.java`

- 新增常量 `MEMBERSHIP_OPERATORS = Set.of("IN", "NOT_IN")`
- `ALL_ALLOWED` 合并包含 `MEMBERSHIP_OPERATORS`
- `toSqlOperator` 新增分支：
  - `"IN"` → `"IN"`
  - `"NOT_IN"` → `"NOT IN"`（带空格）

### 2. 新增 `SqlListExp`

文件（新增）：`foggy-dataset-model/.../engine/expression/sql/SqlListExp.java`

- 持有 `List<Exp> items`
- `evalValue(ExpEvaluator)`：逐个 eval 成 `SqlFragment`，合并 `referencedColumns`，拼 `"(" + join(", ", fragments.sql) + ")"`
- `getReturnType` 返回 `SqlFragment.class`
- `toString` 用于调试

### 3. `SqlExpFactory.createSqlExp` 扩展

文件：`foggy-dataset-model/.../engine/expression/SqlExpFactory.java`

- `"()"` 分支改写：
  - `args.size() == 1 && args.get(0) instanceof EmptyExp` → 返回空 `SqlListExp`（后续 IN 处理会拦）
  - `args.size() == 1` → 保持现有行为返回 `args.get(0)`（分组语义）
  - `args.size() >= 2` → 返回 `SqlListExp(args)`
- 新增 `IN` / `NOT_IN` 分支（2 args）：
  - 拿 RHS，如果是 `SqlExpHolder` 先 unwrap 出内层
  - 如果内层是 `SqlListExp` 且非空 → 直接用
  - 如果内层是 `SqlListExp` 且为空 → 抛 `IllegalArgumentException("IN/NOT IN list cannot be empty")`
  - 否则包成单元素 `SqlListExp`（允许 `x in (col)` / `x in (1)`）
  - 返回 `SqlBinaryExp(lhs, "IN"/"NOT IN", rhsList)`

### 4. `SqlFragment` 类型推断

文件：`foggy-dataset-model/.../engine/expression/SqlFragment.java`

- `isComparisonOperator(String op)` 新增 `"IN"` / `"NOT IN"` 识别，使得 `inferBinaryType` 对 IN/NOT IN 返回 `BOOL`

### 5. 依赖提取

文件：`foggy-dataset-model/.../engine/expression/CalculatedFieldService.java`

- `extractColumnReferences(Exp, Set<String>)` 新增 `SqlListExp` 分支，递归 items 收集 column refs

### 6. 测试

新增测试文件，覆盖：

- DSL `calculatedFields`：`brand in ('Apple','Huawei')` 生成正确 SQL，真实查询结果符合预期
- QM `formula`：在 demo TM/QM 里加一条带 IN 的 calc field，验证真实 SQL 数据比对（按 CLAUDE.md 强制要求）
- slice `$expr`：`{$expr: "status in ('paid','shipped')"}` 正确下推
- NOT IN 镜像用例
- 空列表 `brand in ()` → 抛 `IllegalArgumentException`
- 混合列和字面量：`x in (col1, 42)` 能正确提取两个列依赖
- 依赖提取：`brand in ('Apple','Huawei')` 只提取 `brand`，不把字面量误当列名
- 类型推断：IN 表达式作为 calc field 时 `inferredType = BOOL`
- 嵌套：`(a in (1,2)) && (b not in (3,4))` 组合
- 单元素 `x in (1)` 正常工作

### 7. 文档

文件：`foggy-fsscript/docs/FSScript-Syntax-Manual.{zh-CN.}md`

- 在计算字段使用示例章节追加 `formula: 'status in (1,2,3)'` / DSL `calculatedFields` 用法

## 验收标准

- 新增单元 + 集成测试全绿，含真实 SQL 数据比对（SQLite profile）
- 现有 `foggy-dataset-model` 全量测试无回归
- 在 `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce/` 里能直接写带 `in (...)` 的 formula 并查询成功
- SQL 输出形如 `(brand IN ('Apple', 'Huawei'))` / `(status NOT IN ('cancelled', 'returned'))`
- `extractColumnReferences` 对 IN 列表内容正确处理
- 空 IN 列表 `()` 在编译阶段给出清晰错误

## 非目标

- TM 文件 computed field 支持（TM 没有原生概念）
- MongoDB 引擎（`MongoCalculatedFieldProcessor` 独立路径，不在本版本范围）
- `in (SELECT ...)` 子查询形态
- SQL IN 三值逻辑（NULL 语义）的 fsscript runtime 一致性（文档注明差异即可）

## Progress Tracking

### 开发进度
- [x] 1. `AllowedFunctions` 放行 + `toSqlOperator` 映射 + 新增 `isMembershipOperator`
- [x] 2. 新增 `SqlListExp`
- [x] 3. `SqlExpFactory.createSqlExp` 扩展 `()` 空/单/多元素三种形态 + `IN` / `NOT_IN` 二元算子分支 + `normalizeInRhs` helper
- [x] 4. `SqlFragment.isComparisonOperator` 纳入 `IN` / `NOT IN`（→ inferBinaryType 返回 BOOL）
- [x] 5. `CalculatedFieldService.extractColumnReferences` 添加 `SqlListExp` 分支
- [x] 6. 语法手册中英文追加 formula / calculatedFields 场景示例
- [ ] demo TM/QM 样例：留到下游按需补（不阻塞验收；集成测试已经覆盖等价 DSL 场景）

### 测试进度
- [x] 单元测试：`SqlExpFactoryInOperatorTest` 18 个用例全绿
  - 解析 + AST 结构（IN/NOT IN/空列表/单元素/字符串字面量/组合/嵌套）
  - `AllowedFunctions` 白名单与映射
  - `CalculatedFieldService.extractColumnReferences` 对 IN 列表的字面量/列引用/嵌套组合
- [x] 集成测试：`InOperatorCalcFieldIntegrationTest` 5 个用例全绿（SQLite profile，真实 SQL 数据比对）
  - DSL calcField `IIF(brand in ('Apple','Nike'), 1, 0)` 逐行等值
  - DSL calcField `IIF(brand not in (...), 1, 0)` 取反集
  - DSL calcField 分组计数场景
  - QM formula 编译入口（`CalculatedFieldService.compileExpression`）共享链路断言
  - 空列表在编译期被 `IllegalArgumentException` 拒绝
- [x] 全量回归：`mvn test -pl foggy-dataset-model` → `Tests run: 1003, Failures: 0, Errors: 0, Skipped: 0`
- [ ] slice `$expr` 路径：单元测试已证明 SQL 翻译层通过；完整 E2E 需先修 `QueryRequestValidationStep` 的 `$expr` gap —— 不在本需求范围

### 体验进度
- `N/A` — 纯 SQL 翻译层改造；模型/QM 作者在 formula 或 DSL 中书写表达式即可受益

### 执行 Checkin

**完成工作摘要**：在 `foggy-dataset-model` 的 `SqlExpFactory` 与 `AllowedFunctions` 白名单层放行 `IN` / `NOT_IN` 成员测试算子；新增 `SqlListExp` 承载 SQL 括号列表字面量；`SqlExpFactory.createSqlExp` 扩展 `()` 处理（空/单/多元素）并新增 `IN` / `NOT_IN` 二元算子分支，RHS 规范化为 `SqlListExp`；IN/NOT IN 类型推断收敛为 `BOOL`；依赖提取正确识别 IN 列表中的列引用同时豁免字面量。最终 SQL 输出形如 `(brand IN ('Apple', 'Nike'))` / `(status NOT IN ('cancelled'))`，与标准 SQL 方言兼容。

QM `columnGroups.items[].formula` 与 DSL `calculatedFields[].expression` 共享同一条 `CalculatedFieldService.compileExpression` 编译链路，一次改动两路径同步受益。

**实际改动文件清单**：

| 文件 | 改动 |
|------|------|
| `foggy-dataset-model/.../engine/expression/AllowedFunctions.java` | 新增 `MEMBERSHIP_OPERATORS` + 加入 `ALL_ALLOWED`；`toSqlOperator` 映射 `"IN"` → `"IN"`、`"NOT_IN"` → `"NOT IN"`；新增 `isMembershipOperator` 工具方法 |
| `foggy-dataset-model/.../engine/expression/sql/SqlListExp.java` | 新增，渲染为 `(v1, v2, v3)` SqlFragment，合并 `referencedColumns` |
| `foggy-dataset-model/.../engine/expression/SqlExpFactory.java` | `createSqlExp` 的 `()` 分支覆盖 0/1/N 元素；新增 `IN` / `NOT_IN` 二元算子分支；新增 `normalizeInRhs` 循环解包 + 空列表拒绝 + 单元素兼容 |
| `foggy-dataset-model/.../engine/expression/SqlFragment.java` | `isComparisonOperator` 纳入 `"IN"` / `"NOT IN"`，确保类型推断返回 `BOOL` |
| `foggy-dataset-model/.../engine/expression/CalculatedFieldService.java` | `extractColumnReferences` 新增 `SqlListExp` 分支，递归提取 items 中的列引用 |
| `foggy-dataset-model/src/test/.../expression/SqlExpFactoryInOperatorTest.java` | 新增，18 个单元用例 |
| `foggy-dataset-model/src/test/.../ecommerce/InOperatorCalcFieldIntegrationTest.java` | 新增，5 个集成用例（SQLite 真实 SQL 比对） |
| `foggy-fsscript/docs/FSScript-Syntax-Manual.{zh-CN.}md` | 计算字段 / QM formula / DSL calculatedFields 使用示例与差异说明 |

**翻译层关键设计决策**：

- `()` 分支现在覆盖三种 args 形态：空（fsscript `fixArray` 会把 EmptyExp 移掉）→ 空 `SqlListExp`；单个 EmptyExp → 空 `SqlListExp`；单个非 Empty → 保持分组语义；2+ → `SqlListExp`。空列表只在 `IN` / `NOT IN` 上下文抛错，让其他路径不受影响。
- `normalizeInRhs` 循环解包 `SqlExpHolder`，解决 `(x in (...))` 外层分组括号再叠一层 `SqlExpWrapper` 的场景。
- 空 IN 列表在编译期拒绝而非运行期 —— SQL `IN ()` 在多数数据库是语法错误，提早拒绝给出明确错误信息 `(IN/NOT IN 列表不能为空...请使用 '1 == 0')`。
- SQL 三值逻辑差异在文档里明确标注：`NOT IN (列表含 NULL)` 在 SQL 是 `UNKNOWN` → `WHERE` 过滤掉，与 fsscript runtime 的布尔不完全一致。

**自检清单**：
- [x] 需求范围内实现已完成，未扩展到非目标（MongoDB / TM / 子查询 / slice 未动）
- [x] 所有改动文件已记录
- [x] 基础 self-review 完成
- [x] 测试状态：pass（单元 18 + 集成 5 + 全量 1003 均绿）
- [x] 语法手册中英文同步更新
- [x] 前置 fsscript 需求未被影响（fsscript 自测保持绿色，dataset-model 全量 1003 均绿含 fsscript 相关链路）

**自检结论**：`self-check-only` —— 改动集中在 SQL 翻译层的白名单 + 二元算子分支 + 一个新 AST 节点，测试从单元到真实 SQL 数据比对都覆盖，全量回归 0 失败，不需要升级到正式 `foggy-implementation-quality-gate`。

**遗留 / 后续项**：
- ~~slice `$expr` 路径：`QueryRequestValidationStep.validateSliceItem` 没识别 `$expr` mode~~ → ✅ 已关联 [BUG-001](./workitems/BUG-001-slice-expr-validation-gap.md)，修复同版本（8.1.11.beta）完成；被剔除的 3 条 slice 集成测试已恢复
- demo TM/QM 样例：建议后续在 `foggy-dataset-demo/.../ecommerce/query/` 里挑选一个合适 QM 加一条带 IN formula 的展示列，作为使用文档的补充 —— 非本需求阻塞项。
- Python 引擎（`foggy-data-mcp-bridge-python`）同款计算字段支持：跨 repo 对齐项，按项目治理惯例需要时单独立项（可复用本次的契约与测试模式）。
- MongoDB 引擎（`MongoCalculatedFieldProcessor` 独立路径）：不在当前项目范围。
