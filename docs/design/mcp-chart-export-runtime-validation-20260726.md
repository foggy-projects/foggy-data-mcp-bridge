# MCP 图表导出运行验证记录（2026-07-26）

## 结论

本轮在 `foggy-mcp-launcher` 的 `lite` profile 上完成了真实进程验证。默认 XChart
路径不依赖浏览器或额外渲染服务，可以从直接数据、普通 DSL、timeWindow 和 DSL_CTE
及 Pivot 结果生成图片，并通过返回 URL 下载。显式 ECharts 路径在外部服务不可用时
保留查询结果，同时返回 `chartError`，没有隐式回退到 XChart。

第四阶段已经关闭运行验证中发现的两个缺口：

1. `payload.pivot` 已完整映射到 `SemanticQueryRequest.pivot`。
2. lite profile 的配置模型目录改为通过 catalog refresh authority 按配置子集物化，
   不再因为扫描并加载当前 SQLite 未提供物理表的无关模型而失败。

## 验证环境

- Java: 17.0.19
- Maven: 3.8.7
- Launcher profile: `lite`
- HTTP port: `18066`
- Runtime API: enabled，仅使用 `none-dev-test-only` 本地验证模式
- Database: 独立 SQLite 文件
- Chart storage: 独立临时本地目录
- Java AWT: headless

验证过程没有记录或落盘任何 LLM Key、Token 或完整 Base URL。

## 构建与启动修复

### Renderer Bean 缺失

首次启动发现 `DatasetMcpAutoConfiguration` 显式 import 场景没有装配
`ChartRendererRegistry`。已将以下实现纳入配置：

- `ChartRendererRegistry`
- `XChartRenderer`
- `EChartsRenderer`

修复后 launcher smoke test 与 28 模块 package reactor 通过。

### 本地图片 URL 404

图片已写入磁盘，但 `/charts/{filename}` 返回 404。根因是
`ChartImageController` 使用 `@ConditionalOnBean(LocalChartStorageAdapter.class)`，
在显式 import 的配置处理顺序下未稳定注册。

控制器条件已改为与存储配置一致的：

```java
@ConditionalOnProperty(
    name = "foggy.chart.storage.type",
    havingValue = "local",
    matchIfMissing = true
)
```

同时将 launcher smoke test 使用的错误属性
`foggy.chart.storage.local.base-dir` 修正为
`foggy.chart.storage.local.directory`，并增加 `/charts/stats` 路由断言。

## MCP 与健康检查

真实进程检查结果：

- `/actuator/health`: `UP`
- `/healthz`: HTTP 200
- `/readyz`: HTTP 200，工具已装配
- `/api/v1/capabilities`: HTTP 200
- `/mcp/analyst/rpc` 的 `tools/list` 包含：
  - `chart.generate`
  - `dataset.export_with_xchart`
  - `dataset.export_with_echarts`

## 功能验证

### 普通 DSL + XChart

查询：

```json
{
  "model": "FactOrderQueryModel",
  "payload": {
    "columns": ["orderStatus", "sum(payAmount) as totalPay"],
    "groupBy": ["orderStatus"],
    "orderBy": ["-totalPay"]
  }
}
```

XChart 使用原生 `CategoryChart` 配置，按 `orderStatus` 和 `totalPay` 绑定柱状图。

结果：

- 返回 4 行订单状态聚合数据
- 生成 900 × 540 PNG
- MCP 元数据 `fileSize`: 17,892 bytes
- 图片 URL: HTTP 200
- 下载大小: 17,892 bytes
- 文件识别: PNG image data, 900 × 540
- 文件头: `89504e470d0a1a0a`

### direct data + XChart

`chart.generate` 使用三行渠道订单量直接数据与原生 `PieChart`/`Donut` 配置。

结果：

- `success=true`
- 生成 800 × 600 PNG
- `fileSize`: 25,501 bytes

### timeWindow + XChart

使用 `FactOrderQueryModel`：

```json
{
  "columns": [
    "orderDate$year",
    "orderDate$month",
    "payAmount",
    "payAmount__prior",
    "payAmount__ratio"
  ],
  "groupBy": ["orderDate$year", "orderDate$month"],
  "timeWindow": {
    "field": "orderDate$id",
    "grain": "month",
    "comparison": "yoy",
    "value": ["2024-01-01", "2025-01-01"],
    "targetMetrics": ["payAmount"]
  }
}
```

结果：

- 返回 1 行月度汇总数据
- `payAmount__prior`、`payAmount__diff`、`payAmount__ratio` 为合法 `null`
- XChart 将 `null` 作为缺口处理，图片生成成功
- 生成 900 × 540 PNG，`fileSize`: 7,127 bytes

注意：YoY month 查询的源输出必须包含 `orderDate$year`。缺失时引擎会明确报：
`derived query references unknown field 'orderDate$year'`。

### DSL_CTE + XChart

使用 `ServiceTicketQueryModel`，受控 CTE 包含 derive 与 aggregate stage，最终输出：

- `team$caption`
- `ticketCount`
- `slaHitCount`

结果：

- 返回 3 个客服团队的 SLA 汇总
- XChart 只绑定最终 output 别名
- 生成 900 × 540 PNG
- `fileSize`: 24,693 bytes

### 显式 ECharts 服务不可用

普通 DSL 查询成功后，将原生 ECharts Option 发送至默认外部渲染地址；本机未启动
ECharts renderer。

结果符合合同：

- 4 行查询数据仍正常返回
- `summary`: 查询完成，但图表生成失败
- `exports.chartError`: 外部服务连接被拒绝
- 没有自动回退到 XChart

### Pivot + XChart

请求使用：

```json
{
  "pivot": {
    "rows": ["orderStatus"],
    "metrics": ["payAmount"],
    "outputFormat": "flat"
  }
}
```

结果：

- Pivot 正常进入引擎并返回 4 行 flat 数据
- `CANCELLED`: 29.9
- `COMPLETED`: 14,696.2
- `PENDING`: 1,143.12
- `PROCESSING`: 1,298.0
- XChart 生成 900 × 540 PNG
- 最终 clean fat jar 复验 `fileSize`: 22,686 bytes
- 图片 URL: HTTP 200
- 文件头: `89504e470d0a1a0a`

本轮补齐了 `SemanticQueryPayloadMapper` 与 `LocalDatasetAccessor` 的 Pivot 映射。
Pivot、普通 DSL、timeWindow 与 DSL_CTE 均继续使用查询最终输出列绑定原生图表配置，
图表 `config` 不需要理解或复制这些查询模式的内部结构。

## LLM 真实调用

环境中已有 `OPENAI_API_KEY` 与 OpenAI-compatible Base URL，但：

1. Base URL 带 `/v1`，与项目“Spring AI 自动追加 `/v1`”的配置规则冲突，首次请求
   返回 HTTP 404。
2. 去除 Base URL 末尾 `/v1` 后，默认 `gpt-4o-mini` 不在该服务的模型路由中。
3. 通过只读 `/v1/models` 查询选择 `gpt-5.4-mini`，不输出凭证或完整服务地址。

使用 `gpt-5.4-mini` 后，第四阶段最终 clean fat jar 验证结果：

- LLM 请求成功，19.745 秒完成
- `dataset.list_models` 返回 lite 配置的 4 个模型：
  - `FactOrderQueryModel`
  - `CrmLead`
  - `CustomerOrderLifecycleQueryModel`
  - `ServiceTicketQueryModel`
- LLM 完成模型描述与查询工具调用
- 共注册 3 个内部工具，执行 4 次调用，全部成功
- LLM 先执行明细口径查询，再自行修正为
  `count(orderId)` 与 `sum(payAmount)` 聚合查询
- 最终返回 4 行按支付金额降序的订单状态统计：
  - `COMPLETED`: 3 单，14,696.2
  - `PROCESSING`: 1 单，1,298.0
  - `PENDING`: 1 单，1,143.12
  - `CANCELLED`: 1 单，29.9

这同时验证了 LLM Provider、Spring AI、模型目录、MCP tool calling、查询执行与错误恢复
链路。当前环境中的 Key 已可用，不需要额外提供。

在此前一次真实调用中，LLM 曾使用不存在的 `orderStatus$caption`，收到受控字段错误后
自动改为 `orderStatus` 并成功重试；最终 clean fat jar 复验则没有失败调用。

## 第四阶段实现说明

### Pivot 透传

- `SemanticQueryPayloadMapper` 负责解析 `pivot`
- `LocalDatasetAccessor` 将解析结果设置到 `SemanticQueryRequest`
- 同时支持字符串轴字段与对象形式的轴定义
- 非法 Pivot 结构返回精确参数错误

### lite 配置目录按需物化

- `SemanticModelCatalogReadPort` 增加指定模型集合的 catalog view
- MCP 在显式/default 配置模型列表时只请求该子集
- 动态发现与显式 namespace 场景仍保留完整 namespace view
- 生命周期实现通过 `CatalogRefreshCoordinator` 的 models scope 发布子集快照，
  不绕过统一 catalog authority
- 返回的名称、别名、模型对象和 provenance 仍绑定同一 catalog identity

## 验证命令结果

- model engine full tests: 3,181 passed，0 failures，0 errors，2 skipped
- MCP full tests: 522 passed，0 failures，0 errors，0 skipped
- launcher targeted smoke tests: 2 passed，0 failures，0 errors
- launcher clean fat jar package: BUILD SUCCESS
- 28-module package reactor: BUILD SUCCESS
- 真实 launcher startup: success
- MCP ordinary DSL/XChart: pass
- direct data/XChart: pass
- timeWindow/XChart: pass
- DSL_CTE/XChart: pass
- ECharts explicit failure semantics: pass
- Pivot local mapping/XChart: pass
- LLM provider/tool calling: pass
- lite configured catalog subset: pass
- LLM business result on lite catalog: pass
- LLM controlled error recovery: pass

## 已知验证脚本债务

历史脚本 `scripts/verify-v933-batch6-catalog-authority.sh` 在进入测试前要求
`SemanticServiceResolverImpl` 保留一个只声明、不调用的 dead watcher 方法；当前源码
已删除该死代码，因此该旧静态断言不再适用于当前分支。本轮没有为通过历史脚本而恢复
死代码，改由 engine/MCP 全量测试、launcher smoke、clean package 与真实进程验证覆盖。
