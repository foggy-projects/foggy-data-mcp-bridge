---
layout: home
hero:
  name: Foggy Data MCP Bridge
  text: AI 原生语义层框架
  tagline: 让 AI 助手安全、准确地通过 MCP 协议查询业务数据
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/mcp/guide/quick-start
    - theme: alt
      text: MCP 集成指南
      link: /zh/mcp/guide/introduction
    - theme: alt
      text: GitHub
      link: https://github.com/foggy-projects/foggy-data-mcp-bridge

features:
  - icon: 🔒
    title: 安全优先
    details: 基于 DSL 的查询消除 SQL 注入风险。字段级访问控制，只读设计，运行时权限注入。
    link: /zh/dataset-model/api/authorization
    linkText: 了解权限控制
  - icon: 🎯
    title: 模型即代码
    details: 使用 FSScript（类 JavaScript 语法）定义语义层。支持函数、导入和动态逻辑。
    link: /zh/dataset-model/tm-qm/tm-syntax
    linkText: TM/QM 语法
  - icon: 🌐
    title: 多数据库支持
    details: MySQL、PostgreSQL、SQL Server、SQLite、MongoDB - 同一套 DSL 查询适用所有数据库。
    link: /zh/dataset-query/guide/introduction
    linkText: 数据库支持
  - icon: 🤖
    title: AI 原生 MCP
    details: 原生支持 MCP 协议。开箱即用集成 Claude Desktop、Cursor 等 AI 工具。
    link: /zh/mcp/guide/introduction
    linkText: MCP 集成
  - icon: 📊
    title: 自动可视化
    details: 自动生成图表 - 趋势图、柱状图、饼图等。内置图表渲染服务。
    link: /zh/mcp/tools/chart
    linkText: 图表功能
  - icon: 🚀
    title: 生产就绪
    details: 基于 Spring Boot，支持 Docker 部署，中英双语文档，可扩展的插件系统。
    link: /zh/mcp/guide/quick-start
    linkText: 快速部署
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #bd34fe 30%, #41d1ff);
}

.problem-table {
  margin: 1rem 0;
}
</style>

## 为什么选择这个项目？

### ❌ 问题：让 AI 直接写 SQL 很危险

| 问题 | 影响 |
|------|------|
| **安全风险** | AI 可能生成 `DELETE`、`UPDATE` 或访问敏感表 |
| **Schema 暴露** | 必须将完整数据库结构分享给 AI |
| **缺乏业务语义** | `order_status=3` 是什么意思？AI 不知道 |
| **复杂 JOIN** | 多表关联和聚合查询容易出错，调试成本高 |

### ✅ 我们的方案：语义层 + DSL 查询语言

```
AI → JSON DSL 查询 → 语义层 → 安全 SQL → 数据库
                         ↓
                 • 防止 SQL 注入
                 • 强制访问控制
                 • 处理多表 JOIN
                 • 抽象数据库方言
```

**示例**：AI 只需知道语义含义，无需了解数据库内部结构

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["customer$name", "sum(totalAmount)"],
  "filters": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}],
  "orderBy": [{"field": "totalAmount", "dir": "DESC"}],
  "limit": 10
}
```

## 🎯 典型应用场景

<div class="use-cases">

### 📊 商业智能
- **即席查询** - 业务用户用自然语言提问
- **多维分析** - 按维度分组，聚合度量
- **KPI 仪表盘** - 使用计算字段追踪指标

### 🔍 数据分析平台
- **自助分析** - 非技术用户无需 SQL 即可查询
- **动态过滤** - 灵活条件无需了解 Schema
- **数据探索** - AI 帮助发现洞察

### 🏢 企业数据网关
- **统一数据访问** - 跨多数据库的单一语义层
- **访问控制** - 基于角色的字段级权限
- **审计日志** - 追踪所有数据访问

</div>

## 🚀 快速开始

**Docker 一键启动**：

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
docker compose up -d
```

**Claude Desktop 配置**：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

然后就可以用自然语言查询数据了：
- *"显示上周各品牌的销售额"*
- *"哪些产品上个月退货率最高？"*
- *"生成各区域收入对比图表"*

## 📦 核心模块

<div class="modules-grid">

| 模块 | 说明 | 文档 |
|------|------|------|
| **foggy-dataset-model** | 语义层引擎 - TM/QM 建模、DSL 查询执行 | [入门指南](/zh/dataset-model/guide/introduction) |
| **foggy-dataset** | 数据库抽象层 - 多数据库方言支持 | [查询层文档](/zh/dataset-query/guide/introduction) |
| **foggy-fsscript** | 脚本引擎 - 类 JavaScript 语法解析 TM/QM 文件 | [FSScript 指南](/zh/fsscript/guide/introduction) |
| **foggy-dataset-mcp** | MCP 服务器 - AI 助手集成 | [MCP 服务](/zh/mcp/guide/introduction) |

</div>

### 📘 TM/QM 建模
- [TM 语法手册](/zh/dataset-model/tm-qm/tm-syntax) - 表模型定义
- [QM 语法手册](/zh/dataset-model/tm-qm/qm-syntax) - 查询模型定义
- [Query DSL API](/zh/dataset-model/api/query-api) - JSON 查询参考
- [计算字段](/zh/dataset-model/tm-qm/calculated-fields) - 派生指标定义

### 📗 FSScript 脚本引擎
- [为什么选择 FSScript](/zh/fsscript/guide/why-fsscript) - 设计理念
- [语法指南](/zh/fsscript/syntax/variables) - 语言参考
- [Spring Boot 集成](/zh/fsscript/java/spring-boot) - Java 集成
