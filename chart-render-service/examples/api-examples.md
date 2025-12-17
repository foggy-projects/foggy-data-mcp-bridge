# Chart Render Service API 调用示例

## 基础配置

- **服务地址**: `http://localhost:3001`
- **认证Token**: `default-render-token`
- **请求头**: `Authorization: default-render-token`

## 1. 统一语义渲染 (POST /render/unified)

### 1.1 柱状图示例

```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "bar",
      "title": "月度销售数据",
      "xField": "month",
      "yField": "sales",
      "topN": 10,
      "color": "#5470c6",
      "showLegend": true,
      "showLabel": true
    },
    "data": [
      {"month": "1月", "sales": 12000},
      {"month": "2月", "sales": 15000},
      {"month": "3月", "sales": 18000},
      {"month": "4月", "sales": 14000},
      {"month": "5月", "sales": 16000},
      {"month": "6月", "sales": 20000}
    ],
    "image": {
      "format": "png",
      "width": 800,
      "height": 600,
      "backgroundColor": "#ffffff"
    }
  }'
```

### 1.2 折线图示例

```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "line",
      "title": "用户增长趋势",
      "xField": "date",
      "yField": "users",
      "smooth": true,
      "showLegend": false,
      "showLabel": true
    },
    "data": [
      {"date": "2024-01", "users": 1000},
      {"date": "2024-02", "users": 1200},
      {"date": "2024-03", "users": 1500},
      {"date": "2024-04", "users": 1800},
      {"date": "2024-05", "users": 2200},
      {"date": "2024-06", "users": 2800}
    ],
    "image": {
      "format": "png",
      "width": 1000,
      "height": 500
    }
  }'
```

### 1.3 饼图示例

```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "pie",
      "title": "市场份额分布",
      "nameField": "brand",
      "valueField": "share",
      "showLegend": true,
      "showLabel": true
    },
    "data": [
      {"brand": "苹果", "share": 35},
      {"brand": "三星", "share": 25},
      {"brand": "华为", "share": 20},
      {"brand": "小米", "share": 12},
      {"brand": "其他", "share": 8}
    ],
    "image": {
      "format": "png",
      "width": 600,
      "height": 600
    }
  }'
```

### 1.4 散点图示例

```bash
curl -X POST http://localhost:3001/render/unified \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "unified": {
      "type": "scatter",
      "title": "身高体重关系",
      "xField": "height",
      "yField": "weight",
      "showLegend": false
    },
    "data": [
      {"height": 165, "weight": 55},
      {"height": 170, "weight": 65},
      {"height": 175, "weight": 70},
      {"height": 180, "weight": 75},
      {"height": 185, "weight": 80},
      {"height": 160, "weight": 50}
    ],
    "image": {
      "format": "svg",
      "width": 800,
      "height": 600
    }
  }'
```

## 2. 原生ECharts渲染 (POST /render/native)

### 2.1 复杂柱状图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "多系列销售对比",
        "left": "center",
        "textStyle": {"fontSize": 18, "color": "#333"}
      },
      "tooltip": {
        "trigger": "axis",
        "backgroundColor": "rgba(255,255,255,0.9)",
        "borderColor": "#ccc",
        "borderWidth": 1
      },
      "legend": {
        "data": ["2023年", "2024年"],
        "top": "10%"
      },
      "xAxis": {
        "type": "category",
        "data": ["Q1", "Q2", "Q3", "Q4"],
        "axisLabel": {"color": "#666"}
      },
      "yAxis": {
        "type": "value",
        "axisLabel": {"color": "#666"},
        "splitLine": {"lineStyle": {"color": "#eee"}}
      },
      "series": [
        {
          "name": "2023年",
          "type": "bar",
          "data": [120, 200, 150, 80],
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "2024年",
          "type": "bar",
          "data": [140, 230, 180, 100],
          "itemStyle": {"color": "#91cc75"}
        }
      ]
    },
    "image": {
      "format": "png",
      "width": 900,
      "height": 600,
      "backgroundColor": "#f9f9f9"
    }
  }'
```

### 2.2 面积图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "网站访问量变化",
        "subtext": "数据来源：统计系统"
      },
      "xAxis": {
        "type": "category",
        "boundaryGap": false,
        "data": ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
      },
      "yAxis": {
        "type": "value"
      },
      "series": [{
        "name": "访问量",
        "type": "line",
        "smooth": true,
        "areaStyle": {
          "color": {
            "type": "linear",
            "x": 0, "y": 0, "x2": 0, "y2": 1,
            "colorStops": [
              {"offset": 0, "color": "rgba(84, 112, 198, 0.3)"},
              {"offset": 1, "color": "rgba(84, 112, 198, 0.1)"}
            ]
          }
        },
        "data": [820, 932, 901, 934, 1290, 1330, 1320],
        "itemStyle": {"color": "#5470c6"}
      }]
    },
    "image": {
      "format": "png",
      "width": 1200,
      "height": 400
    }
  }'
```

### 2.3 环形图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {
        "text": "支出分类",
        "left": "center"
      },
      "tooltip": {
        "trigger": "item",
        "formatter": "{a} <br/>{b}: {c} ({d}%)"
      },
      "series": [{
        "name": "支出",
        "type": "pie",
        "radius": ["40%", "70%"],
        "center": ["50%", "60%"],
        "avoidLabelOverlap": false,
        "itemStyle": {
          "borderRadius": 10,
          "borderColor": "#fff",
          "borderWidth": 2
        },
        "label": {
          "show": false,
          "position": "center"
        },
        "emphasis": {
          "label": {
            "show": true,
            "fontSize": "20",
            "fontWeight": "bold"
          }
        },
        "labelLine": {
          "show": false
        },
        "data": [
          {"value": 1048, "name": "餐饮"},
          {"value": 735, "name": "交通"},
          {"value": 580, "name": "购物"},
          {"value": 484, "name": "娱乐"},
          {"value": 300, "name": "其他"}
        ]
      }]
    },
    "image": {
      "format": "png",
      "width": 600,
      "height": 600
    }
  }'
```

## 3. 多系列数据示例

### 3.1 多系列折线图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "多产品销售趋势"},
      "tooltip": {"trigger": "axis"},
      "legend": {"data": ["产品A", "产品B", "产品C"]},
      "xAxis": {
        "type": "category",
        "data": ["1月", "2月", "3月", "4月", "5月", "6月"]
      },
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "产品A",
          "type": "line",
          "data": [120, 132, 101, 134, 90, 230],
          "itemStyle": {"color": "#5470c6"}
        },
        {
          "name": "产品B",
          "type": "line",
          "data": [220, 182, 191, 234, 290, 330],
          "itemStyle": {"color": "#91cc75"}
        },
        {
          "name": "产品C",
          "type": "line",
          "data": [150, 232, 201, 154, 190, 330],
          "itemStyle": {"color": "#fac858"}
        }
      ]
    },
    "image": {"format": "png", "width": 1000, "height": 600}
  }'
```

### 3.2 堆叠柱状图

```bash
curl -X POST http://localhost:3001/render/native \
  -H "Authorization: default-render-token" \
  -H "Content-Type: application/json" \
  -d '{
    "engine": "echarts",
    "engine_spec": {
      "title": {"text": "堆叠柱状图"},
      "tooltip": {"trigger": "axis"},
      "legend": {"data": ["销售", "营销", "技术"]},
      "xAxis": {
        "type": "category",
        "data": ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
      },
      "yAxis": {"type": "value"},
      "series": [
        {
          "name": "销售",
          "type": "bar",
          "stack": "总量",
          "data": [120, 132, 101, 134, 90, 230, 210]
        },
        {
          "name": "营销",
          "type": "bar",
          "stack": "总量",
          "data": [220, 182, 191, 234, 290, 330, 310]
        },
        {
          "name": "技术",
          "type": "bar",
          "stack": "总量",
          "data": [150, 232, 201, 154, 190, 330, 410]
        }
      ]
    },
    "image": {"format": "png", "width": 800, "height": 600}
  }'
```

## 4. 管理接口

### 4.1 获取存储统计

```bash
curl -X GET http://localhost:3001/render/storage/stats \
  -H "Authorization: default-render-token"
```

### 4.2 获取队列状态

```bash
curl -X GET http://localhost:3001/render/queue/status \
  -H "Authorization: default-render-token"
```

### 4.3 清理旧图片 (超过24小时)

```bash
curl -X DELETE "http://localhost:3001/render/storage/cleanup?maxAge=24" \
  -H "Authorization: default-render-token"
```

## 5. 响应格式

### 5.1 成功响应 (带本地保存)

```json
{
  "success": true,
  "renderTime": 1250,
  "format": "png",
  "size": {
    "width": 800,
    "height": 600
  },
  "image": "iVBORw0KGgoAAAANSUhEUgAA...", // Base64编码的图片
  "mimeType": "image/png",
  "localSave": {
    "saved": true,
    "filename": "chart_bar_2024-09-22T14-30-25-123Z_a1b2c3.png",
    "absolutePath": "/path/to/chart-render-service/images/chart_bar_2024-09-22T14-30-25-123Z_a1b2c3.png"
  }
}
```

### 5.2 存储统计响应

```json
{
  "success": true,
  "timestamp": "2024-09-22T14:30:25.123Z",
  "storage": {
    "totalFiles": 25,
    "totalSize": 2048576,
    "formatCounts": {
      "png": 20,
      "svg": 5
    },
    "directory": "./images",
    "enabled": true
  }
}
```

## 6. 配置选项

在 `.env` 文件中可配置以下选项：

```env
# 图片本地保存配置
SAVE_IMAGES_LOCALLY=true
LOCAL_IMAGES_DIR=./images
IMAGE_FILENAME_PREFIX=chart_

# 其他相关配置
DEFAULT_WIDTH=800
DEFAULT_HEIGHT=600
MAX_WIDTH=4000
MAX_HEIGHT=4000
```

## 7. 注意事项

1. **文件命名**: 保存的图片文件名格式为 `{prefix}_{type}_{timestamp}_{random}.{format}`
2. **元数据**: 每个图片都会生成对应的元数据JSON文件
3. **自动清理**: 建议定期清理旧图片文件以节省存储空间
4. **目录权限**: 确保服务对图片目录有读写权限
5. **配置优先级**: 环境变量 > .env文件 > 默认值