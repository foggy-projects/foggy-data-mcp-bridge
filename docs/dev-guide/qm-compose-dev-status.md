# QM Compose 开发状态文档

> 最后更新：2026-03-17

## 一、实现进度（对照 ROADMAP.md）

| Phase | 状态 | 说明 |
|---|---|---|
| **Phase 1 — MVP（dsl() + ID 下推）** | ✅ 已完成 | dsl() 桥接、DataSetResult 基础包装、ComposeQueryTool MCP 工具、AI 沙箱 |
| **Phase 2 — CTE 组合** | ✅ 已完成 | toSql 模式、CteComposer、withJoin 延迟执行、FDialect.supportsCte() |
| **Phase 3 — 二次计算** | ✅ 已完成 | filter() / sort() / compute() 内存操作 |

### ROADMAP 中 QM Compose 以外的条目

| 条目 | 状态 |
|---|---|
| 日期维度表 (`dim_date`) | 🔲 未实现 |
| QM 预定义计算指标 | 🔲 未实现 |

---

## 二、变更文件清单

### 新建文件（6 个）

| 文件 | 包路径 | Phase | 职责 |
|---|---|---|---|
| `SqlGenerationResult.java` | `engine/compose/` | P2 | 持有生成的 SQL + params（不执行） |
| `CteUnit.java` | `engine/compose/` | P2 | CTE 单元（alias, sql, params, columns） |
| `JoinSpec.java` | `engine/compose/` | P2 | JOIN 描述（type, leftKey, rightKey, columns） |
| `ComposedSql.java` | `engine/compose/` | P2 | 组合后的最终 SQL + 合并参数 |
| `CteComposer.java` | `engine/compose/` | P2 | 纯字符串拼接层（CTE / 子查询回退） |
| `ComposedDataSetResult.java` | `engine/compose/` | P2 | 延迟执行组合查询，实现 PropertyFunction |

### 修改文件（9 个）

| 文件 | 模块 | 改动摘要 |
|---|---|---|
| `DataSetResult.java` | dataset-model | Phase 1 创建 → P2 加 withJoin/ComposeContext/dslParams → P3 加 filter/sort/compute |
| `DslQueryFunction.java` | dataset-model | Phase 1 创建 → P2 加 DataSource 注入、设置 composeContext/dslParams |
| `JdbcQueryModelImpl.java` | dataset-model | P2: 新增 `generateSql()` 方法 |
| `QueryFacade.java` | dataset-model | P2: 新增 `buildSqlOnly()` 接口声明 |
| `QueryFacadeImpl.java` | dataset-model | P2: 实现 `buildSqlOnly()` |
| `SemanticQueryServiceV3.java` | dataset-model | P2: 新增 `generateSql()` 接口声明 |
| `SemanticQueryServiceV3Impl.java` | dataset-model | P2: 实现 `generateSql()` |
| `FDialect.java` | dataset | P2: 新增 `supportsCte()` 默认 true |
| `MysqlDialect.java` | dataset | P2: 覆写 `supportsCte()` 返回 false（MySQL 5.7） |
| `ComposeQueryTool.java` | dataset-mcp | P1 创建 → P2 加 DataSource 注入 |

---

## 三、测试覆盖

### 现状：专项单元测试覆盖率 = 0

| 组件 | 需要的测试类型 | 优先级 |
|---|---|---|
| `CteComposer` | 纯单元测试（无需 DB），验证 CTE/子查询 SQL 拼接 | **P0** |
| `DataSetResult.filter/sort/compute` | 纯单元测试（无需 DB），验证内存操作 | **P0** |
| `DataSetResult.column/toList/first/size/value` | 纯单元测试 | P1 |
| `ComposedDataSetResult` | 集成测试（需 DataSource + QM），验证延迟执行 | P2 |
| `SqlGenerationResult` | 集成测试（通过 QueryFacade 间接验证） | P2 |
| `ComposeQueryTool` | MCP 层集成测试 | P3 |

### 已有测试影响

运行 `mvn test -pl foggy-dataset-model -Dspring.profiles.active=sqlite`：

- **631 tests run, 1 failure**
- 失败测试：`AdvancedAnalyticsTest.testDslOverrideQmPredefined`
- **原因**：此失败与 QM Compose 无关，是 main 分支 commit `5f48802`（"fix: handle AI误传预定义calculatedFields冲突"）引入的已有问题
- **验证**：QM Compose 未修改 `JdbcModelQueryEngine.java`，`git diff HEAD` 确认无改动

---

## 四、下一步工作

1. **修复已有测试失败**：`AdvancedAnalyticsTest.testDslOverrideQmPredefined`（main 上已有，非 Compose 引入）
2. **补充 QM Compose 单元测试**：
   - `CteComposerTest` — CTE 模式 / 子查询模式 / 多表 JOIN / 参数合并
   - `DataSetResultTest` — filter / sort / compute / column / 链式调用
3. **更新 AI 文档**：`compose_query.md` 补充 withJoin / filter / sort / compute API 说明
4. **端到端验证**：通过 MCP 工具实际运行 fsscript 编排脚本
