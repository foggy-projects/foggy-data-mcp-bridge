# 时间属性与时间维度定义分析 (TM/QM vs 新标准)

根据对 `foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce` 模板的分析，以及运行服务生成的 `qm_describe.md` 结果，以下是对当前时间定义与新标准兼容性的评估和反推设计。

## 1. 现状分析

在 `FactSalesModel.tm` 和 `FactSalesQueryModel.qm` 中，时间被分为两种形式：

### A. 作为时间属性 (Time Property)
- **TM 定义**：`FactSalesModel.tm` 定义了 `created_at` (type: DATETIME) 属性。
- **QM 暴露**：`FactSalesQueryModel.qm` **没有**将 `created_at` 放入 `columnGroups` 中暴露给外部。
- **Markdown 结果**：由于 QM 未暴露该字段，生成的 Markdown 中没有出现我们刚才开发的 `## 时间字段` (Time Fields) 区域。
- **兼容性**：即使暴露了，TM 模板中也没有为其显式配置 `timeRole: 'system_time'` 或 `recommendedUse`，不过我们的兜底逻辑会自动将其推断为 `system_time`。

### B. 作为时间维度 (Time Dimension)
- **TM 定义**：核心的业务时间 `salesDate` 是作为一个 **关联维度 (Dimension)** 定义的，关联到 `dim_date` 表。
  ```javascript
  {
      name: 'salesDate',
      tableName: 'dim_date',
      caption: '销售日期',
      properties: [ { column: 'year' }, { column: 'month' } ... ]
  }
  ```
- **QM 暴露**：QM 中完整暴露了 `salesDate` 及其所有的属性维度（年、季度、月等）。
- **Markdown 结果**：在生成的 Markdown 中，`salesDate` 系列字段全部渲染在 `## 维度字段` 区域下。没有任何语义标识说明它是一个 "时间" 维度（只有人为阅读 caption "销售日期" 才能知道）。
- **兼容性**：**不符合新标准**。目前我们新增的 `timeRole` 和 `recommendedUse` 仅添加在了 `DbProperty` (属性字段) 上，并没有应用在 `DbDimension` (维度定义) 上。

### C. 配合时间窗口函数
在 `FactSalesQueryModel.qm` 中，定义了一个 7日移动平均的窗口字段：
```javascript
{
    name: 'ma7',
    caption: '7日移动平均',
    formula: 'AVG(salesAmount)',
    partitionBy: ['product$caption'],
    windowOrderBy: [{ field: 'salesDate$caption', dir: 'asc' }],
    windowFrame: 'ROWS BETWEEN 6 PRECEDING AND CURRENT ROW',
    type: 'NUMBER'
}
```
**问题**：AI 想要生成类似的单 DSL 查询时，必须知道按哪个字段进行 `windowOrderBy`。目前它只能通过 `salesDate$caption` (或者 `$id`) 去猜。如果没有给 `salesDate` 维度显式赋予 `timeRole = 'business_date'`，AI 在跨模型分析时极易产生幻觉（例如错误地使用了 `created_at` 作为窗口的时间轴）。

---

## 2. 反推设计与改进建议

结合目前的分析，为了彻底打通时间维度的语义，我们需要对当前设计进行以下延展：

### 建议 1: 扩展 `timeRole` 到维度级 (`DbDimension`)
不仅是普通的时间属性，关联的**时间维度表**也必须支持定义时间角色。
- **修改 Java/Python 定义**：在 `DbDimensionDef` / `DbDimension` 接口中，增加 `timeRole` (如 `business_date`, `event_time`) 和 `recommendedUse`。
- **渲染逻辑优化**：在生成 Markdown 时，检测到具有 `timeRole` 的维度，可以将其与普通的 `## 时间字段` 合并，统一渲染为一个 **"## 时间维度与字段 (Time Dimensions & Fields)"** 区域。例如：
  | 字段名 | 名称 | 类型 | 时间角色 | 说明 |
  |--------|------|------|----------|------|
  | salesDate$id | 销售日期(ID) | DIM | business_date | 核心业务时间轴，用于同环比和窗口函数 |
  | created_at | 创建时间 | DATETIME | system_time | 用于系统排障，勿用于业务统计 |

### 建议 2: TM 模板补充显式定义
我们需要在演示模板中补充新标准的定义，作为 AI 的示例：
1. `FactSalesModel.tm` 的 `salesDate` 维度增加：`timeRole: 'business_date'`。
2. `FactSalesModel.tm` 的 `created_at` 属性增加：`timeRole: 'system_time'`，并在 QM 中暴露出来。

### 建议 3: AI DSL 时间窗口推导规范
当 AI 需要生成含有 `timeWindow` 或 `windowOrderBy` 的 DSL 时：
1. **优先**查找 `timeRole = 'business_date'` 的字段或维度。
2. 如果是维度，使用 `[维度名]$id` 或指定的默认排序字段。
3. **坚决避免**使用 `system_time` (如 `created_at`, `updated_at`) 作为业务数据的窗口时间轴。

### 建议 4: 时间维度补充真实样例行

当前 `qm_describe.md` 已能标出 `salesDate$id` 是 `business_date`，但 AI 仍无法仅凭描述稳定判断各时间字段的真实取值形态。例如 `salesDate$id` 是 `20240101`，`salesDate$caption` 是 `2024-01-01`，`salesDate$dayOfWeek` 使用 `1=周一`，这些信息最好由元数据直接给出。

建议在 `dataset.describe_model_internal` 的单模型 Markdown 输出中，为 `timeRole=business_date` 的时间维度增加一个受控的 `### 时间维度样例行` 区域：

- 仅采样时间维表，如 `dim_date`
- 每个时间维度最多返回 1 行
- 展示物理表、主键、caption 列与 QM 字段样例值映射
- 采样失败时降级，不影响元数据主返回
- 不对客户、商品、门店等普通维度默认返回真实样例，避免数据泄漏

需求已拆出独立 work item：[P2-Metadata时间维度样例行-需求](./P2-Metadata时间维度样例行-需求.md)。

### 建议 5: 重新评估 `get_metadata` 作为模型发现入口的默认返回体量

本地启动 MCP 服务并实测 `dataset.get_metadata` 后发现，它当前不仅返回模型索引，还返回字典定义与全局字段索引。demo 环境 5 个模型已经返回 8257 字符 Markdown、405 行内容、127 个 `[field:*]` 字段索引。

该结果对“AI 首轮发现有哪些模型”来说偏重，建议将模型发现与完整字段索引拆开：首轮只返回模型列表、主时间轴与少量路由信息；完整字段、字典枚举、时间维度样例行下沉到 `dataset.describe_model_internal`。

评估与返回样例已拆出独立 work item：[P2-get_metadata模型发现返回样例与瘦身评估](./P2-get_metadata模型发现返回样例与瘦身评估.md)。

补充决策：新增 `dataset.list_models` 作为正式模型发现入口；完成后 AI 首轮流程切换到 `dataset.list_models`，并将 `dataset.get_metadata` 从 AI 可见工具列表隐藏。执行需求见：[P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md)。
