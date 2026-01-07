# dataset_nl.query

自然语言查询数据集，支持自动生成图表。M1层统一查询入口。

## 核心能力
- 中文自然语言查询，无需了解底层结构
- 智能意图识别：明细/统计/趋势/对比/占比
- 自动图表生成：趋势→LINE，对比→BAR，占比→PIE
- 多步骤分析：自动分解复杂问题

## 参数

### query (必填)
自然语言查询，支持：
- 明细查询："查询客户信息"、"找出北京地区的企业客户"
- 统计查询："按团队统计订单数量"
- 趋势分析："近一周的运单趋势"
- 对比分析："对比各网点的业绩"
- 占比分析："各地区销售占比"

### session_id (可选)
会话ID，保持上下文连续性，支持追问和细化查询

### cursor (可选)
分页游标，从上次响应的 `nextCursor` 获取

### hints (可选)
查询优化提示：
```json
{"prefer_model": "TmsCustomerModel", "prefer_fields": ["customerName"], "time_range": "last_7_days"}
```

### format (可选)
输出格式：table(默认)/json/summary

### stream (可选)
流式响应，默认true，大数据量推荐

## 返回值
```json
{
  "type": "result",
  "items": [...],
  "total": 100,
  "summary": "查询结果摘要",
  "exports": {
    "charts": [{"url": "https://...", "type": "LINE", "title": "图表标题"}]
  },
  "hasNext": false,
  "nextCursor": null
}
```

## 多步骤分析示例
查询："找出2025-01月发货量前10的客户，分析他们2025年的发货量"
- 步骤1: 查询2025-01月前10客户
- 步骤2: 根据客户ID查询全年数据
- 步骤3: 生成综合分析报告

## 常见错误
- QUERY_PARSE_ERROR → 使用更明确的查询语句
- MODEL_NOT_FOUND → 在hints中指定prefer_model
- FIELD_NOT_FOUND → 检查字段名或使用更通用描述
- QUERY_TIMEOUT → 缩小查询范围

## 最佳实践
- 使用明确业务术语："查询客户信息" 而非 "给我看看数据"
- 指定时间范围："近一周的订单" 而非 "最近的订单"
- 明确统计维度："按网点统计" 而非 "统计一下"
- 连续查询使用相同session_id
- 图表URL有效期24小时
