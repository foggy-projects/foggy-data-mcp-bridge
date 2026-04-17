# P1-Compose Query 多查询编排能力收口 — Code Inventory

## 基本信息

- 目标版本：`8.1.11`
- 上游需求：`docs/8.1.11/P1-Compose Query多查询编排能力收口-需求.md`
- 模块职责：`docs/8.1.11/P1-Compose Query多查询编排能力收口-module-responsibility.md`
- 仓库：`foggy-data-mcp-bridge`

## Code Inventory

### 核心 compose 引擎

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/DataSetResult.java`
- role: 轻量结果容器；暴露 `column / withJoin / joinInMemory / filter / sort / compute`
- expected change: `update`
- notes: 收口对外能力边界；必要时补齐链式调用、资源限制、结果契约说明

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/ComposedDataSetResult.java`
- role: `withJoin()` 延迟执行容器
- expected change: `update`
- notes: 收口请求重建逻辑；补齐与 `dsl()` 一致的语义；补数据库执行和结果包装策略

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/DslQueryFunction.java`
- role: `dsl()` 桥接；构建 `SemanticQueryRequest`
- expected change: `update`
- notes: 与 `ComposedDataSetResult` 共享请求构建逻辑；保留查询次数限制和 compose hints

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/CteComposer.java`
- role: CTE / 子查询 SQL 拼接器
- expected change: `update`
- notes: 仅在需要补公开链式能力或字段选择策略时调整；否则以测试补强为主

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/CteUnit.java`
- role: CTE 单元封装
- expected change: `read-only-analysis`
- notes: 除非链式拼装协议调整，否则保持稳定

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/JoinSpec.java`
- role: JOIN 规格描述
- expected change: `read-only-analysis`
- notes: 除非对外 join 协议变化，否则以复用为主

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/ComposedSql.java`
- role: 组合后 SQL + 参数封装
- expected change: `read-only-analysis`
- notes: 当前主要用于结果校验与日志

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/compose/SqlGenerationResult.java`
- role: `generateSql()` 返回结构
- expected change: `read-only-analysis`
- notes: 如需补响应元数据或 planner 信息，再评估是否扩展

### 语义查询主链

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/SemanticQueryServiceV3.java`
- role: 语义查询服务接口
- expected change: `update`
- notes: 如需要抽公共 request builder 或增强 compose 专用接口，可在此收口

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java`
- role: `queryModel()` / `generateSql()` 主实现
- expected change: `update`
- notes: 需要保障 compose 请求与常规语义请求在前处理和 hints 传递上保持一致

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/QueryFacade.java`
- role: 主查询门面接口
- expected change: `read-only-analysis`
- notes: 仅在 SQL-only 构建契约需要调整时改动

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/service/impl/QueryFacadeImpl.java`
- role: `buildSqlOnly()` 与查询生命周期入口
- expected change: `update`
- notes: 核对 compose 场景的 `beforeQuery`、JDBC-only 限制和错误信息

### 数据库方言

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset/src/main/java/com/foggyframework/dataset/db/dialect/FDialect.java`
- role: 方言能力抽象
- expected change: `read-only-analysis`
- notes: 当前已有 `supportsCte()`，如需更细粒度能力暴露再扩展

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset/src/main/java/com/foggyframework/dataset/db/dialect/MysqlDialect.java`
- role: MySQL 方言特殊处理
- expected change: `update`
- notes: 重点验证 MySQL 5.7 回退策略；如版本判定需细化再修改

### MCP 暴露层

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/tools/ComposeQueryTool.java`
- role: MCP 工具入口与结果转换
- expected change: `update`
- notes: 收口错误提示、返回 envelope、脚本沙箱说明、运行保护

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-mcp/src/main/resources/schemas/compose_query_schema.json`
- role: MCP tool schema
- expected change: `update`
- notes: 若增加 planner hints、结果契约说明或错误示例，需要同步更新

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-mcp/src/main/resources/schemas/descriptions/compose_query.md`
- role: 给 AI 的工具描述
- expected change: `update`
- notes: 文档必须与实现一致，尤其是 `withJoin()` 能力边界和选择规则

### 测试

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/compose/DataSetResultTest.java`
- role: 结果容器与内存操作单元测试
- expected change: `update`
- notes: 补边界和 contract test；对链式能力有结论后同步修改

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/compose/CteComposerTest.java`
- role: CTE / 子查询拼接单元测试
- expected change: `update`
- notes: 补字段选择、参数顺序、方言差异场景

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/service/QueryFacadeImplTest.java`
- role: 查询主链相关测试
- expected change: `update`
- notes: 评估是否补 compose 相关 SQL-only 验证

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/compose/ComposedDataSetResultIntegrationTest.java`
- role: `withJoin()` 数据库集成测试
- expected change: `create`
- notes: 覆盖同库 CTE、MySQL 5.7 子查询回退、请求语义一致性

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/tools/ComposeQueryToolTest.java`
- role: MCP 工具端到端测试
- expected change: `create`
- notes: 覆盖成功脚本、无返回脚本、非法脚本、超过查询次数等场景

- repo: `foggy-data-mcp-bridge`
- path: `foggy-dataset-model/src/test/resources/compose/`
- role: compose 集成测试资源目录
- expected change: `create`
- notes: 放置测试模型、SQL 初始化脚本或 compose 样例

### 文档

- repo: `foggy-data-mcp-bridge`
- path: `docs/dev-guide/compose-query.md`
- role: 开发指南与能力说明
- expected change: `update`
- notes: 必须与最终能力边界、测试覆盖、下一步工作同步

- repo: `foggy-data-mcp-bridge`
- path: `docs/8.1.11/`
- role: 本版本总控文档与执行材料
- expected change: `create`
- notes: 版本评审、执行、测试、验收的统一落点
