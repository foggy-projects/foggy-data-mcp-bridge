# Query DSL 输入属性治理

Foggy 在公共 Query Model payload 映射为 `SemanticQueryRequest` 之前检查未知属性，避免 Jackson 或手工映射静默丢弃拼写错误。该检查覆盖 Runtime API、Dataset Native/legacy REST、MCP local accessor、Analytics advanced function 和 authoring query 入口。

Runtime 在 JSON 转为 `JsonNode`/`Map` 之前额外检查同一对象中的重复键。检测必须发生在原始 JSON 边界，因为经过 Jackson 或协议层反序列化后只会保留最后一个值，后续入口无法恢复已折叠的重复键。

MCP schema 会在可行的固定对象上使用 `additionalProperties: false` 提供更早的客户端反馈。服务端检查仍然保留，用于旧版或宽松 schema、REST/CLI，以及 MCP 本地直调等能够到达 Runtime/mapper 的输入。

## 配置

```yaml
foggy:
  dataset:
    query:
      unknown-property-policy: WARN # IGNORE | WARN | STRICT
```

- `WARN`（默认）：忽略普通未知属性，并保留重复键的最后一个值；继续验证或执行，在响应的 `queryInputWarnings` 中返回结构化诊断。
- `STRICT`：在查询规划和执行前拒绝所有未知属性和重复键。
- `IGNORE`：兼容旧行为，不返回普通未知属性或重复键告警。

与认证、权限、namespace、datasource、model、route/routing、mutation 或 governance 等边界相关的未知属性或重复键，在三种模式下都 fail closed。

## 响应契约

`SemanticQueryResponse.warnings` 保持原有 `List<String>` 契约。输入检查诊断使用独立字段：

```json
{
  "queryInputWarnings": [
    {
      "code": "UNKNOWN_QUERY_PROPERTY_IGNORED",
      "path": "$.groupBy[0].grain",
      "message": "Unknown Query DSL property 'grain' was ignored; query results may differ.",
      "suggestedNextAction": "Use a model-defined time grain field; groupBy does not accept grain.",
      "safeToAutoRepair": false,
      "normalizedFragment": {
        "field": "orderDate"
      },
      "docsRef": "query-dsl/group-by",
      "details": {
        "property": "grain",
        "allowedProperties": ["field", "agg"]
      }
    }
  ]
}
```

结构化 JSON 是跨 REST、MCP 和 Function 接口的权威契约。面向人的 Markdown 或文本提示应由客户端根据这些字段渲染，不作为另一套传输协议。诊断不会回显未知属性的原始值。

重复键使用相同的小型结构化契约，`code` 为 `DUPLICATE_QUERY_PROPERTY`，`path` 指向重复字段，`details.occurrences` 表示出现次数；告警不携带任一重复值。

严格模式和受保护属性违规返回全部 `violations`。Runtime API 将其放在
`diagnostics.attributes.violations`，以保持冻结的 `RuntimeError` DTO 二进制契约；Dataset REST
在错误详情中返回 `violations`，MCP 使用 `RX.code=400` 并在错误对象中返回 `violations`。
MCP `tools/call` 同时在 `structuredContent` 中保留该 RX 错误对象；Analytics Function/HTTP
在 `error.violations` 中保留同一组结构化违规。其他非结构化业务失败继续保持原有文本兼容行为。
这些入口都不会调用查询服务。

`groupBy.grain` 仍然不是支持的 DSL。时间分组应直接引用模型中定义的粒度字段，例如 `orderDate$month`，不会自动猜测或改写语义。
