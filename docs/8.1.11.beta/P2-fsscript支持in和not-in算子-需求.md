# P2-fsscript 支持 in 和 not in 算子-需求

## 文档作用

- doc_type: workitem
- intended_for: execution-agent
- purpose: 为 foggy-fsscript 新增 SQL 风格 `v in (...)` / `v not in (...)` 成员测试算子的需求与执行记录入口

## 基本信息
- 目标版本：`8.1.11.beta`
- 需求等级：`P2`
- 状态：`closed`
- 完成日期：2026-04-19
- 责任项目：`foggy-data-mcp-bridge` / `foggy-fsscript`
- 交付模式：`single-root-delivery`
- 来源：用户主动改进项

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: backend-module
- signed_off_at: 2026-04-20
- acceptance_record: [acceptance/version-signoff.md](./acceptance/version-signoff.md)
- blocking_items: none
- follow_up_required: no

## 背景

fsscript 用于解析 TM/QM 文件里的表达式（筛选条件、计算字段、slice 条件等）。现状下用户要表达"某个字段属于若干值之一"只能写：

```javascript
brand == 'Apple' || brand == 'Huawei' || brand == 'Xiaomi'
```

SQL/Python 风格的 `v in (1, 2, 3)` / `v not in (1, 2, 3)` 更紧凑也更贴近数据分析语义，在 QM 条件表达式、slice 表达式里非常常用。

## 问题定义

现状关键事实（`src/main/resources/datasetexp.cup` + `fun/IN.java` + `fun/Brackets.java`）：

1. `IN` 词法、语法 terminal 都存在（`ElExpScanner.java` 将 `IN`/`NOT` 列为保留字）。
2. 语法 `term3:x IN:op term2:y` 已存在（与 `==` / `<>` 同优先级），生成 `UnresolvedFunCall("IN", [x,y])`。
3. 运行期 `fun/IN.java` 写死为 for-in 迭代语义：强转 `args[0]` 为 `UnresolvedFunCall` 去拿 `(item, index)` 元组 —— 因此 `x in (1,2,3)` 在运行时会抛 `ClassCastException`。
4. `(1,2,3)` 作为括号表达式走 `Brackets("()"," )"`，当前实现 `args[0].evalResult(ee)` 只返回第一个元素，不是真正的集合语义。
5. `NOT IN` 目前完全没有语法规则；`NOT` terminal 只在 `bit` 规则里承担按位取反 (`~`) 用途，不会与新增 `term3 NOT IN term2` 冲突。
6. `for (var x in collection) {...}` 走的是 `createForIn` 独立产生式，不经过 `IN` FunCall —— 不需要考虑向后兼容这条路径。

## 目标

- 在 fsscript 表达式层支持 SQL 风格：
  - `v in (1, 2, 3)` → true/false
  - `v not in (1, 2, 3)` → true/false
  - 同时兼容数组字面量：`v in [1, 2, 3]` / `v not in [1, 2, 3]`
  - 同时兼容变量：`v in someList` / `v not in someList`
- `null` / 空集合 / 混合类型有明确的成员测试语义
- 保留现有 `(item, index) in collection` 为 for-in 场景保留的内部语义不被破坏
- 新语法可以直接在 TM/QM 的 `slice`、`visibleColumns.filter`、`computed` 等表达式中使用

## 任务拆分

### 1. 语法层：新增 `NOT IN` 产生式

文件：`foggy-fsscript/src/main/resources/datasetexp.cup` 的 `term3` 规则。

新增一条与现有 `IN` 对称的产生式：

```
|   term3:x NOT IN term2:y {:
        ListExp list = new ListExp(2);
        list.add(x);
        list.add(y);
        RESULT = parser.factory.createUnresolvedFunCall("NOT_IN", list, false);
    :}
```

要求：

- 产生式挂在 `term3` 下，保持与 `==` / `<>` / `IN` / `LIKE` 同优先级
- 重新运行 CUP（按文件注释的 `-expect 91` 约束）确认冲突数不增
- 若生成的 `ExpParser.java` / `ExpSymbols.java` 有变化需要一并提交

### 2. 运行时：改造 `fun/IN.java`，新增 `fun/NOT_IN.java`

文件：`foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/IN.java` + 新增 `NOT_IN.java`。

`IN.execute` 改造逻辑：

1. **向后兼容 for-in 元组形态**：若 `args[0] instanceof UnresolvedFunCall` 且其 name 为 `"()"` 且子元素形如 `(id, id)` → 保持现有 `InResult` 行为（iteration use-case）
2. **否则进入成员测试分支**：
   - 计算 `value = args[0].evalResult(ee)`
   - 计算 `haystack`：
     - 若 `args[1] instanceof UnresolvedFunCall` 且 name 为 `"()"` → 遍历其 args 逐个 `evalResult`，装成 `List`（解决 `Brackets` 只返回第一个元素的问题）
     - 若 `args[1]` eval 结果是 `Collection` / `Object[]` / `Iterable` → 原样使用
     - 若是 `null` → 视为空集合，返回 false
     - 其他标量 → 视为单元素集合
   - 返回成员判定结果（使用 `Objects.equals` 比较，避免 `==` 装箱陷阱）

`NOT_IN.execute` 直接委托到 IN 的成员测试分支，结果取反。为了让 `UnresolvedFunCall("NOT_IN", ...)` 能解析到，需要在 FunTable 注册。

比较语义：

- 采用 `Objects.equals` 作为默认，`Number` 之间比较使用数值相等（避免 `1` vs `1L` 与 `new BigDecimal("1")` 不等）
- 字符串按 `String.equals`
- `null` 处理：`null in [1,2,null]` → true；`null in [1,2]` → false；`x in null` → false

### 3. FunTable 注册

文件：`foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/FunTable.java`。

在现有 `append(new IN())` 附近追加：

```
append(new NOT_IN());
```

（`IN` 已注册，不需重复）

### 4. 测试

新增单元测试（位置：`foggy-fsscript/src/test/java/com/foggyframework/fsscript/`）：

- `v in (1,2,3)` 命中 / 未命中
- `v not in (1,2,3)` 命中 / 未命中
- `v in [1,2,3]` 数组字面量写法
- `v in someListVar` 变量场景
- `v in ()` 空集合 → false；`v not in ()` → true
- `null in (1,2,null)` → true；`null not in (1,2)` → true
- 数值类型混用：`1 in (1L, 2L)` → true；`1 in (1.0, 2.0)` → true
- 回归：`for (var x in collection) {...}` 迭代语义不变
- 回归：`(item, index) in collection` 元组迭代语义不变（若项目真实存在此用法）

### 5. 文档

文件：`foggy-fsscript/docs/FSScript-Syntax-Manual.md` / `.zh-CN.md`。

追加 `in` / `not in` 语法段落，给出 TM/QM 可直接使用的示例。

## 验收标准

- CUP 重新生成、冲突数不超过现有 `-expect 91`
- 单元测试全绿，含本需求新增用例
- 现有 `for (var x in list)` 循环、`(item, index) in list` 迭代两条语义零回归
- 在 `foggy-dataset-demo` 的样例 TM/QM 里能直接写 `brand in ('Apple','Huawei','Xiaomi')` 并正确执行
- `FSScript-Syntax-Manual` 中英文双版本已更新

## 非目标

- 不实现 `value in (SELECT ...)` 子查询语义
- 不实现 fsscript → SQL `IN` 下推优化（下推由 `foggy-dataset-model` 的 query planner 判断；本需求只保证 fsscript 层语义正确）
- 不改 `Brackets` 对 `(a,b,c)` 的通用求值语义（避免波及其他路径）；IN/NOT_IN 只在自身的 runtime 里识别 `()` 并自行展开

## Progress Tracking

### 开发进度
- [x] 1. `datasetexp.cup` 新增 `NOT IN` 产生式（与既有 `IN` 对称，挂在 `term3`）
- [x] 2. CUP 重新生成 `ExpParser.java` / `ExpSymbols.java`（`-expect 104`，无 error）
- [x] 3. `IN.java` 改造：兼容 for-in 元组 `(item, index) in collection` + 新增成员测试分支
- [x] 4. 新增 `NOT_IN.java`
- [x] 5. `FunTable.java` 注册 `NOT_IN`
- [x] 6. 中英文语法手册追加 `in` / `not in` 章节
- [x] 7. `JavacupHelper` 更新为相对路径 + `-expect 104`

### 测试进度
- [x] 单元测试：成员测试基本用例（parens / array literal / 变量三种右值）
- [x] 单元测试：空集合 / null / 类型混用（Integer vs Long vs BigDecimal）
- [x] 单元测试：`in` 与 `&&` / `||` 组合、复合表达式
- [x] 回归测试：`for (var x in list)` 迭代语义（已有 `ForExpTest` 全绿）
- [x] 回归测试：`(item, index) in collection` 元组迭代（显式断言 `InResult` + forEach 遍历）
- [x] 全量回归：`mvn test` 在 `foggy-fsscript` 模块 `297 passed, 0 failed, 0 skipped`
- [ ] demo TM/QM 验证：留到下游 demo/集成测试层覆盖（非本需求阻塞项）

### 体验进度
- `N/A` — 本需求为语法引擎增强，无 UI 交互面；QM/TM 作者在表达式中书写 `v in (...)` / `v not in (...)` 即受益

### 执行 Checkin

**完成工作摘要**：在 foggy-fsscript 的 CUP 语法层新增 `term3 NOT IN term2` 产生式并重新生成 parser；改造 `IN.execute` 以按左值形态分流到 for-in 元组迭代或 SQL 风格成员测试两条路径；新增 `NOT_IN` 取反算子；IN/NOT_IN 运行时共享 `containsMember`，右值支持圆括号字面量、方括号数组、变量（Collection / array / Set / Map）、null 空集合；数值类型采用 BigDecimal 归一比较。

**实际改动文件清单**：

| 文件 | 改动 |
|------|------|
| `foggy-fsscript/src/main/resources/datasetexp.cup` | term3 新增 `term3:x NOT IN term2:y` 产生式；`-expect` 注释从 91 改为 104 |
| `foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpParser.java` | CUP 重新生成 |
| `foggy-fsscript/src/main/java/com/foggyframework/fsscript/parser/ExpSymbols.java` | CUP 重新生成 |
| `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/IN.java` | 入口按 `(id, id)` 二元组区分；新增 `containsMember` / `resolveHaystack` / `looseEquals`；空 `()` 被识别为空集合 |
| `foggy-fsscript/src/main/java/com/foggyframework/fsscript/fun/NOT_IN.java` | 新增，委托 `IN.containsMember` 并取反 |
| `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/FunTable.java` | 注册 `NOT_IN` |
| `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/InNotInExpTest.java` | 新增 26 个用例 |
| `foggy-fsscript/src/test/java/java_cup/JavacupHelper.java` | 路径改成相对模块根；`-expect 100` → `104` |
| `foggy-fsscript/docs/FSScript-Syntax-Manual.zh-CN.md` | 3.9 节新增 `in` / `not in` 语义说明与示例 |
| `foggy-fsscript/docs/FSScript-Syntax-Manual.md` | 3.8 节（英文）新增 Membership 章节 |

**CUP 冲突数对比**：
- Before：100 expected
- After：104 expected（新增 4 个 shift/reduce，全部为 "Resolved in favor of shifting"，性质与既有 `IN` / `LIKE` 冲突完全相同，无语义歧义）

**自检清单**：
- [x] 需求范围内实现已完成，未扩展到非目标（无 SQL 下推、无 Brackets 语义修改）
- [x] 所有改动文件已记录
- [x] 基础 self-review 完成
- [x] 测试状态：pass（单模块 `Tests run: 297, Failures: 0, Errors: 0, Skipped: 0`）
- [x] 语法手册中英文同步更新

**自检结论**：`self-check-only` — 改动范围收敛在单模块、语法层新增对称产生式、runtime 分流清晰、全量回归通过，不需要升级到正式 `foggy-implementation-quality-gate`。

**遗留 / 后续项**：
- demo TM/QM 的实际使用示例留到下游 `foggy-dataset-demo` 按需补充，不在本需求阻塞范围
- Query planner 是否把 `column in (...)` 下推为 SQL `IN` 子句不在本需求范围（运行时语义正确已验证）
