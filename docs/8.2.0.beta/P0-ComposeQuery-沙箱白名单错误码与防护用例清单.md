# P0-Compose Query 沙箱白名单错误码与防护用例清单

## 文档作用

- doc_type: security-specification
- intended_for: execution-agent / security-reviewer
- purpose: 为 8.2.0.beta Compose Query 三层白名单（Layer A/B/C）提供统一的错误码定义、错误形态规范、最小防护用例集合，作为实现期 sandbox 测试的硬验收锚点

## 基本信息

- 目标版本：`8.2.0.beta`
- 状态：`draft`
- 关联文档：
  - `P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md § 白名单与隔离`
  - `P0-ComposeQuery-QueryPlan派生查询与关系复用规范-实现规划.md § 白名单与隔离实现规划`

## 错误码体系

### 总体命名约定

- 顶层错误类：`compose-sandbox-violation`
- 子错误码格式：`compose-sandbox-violation/<layer>/<kind>`
- 所有沙箱违规必须 fail-closed，不允许降级放行
- 错误结构至少包含：`code`、`layer`、`kind`、`phase`、`message`、`script_location?`（行号/列号，若可用）

### 错误码定义

#### Layer A · 脚本宿主层

| code | 触发情形 | message 范式 |
|---|---|---|
| `compose-sandbox-violation/A/eval-denied` | 脚本使用 `eval / Function / new Function` | `Dynamic evaluation is not allowed in compose scripts.` |
| `compose-sandbox-violation/A/async-denied` | 脚本使用 `setTimeout / setInterval / Promise / async / await` | `Asynchronous primitives are not allowed in compose scripts.` |
| `compose-sandbox-violation/A/network-denied` | 脚本使用 `fetch / XMLHttpRequest / WebSocket` | `Network primitives are not available in compose scripts.` |
| `compose-sandbox-violation/A/io-denied` | 脚本使用 `File / fs / process / require / import` | `File/process/module primitives are not available in compose scripts.` |
| `compose-sandbox-violation/A/global-denied` | 脚本访问 `globalThis / window / self / Reflect / Object.getPrototypeOf` | `Reflective or global access is blocked.` |
| `compose-sandbox-violation/A/time-denied` | 脚本使用 `Date.now() / new Date() / Date.parse` | `Direct time access is blocked; time must be injected by host.` |
| `compose-sandbox-violation/A/security-param-denied` | 在 `from({...})` / `plan.query({...})` 中显式传入 `authorization / userId / tenantId / roles / namespace / deniedColumns / systemSlice / fieldAccess / policySnapshotId` 等安全参数 | `Security parameters cannot be passed through DSL body; they are bound by ComposeQueryContext.` |
| `compose-sandbox-violation/A/context-access-denied` | 脚本尝试访问 `ComposeQueryContext / principal / authorityResolver` | `ComposeQueryContext is not accessible from scripts.` |

#### Layer B · DSL 表达式层（FSScript）

| code | 触发情形 | message 范式 |
|---|---|---|
| `compose-sandbox-violation/B/function-denied` | 表达式使用 `AllowedFunctions` 白名单之外的函数 | `Function '<name>' is not in the allowed list.` |
| `compose-sandbox-violation/B/derived-plan-function-denied` | `DerivedQueryPlan.columns` 表达式使用仅允许在 `BaseModelPlan` 使用的函数（若未来引入 `RAW_SQL` 等） | `Function '<name>' is not allowed in derived plans.` |
| `compose-sandbox-violation/B/injection-suspected` | 表达式匹配 v1.4 M5 Step 5.3 安全用例中识别的注入模式（sec-01 ~ sec-20） | `Expression contains a blocked injection pattern.` |

#### Layer C · Plan 动词白名单

| code | 触发情形 | message 范式 |
|---|---|---|
| `compose-sandbox-violation/C/method-denied` | 脚本调用 `QueryPlan` 不暴露的方法（`raw / memoryFilter / toArray / forEach` 等） | `Method '<name>' is not part of the QueryPlan public surface.` |
| `compose-sandbox-violation/C/result-iteration-denied` | 脚本尝试遍历 `DataSetResult`（`.items / .rows / iterator` 等访问方式） | `DataSetResult does not support script-side iteration.` |
| `compose-sandbox-violation/C/cross-datasource-denied` | `union` / `join` 跨数据源 | `Cross-datasource composition is not supported in 8.2.0.beta.` |

### 错误 phase 枚举

所有沙箱错误必须填一个 `phase` 标签，用于区分违规发生的阶段：

- `script-parse`：脚本解析阶段（JS lexer/parser）
- `script-eval`：脚本执行阶段（JS 运行时）
- `plan-build`：`QueryPlan` 节点构造阶段
- `schema-derive`：schema 推导阶段
- `authority-resolve`：权限解析阶段
- `compile`：SQL 编译阶段
- `execute`：SQL 执行阶段

### 错误载荷格式

```json
{
  "code": "compose-sandbox-violation/A/eval-denied",
  "layer": "A",
  "kind": "eval-denied",
  "phase": "script-eval",
  "message": "Dynamic evaluation is not allowed in compose scripts.",
  "script_location": { "line": 12, "column": 5 }
}
```

错误消息**不得**回显脚本源代码片段、敏感物理列名或其他用户身份信息。`script_location` 可选，但如果可得必须填。

## 最小防护用例清单

每层至少 5 条用例，覆盖典型攻击面 + 典型合法边界。实施期必须在 `compose-sandbox-violation-tests` 测试集合中落地。

### Layer A · 脚本宿主层

| # | 用例 ID | 输入脚本（节选） | 期望 code |
|---|---|---|---|
| A-01 | `eval-basic` | `eval("from({model: 'X'})")` | `compose-sandbox-violation/A/eval-denied` |
| A-02 | `function-ctor` | `new Function("return from({model:'X'})")()` | `compose-sandbox-violation/A/eval-denied` |
| A-03 | `async-fetch` | `await fetch('http://evil.example/')` | `compose-sandbox-violation/A/async-denied` 或 `network-denied`（二选一，由引擎先触发为准） |
| A-04 | `global-reflect` | `Object.getPrototypeOf(from)` | `compose-sandbox-violation/A/global-denied` |
| A-05 | `date-now` | `from({model:'X', slice:[{field:'t', op:'>', value: Date.now()}]})` | `compose-sandbox-violation/A/time-denied` |
| A-06 | `security-param-injection` | `from({model:'X', authorization:'Bearer hack'})` | `compose-sandbox-violation/A/security-param-denied` |
| A-07 | `security-param-in-derived` | `plan.query({columns:[...], systemSlice:[{field:'orgId', op:'=', value:'other-org'}]})` | `compose-sandbox-violation/A/security-param-denied` |
| A-08 | `context-access` | `const p = __context__.principal` | `compose-sandbox-violation/A/context-access-denied` |
| A-09 | `module-import` | `const fs = require('fs')` | `compose-sandbox-violation/A/io-denied` |
| A-10 | `legal-business-param` | `from({model:'X', slice:[{field:'orgId', op:'=', value: params.orgId}]})` | **合法**，不得抛错；params 是受控通道 |

### Layer B · DSL 表达式层

| # | 用例 ID | 表达式 | 期望结果 |
|---|---|---|---|
| B-01 | `blocked-function-hex` | `columns: ['CHAR(0x41) as x']` | `compose-sandbox-violation/B/function-denied`（或 Layer B 等价拒绝） |
| B-02 | `blocked-function-sleep` | `columns: ['SLEEP(5) as x']` | `compose-sandbox-violation/B/function-denied` |
| B-03 | `injection-union-select` | `slice: [{field:'name', op:'=', value:"a' UNION SELECT ..."}]` | `compose-sandbox-violation/B/injection-suspected` 或被参数化安全处理（对齐 v1.4 sec-01 ~ sec-20） |
| B-04 | `derived-raw-sql` | `plan.query({columns:['RAW_SQL("DROP TABLE x")']})` | `compose-sandbox-violation/B/derived-plan-function-denied`（若 `RAW_SQL` 未实装则归为 `function-denied`） |
| B-05 | `allowed-date-diff` | `columns: ['DATE_DIFF(create_date, write_date) as days']` | **合法**（v1.4 已白名单），不得抛错 |
| B-06 | `allowed-iif-sum` | `columns: ['SUM(IIF(state == 1, 1, 0)) as openCount']` | **合法** |
| B-07 | `blocked-load_file` | `columns: ['LOAD_FILE("/etc/passwd") as x']` | `compose-sandbox-violation/B/function-denied` |

### Layer C · Plan 动词白名单

| # | 用例 ID | 输入脚本（节选） | 期望 code |
|---|---|---|---|
| C-01 | `method-raw` | `plan.raw("select * from sale_order")` | `compose-sandbox-violation/C/method-denied` |
| C-02 | `method-memory-filter` | `plan.memoryFilter(x => x.id > 0)` | `compose-sandbox-violation/C/method-denied` |
| C-03 | `method-for-each` | `plan.forEach(row => console.log(row))` | `compose-sandbox-violation/C/method-denied` |
| C-04 | `result-iterate` | `const res = plan.execute(); res.items.forEach(...)` | `compose-sandbox-violation/C/result-iteration-denied` |
| C-05 | `cross-datasource-join` | `planA.join(planB, {...})` 其中 A/B 来自不同 data source | `compose-sandbox-violation/C/cross-datasource-denied` |
| C-06 | `legal-chain` | `base.query({...}).union(other).query({...}).execute()` | **合法** |
| C-07 | `legal-tosql-debug` | `base.query({...}).toSql()` | **合法**（调试用） |

## 用例落地规范

### 测试组织

- Java 测试集合：`foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/compose/sandbox/`
  - `SandboxLayerATest.java`
  - `SandboxLayerBTest.java`
  - `SandboxLayerCTest.java`
- Python 测试集合：`foggy-data-mcp-bridge-python/tests/compose/sandbox/`
  - `test_sandbox_layer_a.py`
  - `test_sandbox_layer_b.py`
  - `test_sandbox_layer_c.py`
- 每个测试类必须覆盖上表的全部用例 ID，缺一不补即为验收不通过

### 断言要求

每条用例必须断言至少三项：

1. `code` 与表中约定完全一致
2. `layer` 与 `kind` 分别匹配
3. `phase` 属于枚举范围内的合法值

合法用例（标记"**合法**"的）必须断言脚本执行成功且无任何 sandbox 错误抛出，并通过真实 SQL 数据比对（对齐 root CLAUDE.md"集成测试规范：真实 SQL 数据比对"）验证语义正确。

### 跨语言一致性

- Java 与 Python 两仓测试必须使用同一套用例 ID
- 错误码字符串两仓完全一致，不允许本地化或改写
- 两仓测试结果差异（同一用例 ID 产生不同错误码或一边通过一边失败）视为回归

### 错误消息国际化

- `message` 字段为面向开发者/运维的诊断信息，本期统一英文，不做 i18n
- 面向终端用户的提示文案由上游 MCP 工具层二次包装，不进入沙箱错误载荷

## 与 REQ-FORMULA-EXTEND M5 Step 5.3 的关系

Layer B 的 `injection-suspected` 检测直接沿用 v1.4 M5 Step 5.3 已落地的 `FormulaSecurityTest` (Java) / `test_formula_security.py` (Python) 中的 20 条 sec-01 ~ sec-20 规则，不重复建设。

本清单中 Layer B 的 5+ 条用例 **除了引用** sec-* 规则之外，额外补一条 Compose 层专属的 `derived-plan-function-denied` 用例（B-04），确保派生层的额外约束被独立覆盖。

## 验收标准

- 错误码表每一项在实现层都有对应抛出点（`grep` 可查到 `code == "compose-sandbox-violation/*"`）
- 每层至少 5 条用例在 Java / Python 两仓同名落地，且全部通过
- 合法用例（A-10 / B-05 / B-06 / C-06 / C-07）不被误拦截
- 任一沙箱错误发生时，用户不可通过错误消息反推出任何敏感信息（物理列名、`ir.rule` 文本、其他用户身份）
- 本文档作为 8.2.0.beta 验收附录被 `P0-ComposeQuery-QueryPlan派生查询与关系复用规范-需求.md` §验收标准引用
