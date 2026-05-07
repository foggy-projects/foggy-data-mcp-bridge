# P1-数据查询内核原生 REST 接口-需求

## 文档作用

- doc_type: workitem / requirement
- intended_for: execution-agent / reviewer / gateway-integrator
- purpose: 记录 `foggy-dataset-model` 内核模块去 MCP 强依赖后的原生 HTTP 查询入口，供上层业务服务只依赖内核包时暴露 Query Model、Compose Script、List Models 能力
- 目标版本: 8.3.0.beta
- 需求等级: P1
- 状态: ready-for-review
- source type: architecture decoupling / API-contract
- 责任仓: foggy-data-mcp-bridge
- 责任模块: foggy-dataset-model
- 记录时间: 2026-05-07

## 背景

TMS 业务侧 Navigator Agent 已将外层调用链从 MCP JSON-RPC 协议迁移到业务网关自带的 Agent Function 体系。网关层负责路由、鉴权和函数调用协议，数据查询内核只需要提供稳定的业务 REST API。

原实现中，`dataset.compose_script` 与 `dataset.list_models` 的可用入口主要集中在 `foggy-dataset-mcp` 的工具分发和 Controller 层。业务微服务如果只引入 `foggy-dataset-model`，只能复用有限测试接口，无法直接通过标准 HTTP 触达 Compose 和模型发现能力。

## 目标

1. 在 `foggy-dataset-model` 内新增纯 REST Controller，不依赖 `foggy-dataset-mcp`。
2. HTTP RequestBody 直接使用原 MCP tool schema 的业务 payload，不包 JSON-RPC 壳。
3. 所有接口按项目 REST 约定返回 `RX`。
4. Query、Compose、List Models 三类能力在内核模块内闭环。

## API 契约

### Query Model

- Method: `POST`
- Path: `/semantic/v3/dataset/query`
- Body: 对齐 `query_model_v3_schema.json`

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": ["salesAmount", "storeName"],
    "slice": {
      "businessDate": {
        "op": "between",
        "value": ["2026-05-01", "2026-05-07"]
      }
    },
    "groupBy": ["storeName"],
    "pagination": {
      "page": 1,
      "size": 20
    }
  },
  "mode": "execute"
}
```

执行逻辑：将 `payload` 映射为 `SemanticQueryRequest`，调用 `SemanticServiceV3.queryModel(model, request, context)`。

### Compose Script

- Method: `POST`
- Path: `/semantic/v3/dataset/compose`
- Body: 对齐 `compose_query_schema.json`

```json
{
  "script": "var sales = Query.query('FactSalesQueryModel').columns(['storeName', 'salesAmount']).toSql(); return sales;",
  "preview": false,
  "params": {
    "tenant": "demo"
  }
}
```

执行逻辑：构造 `ComposeQueryContext` 后通过 `ScriptRuntime.runScript(...)` 执行 DSL 脚本，返回 `status/data/error` 结构。运行期优先使用业务侧注入的 `AuthorityResolver` Bean；未注入时采用允许执行的空权限绑定，适用于网关层已经完成鉴权的部署方式。

### List Models

- Method: `POST`
- Path: `/semantic/v3/dataset/list_models`
- Alias: `POST /semantic/v3/dataset/list-models`
- Method: `GET`
- Path: `/semantic/v3/dataset/models`
- Body: 可选

```json
{
  "format": "json",
  "modelNames": ["FactSalesQueryModel"],
  "fieldLimit": 20,
  "visibleFields": {
    "FactSalesQueryModel": ["storeName", "salesAmount"]
  },
  "deniedColumns": {
    "FactSalesQueryModel": ["internalCost"]
  }
}
```

执行逻辑：扫描当前 `SystemBundlesContext` 下可用 `.qm` 模型，加载模型元数据，返回轻量 discovery catalog。返回结构保留 `format/content/data`，其中 `data` 包含 `models/count/recommendedNext/items`。

## 权限与上下文

- `X-Namespace` / `namespace`: 指定模型命名空间。
- `Authorization`: 透传到 `SemanticRequestContext.authToken`。
- `X-User-Id` / `userId`: 作为 compose 上下文 principal。
- `visibleFields` / `fieldAccess`: 可限制字段可见范围。
- `deniedColumns`: 可限制物理列访问。
- `systemSlice`: 可注入系统行级过滤条件。

## 非目标

- 不替换或删除 MCP 入口。
- 不变更 MCP tool schema。
- 不在本次需求内定义网关 Feign Client。
- 不引入新的跨模块依赖或新的协议包装。

## 验收标准

| AC | 验收点 | 状态 |
|---|---|---|
| AC-1 | `foggy-dataset-model` 提供 `/semantic/v3/dataset/query` | passed |
| AC-2 | `foggy-dataset-model` 提供 `/semantic/v3/dataset/compose` | passed |
| AC-3 | `foggy-dataset-model` 提供 `/semantic/v3/dataset/list_models` 与 `/semantic/v3/dataset/models` | passed |
| AC-4 | 新增实现不依赖 `foggy-dataset-mcp` | passed |
| AC-5 | Reactor-aware 编译通过 | passed |

