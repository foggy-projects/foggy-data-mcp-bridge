---
doc_type: design
intended_for: DataViewer, query engine, generator and application implementers
purpose: Record the agreed flat group table MVP and the deferred exact count decision.
status: browser-verified-in-demo
related_issue: https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/137
date: 2026-09-26
---

# DataViewer 平铺分组表 MVP

## 范围

DataViewer 表格增加 `normal` 和 `groupBy` 两种模式。`groupBy` 的 QM、分组键和展示度量由页面固定；每行表示一个分组，不做透视。按月统计时，TM/QM 必须提供明确的月份维度，并把它放进当前 `groupBy` 与返回列。每条分组行的 QM 聚合度量正常显示。

分组模式的表头条件统一进入 `having`。可筛选字段限于当前 `groupBy` 的维度键与 QM 聚合度量；普通明细字段不能筛选。它可以表达“月份 = 2026-03”和“分组收入 > 10000”，不能表达“只让 3 月 10～20 日的明细参与 3 月指标”。页面固定条件和权限仍通过原查询链生效，前者可以使用 `slice`；用户不选择过滤阶段。

分组模式不展示自定义查询方案、ListPreset、默认查询配置、查询面板，也不显示跨全部分组的合计行。普通模式保持现有行为。组件保留 `executeQuery` 独立分页查询入口，导出调用方需用相同的 `groupBy`、`having`、权限、排序逐页导出全部匹配组，并遵守导出行数上限。

## 分页决定

本期不增加引擎准确分组总条数。分组查询发送 `returnTotal=false`；Viewer 直连接口读取 `limit+1` 条，返回最多 `limit` 条及准确的 `hasNext`。表格仅显示当前页码、上一页、下一页和每页条数，不显示总页数或虚构的总条数；`total=-1` 只作为未知总数的传输值。末页恰好满页时，额外一条探测仍能正确返回 `hasNext=false`。

示例：

```json
{
  "columns": ["stationId", "recognitionMonth", "allocationAmount"],
  "groupBy": [{"field": "stationId"}, {"field": "recognitionMonth"}],
  "having": [
    {"field": "recognitionMonth", "op": "=", "value": "2026-03"},
    {"field": "allocationAmount", "op": ">", "value": 10000}
  ],
  "orderBy": [{"field": "recognitionMonth", "dir": "asc"}, {"field": "stationId", "dir": "asc"}],
  "start": 0,
  "limit": 30,
  "returnTotal": false
}
```

## 分层合同

| 层 | 本期行为 |
| --- | --- |
| 引擎 | `having` 仅接受聚合度量或当前 `groupBy` 中的维度键；拒绝非分组明细字段和表达式条件。继续保留 COUNT_DISTINCT 的 `totalData` 不可合并门禁。 |
| Viewer | 直连请求透传 `groupBy`、`having`、`returnTotal`；普通请求仍默认 `returnTotal=true`。无总数请求多取一条并返回 `hasNext`。 |
| 前端组件 | `tableMode='groupBy'` 与固定 `groupBy`；表头条件进入 `having`，固定业务条件可留在 `slice`；使用无总数分页，隐藏底部合计和方案入口。 |
| 生成器 | 生成的 wrapper 透传模式和分组配置，生成的 API 透传查询字段。旧生成组件无需迁移。 |
| 导出消费方 | 使用组件的 `executeQuery` 分页读取，依据 `hasNext` 停止；上限超出时失败，不能把当前页当作完整导出。 |

分组键、展示列和排序应由应用提供稳定的组合。组件在用户排序后追加未出现的分组键作为稳定排序字段。用户隐藏一列不能改变分组粒度。切换表头条件、排序或每页条数时回到第一页。`groupBy` 缺失时分组模式应明确报错。

## 上游接入门槛

当前源码已覆盖组件、Viewer 直连接口、引擎和生成器，但不能只升级前端 npm 包就交给 TMS 使用。目标应用需要同时完成：

1. 发布带本能力的新 DataViewer 前端版本，并部署包含 Viewer 与引擎改动的对应 Java 产物。确认目标环境中的两端合同一致。
2. 迁移 TMS 在 `1.0.1-beta.57` 上的自定义查询展示字段上限补丁（50 改 120）；升级版本时不能让现有普通表退回 50 字段。
3. 将 TMS Excel 导出改为未知总数时按 `hasNext` 逐页读取，并对空页、取消及最大行数作明确处理；当前按 `total` 停止的实现会漏掉第二页以后数据。
4. 目标分组表的 TM/QM 需具备明确的分组维度和聚合度量；基于新版生成器重新生成该表的 API/wrapper，透传 `groupBy`、`having`、`returnTotal`、`hasNext`，不得手改既有 generated 文件。
5. 在 TMS 真实模型与权限链路验证分组键 HAVING、聚合度量 HAVING、跨页分页和多页导出。现有组件单测及引擎集成测试不能替代这项应用验收。

完成这些项目后再切换 TMS 依赖版本。普通表保持 `normal` 默认模式，不要求迁移既有页面。

## 本模块验证（2026-09-26）

`verification-app` 已加入固定分组汇总页，使用电商演示库的 `FactSalesQueryModel`，按销售年、月、门店分组。Edge 浏览器通过本地 Vite 代理直连当前源码构建的 Foggy Viewer 和查询引擎，验证了分组维度及聚合度量的表头条件都进入 `having`、`slice` 保持空、`returnTotal=false` 返回 `total=-1` 与 `hasNext`、翻页和 CSV 逐页导出。独立拦截数据的浏览器用例覆盖 205 个分组跨三页导出；真实演示库用例验证了接口响应和页面交互。

这证明模块内的演示模型与浏览器链路，不代表 TMS 的 QM、权限、补丁或 Excel 导出已验收。真实浏览器用例和启动条件见 `addons/foggy-data-viewer/verification-app/README.md`。

## 后续：准确分组总条数

需要总条数时，不能从 `hasNext`、当前页数或 `withSubtotals` 推算，也不能直接使用现有 `returnTotal=true`：它还会请求全量 `totalData`，遇到 COUNT_DISTINCT 等不可合并度量时会失败。建议另行评估独立 `returnCount`：在相同权限、固定条件、`groupBy` 和 `having` 后，对分页前的最终分组结果做 `COUNT(*)`，不计算跨组度量合计。计数 SQL 必须复用结构化查询计划和绑定参数，不通过字符串删除分页/排序。

此前粗估的独立准确计数约需 3–5 个有效开发日，另需真实数据量下的性能与权限验证。这项能力和显示总页数均明确留到下一阶段。
