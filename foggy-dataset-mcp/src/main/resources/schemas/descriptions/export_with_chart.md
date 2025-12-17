# export_with_chart 工具描述

## 工具描述

查询数据模型并生成图表的可视化工具。该工具结合了数据查询和图表生成功能，可一次调用完成数据查询和图表可视化。
**注意**：query参数格式与query_model_v3完全相同！

### 核心功能
- 执行语义查询获取数据
- 根据数据自动生成图表（支持多种图表类型）
- 图表自动上传到云存储并返回公共访问URL

### 适用场景
- 生成数据分析报告
- 创建可视化图表
- 数据可视化展示

### 图表类型支持
- LINE: 折线图，适合展示趋势，🆕 **自动支持多系列**
- BAR: 柱状图，适合比较数据，🆕 **自动支持分组对比**
- PIE: 饼图，适合展示占比
- SCATTER: 散点图，适合相关性分析

### 🆕 多系列图表自动支持

**三维数据自动识别**：
- 当查询包含多个groupBy字段时，工具自动检测数据维度
- X轴：时间/类别维度（如 takingTimeDim$timeDayId）
- Y轴：数值维度（如 weight）
- 系列维度：分组维度（如 startTeam$caption）

**智能配置增强**：
- 自动为每个分组生成独立的图表系列
- 自动配置图例显示
- 自动设置提示框格式
- 支持LINE和BAR图表类型

**⚠️ 重要：维度字段选择原则**
- **维度本身作为分组/轴时，必须使用 `$caption` 字段**（用户可读的名称）
  - ✅ 正确：`startTeam$caption`（网点名称）
  - ❌ 错误：`startTeam$id`（网点ID，用户无法理解）
  - ✅ 正确：`workon$caption`（员工名称）
  - ❌ 错误：`workon$id`（员工ID，用户无法理解）

- **维度的具体属性可以直接使用**（属性本身有业务含义）
  - ✅ 正确：`takingTimeDim$timeDayId`（日期，本身就是可读的）
  - ✅ 正确：`workon$sex`（员工性别，有业务含义）
  - ✅ 正确：`workon$department`（员工部门，有业务含义）

- **判断规则**：如果字段是 `维度名$id` 或 `维度名$caption` 的形式，优先用 `$caption`；如果是 `维度名$具体属性` 的形式，按实际业务需求使用

**⚠️ 🔥 最重要:普通列作为轴的字段选择原则(最高优先级!)**

**黄金法则: 当同时存在ID列和Name列时,图表必须使用Name列,绝对不能使用ID列!**

**常见ID/Name字段对应** (左侧❌禁用,右侧✅必用):
- ❌ `tmsCustomerId` (TC1111070097) → ✅ `customerName` (道宇)
- ❌ `userId` (100234) → ✅ `userName` (张三)
- ❌ `productId` (P20251009) → ✅ `productName` (iPhone 15)

**完整示例 - 客户发货趋势图**:
```json
{
  "query": {
    "columns": ["customerName", "takingTimeDim$month", "weight"],  // ✅ 使用customerName而非tmsCustomerId
    "slice": [
      {"field": "tmsCustomerId", "op": "in", "value": ["TC111...", "TC222..."]},  // ID仅用于过滤
      {"field": "takingTimeDim$year", "op": "=", "value": "2025"}
    ],
    "groupBy": [
      {"field": "customerName"},  // ✅ 分组用customerName
      {"field": "takingTimeDim$month"},
      {"field": "weight", "agg": "SUM"}
    ],
    "orderBy": [{"field": "takingTimeDim$month", "dir": "ASC"}]
  },
  "chart": {
    "type": "LINE",
    "xAxis": {"field": "takingTimeDim$month", "label": "月份"},
    "yAxis": {"field": "weight", "label": "发货量(kg)"},
    "groupBy": "customerName"  // ✅ 图表系列用customerName,不是tmsCustomerId!
  }
}
```

**为什么必须用Name字段?**
- ✅ 用户看到 "道宇"、"张三" 能理解
- ❌ 用户看到 "TC1111070097"、"100234" 无法理解

### 使用示例

**🆕 多网点趋势图（三维数据）**：
```json
{
  "model": "TeamNoAuthEsOrderDataModel",
  "query": {
    "columns": ["startTeam$caption", "takingTimeDim$timeDayId", "weight"],
    "slice": [{
      "field": "takingTimeDim$timeDayId",
      "op": "[)",
      "value": ["2025-09-15", "2025-09-22"]
    }],
    "groupBy": [
      {"field": "startTeam$caption"},
      {"field": "takingTimeDim$timeDayId"},
      {"field": "weight", "agg": "SUM"}
    ],
    "orderBy": [{"field": "takingTimeDim$timeDayId", "dir": "ASC"}]
  },
  "chart": {
    "type": "LINE",
    "title": "近一周各网点开单货量趋势图",
    "mode": "UNIFIED",
    "xAxis": {"field": "takingTimeDim$timeDayId", "label": "日期", "type": "time"},
    "yAxis": {"field": "weight", "label": "货量（kg）"},
    "groupBy": "startTeam$caption",
    "legend": {"show": true, "position": "top"},
    "tooltip": {"show": true, "trigger": "axis"}
  }
}
```
✨ **效果**：生成多条折线，每个网点一条线，自动显示图例
💡 **注意**：`startTeam$caption` 使用网点名称（而非ID），`takingTimeDim$timeDayId` 是时间维度的日期属性（本身可读）

**按员工性别分组的业绩对比（维度属性示例）**：
```json
{
  "model": "EmployeeSalesModel",
  "query": {
    "columns": ["workon$sex", "salesAmount"],
    "groupBy": [
      {"field": "workon$sex"},
      {"field": "salesAmount", "agg": "SUM"}
    ]
  },
  "chart": {
    "type": "BAR",
    "title": "员工性别业绩对比",
    "xField": "workon$sex",
    "yField": "salesAmount"
  }
}
```
💡 **注意**：这里使用 `workon$sex` 而不是 `workon$caption`，因为需要按性别分组，而不是按员工名称

**按员工的业绩对比（维度本身示例）**：
```json
{
  "model": "EmployeeSalesModel",
  "query": {
    "columns": ["workon$caption", "salesAmount"],
    "groupBy": [
      {"field": "workon$caption"},
      {"field": "salesAmount", "agg": "SUM"}
    ],
    "orderBy": [{"field": "salesAmount", "dir": "DESC"}],
    "limit": 10
  },
  "chart": {
    "type": "BAR",
    "title": "Top 10 员工业绩排行",
    "xField": "workon$caption",
    "yField": "salesAmount"
  }
}
```
💡 **注意**：这里使用 `workon$caption`（员工名称）而不是 `workon$id`（员工ID），确保用户看到可读的名字

**生成饼图示例（重要！完整参数）**：
```json
{
  "model": "TeamNoAuthEsOrderDataModel",
  "query": {
    "columns": ["startTeam$caption", "totalYfValue"],
    "groupBy": [{"field": "startTeam$caption"}, {"field": "totalYfValue", "agg": "SUM"}]
  },
  "chart": {
    "type": "PIE",
    "title": "近一周各网点运费占比分布图",
    "xField": "startTeam$caption",  // 饼图分类字段 (必需)
    "yField": "totalYfValue",       // 饼图数值字段 (必需)
    "showLabel": true,              // 显示标签，不要用spec.label！
    "showLegend": true,             // 显示图例，不要用spec.legend！
    "width": 800,
    "height": 600,
    "format": "png"
  }
}
```

**重要**：chart参数使用扁平结构，不要使用spec嵌套！

## 参数说明

### model (必填)
- 类型: string
- 说明: 数据模型名称，如 TeamEsOrderDataModel、TmsCustomerModel 等

### query (必填)
- 类型: object
- 说明: 语义查询参数
- 子字段:
  - columns: 查询列数组，支持$caption和$id后缀
    - ⚠️ **容错机制**: 系统会自动将groupBy中的字段补充到columns中，即使你忘记添加也不会报错
    - 💡 **最佳实践**: 建议明确列出所有需要的字段，便于理解查询意图
  - filters/slice: 过滤条件数组
  - groupBy: 分组配置
  - orderBy: 排序配置
  - limit: 返回数量限制
  - totalColumn: 是否返回总计（默认true）

### chart (必填)
- 类型: object
- 说明: 图表配置，**使用扁平结构，不要用spec嵌套！**
- 子字段:
  - type: 图表类型 (LINE/BAR/PIE/SCATTER)
  - title: 图表标题
  - **PIE图表字段**:
    - xField: X轴数据字段名(PIE图表必需-分类字段)
    - yField: Y轴数据字段名(PIE图表必需-数值字段)
  - **LINE/BAR图表字段**:
    - xAxis: X轴配置 {"field": "字段名", "label": "标签"}
    - yAxis: Y轴配置 {"field": "字段名", "label": "标签"}
  - **通用可选字段**:
    - showLabel: 是否显示数据标签 (布尔值)
    - showLegend: 是否显示图例 (布尔值)
    - smooth: 是否平滑曲线(折线图用，布尔值)
    - width: 图片宽度（默认800）
    - height: 图片高度（默认600）
    - format: 图片格式 ("png"/"svg"，默认"png")


## 返回值说明

成功返回包含以下字段的对象：
```json
{
  "chartUrl": "https://junda-ai.obs.cn-east-5.myhuaweicloud.com/exports/charts/chart_xxx.png",
  "rowCount": 100,
  "durationMs": 1250,
  "preview": [
    {"date": "2025-01-01", "amount": 10000},
    {"date": "2025-01-02", "amount": 12000}
  ]
}
```

### 返回字段说明
- chartUrl: 生成的图表URL（永久有效）
- rowCount: 数据行数
- durationMs: 处理耗时（毫秒）
- preview: 数据预览（前10行）

## 错误处理

### 常见错误
1. **MODEL_NOT_FOUND**: 模型名称错误
   - 解决: 使用get_metadata()查看可用模型列表

2. **INVALID_COLUMN**: 字段名称错误
   - 解决: 使用description_model_internal()查看模型字段

3. **CHART_GENERATION_FAILED**: 图表生成失败
   - 解决: 检查数据格式和图表类型是否匹配


## 性能建议

1. **数据量控制**: 单次查询建议不超过10万行
2. **字段选择**: 只查询需要的字段，避免select *
3. **图表数据点**: 图表数据点建议不超过1000个
4. **使用分页**: 大数据集使用limit和offset分页

## 与其他工具配合

1. 使用 `query_model_v3()` 测试查询
2. 最后使用 `export_with_chart()` 生成报告

## 注意事项

- 图表类型要与数据结构匹配（如PIE图需要分类数据）
- Y轴标签会显示在图表上，建议包含单位
- 导出的文件会自动上传到云存储
- URL默认24小时有效，之后需要重新生成