---
doc_type: contract-truth-pack
intended_for: Engine owners, Runtime API owners, MCP/CLI/Console integrators
purpose: Freeze the public, legacy-adapter, internal and deprecated Engine entry contracts for P0-2
status: baseline-recorded
date: 2026-08-28
---

# Stream A P0-2：Engine Contract Truth Pack

本文件冻结当前源码可证明的 Engine 公共合同。数字版本只记录源码事实，不把候选制品写成正式发布：bridge 当前为 `9.3.0-SNAPSHOT`，CLI 源码基线为 `0.1.22`；本轮 0.1.22 测试不构成 0.1.23 发布证据，CLI 发布版本和 Release/BOM 仍以 Stream B Truth Pack 为权威。

机器可读矩阵见同目录的 `engine-contract-matrix-v1.json`。可执行协议/适配测试见 `scripts/contract-truth/engine-contract-conformance.py`。

## 归属决策：members

采用 **stable Runtime API adapter + preserved legacy adapter**：

- canonical public route：`POST /api/v1/members/{model}/{dimension}`，action=`MEMBER_QUERY`，capability=`members.list`。
- implementation owner：Runtime API 只负责路径、namespace、Authorization 透传、`RuntimeEnvelope` 和稳定错误；实际成员解析、synthetic member QM、行/字段权限和查询执行继续由 Engine `JdbcService` 负责。
- compatibility route：`POST /jdbc-model/dimension/v2/{model}/{dimension}` 保留为 `legacy-adapter`；旧 `queryDimensionData` route 继续标记 deprecated。
- 不做 `/api/v1` 到另一套成员语义的复制；稳定 adapter 和 legacy route 必须共享同一个 `JdbcService.queryDimensionData(form, authorization, namespace)`。
- `Authorization` 是不透明 data-plane 值；`X-Foggy-Runtime-Code` 仍只属于 Runtime management auth，不得用于业务数据权限。

源码依据：

- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/RuntimeApiRoutes.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeQueryController.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeCapabilitiesController.java`
- `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/controller/RuntimeMembersController.java`
- `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/controller/DimensionDataStoreController.java`
- `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/service/impl/JdbcServiceImpl.java`
- `foggy-dataset-model-engine/src/main/java/com/foggyframework/dataset/model/plugins/result_set_filter/ModelPermissionEnforcementStep.java`

## Contract rules

1. `/api/v1/*` 是 Runtime public contract；Runtime Console 和 CLI 只能消费这些公开 routes，不能直接调用 Engine Java 类型或私有端口。
2. MCP `/mcp/{role}/rpc` 与 `/stream` 是独立 transport；MCP tool 名称和 JSON-RPC envelope 不能与 Runtime route 做隐式 alias。当前 bridge 源码证明 legacy JSON-RPC；Streamable HTTP 的握手能力仍需单独验证。
3. namespace precedence 固定为 `X-NS > body.namespace > configured default-namespace > empty namespace`；path namespace（如 datasource binding）不替换请求上下文 namespace。
4. management auth 和 data-plane auth 分离。query、model describe/list、members、Compose 的 `Authorization` 只作为 opaque data-plane input；bundle/datasource/model lifecycle/fsscript 管理动作由 Runtime auth gate 保护。
5. model `DISCOVER`、`DESCRIBE`、`VALIDATE`、`EXECUTE`、`MEMBER_QUERY` 是不同动作。能够发现模型不等于可以执行查询。
6. 错误 fingerprint 只使用稳定 `success=false`、`error.code`、`error.phase`、target kind，不包含 token、SQL 参数或 datasource secret。
7. legacy route 保留兼容，不提升为新能力；移除或改写前必须有 owner、受影响调用者、迁移、回滚和 old/new contract tests。

## Route/action/auth/namespace/capability matrix

| Surface | Route/command | Action | Auth | Namespace | Capability | Classification |
|---|---|---|---|---|---|---|
| Runtime API | `GET /api/v1/capabilities` | capabilities | public read；`management-all` 可整体保护 | none | `runtime.capabilities` | public |
| Runtime API | `GET /api/v1/models` | `DISCOVER` | optional opaque `Authorization` | `X-NS` | `models.list` | public |
| Runtime API | `POST /api/v1/models/{model}/describe` | `DESCRIBE` | optional opaque `Authorization` | `X-NS`/body | `models.describe` | public |
| Runtime API | `POST /api/v1/query/{model}/validate` | `VALIDATE` | optional opaque `Authorization` | `X-NS` > body | `query.validate` | public |
| Runtime API | `POST /api/v1/query/{model}/execute` | `EXECUTE` | optional opaque `Authorization` | `X-NS` > body | `query.execute` | public |
| Runtime API | `POST /api/v1/query/{model}/explain` | `DESCRIBE`/explain | optional opaque `Authorization` | `X-NS` > body | `query.explain` | public |
| Runtime API | `POST /api/v1/members/{model}/{dimension}` | `MEMBER_QUERY` | optional opaque `Authorization` | `X-NS` | `members.list` | public |
| Runtime API | `POST /api/v1/compose/{validate,preview,execute}` | governed compose action | optional opaque `Authorization` for data plane | `X-NS`/script context | `compose.*` | public |
| Runtime API | `/api/v1/authoring/**` | workspace/release lifecycle | Runtime management auth; authoring always protected | explicit workspace namespace | `authoring.*` | public management |
| Runtime Console | same-origin `/api/v1/**` | same as Runtime API | session/runtime auth; no independent engine authority | page/header namespace | capability-dependent | public feature adapter |
| CLI source baseline 0.1.22 (release authority: Stream B) | `models list/describe`, `query validate/execute/explain`, `members list`, `compose *` | same mapped data-plane action; members currently uses legacy path | `--authorization` only data plane; `--auth-code` management | `--namespace` → `X-NS` | query/models/compose capability preflight; members has no preflight and remains compatibility route | public client |
| MCP analyst | `POST /mcp/analyst/rpc` JSON-RPC `server/discover`, `tools/list`, `tools/call` | tool-specific governed action | role/policy/auth middleware | tool context / `X-NS` where supported | MCP tool catalog | public adapter |
| MCP role stream | `POST /mcp/{admin,analyst,business}/stream` | JSON-RPC stream | role/policy/auth middleware | role/tool context | stream capability | legacy-adapter; modern handshake not frozen |
| Engine native REST | `/semantic/v3/dataset/{query,list_models,describe_model_internal}` | semantic query/catalog | `Authorization` and engine policy | `X-NS` > body | native semantic capabilities | legacy-adapter |
| Engine legacy | `POST /jdbc-model/dimension/v2/{model}/{dimension}` | `MEMBER_QUERY` | optional opaque `Authorization` | `X-NS` | none; consumed by compatibility clients | legacy-adapter |
| Engine legacy | `POST /jdbc-model/dimension/queryDimensionData` | `MEMBER_QUERY` | optional opaque `Authorization` | `X-NS` | none | deprecated |
| Runtime legacy | `/api/bundles/**` | bundle management | Runtime auth compatibility | body/default | none | deprecated |
| Engine Java SPI | `QueryFacade` / `JdbcService` | internal execution | caller-owned context; no HTTP contract | explicit context | typed SPI | internal |

完整字符串、证据路径和 source version 见 JSON 矩阵；本表不扩展 Engine 所有内部类为公共 API。

## Same-fixture conformance harness

`engine-contract-conformance.py` 使用一个不含业务机密的 deterministic fixture，检查：

- Runtime query 与 CLI query 的 request/result fingerprint 一致；
- Runtime model metadata 与 MCP `dataset.list_models` 的 projection fingerprint 一致；
- stable members route、legacy members adapter 与 CLI `members list` 的 result fingerprint 一致；
- 缺失 data-plane authorization 返回相同稳定 error fingerprint；
- `X-NS` 覆盖 body namespace，缺失 header 时回退 body namespace；`tenant-a` 与 `tenant-b` 的结果 fingerprint 不串；
- matrix 至少声明 `query.execute`、`models.list`、`members.list` 和 namespace precedence。

默认 harness 是本地协议合同测试，不宣称真实 Java engine、datasource、权限 resolver 或 SQL 正确性已通过。真实 engine 证据仍需 Maven/真实数据库 fixture；遇到私有 Nexus 依赖阻塞时必须报告未验证风险。

示例：

```text
python scripts/contract-truth/engine-contract-conformance.py --cli-root D:/workspace/foggy-runtime-cli
```

## 聚焦验证与残余门禁

- Runtime API：先运行 `mvn -o -B -ntp -pl foggy-runtime-api -DskipITs -Dtest=RuntimeMembersControllerTest,RuntimeCapabilitiesControllerEnabledTest test`；完整 reactor 仍需在可解析全部私有 Nexus 父 POM/构件的环境中执行。
- CLI：`PYTHONPATH=src python -m unittest discover -s tests`（或使用 source 的 editable 安装）；不能从已安装旧 site-package 推导 0.1.23 发布事实。
- Console：只验证已有 frontend unit tests；Console 不拥有独立合同。
- members：稳定 route controller test、capability test 和离线 legacy adapter parity 已通过；CLI 仍固定在 legacy route，待 Engine immutable release 后再单独升级 CLI 并提供 old/new consumer tests。
- 正式 CLI artifact、digest、Release/BOM、安装验证以 Stream B Truth Pack 为准；本文件不替代 P0-1。

### 本轮实际执行证据（2026-08-28）

- `python scripts/contract-truth/engine-contract-conformance.py --cli-root D:/workspace/foggy-runtime-cli-compare`：通过；覆盖 Runtime/CLI query、Runtime/MCP metadata、stable/legacy/CLI members、auth failure、namespace precedence/isolation。
- `PYTHONPATH=src python -m unittest discover -s tests`（`foggy-runtime-cli-compare`）：`115 tests`，`OK`。该结果仅是当前 `0.1.22` source baseline 证据，不是 `0.1.23` release 证据。
- `npm run test:unit`（Runtime Console frontend）：`11` files、`38` tests passed。
- Runtime API 聚焦 Maven：126 个主源码、39 个测试源码完成 Java 17 编译；`RuntimeMembersControllerTest` 2/2、`RuntimeCapabilitiesControllerEnabledTest` 51/51，共 53 tests，0 failures/errors。
- 首次集成测试发现新增 controller 缺少测试上下文 `JdbcService` Bean；在现有 Spring test 中加入 `@MockitoBean` 后重跑通过。完整 `-am` reactor 的隔离缓存仍缺 `org.sonatype.oss:oss-parent:pom:5` 等远程父 POM，因此该环境门禁不冒充全 reactor 通过。
- 未修改 `integrations/deepseek-harness`、FDP、FAP 或 LC-FAP；CLI 的候选 stable-route 改动已撤回，避免未发布 Engine 与现有 CLI 制品漂移。
