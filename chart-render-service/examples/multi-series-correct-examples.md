# 多系列图表正确示例

## 问题说明
统一语义渲染 (`/render/unified`) **不支持多系列数据**，只能生成单系列图表。
要生成多商品的分组折线图，必须使用**原生ECharts渲染** (`/render/native`)。

## 1. 正确的多商品折线图示例 (原生ECharts)

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "商品销售趋势对比",
        "left": "center",
        "textStyle": {"fontSize": 18, "color": "#333"}
      },
      "tooltip": {
        "trigger": "axis",
        "backgroundColor": "rgba(255,255,255,0.9)",
        "borderColor": "#ccc",
        "formatter": "{b}<br/>{a0}: ¥{c0:,}<br/>{a1}: ¥{c1:,}<br/>{a2}: ¥{c2:,}<br/>{a3}: ¥{c3:,}"
      },
      "legend": {
        "data": ["iPhone", "MacBook", "iPad", "AirPods"],
        "top": "8%"
      },
      "xAxis": {
        "type": "category",
        "data": ["2024-01", "2024-02", "2024-03", "2024-04", "2024-05", "2024-06"]
      },
      "yAxis": {
        "type": "value",
        "name": "销售金额(元)",
        "axisLabel": {"formatter": "¥{value:,}"}
      },
      "series": [
        {
          "name": "iPhone",
          "type": "line",
          "smooth": true,
          "data": [85000, 92000, 78000, 105000, 118000, 125000],
          "itemStyle": {"color": "#007AFF"},
          "lineStyle": {"width": 3}
        },
        {
          "name": "MacBook",
          "type": "line",
          "smooth": true,
          "data": [65000, 71000, 58000, 82000, 95000, 88000],
          "itemStyle": {"color": "#34C759"},
          "lineStyle": {"width": 3}
        },
        {
          "name": "iPad",
          "type": "line",
          "smooth": true,
          "data": [42000, 48000, 35000, 55000, 62000, 59000],
          "itemStyle": {"color": "#FF9500"},
          "lineStyle": {"width": 3}
        },
        {
          "name": "AirPods",
          "type": "line",
          "smooth": true,
          "data": [28000, 32000, 25000, 38000, 45000, 42000],
          "itemStyle": {"color": "#FF3B30"},
          "lineStyle": {"width": 3}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 1200,
      "height": 600,
      "backgroundColor": "#ffffff"
    }
  }'
```

## 2. 简化版多商品折线图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "商品销售趋势"},
      "tooltip": {"trigger": "axis"},
      "legend": {"data": ["iPhone", "MacBook", "iPad", "AirPods"]},
      "xAxis": {
        "type": "category",
        "data": ["1月", "2月", "3月", "4月", "5月", "6月"]
      },
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "iPhone",
          "type": "line",
          "data": [85000, 92000, 78000, 105000, 118000, 125000]
        },
        {
          "name": "MacBook",
          "type": "line",
          "data": [65000, 71000, 58000, 82000, 95000, 88000]
        },
        {
          "name": "iPad",
          "type": "line",
          "data": [42000, 48000, 35000, 55000, 62000, 59000]
        },
        {
          "name": "AirPods",
          "type": "line",
          "data": [28000, 32000, 25000, 38000, 45000, 42000]
        }
      ]
    },
    "image": {"format": "png", "width": 1000, "height": 600}
  }'
```

## 3. 如果坚持使用统一语义渲染

如果你想使用统一语义渲染，只能生成**单商品**的折线图：

```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "line",
      "title": "iPhone销售趋势",
      "xField": "month",
      "yField": "amount",
      "smooth": true,
      "showLegend": false,
      "showLabel": true
    },
    "data": [
      {"month": "2024-01", "amount": 85000},
      {"month": "2024-02", "amount": 92000},
      {"month": "2024-03", "amount": 78000},
      {"month": "2024-04", "amount": 105000},
      {"month": "2024-05", "amount": 118000},
      {"month": "2024-06", "amount": 125000}
    ],
    "image": {"format": "png", "width": 800, "height": 600}
  }'
```

## 4. 多商品柱状图对比 (原生ECharts)

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "各月商品销售对比"},
      "tooltip": {"trigger": "axis"},
      "legend": {"data": ["iPhone", "MacBook", "iPad", "AirPods"]},
      "xAxis": {
        "type": "category",
        "data": ["1月", "2月", "3月", "4月", "5月", "6月"]
      },
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "iPhone",
          "type": "bar",
          "data": [85000, 92000, 78000, 105000, 118000, 125000],
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "MacBook",
          "type": "bar",
          "data": [65000, 71000, 58000, 82000, 95000, 88000],
          "itemStyle": {"color": "#91cc75"}
        },
        {
          "name": "iPad",
          "type": "bar",
          "data": [42000, 48000, 35000, 55000, 62000, 59000],
          "itemStyle": {"color": "#fac858"}
        },
        {
          "name": "AirPods",
          "type": "bar",
          "data": [28000, 32000, 25000, 38000, 45000, 42000],
          "itemStyle": {"color": "#ee6666"}
        }
      ]
    },
    "image": {"format": "png", "width": 1000, "height": 600}
  }'
```

## 总结

- **多系列图表** → 使用 `/render/native` (原生ECharts)
- **单系列图表** → 可以使用 `/render/unified` (统一语义)
- **复杂配置** → 必须使用 `/render/native`

原生ECharts渲染虽然配置复杂一些，但支持所有ECharts的功能特性。