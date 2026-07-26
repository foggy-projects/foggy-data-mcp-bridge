# MCP 原生图表配置与查询模式指南

## 1. 选择工具

| 场景 | 工具 | 数据来源 | 配置格式 |
|---|---|---|---|
| 已有数据，默认 JVM 内生成图片 | `chart.generate` | `data` | 原生 XChart JSON |
| 已有数据，明确要求 ECharts | `chart.generate` | `data` | 原生 ECharts Option |
| 查询模型并默认生成图片 | `dataset.export_with_xchart` | `model + payload` | 原生 XChart JSON |
| 查询模型并使用 ECharts 私有能力 | `dataset.export_with_echarts` | `model + payload` | 原生 ECharts Option |

默认选择 `dataset.export_with_xchart`。它不依赖浏览器、Node.js 或额外服务。

两个 export 工具共用 `dataset.query_model` 查询合同，但图表配置完全独立：

- XChart 配置不转换成 ECharts Option。
- ECharts Option 不转换成 XChart 配置。
- 不存在需要 LLM 学习的 Foggy 通用 chart config。

## 2. 数据来源规则

`chart.generate` 必须直接传 `data`：

```json
{
  "engine": "xchart",
  "config": {},
  "data": [{}],
  "image": {}
}
```

两个 export 工具不接受调用方提供的 `data`：

```json
{
  "model": "FactOrderQueryModel",
  "payload": {},
  "chart": {
    "config": {},
    "image": {}
  }
}
```

export 的唯一图表数据源是受治理查询实际返回的最终 `items`。查询失败或结果为空时，
不会渲染图表。

## 3. XChart 原生配置

### 3.1 CategoryChart 柱状图

```json
{
  "model": "FactOrderQueryModel",
  "payload": {
    "columns": ["orderStatus", "sum(payAmount) as totalPay"],
    "groupBy": ["orderStatus"],
    "orderBy": ["-totalPay"]
  },
  "chart": {
    "config": {
      "chartType": "CategoryChart",
      "title": "订单状态支付金额",
      "xAxisTitle": "订单状态",
      "yAxisTitle": "支付金额",
      "theme": "XChart",
      "styler": {
        "legendVisible": true,
        "plotGridLinesVisible": true,
        "labelsVisible": true,
        "xAxisLabelRotation": 20,
        "yAxisDecimalPattern": "#,##0.00"
      },
      "series": [
        {
          "name": "支付金额",
          "xField": "orderStatus",
          "yField": "totalPay",
          "renderStyle": "Bar"
        }
      ]
    },
    "image": {
      "width": 900,
      "height": 540,
      "format": "png"
    }
  }
}
```

### 3.2 CategoryChart 多系列折线

```json
{
  "chartType": "CategoryChart",
  "title": "月度支付金额同比",
  "series": [
    {
      "name": "本期",
      "xField": "orderDate$month",
      "yField": "payAmount",
      "renderStyle": "Line",
      "smooth": true
    },
    {
      "name": "上年同期",
      "xField": "orderDate$month",
      "yField": "payAmount__prior",
      "renderStyle": "Line",
      "smooth": true
    }
  ]
}
```

timeWindow 的首期 prior/ratio 可以为 `null`。XChart 会保留为图形缺口，不补零。

### 3.3 XYChart

```json
{
  "engine": "xchart",
  "config": {
    "chartType": "XYChart",
    "title": "客单价与订单量",
    "xAxisTitle": "订单量",
    "yAxisTitle": "客单价",
    "styler": {
      "legendVisible": false,
      "markerSize": 8
    },
    "series": [
      {
        "name": "门店",
        "xField": "orderCount",
        "yField": "avgOrderAmount",
        "renderStyle": "Scatter"
      }
    ]
  },
  "data": [
    {"store": "A", "orderCount": 120, "avgOrderAmount": 318.5},
    {"store": "B", "orderCount": 86, "avgOrderAmount": 402.1}
  ],
  "image": {
    "width": 900,
    "height": 540,
    "format": "png"
  }
}
```

XYChart 的 `xField` 与 `yField` 都必须是数值。

### 3.4 PieChart / Donut

```json
{
  "engine": "xchart",
  "config": {
    "chartType": "PieChart",
    "title": "渠道订单量占比",
    "nameField": "channel",
    "valueField": "orders",
    "renderStyle": "Donut",
    "styler": {
      "legendPosition": "OutsideE",
      "labelsVisible": true,
      "labelType": "NameAndPercentage",
      "donutThickness": 0.55
    }
  },
  "data": [
    {"channel": "WEB", "orders": 42},
    {"channel": "APP", "orders": 35},
    {"channel": "STORE", "orders": 23}
  ],
  "image": {
    "width": 800,
    "height": 600,
    "format": "png"
  }
}
```

### 3.5 动态拆分系列

当查询结果为 long format 时，可使用 `seriesField`：

```json
{
  "chartType": "CategoryChart",
  "title": "各渠道月度销售额",
  "series": [
    {
      "name": "销售额",
      "xField": "month",
      "yField": "amount",
      "seriesField": "channel",
      "renderStyle": "Line"
    }
  ]
}
```

引擎按 `channel` 的实际值拆分多个系列。

## 4. ECharts 原生 Option

```json
{
  "model": "FactOrderQueryModel",
  "payload": {
    "columns": ["orderStatus", "sum(payAmount) as totalPay"],
    "groupBy": ["orderStatus"],
    "orderBy": ["-totalPay"]
  },
  "chart": {
    "config": {
      "title": {"text": "订单状态支付金额"},
      "tooltip": {"trigger": "axis"},
      "dataset": {
        "dimensions": ["orderStatus", "totalPay"]
      },
      "xAxis": {"type": "category"},
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "支付金额",
          "type": "bar",
          "encode": {
            "x": "orderStatus",
            "y": "totalPay"
          }
        }
      ]
    },
    "image": {
      "width": 900,
      "height": 540,
      "format": "png"
    }
  }
}
```

Foggy 会把最终查询 `items` 注入单个 `dataset.source`。调用方不得传：

- `dataset.source`
- `series[*].data`
- `xAxis.data`
- dataset 数组
- `transform` 或 dataset 链

这些限制保证查询结果是唯一数据源，避免配置中的第二份数据绕过治理。

## 5. 查询模式与图表字段绑定

| 查询模式 | `payload` 变化 | 图表配置变化 |
|---|---|---|
| 普通 DSL | `columns/slice/groupBy/orderBy` | 绑定查询最终字段或别名 |
| DSL_CTE | `route=DSL_CTE + executable_plan` | 只绑定 `cte_plan.output` 最终别名 |
| timeWindow | 增加 `timeWindow` | 可绑定 `metric__prior/diff/ratio` |
| Pivot | 增加 `pivot`，必须 flat | 绑定 flat 行中的轴字段与 metric |

图表 schema 不因 DSL、CTE、timeWindow 或 Pivot 改变。变化的只是查询输出字段。

### 5.1 普通 DSL

```json
{
  "columns": ["orderStatus", "sum(payAmount) as totalPay"],
  "groupBy": ["orderStatus"],
  "orderBy": ["-totalPay"]
}
```

图表绑定 `orderStatus` 与 `totalPay`。

### 5.2 timeWindow

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

注意：

- YoY month 源输出需包含 year 与 month。
- `targetMetrics` 使用模型中可聚合的 metric 名。
- `pivot` 与 `timeWindow` 互斥。

### 5.3 DSL_CTE

```json
{
  "route": "DSL_CTE",
  "executable_plan": {
    "cte_plan": {
      "stages": [
        {
          "name": "ticket_scope",
          "type": "derive",
          "input": {"model": "ServiceTicketQueryModel"},
          "derived": [
            {
              "name": "firstResponseHours",
              "expr": "hours_between(createdAt, firstResponseAt)"
            },
            {
              "name": "slaHit",
              "expr": "firstResponseAt is not null and firstResponseHours <= 48"
            }
          ]
        },
        {
          "name": "team_sla",
          "type": "aggregate",
          "inputs": ["ticket_scope"],
          "groupBy": ["team$caption"],
          "metrics": [
            {"name": "ticketCount", "expr": "count(*)"},
            {"name": "slaHitCount", "expr": "sum(slaHit)"}
          ]
        }
      ],
      "output": ["team$caption", "ticketCount", "slaHitCount"]
    }
  }
}
```

图表只能绑定 `team$caption`、`ticketCount`、`slaHitCount`，不能引用中间 stage 字段。

### 5.4 Pivot flat

```json
{
  "pivot": {
    "rows": ["orderStatus"],
    "metrics": ["payAmount"],
    "outputFormat": "flat",
    "options": {
      "grandTotal": true
    }
  }
}
```

规则：

- export 未声明 `outputFormat` 时自动补 `flat`。
- 显式 `grid`、`tree` 或 `hierarchyMode=tree` 会被拒绝。
- subtotal/grand-total 元数据行只保留在查询结果中，渲染前会过滤。

## 6. 能力矩阵

| 能力 | XChart | ECharts |
|---|---|---|
| 默认可用 | 是 | 否，需要外部 renderer |
| JVM 内渲染 | 是 | 否 |
| 原生配置 | XChart JSON | ECharts Option |
| Category/Bar/Line/Area | 是 | 是 |
| XY/Scatter | 是 | 是 |
| Pie/Donut | 是 | 是 |
| 复杂坐标系/visualMap/自定义组件 | 有限 | 是 |
| 图片格式 | PNG、JPG | PNG、SVG |
| 服务失败自动回退 | 不适用 | 否 |
| direct data | 是 | 是 |
| DSL/DSL_CTE/timeWindow/Pivot flat | 是 | 是 |

## 7. 常见错误

| 错误或现象 | 原因 | 处理 |
|---|---|---|
| `Unsupported chart engine` | `engine` 非 xchart/echarts | 使用工具 schema 中的枚举 |
| XChart 缺少字段 | `xField/yField/nameField/valueField` 与最终数据不一致 | 先检查查询最终字段与别名 |
| XYChart 数值转换失败 | x/y 数据不是数字 | 使用 CategoryChart，或修正查询输出 |
| timeWindow unknown year field | YoY 输出缺 year | 将 `date$year` 加入 columns/groupBy |
| `TIMEWINDOW_TARGET_NOT_AGGREGATE` | targetMetric 使用了运行期别名而非模型 metric | 使用模型定义的可聚合 metric |
| Pivot 拒绝 grid/tree | 图表只支持 flat | 改为 `outputFormat=flat` |
| ECharts config 含 `series.data` | 出现第二数据源 | 删除并使用 `dataset.dimensions + encode` |
| ECharts `chartError` connection refused | 外部 renderer 未启动 | 启动服务，或改用默认 XChart |
| 图片文件生成但 URL 404 | 本地图片控制器未装配或存储类型不一致 | 检查 `foggy.chart.storage.type=local` 与 `/charts/stats` |
| LLM HTTP 404 | Base URL 已含 `/v1` | 按项目约定移除 Base URL 末尾 `/v1` |
| LLM unknown provider/model | 模型名不受兼容服务支持 | 显式设置该服务返回的可用模型名 |

## 8. 推荐给 LLM 的决策顺序

1. 用户未指定引擎：选择 `dataset.export_with_xchart`。
2. 用户已有数据而不需要查询：选择 `chart.generate`，默认 `engine=xchart`。
3. 用户明确要求 ECharts，或需要 ECharts 私有组件：选择 ECharts。
4. 先确定查询最终字段，再按引擎原生格式生成配置。
5. 不创建 Foggy 通用 chart config，也不尝试跨引擎转换。

