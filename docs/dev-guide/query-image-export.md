---
doc_type: code-inventory
intended_for: developers-and-users
purpose: 说明 Java Runtime 与 CLI 共用的查询图片入口、权限边界、排版限制和验证方式。
---

# 查询结果 PNG 导出

`dataset.export_image` 在 Java Runtime 内执行一次受治理的 QueryModel 查询，然后生成表格、柱状图或折线图。
Foggy CLI 调用这个 MCP 工具，因此两个入口共用查询、权限、字段选择和渲染逻辑。

## 实现选择

复用仓库已有的 XChart 绘制柱状图和折线图，使用 JDK Java2D/ImageIO 绘制表格、标题和页脚。
这条路径无新增绘图库依赖，无需 Docker、Node、浏览器或远程渲染服务，支持 Java 17 headless。
旧 `dataset.export_with_chart`、`chart.generate_xchart` 及可选 ECharts 适配器仍保留原有入口。

中文使用随包交付的 Noto Sans SC 静态字体，原始字体约 8.3 MB，授权和来源在
`foggy-dataset-mcp/src/main/resources/fonts/`。首次使用加载字体，后续复用；运行时不下载字体，
不依赖操作系统安装中文字体。超出字体覆盖的字符会明确报错。

适合查询分享和 LLM 展示的固定样式。暂不提供多系列、连续数值/时间轴、报表设计器、
分页图片、Compose 多结果或树状 Pivot；折线图按查询返回顺序连接分类点，null 保留空隙。
需要排序、聚合或日期补齐时，应在查询 DSL/模型中明确实现。

## MCP 调用

在 `/mcp/analyst/rpc` 通过 `tools/list` 发现工具，使用普通查询的 `X-NS` 和可选
`Authorization` 请求头。模型、字段、行权限由现有语义引擎执行，不建立另一套查询通道。

```json
{
  "jsonrpc": "2.0", "id": 1, "method": "tools/call",
  "params": {
    "name": "dataset.export_image",
    "arguments": {
      "model": "SalesQueryModel",
      "payload": {"columns": ["month", "amount"]},
      "kind": "bar", "xField": "month", "yField": "amount",
      "title": "月度销售额", "unit": "元"
    }
  }
}
```

默认 `delivery: inline` 返回 `content` 中原生 `{type:"image",mimeType:"image/png",data:...}`，
同时返回无 Base64 的文本和 `structuredContent` 元数据。此模式不保存公开图片文件。
显式 `delivery: link` 沿用 `ChartStorageAdapter` 返回 URL，部署方需配置可访问的前缀和已有访问策略。
查询失败、拒绝访问、无数据或无法清楚排版时返回 `isError: true`，不生成或保存图片。

表格可用 `columns: [{field,caption,unit}]` 选择最终结果字段；图表必须显式指定 `xField` 和
`yField`。省略标题覆盖时，字段名称取自查询 schema；当前 schema 没有单位属性，单位须显式声明，
不会猜测。已有单位后缀不会重复追加。

图片只使用最终结果、字段名称、标题和单位，不展示连接信息、token、查询参数或异常详情。
内部可信宿主治理选项沿用原查询链路，公开工具 schema 不提供这些选项。

## CLI 保存

```powershell
foggy --base-url http://127.0.0.1:18066 --namespace demo `
  query export-image SalesQueryModel --payload .\monthly-sales.json `
  --kind bar --x month --y amount --title "月度销售额" --unit "元" `
  --out .\sales-bar.png

foggy --base-url http://127.0.0.1:18066 --namespace demo `
  query export-image SalesQueryModel --payload .\monthly-sales.json `
  --kind table --title "月度销售明细" --out .\sales-table.png
```

CLI 使用既有数据身份选项或环境变量，凭据只进请求头。它请求 inline 图片，严格校验 Base64、PNG、
尺寸和大小后同目录原子替换指定文件；失败保留原文件。输出为保存路径、尺寸、大小和截断状态等 JSON，
不输出 Base64、运行连接参数或凭据。输出目录需已存在。

## 排版与边界

| 项目 | 约束 |
|---|---|
| 表格 | 最多 50 行、12 列；长文字换行，行高和总高度自动增加 |
| 柱状图 / 折线图 | 单系列，最多 40 / 100 个点 |
| 宽度 | 640–2400 px，默认 1200 px |
| 高度 | 表格自动高度 360–4096 px；图表 520–2400 px，默认 700 px |
| 大小 | 最多 800 万像素、8 MiB PNG |
| 字号 | 标题 28 px；表头、正文和轴名称 18 px；刻度 16 px；页脚 14 px |

表格每列至少 96 px，12 列需增加宽度；标签、标题或文字无法清晰容纳时拒绝渲染，提示减少内容或增加尺寸。
不通过缩小字号挤入过多信息。图片标明实际展示行数和本次返回行数，有可信总行数时才显示总行数。
渲染上限、分页或引擎截断都会标记“部分结果”，不把截断数据当作全部数据。

## 验证

`QueryImageExportIntegrationTest` 使用真实 SQLite 数据源、TM/QM 模型、LocalDatasetAccessor、
查询引擎和渲染器，独立 SQL 对比实际输入。覆盖同名模型的双 namespace、模型/字段/物理列权限、
行权限、systemSlice、空结果、负数/null、长中文和截断，保存可目视检查的 PNG。

设置 `-Dfoggy.image.evidence-dir=<绝对目录>` 保存验收图片。可选
`-Dfoggy.image.cli-python=<CLI Python可执行文件>` 和
`-Dfoggy.image.cli-project=<foggy-runtime-cli源码目录>` 执行真实 HTTP MCP 与 CLI 端到端验证；
此 Python 仅用于运行既有 CLI，不参与 Java 渲染。
