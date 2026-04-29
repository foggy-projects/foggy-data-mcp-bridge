# P2-get_metadata模型发现返回样例与瘦身评估

## 文档作用

- doc_type: requirement / evaluation
- intended_for: design-review / execution-agent
- purpose: 记录 `dataset.get_metadata` 当前作为 AI 模型发现入口时的真实返回格式，并评估是否需要瘦身或拆分工具
- 目标版本: 8.3.0.beta
- 需求等级: P2
- 状态: draft
- 责任仓: foggy-data-mcp-bridge
- 责任模块: foggy-dataset-mcp / foggy-mcp-launcher
- 记录时间: 2026-04-26
- 决策状态: accepted（新增 `dataset.list_models`，完成后切换 AI 首轮入口并隐藏 `dataset.get_metadata`）

## 关联文档

- 元数据时间维度分析：[P2-Metadata时间维度与属性分析报告](./P2-Metadata时间维度与属性分析报告.md)
- 执行需求：[P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md)
- 单模型元数据输出：[qm_describe.md](./qm_describe.md)
- 时间维度样例行需求：[P2-Metadata时间维度样例行-需求](./P2-Metadata时间维度样例行-需求.md)

## 1. 当前 AI 使用哪个工具发现所有模型

当前 MCP 暴露给 AI 的模型发现入口是：

```text
dataset.get_metadata
```

该工具当前没有必填参数。AI 在不知道具体模型名时，先调用 `dataset.get_metadata`，再从返回内容中的 `## 模型索引` 找到候选模型与简称，例如：

```markdown
## 模型索引
- FS(FactSalesQueryModel): 销售明细查询
- FO(FactOrderQueryModel): 订单查询
- FP(FactPaymentQueryModel): 支付查询
- FR(FactReturnQueryModel): 退货查询
- FIS(FactInventorySnapshotQueryModel): 库存快照查询
```

但实测返回不只是模型列表，还包含字典定义与全局字段索引，因此它现在承担了三类职责：

| 职责 | 当前是否在 `get_metadata` 中返回 | 评价 |
|------|----------------------------------|------|
| 发现有哪些模型 | 是 | 必需 |
| 提供字段检索索引 | 是 | 有用，但体量会随模型数快速增长 |
| 提供字典完整枚举 | 是 | 对首轮模型发现不是必需 |

## 2. 当前调用样例

实测服务：

```text
POST http://localhost:8066/mcp/analyst/rpc
```

请求：

```json
{
  "jsonrpc": "2.0",
  "id": "metadata",
  "method": "tools/call",
  "params": {
    "name": "dataset.get_metadata",
    "arguments": {}
  }
}
```

## 3. 当前返回包络格式

当前返回不是直接 Markdown，而是三层结构：

1. MCP JSON-RPC 外层
2. `result.content[0].text` 文本
3. 文本内部再嵌套业务 JSON，业务 JSON 的 `data.content` 才是真正 Markdown

外层格式样例：

```json
{
  "jsonrpc": "2.0",
  "id": "metadata",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"code\":200,\"data\":{\"content\":\"# 数据模型语义索引 V3\\n\\n## 模型索引\\n- FS(FactSalesQueryModel): 销售明细查询\\n...\",\"data\":null,\"format\":\"markdown\"}}"
      }
    ]
  }
}
```

内层业务 JSON 格式：

```json
{
  "code": 200,
  "data": {
    "content": "# 数据模型语义索引 V3\n\n## 模型索引\n- FS(FactSalesQueryModel): 销售明细查询\n...",
    "data": null,
    "format": "markdown"
  }
}
```

对调用方而言，稳定读取路径是：

```text
result.content[0].text -> JSON.parse -> data.content
```

## 4. 当前 Markdown 返回样例

以下为本地 demo 模板实测节选：

```markdown
# 数据模型语义索引 V3

## 模型索引
- FS(FactSalesQueryModel): 销售明细查询
- FO(FactOrderQueryModel): 订单查询
- FP(FactPaymentQueryModel): 支付查询
- FR(FactReturnQueryModel): 退货查询
- FIS(FactInventorySnapshotQueryModel): 库存快照查询

## 字典定义
| ID | 名称 | 取值 |
|----|------|------|
| order_status | 订单状态 | PENDING=待处理, CONFIRMED=已确认, PROCESSING=处理中, SHIPPED=已发货, DELIVERED=已送达, COMPLETED=已完成, CANCELLED=已取消, REFUNDED=已退款 |
| payment_method | 支付方式 | 1=现付, 2=到付, 3=货到付款 |

## 字段索引

## 索引格式
### 字段业务名
- 描述
    - 实际字段名 | 模型索引

### 时间维度与字段 (Time Dimensions & Fields)
- 销售日期的ID/值 | 日期主键，格式yyyyMMdd，如20240101
    - [field:salesDate$id] | FS
- 销售日期的显示名称 | 销售日期主键
    - [field:salesDate$caption] | FS
- 销售发生的年份
    - [field:salesDate$year] | FS
```

## 5. 体量观测

本次基于 `foggy-mcp-launcher` lite profile + SQLite demo 数据启动后调用 `dataset.get_metadata`，得到以下观测：

| 指标 | 数值 |
|------|------|
| 外层 JSON 字符数 | 9301 |
| 内层 Markdown 字符数 | 8257 |
| 内层 Markdown 行数 | 405 |
| 模型数量 | 5 |
| 字典行数 | 10 |
| `[field:*]` 字段索引数量 | 127 |

当前 demo 仅 5 个模型已经返回 127 个字段索引。若生产环境存在几十个模型，`get_metadata` 首次调用会变成高 token 成本的大上下文，同时会把 AI 首轮任务从“选择模型”扩展成“扫描全字段与全字典”。

## 6. 问题判断

`dataset.get_metadata` 当前存在职责过载：

1. **模型发现入口过重**：AI 只需要知道有哪些模型和每个模型适合回答什么问题，却被迫读取所有字段索引。
2. **字典枚举前置过早**：字典完整取值对生成筛选条件有价值，但不是模型路由阶段必需信息。
3. **字段索引无分页或按需过滤**：字段条目与模型数线性增长，多模型场景下容易膨胀。
4. **返回包络较绕**：MCP 外层 `text` 内再嵌套业务 JSON，调用方需要二次解析；这可以保留，但必须在文档中明确。

结论：`dataset.get_metadata` 可以继续作为兼容入口，但不宜长期承担“全模型字段总目录”的默认返回。

## 7. 重新评估建议

### 建议 A：新增轻量模型发现工具（推荐）

新增：

```text
dataset.list_models
```

职责仅限模型发现与路由，返回建议：

```markdown
# 数据模型列表

| 模型 | 简称 | 说明 | 主时间轴 | 推荐下一步 |
|------|------|------|----------|------------|
| FactSalesQueryModel | FS | 销售明细查询 | salesDate$id | describe_model_internal |
| FactOrderQueryModel | FO | 订单查询 | orderDate$id | describe_model_internal |
| FactPaymentQueryModel | FP | 支付查询 | payDate$id | describe_model_internal |
```

优点：

- AI 首轮模型发现成本最小
- 不破坏 `dataset.get_metadata` 兼容性
- 后续可将 `dataset.get_metadata` 定位为“全局语义索引”，而不是“模型发现”

### 建议 B：瘦身 `dataset.get_metadata` 默认返回

如果短期不新增工具，则调整 `dataset.get_metadata` 的默认无参返回：

```markdown
# 数据模型语义索引 V3 Compact

## 模型索引
- FS(FactSalesQueryModel): 销售明细查询；主时间轴 salesDate$id
- FO(FactOrderQueryModel): 订单查询；主时间轴 orderDate$id

## 核心字段索引
- 销售金额
    - [field:salesAmount] | FS
- 订单金额
    - [field:orderAmount] | FO

## 下一步
- 需要单模型完整字段、时间维度样例、字段类型时，调用 dataset.describe_model_internal
```

瘦身规则建议：

- 保留模型索引
- 每个模型只返回主时间轴、核心度量、核心业务维度
- 字典只返回字典 ID 与名称，不展开完整枚举
- 字段索引总数设置上限，例如默认不超过 50 条
- 完整字段、字典取值、样例行统一下沉到 `dataset.describe_model_internal`

### 建议 C：给 `get_metadata` 增加 mode 参数（谨慎）

可选参数：

```json
{
  "mode": "compact | full"
}
```

该方案对程序调用友好，但对 AI 首轮调用不一定可靠。AI 在不了解参数前仍可能默认无参调用，因此即使支持 `mode`，无参默认也应是 `compact`。

## 8. 建议目标决策

已确认决策：

1. 新增 `dataset.list_models`，作为 AI 首轮发现模型的正式入口。
2. `dataset.list_models` 完成后，QueryExpertService、AI 测试基线和工具说明全部切换到 `dataset.list_models`。
3. `dataset.get_metadata` 保留内部兼容，但从 AI 可见工具列表隐藏。
4. 将完整单模型元数据、时间维度样例行、字段类型与字典明细放入 `dataset.describe_model_internal`。

目标关系：

```text
dataset.list_models
    -> 选择模型
    -> dataset.describe_model_internal
    -> 获取完整字段、时间维度、样例行、字典明细
    -> compose_query / semantic_query
```

执行需求见：[P2-list_models模型发现入口与get_metadata隐藏-需求](./P2-list_models模型发现入口与get_metadata隐藏-需求.md)。

## 9. 验收标准

- [ ] 文档明确说明 AI 当前发现模型使用 `dataset.get_metadata`
- [ ] 文档明确说明当前 MCP 返回包络与内层 Markdown 路径
- [ ] 新增 `dataset.list_models` 后，AI 首轮模型发现不再依赖 `dataset.get_metadata`
- [ ] `dataset.list_models` demo 默认返回控制在可接受体量内，建议不超过 2000 字符且不包含字段索引
- [ ] AI 能从轻量返回中完成模型路由，不依赖扫描全量字段
- [ ] 单模型完整字段仍可通过 `dataset.describe_model_internal` 获取
- [ ] 字典完整枚举不再默认出现在首轮模型发现响应中
- [ ] `dataset.get_metadata` 默认不出现在 AI 可见 `tools/list` 中

## 10. 实测证据

本地临时证据文件：

```text
.codex/tmp/get_metadata_response.json
.codex/tmp/get_metadata_inner.md
```

服务启动状态：

```text
http://localhost:8066/mcp/analyst/rpc
```

启动方式为 `foggy-mcp-launcher` jar，`lite` profile，使用 SQLite demo schema/data 初始化脚本。该服务仅用于本次文档评估，不作为生产部署建议。
