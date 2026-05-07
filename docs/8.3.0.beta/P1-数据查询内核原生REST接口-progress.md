---
type: progress
version: 8.3.0.beta
req_id: P1-数据查询内核原生REST接口
priority: P1
status: ready-for-review
last_updated: 2026-05-07
---

# 数据查询内核原生 REST 接口 — Progress

## 关联规范文档

- 需求：`P1-数据查询内核原生REST接口-需求.md`
- 相关历史：`P2-list_models模型发现入口与get_metadata隐藏-需求.md`

## 当前阶段判断

- 当前阶段：`ready-for-review`
- 当前目标：让 `query-cloud-service` 等业务微服务只依赖 `foggy-dataset-model` 时，也能直接暴露 Query Model、Compose Script、List Models 三类原生 HTTP 端点
- 当前范围：仅 Java `foggy-dataset-model` 模块；MCP 模块无代码改动

## Step 追踪

| step | 内容 | 状态 | 备注 |
|---|---|---|---|
| S0 | 确认合适版本目录并创建需求/progress 文档 | completed | 使用 `docs/8.3.0.beta` |
| S1 | 新增 `NativeDatasetController` | completed | `/semantic/v3/dataset/query`、`/compose`、`/list_models`、`/list-models`、`/models` |
| S2 | 新增 Query payload mapper | completed | 将 MCP `query_model_v3_schema.json` 的 `payload` 平铺映射为 `SemanticQueryRequest` |
| S3 | 新增内核 Compose REST 执行服务 | completed | 直接调用 `ScriptRuntime`，复用 compose planner/runtime 能力，无 MCP dispatcher |
| S4 | 新增内核模型 catalog 服务 | completed | MCP-free 扫描 `.qm`，返回轻量 discovery catalog |
| S5 | 编译验证 | completed | `mvn -pl foggy-dataset-model -am -DskipTests compile` 成功 |

## 实现摘要

| 文件 | 说明 |
|---|---|
| `NativeDatasetController.java` | 原生 REST Controller，统一返回 `RX` |
| `SemanticQueryPayloadMapper.java` | 将 HTTP body 中的查询 payload 转成 `SemanticQueryRequest` |
| `NativeComposeQueryService.java` | 内核 compose script 执行入口，支持注入 `AuthorityResolver` |
| `SemanticModelCatalogService.java` | 内核模型发现服务，构建 `format/content/data` catalog |

## 测试与验证

| 类型 | 命令 / 证据 | 结果 |
|---|---|---|
| Java compile | `mvn -pl foggy-dataset-model -am -DskipTests compile` | BUILD SUCCESS |
| MCP 依赖检查 | `rg -n "foggy-dataset-mcp\|Mcp\|mcp" <新增内核源码>` | 0 matches |

## 对接提示

- 网关 Feign Query 路径：`POST /semantic/v3/dataset/query`
- 网关 Feign Compose 路径：`POST /semantic/v3/dataset/compose`
- 网关 Feign List Models 路径：优先 `POST /semantic/v3/dataset/list_models`；只读无参场景可用 `GET /semantic/v3/dataset/models`
- 如业务侧需要强制字段/行权限，应在请求体传 `visibleFields`、`deniedColumns`、`systemSlice`，或在宿主应用中注册 `AuthorityResolver` Bean。

## 风险与后续

- 当前 compose native REST 在宿主未注入 `AuthorityResolver` 时采用空权限绑定，适合网关前置鉴权场景；如果服务本身直接暴露公网或跨租户入口，必须补宿主级权限 Resolver。
- 本次只做编译验证，未启动实际 Spring 容器做 HTTP smoke test；后续网关联调时建议覆盖三条端点的最小请求。
