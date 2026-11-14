# dataset.export_image

执行真实 QueryModel 查询，并在当前 Java 进程内生成 PNG 表格、柱状图或折线图。
不需要 Docker、Node、浏览器或外部渲染服务；使用内置中文字体。

`model`、`payload` 与 dataset.query_model 的语义一致，沿用调用者的 namespace 和数据权限。
只使用最终查询结果，不接受调用者另传 data、SQL 或图片地址。柱状图和折线图须显式指定
`xField`、`yField`（包括查询返回的别名），使用查询顺序，不补零、不重新聚合。

表格可传 `columns: [{field, caption, unit}]`；省略时使用返回 schema 的标题和字段。
图表标题和轴名称可显式指定，单位用 `unit`；不会猜测单位。
表格默认最多显示 50 行、柱状图最多 40 个点、折线图最多 100 个点，超出的数据和查询分页会标记为部分结果。
长表格自动增加高度，无法清晰排版时返回错误，请减少行列或改为聚合查询。
宽度支持 640–2400 px；表格最低高度 360 px，图表最低 520 px、默认 700 px。
表格自动高度最多 4096 px，PNG 最多 8 MiB。不缩小字体来挤入过多内容。

默认 `delivery: inline` 返回客户端能直接展示的 MCP image 内容，无公开文件写入。
显式 `delivery: link` 使用已有存储配置返回 URL，远程部署需设置可访问的 URL 前缀；
本地 /charts 链接遵循已有访问方式，应只在允许分享查询结果时使用。
查询失败、拒绝访问或空结果时不生成图片。Pivot 仅支持 flat，排除汇总元数据行；
不支持树状 Pivot 或 Compose 多结果。

示例：
```json
{"model":"SalesQueryModel","payload":{"columns":["month","amount"]},
 "kind":"bar","xField":"month","yField":"amount","title":"月度销售额",
 "xLabel":"月份","yLabel":"销售额","unit":"元"}
```
