# 分组图表示例 - 多商品销售趋势

## ✅ 重要更新: 统一语义渲染现已支持多系列！

使用 `seriesField` 参数可以在统一语义渲染中生成多系列图表，大大简化配置复杂度。

### 📊 配置对比

| 方式 | 配置复杂度 | 代码行数 | 推荐场景 |
|-----|----------|---------|---------|
| **统一语义 + seriesField** | 🟢 简单 | ~10行 | 常规多系列图表 |
| **原生ECharts** | 🔴 复杂 | ~50行 | 复杂定制需求 |

### 🎯 服务端口提醒
本地测试请使用 **3000端口**：`http://localhost:3000`

## 1. 统一语义渲染 - 多系列折线图 ✨

### 示例1: 按月销售趋势 (6个月数据)

```bash
curl -X POST http://localhost:3000/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "line",
      "title": "商品销售趋势对比",
      "xField": "month",
      "yField": "amount",
      "seriesField": "product",
      "smooth": true,
      "showLegend": true,
      "showLabel": false
    },
    "data": [
      {"month": "2024-01", "product": "iPhone", "amount": 85000},
      {"month": "2024-02", "product": "iPhone", "amount": 92000},
      {"month": "2024-03", "product": "iPhone", "amount": 78000},
      {"month": "2024-04", "product": "iPhone", "amount": 105000},
      {"month": "2024-05", "product": "iPhone", "amount": 118000},
      {"month": "2024-06", "product": "iPhone", "amount": 125000},

      {"month": "2024-01", "product": "MacBook", "amount": 45000},
      {"month": "2024-02", "product": "MacBook", "amount": 51000},
      {"month": "2024-03", "product": "MacBook", "amount": 38000},
      {"month": "2024-04", "product": "MacBook", "amount": 62000},
      {"month": "2024-05", "product": "MacBook", "amount": 75000},
      {"month": "2024-06", "product": "MacBook", "amount": 68000},

      {"month": "2024-01", "product": "iPad", "amount": 25000},
      {"month": "2024-02", "product": "iPad", "amount": 28000},
      {"month": "2024-03", "product": "iPad", "amount": 22000},
      {"month": "2024-04", "product": "iPad", "amount": 35000},
      {"month": "2024-05", "product": "iPad", "amount": 42000},
      {"month": "2024-06", "product": "iPad", "amount": 39000},

      {"month": "2024-01", "product": "AirPods", "amount": 15000},
      {"month": "2024-02", "product": "AirPods", "amount": 18000},
      {"month": "2024-03", "product": "AirPods", "amount": 12000},
      {"month": "2024-04", "product": "AirPods", "amount": 22000},
      {"month": "2024-05", "product": "AirPods", "amount": 28000},
      {"month": "2024-06", "product": "AirPods", "amount": 25000}
    ],
    "image": {
      "format": "png",
      "width": 1200,
      "height": 600,
      "backgroundColor": "#ffffff"
    }
  }'
```

**说明**: 这个请求会自动生成4条不同颜色的折线，每条代表一个商品的销售趋势。图表会自动显示图例，说明每条线代表的产品名称。

### 🚀 快速测试示例 (简化版)

```bash
curl -X POST http://localhost:3000/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "line",
      "title": "简化版多系列测试",
      "xField": "month",
      "yField": "sales",
      "seriesField": "product",
      "showLegend": true
    },
    "data": [
      {"month": "1月", "product": "产品A", "sales": 100},
      {"month": "2月", "product": "产品A", "sales": 120},
      {"month": "3月", "product": "产品A", "sales": 110},
      {"month": "1月", "product": "产品B", "sales": 80},
      {"month": "2月", "product": "产品B", "sales": 90},
      {"month": "3月", "product": "产品B", "sales": 95}
    ],
    "image": {"format": "png", "width": 800, "height": 500}
  }'
```

**说明**: 此示例生成两条折线(产品A和产品B)，图表顶部会显示图例，清楚标明每条线的含义。图片将自动保存到 `chart-render-service/images/` 目录。

## 2. 原生ECharts渲染 - 多系列折线图

### 示例2: 详细配置的分组折线图

```bash
curl -X POST http://localhost:3000/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "2024年商品销售趋势分析",
        "subtext": "单位：人民币(元)",
        "left": "center",
        "textStyle": {
          "fontSize": 20,
          "color": "#333",
          "fontWeight": "bold"
        },
        "subtextStyle": {
          "color": "#666",
          "fontSize": 14
        }
      },
      "tooltip": {
        "trigger": "axis",
        "backgroundColor": "rgba(255,255,255,0.95)",
        "borderColor": "#ccc",
        "borderWidth": 1,
        "textStyle": {"color": "#333"},
        "formatter": "{b}<br/>{a0}: ¥{c0:,}<br/>{a1}: ¥{c1:,}<br/>{a2}: ¥{c2:,}<br/>{a3}: ¥{c3:,}"
      },
      "legend": {
        "data": ["iPhone", "MacBook", "iPad", "AirPods"],
        "top": "8%",
        "textStyle": {"color": "#333"}
      },
      "grid": {
        "left": "8%",
        "right": "8%",
        "top": "20%",
        "bottom": "15%",
        "containLabel": true
      },
      "xAxis": {
        "type": "category",
        "data": ["1月", "2月", "3月", "4月", "5月", "6月"],
        "axisLabel": {
          "color": "#666",
          "fontSize": 12
        },
        "axisLine": {
          "lineStyle": {"color": "#ddd"}
        }
      },
      "yAxis": {
        "type": "value",
        "name": "销售金额(元)",
        "nameTextStyle": {"color": "#666"},
        "axisLabel": {
          "color": "#666",
          "formatter": "¥{value:,}"
        },
        "splitLine": {
          "lineStyle": {"color": "#f0f0f0"}
        },
        "axisLine": {
          "lineStyle": {"color": "#ddd"}
        }
      },
      "series": [
        {
          "name": "iPhone",
          "type": "line",
          "smooth": true,
          "symbol": "circle",
          "symbolSize": 6,
          "data": [85000, 92000, 78000, 105000, 118000, 125000],
          "itemStyle": {"color": "#007AFF"},
          "lineStyle": {"width": 3},
          "areaStyle": {
            "color": {
              "type": "linear",
              "x": 0, "y": 0, "x2": 0, "y2": 1,
              "colorStops": [
                {"offset": 0, "color": "rgba(0, 122, 255, 0.2)"},
                {"offset": 1, "color": "rgba(0, 122, 255, 0.05)"}
              ]
            }
          }
        },
        {
          "name": "MacBook",
          "type": "line",
          "smooth": true,
          "symbol": "circle",
          "symbolSize": 6,
          "data": [65000, 71000, 58000, 82000, 95000, 88000],
          "itemStyle": {"color": "#34C759"},
          "lineStyle": {"width": 3}
        },
        {
          "name": "iPad",
          "type": "line",
          "smooth": true,
          "symbol": "circle",
          "symbolSize": 6,
          "data": [42000, 48000, 35000, 55000, 62000, 59000],
          "itemStyle": {"color": "#FF9500"},
          "lineStyle": {"width": 3}
        },
        {
          "name": "AirPods",
          "type": "line",
          "smooth": true,
          "symbol": "circle",
          "symbolSize": 6,
          "data": [28000, 32000, 25000, 38000, 45000, 42000],
          "itemStyle": {"color": "#FF3B30"},
          "lineStyle": {"width": 3}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 1400,
      "height": 700,
      "backgroundColor": "#fafafa"
    }
  }'
```

## 3. 扩展示例 - 更多商品类别

### 示例3: 电商平台商品分类销售

```bash
curl -X POST http://localhost:3000/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "电商平台各类商品月销售额",
        "left": "center"
      },
      "tooltip": {
        "trigger": "axis",
        "axisPointer": {"type": "cross"}
      },
      "legend": {
        "data": ["数码电子", "服装鞋包", "家居用品", "美妆护肤", "食品饮料", "运动户外"],
        "top": "8%"
      },
      "xAxis": {
        "type": "category",
        "data": ["2024-01", "2024-02", "2024-03", "2024-04", "2024-05", "2024-06", "2024-07", "2024-08"]
      },
      "yAxis": {
        "type": "value",
        "name": "销售额(万元)",
        "axisLabel": {"formatter": "{value}万"}
      },
      "series": [
        {
          "name": "数码电子",
          "type": "line",
          "data": [320, 380, 290, 450, 520, 480, 580, 620],
          "smooth": true,
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "服装鞋包",
          "type": "line",
          "data": [280, 320, 260, 380, 420, 450, 520, 580],
          "smooth": true,
          "itemStyle": {"color": "#91cc75"}
        },
        {
          "name": "家居用品",
          "type": "line",
          "data": [180, 220, 190, 280, 320, 300, 380, 420],
          "smooth": true,
          "itemStyle": {"color": "#fac858"}
        },
        {
          "name": "美妆护肤",
          "type": "line",
          "data": [150, 180, 160, 220, 280, 260, 320, 380],
          "smooth": true,
          "itemStyle": {"color": "#ee6666"}
        },
        {
          "name": "食品饮料",
          "type": "line",
          "data": [120, 140, 130, 180, 220, 200, 250, 280],
          "smooth": true,
          "itemStyle": {"color": "#73c0de"}
        },
        {
          "name": "运动户外",
          "type": "line",
          "data": [100, 120, 110, 150, 180, 170, 210, 240],
          "smooth": true,
          "itemStyle": {"color": "#3ba272"}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 1200,
      "height": 600
    }
  }'
```

## 4. 柱状图分组示例

### 示例4: 分组柱状图 - 季度对比

```bash
curl -X POST http://localhost:3000/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "各商品季度销售对比",
        "left": "center"
      },
      "tooltip": {
        "trigger": "axis",
        "axisPointer": {"type": "shadow"}
      },
      "legend": {
        "data": ["Q1", "Q2", "Q3", "Q4"]
      },
      "xAxis": {
        "type": "category",
        "data": ["iPhone", "MacBook", "iPad", "AirPods", "Apple Watch"]
      },
      "yAxis": {
        "type": "value",
        "name": "销售额(万元)"
      },
      "series": [
        {
          "name": "Q1",
          "type": "bar",
          "data": [25.5, 19.5, 12.5, 8.5, 6.2],
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "Q2",
          "type": "bar",
          "data": [31.2, 23.8, 15.2, 10.2, 7.8],
          "itemStyle": {"color": "#91cc75"}
        },
        {
          "name": "Q3",
          "type": "bar",
          "data": [28.8, 21.5, 13.8, 9.5, 7.2],
          "itemStyle": {"color": "#fac858"}
        },
        {
          "name": "Q4",
          "type": "bar",
          "data": [36.5, 26.2, 17.8, 12.8, 9.5],
          "itemStyle": {"color": "#ee6666"}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 1000,
      "height": 600
    }
  }'
```

## 5. 面积堆叠图示例

### 示例5: 堆叠面积图 - 累积销售额

```bash
curl -X POST http://localhost:3000/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "各商品累积销售额贡献",
        "left": "center"
      },
      "tooltip": {
        "trigger": "axis",
        "axisPointer": {"type": "cross"}
      },
      "legend": {
        "data": ["iPhone", "MacBook", "iPad", "AirPods"]
      },
      "xAxis": {
        "type": "category",
        "boundaryGap": false,
        "data": ["1月", "2月", "3月", "4月", "5月", "6月"]
      },
      "yAxis": {
        "type": "value",
        "name": "销售额(万元)"
      },
      "series": [
        {
          "name": "iPhone",
          "type": "line",
          "stack": "总量",
          "areaStyle": {},
          "data": [8.5, 9.2, 7.8, 10.5, 11.8, 12.5],
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "MacBook",
          "type": "line",
          "stack": "总量",
          "areaStyle": {},
          "data": [6.5, 7.1, 5.8, 8.2, 9.5, 8.8],
          "itemStyle": {"color": "#91cc75"}
        },
        {
          "name": "iPad",
          "type": "line",
          "stack": "总量",
          "areaStyle": {},
          "data": [4.2, 4.8, 3.5, 5.5, 6.2, 5.9],
          "itemStyle": {"color": "#fac858"}
        },
        {
          "name": "AirPods",
          "type": "line",
          "stack": "总量",
          "areaStyle": {},
          "data": [2.8, 3.2, 2.5, 3.8, 4.5, 4.2],
          "itemStyle": {"color": "#ee6666"}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 1200,
      "height": 600
    }
  }'
```

## 6. 数据说明

### 数据结构说明：
- **时间轴 (X轴)**: 支持月份、季度、年份等时间维度
- **金额轴 (Y轴)**: 销售金额，支持格式化显示（如：¥、万元）
- **商品分组**: 每个商品作为一个系列，用不同颜色和线条样式区分

### 配置要点：
1. **smooth: true**: 使折线更平滑美观
2. **tooltip.trigger: "axis"**: 显示所有系列的数据
3. **legend**: 显示图例，便于识别各商品
4. **grid**: 调整图表在画布中的位置和大小
5. **formatter**: 自定义数值显示格式

### 图片生成说明：
- ✅ **自动保存**: 图表生成后会自动保存到 `chart-render-service/images/` 目录
- ✅ **文件命名**: 格式为 `chart_{type}_{timestamp}_{randomId}.{format}`
- ✅ **支持格式**: PNG, JPG, JPEG
- ✅ **响应包含**: Base64编码图片数据 + 本地保存路径信息
- ⚠️ **注意**: API返回包含图片的二进制数据，建议通过响应中的 `localSave.absolutePath` 直接访问生成的文件

### 多系列图表图例功能：
- 🎯 **自动生成**: `seriesField` 参数自动为每个系列生成图例
- 🎨 **颜色区分**: 每个系列使用不同颜色，图例显示对应色块
- 📍 **位置**: 图例默认显示在图表底部居中位置
- 📝 **字体**: 图例文字使用14px字体，易于阅读
- 🔧 **控制**: 使用 `showLegend: false` 可以隐藏图例

这些示例涵盖了多种分组图表类型，你可以根据实际需求选择合适的样式和配置。