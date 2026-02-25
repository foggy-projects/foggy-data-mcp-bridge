---
name: foggy-model-workflow
description: Foggy TM/QM 模型开发工作流编排。根据用户需求自动识别数据源类型，按正确顺序调用 tm-generate/tm-generate-mongo → qm-generate → qm-validate，一站式完成模型设计与验证。
---

# Foggy 模型开发工作流

编排 TM/QM 模型开发的完整流程，自动识别数据源类型，按正确顺序调用子技能，一站式完成模型设计、生成和验证。

## 使用场景

当用户需要以下操作时使用：

1. **从数据库表生成完整模型**：
   - "我有 MySQL 表，帮我生成数据模型"
   - "PostgreSQL 表需要转换成 Foggy 模型"
   - "根据这个 DDL 创建 TM/QM 模型"

2. **从 MongoDB 集合生成完整模型**：
   - "我有 MongoDB 集合，需要生成 TM/QM"
   - "这个 mongo collection 的数据要接入 Foggy"

3. **完整模型开发**：
   - "帮我设计完整的查询模型"
   - "创建销售数据的分析模型"

4. **模型验证**：
   - "检查这个模型是否正确"
   - "验证我生成的 TM/QM 文件"

## 工作流流程

```
用户请求
    ↓
1. 识别数据源类型和已有资源
    ├─ JDBC 数据源（MySQL/PostgreSQL/SQL Server/SQLite）
    ├─ MongoDB 集合
    └─ 已有模型（仅生成 QM 或仅验证）
    ↓
2. 选择子技能组合
    ├─ tm-generate（JDBC）
    ├─ tm-generate-mongo（MongoDB）
    ├─ qm-generate（从 TM 生成 QM）
    └─ qm-validate（验证模型）
    ↓
3. 按顺序执行子技能
    ↓
4. 汇总结果，提供集成建议
```

## 执行流程

### 步骤 1：识别用户意图

通过用户输入判断需要执行哪些环节：

| 用户输入 | 识别结果 | 需要的子技能 |
|----------|----------|-------------|
| "我有 MySQL 表..." | 从 JDBC 生成完整模型 | tm-generate → qm-generate → qm-validate |
| "我有 MongoDB 集合..." | 从 MongoDB 生成完整模型 | tm-generate-mongo → qm-generate → qm-validate |
| "帮我从这个 TM 生成 QM" | 已有 TM，生成 QM | qm-generate → qm-validate |
| "验证这个模型" | 已有 TM/QM，仅验证 | qm-validate |
| "根据 DDL 生成 TM" | 仅生成 TM | tm-generate |

### 步骤 2：调用子技能

**JDBC 数据源流程**：
1. 调用 `tm-generate` 技能，传入 DDL 或表名
2. 获取生成的 TM 文件路径
3. 调用 `qm-generate` 技能，传入 TM 文件路径
4. 获取生成的 QM 文件路径
5. 调用 `qm-validate` 技能，验证 TM/QM 正确性

**MongoDB 数据源流程**：
1. 调用 `tm-generate-mongo` 技能，传入集合信息
2. 获取生成的 TM 文件路径
3. 调用 `qm-generate` 技能，传入 TM 文件路径
4. 获取生成的 QM 文件路径
5. 调用 `qm-validate` 技能，验证 TM/QM 正确性

### 步骤 3：汇总结果

输出以下信息：

1. **生成的文件列表**：
   - TM 文件路径
   - QM 文件路径
   - 文件字段摘要

2. **验证结果**：
   - 模型是否通过验证
   - 如有错误，列出错误信息

3. **集成建议**：
   - 需要注册到 `application.yml` 的 QM 模型名称
   - 可选：前端组件生成建议（调用 `frontend-component-generator`）

## 输出格式

```
✅ 模型生成完成

## 生成的文件
- TM 文件：{TM文件路径}
- QM 文件：{QM文件路径}

## 字段摘要
TM 模型包含：
- 属性字段：{数量}个
- 维度字段：{数量}个
- 度量字段：{数量}个

QM 查询模型包含：
- 列组：{数量}个
- 可查询字段：{数量}个

## 验证结果
{验证结果或错误信息}

## 集成步骤
1. 将 QM 模型名称注册到 `application.yml`：
```yaml
mcp:
  semantic:
    model-list:
      - {QM模型名称}
```

2. 重启服务使模型生效

3. (可选) 生成前端组件：使用 `frontend-component-generator` 技能
```

## 决策规则

- 如果用户提供 DDL 或表名 → 判断为 JDBC 数据源，使用 `tm-generate`
- 如果用户提供集合名或 JSON 结构 → 判断为 MongoDB，使用 `tm-generate-mongo`
- 如果用户提供 TM 文件路径 → 仅调用 `qm-generate`
- 如果用户提供 QM 文件路径 → 仅调用 `qm-validate`
- 如果验证失败 → 列出错误，建议修改后重新验证
- 如果用户需要前端组件 → 询问是否调用 `frontend-component-generator`

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| TM 生成失败 | 停止工作流，提示用户检查输入 |
| QM 生成失败 | 停止工作流，提示用户检查 TM |
| 验证失败 | 输出验证结果，建议修改后重新验证 |
| 子技能执行超时 | 停止工作流，提示用户网络或服务问题 |

## 示例对话

### 场景 1：从 MySQL 表生成完整模型

**用户**：
"我有 MySQL 中的 `orders` 表，帮我生成 Foggy 数据模型"

**AI 分析**：
- 数据源类型：JDBC（MySQL）
- 需要流程：tm-generate → qm-generate → qm-validate

**AI 执行**：
1. 使用 `Skill` 工具调用 `tm-generate`，传入表名 `orders`
2. 获取 TM 文件：`FactOrderModel.tm`
3. 使用 `Skill` 工具调用 `qm-generate`，传入 TM 路径
4. 获取 QM 文件：`FactOrderQueryModel.qm`
5. 使用 `Skill` 工具调用 `qm-validate`，验证模型
6. 汇总结果，输出集成建议

### 场景 2：从 MongoDB 集合生成模型

**用户**：
"MongoDB 的 `user_events` 集合需要接入 Foggy"

**AI 分析**：
- 数据源类型：MongoDB
- 需要流程：tm-generate-mongo → qm-generate → qm-validate

**AI 执行**：
1. 使用 `Skill` 工具调用 `tm-generate-mongo`，传入集合名
2. 获取 TM 文件：`FactUserEventsModel.tm`
3. 使用 `Skill` 工具调用 `qm-generate`，传入 TM 路径
4. 获取 QM 文件：`FactUserEventsQueryModel.qm`
5. 使用 `Skill` 工具调用 `qm-validate`，验证模型
6. 汇总结果

### 场景 3：已有 TM，仅生成 QM

**用户**：
"我已有 FactSalesModel.tm，帮我生成 QM"

**AI 分析**：
- 已有 TM 资源
- 需要流程：qm-generate → qm-validate

**AI 执行**：
1. 使用 `Skill` 工具调用 `qm-generate`，传入 TM 路径
2. 获取 QM 文件
3. 使用 `Skill` 工具调用 `qm-validate`，验证模型
4. 汇总结果

## 注意事项

1. **编排职责**：本技能仅负责调用和编排子技能，不重复实现子技能逻辑
2. **保持独立性**：用户仍可单独使用任何子技能（`/tm-generate`、`/qm-generate` 等）
3. **灵活组合**：支持跳过某些环节（如已有 TM，只生成 QM）
4. **错误传播**：子技能失败时，停止工作流并返回错误信息
5. **服务依赖**：`qm-validate` 需要后端服务运行中，如服务不可用则跳过验证

## 参考文档

- **tm-generate**：生成 JDBC TM 模型
- **tm-generate-mongo**：生成 MongoDB TM 模型
- **qm-generate**：从 TM 生成 QM
- **qm-validate**：验证 TM/QM 模型
- **frontend-component-generator**：生成前端组件
- **tm-syntax-reference**：TM 语法参考
- **qm-syntax-reference**：QM 语法参考