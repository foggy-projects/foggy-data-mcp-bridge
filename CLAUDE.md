# Foggy Data MCP Bridge Repository Guide

> 开源仓库。禁止提交真实 key、账号密码、token、连接串或包含这些信息的日志与验收证据。

本文是仓库开发导航，不复制完整架构。当前架构以
[docs/architecture/README.md](docs/architecture/README.md) 为唯一入口。

## 1. 文档归属

- `docs/architecture/`：`main` 上当前有效的整体架构、模块边界、生命周期和 SPI。
- `docs/{major-minor}/{full-version}/`：活跃子迭代的需求、workitem、差异设计、迁移、验证和验收；
  例如 `docs/9.5/9.5.5/`。既有 `docs/{full-version}/` 是兼容保留的历史布局，不作为新迭代模板。
- `docs/design/`：不适合进入整体架构的专题设计。
- `docs/dev-guide/`：开发与接口使用规则。
- `docs-site/`：只保留迁移提示；用户文档源在独立 `foggy-data-mcp-docs` 仓库。

架构发生变化时，在同一个变更集中更新 `docs/architecture/`。不要把新的整体架构只写进版本
目录，也不要回写已经签收的历史验收结论。

## 2. 技术与产品边界

- Java 17
- Spring Boot 3.4.5
- Maven 多模块 reactor
- Apache License 2.0

系统把数据源与 TM/QM 语义模型暴露为 MCP 和 Runtime REST 能力，支持关系数据库，并通过
addon 接入 Mongo、cache、vector、pre-aggregation、GraphQL、viewer、Odoo 等可选能力。

Odoo Python 插件已独立到 `foggy-odoo-bridge` 项目。本仓库只保留
`addons/foggy-odoo-bridge-java`，用于网关模式下随 Java launcher 交付 TM/QM 模型。

## 3. 当前模块地图

### Model SPI 与引擎

- `foggy-dataset-model-api`：JDK-only QueryFacade/DTO、backend SPI v2。
- `foggy-dataset-model-core`：不可变 provider catalog 与 fail-closed 校验。
- `foggy-dataset-model-jdbc`：最小 JDBC QUERY adapter。
- `foggy-dataset-model-starter`：Spring 自动装配。
- `foggy-dataset-model-web`：只读 provider/catalog diagnostics。
- `foggy-dataset-model-tck`：provider 契约测试，不是生产运行时能力。
- `foggy-dataset-model-engine`：TM/QM、loader、semantic、compose、pivot、refresh、SQL 和执行。

9.5.0 已删除旧聚合模块 `foggy-dataset-model` 和旧包根
`com.foggyframework.dataset.db.model.*`。不要恢复旧坐标、双包兼容层或
`loader + model.query()` 旁路。

只需要稳定查询能力的调用方依赖 `foggy-dataset-model-api` 的 QueryFacade/DTO。Engine 的
高级类型留在 `com.foggyframework.dataset.model.*` 内部边界。

### 基础、接入与装配

- `foggy-core`、`foggy-bean-copy`：通用基础。
- `foggy-dataset`：JDBC 数据访问、方言与执行基础。
- `foggy-fsscript`：TM/QM 使用的脚本引擎。
- `foggy-mcp-spi`：MCP tool、category、context、progress 契约。
- `foggy-dataset-mcp`：MCP controller、tool discovery/dispatch、审计与语义工具。
- `foggy-runtime-api`：数据源、namespace、Bundle、模型、查询与 compose 管理 API。
- `foggy-mcp-launcher`：可执行 Spring Boot JAR 和最终装配。
- `foggy-dataset-memory-grid-*`：memory-grid 桥接与 DuckDB 实现。
- `addons/*`：可选 backend、客户端、缓存、viewer、Odoo 等。

精确职责和依赖方向见
[模块边界](docs/architecture/module-boundaries.md)。

## 4. 架构不变量

### Model SPI v2

- provider identity 必须唯一。
- descriptor 和 capability 集合必须不可变。
- capability 只能声明真实实现的 typed role/port。
- duplicate、missing、unsupported capability、capability-role mismatch 全部显式失败。
- namespace isolation 是 load/query/refresh 的强制不变量，不是可选 capability。
- TCK 只验证已实现能力，生产模块不得依赖 TCK 作为运行时功能。

当前能力真值：

| Provider | QUERY | MODEL_LOAD | ATOMIC_REFRESH | CACHE_INVALIDATION |
|---|---:|---:|---:|---:|
| JDBC engine | yes | yes | yes | no |
| minimal JDBC adapter | yes | no | no | no |
| query-cache | no | no | no | yes |
| Mongo/vector/preagg 等未接入 provider | no new claim | no | no | no new claim |

详见 [Model SPI v2 与扩展](docs/architecture/extension-spi.md)。

### Namespace、Bundle 与模型发布

- HTTP namespace 主要通过 `X-NS` 传递。
- Dataset Native REST 的兼容优先级：
  `X-NS > body.namespace > default-namespace > empty namespace`。
- Bundle 注册、模型验证和模型刷新是独立动作。
- 同一 namespace 内 TM/QM canonical name 冲突必须在修改前拒绝。
- refresh 必须 candidate build/validate/admit 后原子发布；失败时保留已发布 generation。
- catalog/cache identity 必须纳入 namespace、source/binding revision 和 generation。

详见 [运行时与模型生命周期](docs/architecture/runtime-and-model-lifecycle.md) 与
[Bundle & Namespace](docs/dev-guide/bundle-namespace.md)。

### 权限

- Runtime API auth code 保护管理接口，但不替代客户 IAM 或业务授权。
- `fieldAccess` 是语义字段白名单，在查询规划前校验，无法解析时 fail closed。
- `deniedColumns` 是物理列黑名单，在 SQL 构建后、执行前校验。
- 新查询路径必须同时考虑语义字段和物理列边界，不得因新 controller、cache 或 backend 绕过。

### API 返回契约

- `foggy-runtime-api` 使用 `RuntimeEnvelope` 与稳定生命周期错误码。
- 其他采用 `RX` 的既有 REST controller 继续遵循
  [API Standards](docs/dev-guide/api-standards.md)。
- 不要为了统一外观，在没有兼容性评估时把 Runtime API 改成 RX，或把既有 RX API 改成
  `ResponseEntity`。

## 5. 主要接口

MCP：

- `/mcp/analyst/rpc`、`/mcp/analyst/stream`
- `/mcp/admin/rpc`、`/mcp/admin/stream`
- `/mcp/business/rpc`、`/mcp/business/stream`

Runtime API 统一前缀 `/api/v1`，覆盖 capabilities、datasources、namespace binding、
bundles/resources、models validate/refresh、query、tables、compose 与 FSScript。

健康检查：`/healthz`、`/readyz`、`/info`。

## 6. TM/QM 与数据库

- 示例/测试 TM/QM：`foggy-dataset-demo/src/main/resources/foggy/templates/`
- `.tm`：表模型；`.qm`：查询模型；语法由 FSScript 解析。
- 关系方言实现位于 `foggy-dataset`，核心查询语义位于 model-engine。
- 多数据库或 addon 测试环境见 `foggy-dataset-demo/docker/`。
- 用户一键演示环境见 `docker/demo/`。

闭包层级、pivot、compose、preagg 等专题规则优先以 model-engine 代码、测试和对应专题文档为准，
不要把 engine 实现细节提升为公共 SPI。

## 7. 开发与验证

优先使用 Maven reactor 解决模块依赖，不用 `mvn install` 写入本地仓库：

```bash
mvn -B -ntp -pl foggy-dataset-model-engine -am test -DskipITs
mvn -B -ntp -pl foggy-runtime-api,foggy-dataset-mcp -am test -DskipITs
```

规则：

- 修改代码后运行与风险相称的 compile、unit 和必要 compatibility/integration tests。
- 使用 `-pl ... -am`，避免用过时本地 artifact 掩盖 reactor 问题。
- 不默认跳过测试；只有任务明确只需编译或已有验证预算时才使用 `-DskipTests`。
- 不把 `mvn install` 作为更新 demo fixture 或跨模块开发的常规步骤。
- 查询链、权限注入、模型刷新和 namespace 隔离测试不能只断言 SQL 字符串或 mock 中间对象；
  风险涉及真实执行结果时，应与真实 SQL/fixture 结果比对。
- 不在普通变更中主动运行大型 release authority、全数据库矩阵、tag、release 或 publish；
  这些流程应由版本 workitem/验收范围明确授权。
- Spring context 测试要验证真实 bean/catalog 边界，provider 测试要覆盖 capability 与 fail-closed。

## 8. 版本化交付

- 新功能、BUG、优化或重构先明确目标版本。
- 版本目录采用“主版本索引 + 子迭代目录”：
  - 主版本索引：`docs/{major-minor}/README.md`，例如 `docs/9.5/README.md`，维护该版本线下的
    子迭代入口、状态和重点链接。
  - 活跃、未签收或仍在补手工验收的子迭代：`docs/{major-minor}/{full-version}/`，例如
    `docs/9.5/9.5.5/`。
  - 已完成版本级签收且不再作为活跃执行入口的历史迭代：
    `docs/archive/iterations/{full-version}/`；统一入口为 `docs/archive/iterations/README.md`。
  - 暂缓、待定或尚未确认目标版本的事项：`docs/待定/`；确认版本后再迁入对应子迭代。
- 默认只维持一个活跃子迭代。不得因单个 feature、BUG、阶段或验收批次自动创建新的版本目录；
  版本切换必须由明确的迭代决策驱动。
- 新建子迭代默认只创建必要结构：
  - `README.md`：汇总该迭代状态、结果和 canonical 链接，不复制完整系统说明。
  - `workitems/`：需求、实施计划、治理项、BUG、SPIKE 和 owning task。
  - `progress/`：开发、联调和手工验证进展。
  - `evidence/`：测试、日志、截图和脚本输出等证据。
  - `quality/`、`coverage/`、`acceptance/`、`sql/`：仅在对应工作实际需要时创建。
- 不默认创建 `basic/`、`pay/`、`tms/`、`web/` 等领域目录；仅当当前子迭代已经采用该结构，
  或跨 owner 工作确实需要分层时创建。
- workitem 延续 `FEATURE-*`、`BUG-*`、`SPIKE-*` 等 canonical ticket 命名；配套 progress、
  evidence、quality、coverage 和 acceptance 文档应复用同一 ticket stem 并添加用途后缀。
- 范围、兼容性、验证预算或风险变化时，先同步对应 workitem。
- 正式签收写入当前子迭代的 `acceptance/`，只陈述实际证据，不因未运行的 authority 流程伪造
  `ACCEPTED`。
- 既有 `docs/{full-version}/` 平铺目录本规则生效时不批量迁移，避免破坏历史链接；迁移必须作为
  独立治理 workitem 分批执行并同步修复引用。

## 9. 文档写作

- 面向开发者和 LLM，优先写结论、边界、流程和可执行规则。
- 避免复制代码即可表达的细节、过长背景和跨目录重复说明。
- 当前事实更新 `docs/architecture/`；历史原因、迁移和验收保留在版本目录。
- 新文档应在 YAML frontmatter、`Document Purpose` 或等效的开头区域声明文档作用、
  `doc_type`、`intended_for` 和一句话 `purpose`。若 delivery spec、signoff Skill 或既有模板已
  定义格式，沿用该格式，不额外添加冲突的重复标题。
- `doc_type` 使用与内容相符的稳定类型，例如 `requirement`、`implementation-plan`、
  `code-inventory`、`execution-prompt`、`progress`、`workitem`、`bug`、`evidence`、`quality`、
  `coverage` 或 `acceptance`。
- 文档正文言简意赅，不为满足目录结构创建空文档，也不把同一事实复制到多个入口。
