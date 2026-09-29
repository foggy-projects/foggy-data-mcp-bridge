# dataset.open_in_viewer

为明细或分组汇总查询生成交互式浏览链接。原查询的 `slice`、显式 `having`、`groupBy`、`orderBy`、`calculatedFields` 和 `extData` 会用于浏览器重查。

## 使用场景

用户要查看报表、核对明细、分页和自主筛选时可使用；明细查询和 `groupBy` 聚合结果都支持。若 AI 只需要少量数值，可直接用 `dataset.query_model`。

## 参数

支持 `columns`、`slice`、`having`、`orderBy`、`groupBy`、`calculatedFields`、`extData`。`slice` 是明细过滤（WHERE），聚合度量过滤必须显式写在 `having`，不会自动转换。`extData` 不应包含密码或 token。

`groupBy` 和 `orderBy` 同时接受 `dataset.query_model` 的字符串简写与对象形式，例如 `"groupBy":["workStation$caption"]`、`"orderBy":["-waybillCount"]`，或 `"groupBy":[{"field":"workStation$caption"}]`、`"orderBy":[{"field":"waybillCount","dir":"desc"}]`。

**重要：slice必须提供至少一个过滤条件**

```json
{
  "model": "DemoWaybillQueryModel",
  "payload": {
    "columns": ["openingSite", "waybillCount", "pieceCount", "weightKg"],
    "slice": [{"field": "businessDate", "op": "=", "value": "2026-09-26"}],
    "groupBy": [{"field": "openingSite"}],
    "orderBy": [{"field": "waybillCount", "dir": "desc"}],
    "having": [{"field": "waybillCount", "op": "[]", "value": [1, 2]}]
  },
  "title": "当日网点货量"
}
```

## 返回值

```json
{"viewerUrl": "https://example.com/open#<one-time-code>", "queryId": "abc123", "expiresAt": "2026-09-27T10:00:00Z", "queryExpiresAt": "2026-09-27T10:00:00Z", "viewerLinkExpiresAt": "2026-09-27T09:00:00Z"}
```

`expiresAt` 为兼容字段，等于 `queryExpiresAt`。`viewerLinkExpiresAt` 是已知时打开交接链接的截止时间；`queryExpiresAt` 是当前查询上下文的到期时间。启用登录交接的部署可在登录后延长查询上下文，网页以更新后的时间为准。交接链接可能一次性且绑定 MCP 凭证对应的用户；过期或已使用时从 Harness 重新生成。不要公开转发链接中的交接码。
