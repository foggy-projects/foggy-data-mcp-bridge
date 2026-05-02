# P2-Metadata时间维度样例行-需求

## 文档作用

- doc_type: workitem
- intended_for: execution-agent / reviewer
- purpose: 定义 `describe_model_internal` 在时间维度元数据中返回一行真实时间维表样例数据的需求边界、输出契约与验收标准
- 目标版本: 8.3.0.beta
- 需求等级: P2
- 状态: draft
- source_type: requirement
- 责任仓: foggy-data-mcp-bridge（Java）
- 责任模块: foggy-dataset-mcp / foggy-dataset-model / foggy-dataset-demo

## 背景

8.3.0.beta 的时间窗口能力依赖 AI 能从模型元数据中稳定识别业务时间轴。当前 `qm_describe.md` 已能渲染 `## 时间维度与字段 (Time Dimensions & Fields)`，并标注 `salesDate$id` 的 `timeRole=business_date`。

但仅有字段说明仍不足以让 AI 稳定判断时间维度字段的真实取值形态，例如：

- `salesDate$id` 是 `20240101` 还是 `2024-01-01`
- `salesDate$caption` 是否为 ISO 日期格式
- `salesDate$monthName` 是否为中文月份
- `salesDate$dayOfWeek` 的起点是否为 `1=周一`
- `salesDate$isWeekend` 是布尔值还是 `0/1`

因此需要在单模型元数据描述中，为带有 `timeRole=business_date` 的时间维度补充一行安全、受控、可选的真实样例数据。

## 问题陈述

AI 生成 `timeWindow`、窗口函数或日期过滤时，需要同时知道“应该选哪个字段”和“该字段真实值长什么样”。当前元数据已解决字段选择问题，但样例值缺失会导致以下风险：

- DSL 中 `value` 使用 ISO 日期，但底层时间键为 `yyyyMMdd` 整数，转换规则不明确
- AI 误用 `salesDate$caption` 做过滤或排序，而不是使用更稳定的 `salesDate$id`
- 月、季度、周几等字段含义仅靠说明文本推断，跨语言或跨模型时容易漂移
- 时间窗口输出示例无法体现真实维表字段的值域和格式

## 目标结果

在 `dataset.describe_model_internal` 返回的 Markdown 元数据中，当模型存在 `timeRole=business_date` 的时间维度时，新增一个紧跟在时间字段表之后的样例区域：

```markdown
### 时间维度样例行

| 维度 | 物理表 | 主键 | Caption列 | 样例来源 |
|------|--------|------|-----------|----------|
| salesDate | dim_date | date_key | full_date | sample-row |

| QM字段 | 样例值 |
|--------|--------|
| salesDate$id | 20240101 |
| salesDate$caption | 2024-01-01 |
| salesDate$year | 2024 |
| salesDate$quarter | 1 |
| salesDate$month | 1 |
| salesDate$monthName | 一月 |
| salesDate$dayOfWeek | 1 |
| salesDate$isWeekend | false |
```

该区域用于帮助 AI 理解时间维度字段值形态，不替代正式的时间窗口 DSL 契约。`timeWindow` 的字段选择、粒度映射和日期转换仍应由结构化元数据或 DSL 解析规则约束。

## 范围

### In Scope

- 仅针对 `describe_model_internal` 的单模型元数据描述增加时间维度样例行。
- 仅对明确标记 `timeRole=business_date` 的时间维度采样。
- 样例数据来源限定为时间维表，例如 `dim_date`。
- 每个时间维度最多返回 1 行样例。
- 样例区域必须同时展示：
  - 逻辑维度名
  - 物理维表名
  - 主键列
  - caption 列
  - QM 字段到样例值的映射
- 样例缺失时不得报错阻断元数据返回，应降级为 `样例不可用` 或省略该区域。

### Out of Scope

- 不对事实表返回真实样例数据。
- 不对客户、商品、门店等普通维度默认返回真实样例数据。
- 不在本需求中实现任意维度的数据 profiling。
- 不在本需求中定义完整的 `timeWindow` 执行逻辑。
- 不把样例行作为权限绕过通道；采样必须遵守现有 namespace、数据源和访问控制边界。

## 输出契约

### Markdown 契约

样例区域插入位置：

1. `## 时间维度与字段 (Time Dimensions & Fields)`
2. 时间字段明细表
3. `### 时间维度样例行`
4. 后续 `## 属性字段`

字段展示规则：

| 项 | 规则 |
|----|------|
| 维度名 | 使用 QM 字段前缀，如 `salesDate` |
| 物理表 | 使用 TM 维度定义中的 `tableName` |
| 主键 | 使用 TM 维度定义中的 `primaryKey` |
| Caption列 | 使用 TM 维度定义中的 `captionColumn` |
| QM字段 | 使用 `salesDate$id`、`salesDate$caption`、`salesDate$property` 形式 |
| 样例值 | 使用数据库真实返回值，按 Markdown 安全转义 |

### JSON 契约（内部调用预留）

如果内部调用方请求 `format=json`，建议在后续结构化元数据中预留以下形态。本需求优先要求 Markdown，JSON 可作为同批实现或后续补齐：

```json
{
  "timeDimensions": [
    {
      "name": "salesDate",
      "timeRole": "business_date",
      "tableName": "dim_date",
      "primaryKey": "date_key",
      "captionColumn": "full_date",
      "sampleRow": {
        "salesDate$id": 20240101,
        "salesDate$caption": "2024-01-01",
        "salesDate$year": 2024,
        "salesDate$quarter": 1,
        "salesDate$month": 1,
        "salesDate$monthName": "一月",
        "salesDate$dayOfWeek": 1,
        "salesDate$isWeekend": false
      }
    }
  ]
}
```

## 采样规则

建议采用确定性采样，避免每次元数据输出漂移：

| 优先级 | 规则 |
|--------|------|
| P1 | 如配置了 `sampleDate`，优先查该日期对应行 |
| P2 | 如维表包含 `2024-01-01`，优先返回该行 |
| P3 | 否则按维表主键升序取第一行 |

采样限制：

- `LIMIT 1`
- 只查询时间维度声明中暴露到 QM 的字段及必要 key/caption 字段
- 不展开事实表 join
- 采样失败不影响主元数据返回
- 样例值需要做 Markdown 表格转义，避免破坏输出格式

## 安全与治理约束

- 只允许对 `timeRole=business_date` 的时间维度启用默认采样。
- 默认禁止对普通业务维度返回真实样例，避免泄漏客户、门店、人员等敏感信息。
- 采样必须沿用当前 `namespace`、数据源解析和权限上下文。
- 异常信息不得暴露未授权物理列细节；错误只记录到日志，Markdown 中使用简短降级文本。
- 如部署方关闭样例采集，应保留配置开关，默认行为由产品配置决定。

## 验收标准

1. `FactSalesQueryModel` 的 `describe_model_internal` Markdown 中，时间字段表后出现 `### 时间维度样例行`。
2. 样例行包含 `salesDate$id`、`salesDate$caption`、`salesDate$year`、`salesDate$quarter`、`salesDate$month`、`salesDate$dayOfWeek`、`salesDate$isWeekend` 的真实值。
3. 样例 metadata 明确展示 `dim_date`、`date_key`、`full_date` 等物理映射信息。
4. 当时间维表无数据或采样失败时，`describe_model_internal` 仍返回完整元数据，不抛出工具级错误。
5. 普通维度不会默认返回真实样例数据。
6. Markdown 表格在样例值包含特殊字符时不破坏格式。
7. 现有 `dataset.get_metadata` 多模型索引输出不因本需求显著膨胀；如需支持多模型样例，应另行立项。

## 测试规划

| 测试类别 | 建议用例 | 说明 |
|----------|----------|------|
| 单模型 Markdown | `FactSalesQueryModel` | 校验时间维度样例区域和字段值 |
| 无数据降级 | 空 `dim_date` 或 mock 采样失败 | 校验元数据返回不失败 |
| 非时间维度隔离 | product/customer/store | 校验不返回普通维度真实样例 |
| Markdown 转义 | caption 包含 `|` 或换行 | 校验表格格式稳定 |
| 配置开关 | sample disabled | 校验关闭后不输出样例区域 |

## Progress Tracking

| 维度 | 状态 | 说明 |
|------|------|------|
| development | not-started | 当前仅需求落盘，未实现代码 |
| testing | not-started | 待实现后补单元测试或集成测试 |
| experience | N/A | 纯后端/MCP 元数据输出能力，无 UI 交互 |

## 执行检查清单

- [ ] 明确样例采样入口属于 `describe_model_internal`，不默认扩展到 `get_metadata` 多模型索引
- [ ] 确认时间维度识别只使用 `timeRole=business_date`
- [ ] 确认采样查询遵守 namespace 和访问控制上下文
- [ ] 补充 Markdown 渲染测试
- [ ] 补充采样失败降级测试
- [ ] 如新增配置开关，补充默认值与关闭场景测试

## 关联文档

- [P2-Metadata时间维度与属性分析报告](./P2-Metadata时间维度与属性分析报告.md)
- [P1-SemanticDSL-时间窗口能力设计](./P1-SemanticDSL-时间窗口能力设计.md)
- [qm_describe.md](./qm_describe.md)
