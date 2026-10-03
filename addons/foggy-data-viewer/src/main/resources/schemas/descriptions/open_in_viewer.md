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
    "columns": ["workStation$caption", "waybillCount", "pieceCount", "weightKg"],
    "slice": [{"field": "businessDate", "op": "=", "value": "2026-09-26"}],
    "groupBy": [{"field": "workStation$caption"}],
    "orderBy": [{"field": "waybillCount", "dir": "desc"}],
    "having": [{"field": "waybillCount", "op": "[]", "value": [1, 2]}]
  },
  "title": "当日网点货量"
}
```

## 返回值

```json
{"viewerUrl": "https://example.com/data-viewer/open#<opaque-link-secret>", "queryId": "abc123", "expiresAt": null, "queryExpiresAt": null, "viewerLinkExpiresAt": null}
```

启用 `foggy.data-viewer.link-authorization` 时，链接关联经过验证的 MCP 服务身份及原查询范围，浏览器直接签发短期探查会话，无需账号密码。链接可以重复打开，默认永久有效，三个到期字段为 `null`；配置有限链接有效期后返回截止时间。浏览器会话默认从签发起 24 小时，到期可用原有效链接重新进入。链接到期、撤销或 MCP 服务身份/权限失效会阻止已签发会话的后续访问。不提供个人身份或个人级审计。原始 MCP token 不会返回给浏览器。不要公开转发链接密钥。
