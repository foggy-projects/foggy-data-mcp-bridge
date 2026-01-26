---
name: qm-schema-viewer
description: 查看 QM（查询模型）的 schema 信息。当用户需要了解某个模型有哪些字段、字段类型、是否可筛选/排序等信息时使用。
---

# QM Schema Viewer

查看语义层 QM 模型的 schema 信息，包括字段列表、类型、描述等元数据。

## 使用场景

当用户需要以下操作时使用：
- 查看某个 QM 模型有哪些可用字段
- 了解字段的类型、描述、是否可聚合/排序
- 搜索包含特定字段的模型
- 在编写 DSL 查询前了解模型结构

## 执行流程

### 第一步：读取或创建配置

检查配置文件（按优先级）：
1. 项目配置：`.claude/config/semantic-api.config.json`
2. 用户配置：`~/.foggy/semantic-api.config.json`

如果配置不存在，询问用户：

```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| apiBaseUrl | SemanticController API 地址 | `http://localhost:7108` |
| namespace | 命名空间（通过 X-NS header 传递） | `default` |
| authorization | 授权信息（可选） | 空 |

### 第二步：确定操作类型

根据用户输入判断：

**A. 已知模型名称** → 直接获取 schema
**B. 搜索模型** → 先搜索再选择

### 第三步：获取 Schema

#### API 端点

**搜索模型**：
```
GET {apiBaseUrl}/mcp/analyst/metadata?q={keywords}

Headers:
  X-NS: {namespace}
  Authorization: {authorization}  // 如果配置了
```

**获取模型 Schema**：
```
GET {apiBaseUrl}/mcp/analyst/description-model-internal?model={modelName}

Headers:
  X-NS: {namespace}
  Authorization: {authorization}  // 如果配置了
```

#### 响应结构

```json
{
  "code": 200,
  "data": {
    "model": "UserQueryModel",
    "caption": "用户查询模型",
    "description": "用于查询用户基本信息",
    "fields": [
      {
        "name": "userId",
        "type": "BIGINT",
        "title": "用户ID",
        "description": "用户唯一标识",
        "aggregatable": false,
        "sortable": true,
        "filterable": true
      },
      {
        "name": "userName",
        "type": "TEXT",
        "title": "用户名",
        "description": "用户登录名",
        "aggregatable": false,
        "sortable": true,
        "filterable": true
      }
    ]
  }
}
```

### 第四步：展示结果

以表格形式展示字段信息：

```
模型: UserQueryModel
描述: 用于查询用户基本信息

字段列表:
| 字段名 | 类型 | 标题 | 可筛选 | 可排序 | 可聚合 | 描述 |
|--------|------|------|--------|--------|--------|------|
| userId | BIGINT | 用户ID | ✓ | ✓ | - | 用户唯一标识 |
| userName | TEXT | 用户名 | ✓ | ✓ | - | 用户登录名 |
| ...
```

## 输入要求

用户提供以下任一输入：
- **模型名称**：直接指定 QM 模型名（如 `UserQueryModel`）
- **搜索关键词**：描述需要的字段或业务场景

## 输出格式

```
📊 模型: {modelName}
📝 描述: {description}

字段列表 ({fieldCount} 个字段):

| 字段名 | 类型 | 标题 | 筛选 | 排序 | 聚合 | 描述 |
|--------|------|------|------|------|------|------|
| {name} | {type} | {title} | {✓/-} | {✓/-} | {✓/-} | {desc} |

维度字段 (使用 维度名$属性 格式引用):
- customer: 客户维度
  - customer$id: 客户ID
  - customer$caption: 客户名称
  - customer$customerType: 客户类型
```

## 约束条件

- 需要网络访问 SemanticController API
- 配置文件使用 JSON 格式
- 统一处理 JDBC 和 MongoDB 模型（语义层已统一封装）

## 决策规则

- 如果 API 调用失败 → 检查 API 地址和网络，提示用户确认服务是否启动
- 如果模型不存在 → 提示用户检查模型名称，或使用搜索功能
- 如果搜索无结果 → 扩大关键词范围，或列出所有可用模型
- 如果配置不存在 → 使用默认值，提示用户可编辑配置文件

## 配置文件模板

`.claude/config/semantic-api.config.json`:

```json
{
  "apiBaseUrl": "http://localhost:7108",
  "namespace": "default",
  "authorization": ""
}
```

## 与其他技能的关系

本技能是基础技能，被以下技能引用：
- `frontend-dsl-query` - 前端 DSL 查询引导
- `frontend-component-generator` - 前端组件生成器
