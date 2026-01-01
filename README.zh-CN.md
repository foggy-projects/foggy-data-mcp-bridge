# Foggy Data MCP Bridge

[English](README.md) | [📚 完整文档](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/)

**AI 原生语义层框架** - 让 AI 助手通过 MCP 协议安全、精准地查询业务数据。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MCP](https://img.shields.io/badge/MCP-兼容-purple.svg)](https://modelcontextprotocol.io/)

---

## 🚀 为什么需要这个项目？

### ❌ 问题：让 AI 直接写 SQL 很危险

让大语言模型直接生成 SQL 存在严重的安全和可维护性问题：

| 问题 | 影响 |
|------|------|
| **安全风险** | AI 可能生成 `DELETE`、`UPDATE` 或访问敏感表，难以有效防范 |
| **Schema 暴露** | 必须将完整数据库结构提供给 AI，暴露内部设计细节 |
| **业务语义缺失** | `order_status=3` 是什么意思？AI 不知道,用户也不想关心 |
| **复杂 JOIN 易错** | 多表关联和聚合逻辑脆弱，调试成本高 |
| **数据库方言混乱** | MySQL、PostgreSQL、SQL Server、MongoDB - AI 需要分别适配 |
| **执行不可控** | 生成的 SQL 不透明，难以拦截或修改 |

### ✅ 我们的方案：基于 DSL 查询语言的语义层

AI 不直接写 SQL，而是发送**结构化 JSON 查询**到语义层：

```
AI → JSON DSL 查询 → 语义层 → 安全 SQL → 数据库
                        ↓
                  • 防止 SQL 注入
                  • 强制权限控制
                  • 处理多表 JOIN
                  • 抽象数据库方言
                  • 支持运行时权限注入
```

**示例**：AI 只需要知道语义含义，无需了解数据库内部结构：

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["customer$name", "sum(totalAmount)"],
  "filters": [{"field": "orderDate", "op": ">=", "value": "2024-01-01"}],
  "orderBy": [{"field": "totalAmount", "dir": "DESC"}],
  "limit": 10
}
```

框架自动生成优化的、安全的 SQL，包含正确的 JOIN 和聚合。

---

## ⭐ 核心特性

### 🔒 **安全第一**
- **基于 DSL 的查询** - AI 永远不接触原始 SQL，从根本上消除注入风险
- **字段级访问控制** - 精确定义每个角色可访问的模型和字段
- **只读设计** - DSL 仅支持 `SELECT`，不支持 `DELETE`/`UPDATE`/`DROP`
- **运行时权限注入** - 在查询执行前拦截并修改查询

### 🎯 **模型即代码**
- **基于 JavaScript 的建模** - 使用 [FSScript](docs-site/zh/fsscript/guide/introduction.md)（类 JavaScript 语法）定义数据模型
- **函数复用** - 不同于静态的 YAML/JSON，支持函数、导入和动态逻辑
- **TM/QM 文件** - 表模型（TM）+ 查询模型（QM）构建语义层
- **计算字段** - 在模型中定义复杂业务指标，而非在查询中

### 🌐 **多数据库支持**
无缝支持：
- ✅ MySQL 5.7+
- ✅ PostgreSQL 12+
- ✅ SQL Server 2012+
- ✅ SQLite 3.30+
- ✅ MongoDB（通过扩展）

同一份 DSL 查询在所有数据库上运行 - 自动方言转换。

### 🤖 **AI 原生集成**
- **MCP 协议** - 原生支持 [Model Context Protocol](https://modelcontextprotocol.io/)
- **基于角色的端点** - `/mcp/admin/rpc`、`/mcp/analyst/rpc`、`/mcp/business/rpc`
- **自然语言查询** - AI 自动将用户问题转换为 DSL
- **Claude Desktop & Cursor** - 开箱即用集成主流 AI 工具

### 📊 **数据可视化**
- **自动图表生成** - 趋势图、柱状图、饼图等
- **图表渲染服务** - 由 `chart-render-service` 扩展提供支持
- **带图表导出** - 下载数据时附带可视化图表

### 🚀 **生产就绪**
- **基于 Spring Boot** - 企业级 Java 框架
- **Docker 支持** - 使用 Docker Compose 一键部署
- **完善文档** - 基于 VitePress 构建的双语文档站点（中/英）
- **可扩展架构** - 图表、MongoDB、基准测试等扩展系统

---

## 🎬 快速开始（Docker）

### 1. 克隆并启动

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo

# 可选：设置 OpenAI API key 以启用自然语言查询
cp .env.example .env
# 编辑 .env 配置 OPENAI_API_KEY（可选）

docker compose up -d
```

### 2. 验证服务

```bash
curl http://localhost:7108/actuator/health
```

### 3. 连接 AI 客户端

**Claude Desktop** - 添加到 `claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "foggy-dataset": {
      "url": "http://localhost:7108/mcp/analyst/rpc"
    }
  }
}
```

**Cursor IDE** - [查看集成指南](docs-site/zh/mcp/integration/cursor.md)

### 4. 开始查询！

现在用自然语言向 AI 提问：
- *"显示上周按品牌分组的销售数据"*
- *"上个月退货率最高的商品有哪些？"*
- *"生成一个按地区比较收入的图表"*

---

## 📖 工作原理

### 1️⃣ 定义数据模型（TM 文件）

使用 FSScript 语法创建 `FactSalesModel.tm`：

```javascript
export const model = {
    name: 'FactSalesModel',
    caption: '销售数据',
    tableName: 'fact_sales',

    dimensions: [{
        name: 'product',
        tableName: 'dim_product',
        foreignKey: 'product_key',
        caption: '商品',
        properties: [
            { column: 'brand', caption: '品牌' },
            { column: 'category', caption: '品类' }
        ]
    }],

    measures: [
        { column: 'quantity', caption: '销量', aggregation: 'sum' },
        { column: 'sales_amount', caption: '销售额', aggregation: 'sum' }
    ]
};
```

### 2️⃣ AI 发送语义查询

AI 不需要知道表结构，只需要知道语义字段：

```json
{
  "model": "FactSalesQueryModel",
  "columns": ["product$brand", "salesAmount"],
  "filters": [{ "field": "orderDate", "op": ">=", "value": "2024-01-01" }],
  "orderBy": [{ "field": "salesAmount", "dir": "DESC" }],
  "limit": 10
}
```

### 3️⃣ 框架生成安全 SQL

```sql
SELECT p.brand, SUM(f.sales_amount) as salesAmount
FROM fact_sales f
LEFT JOIN dim_product p ON f.product_key = p.product_key
WHERE f.order_date >= '2024-01-01'
GROUP BY p.brand
ORDER BY salesAmount DESC
LIMIT 10
```

**没有 SQL 注入风险。没有未授权访问。只有安全的语义查询。**

---

## 🏗️ 项目结构

```
foggy-data-mcp-bridge/
├── foggy-core/                    # 核心工具类
├── foggy-fsscript/                # FSScript 脚本引擎（类 JavaScript）
├── foggy-dataset/                 # 多数据库查询层（方言）
├── foggy-dataset-model/           # 语义层引擎（TM/QM）
├── foggy-dataset-mcp/             # MCP 服务端实现
├── foggy-dataset-demo/            # 演示：电商示例数据
├── foggy-bean-copy/               # Bean 映射工具
├── docs-site/                     # VitePress 文档站点（双语）
│
└── addons/                        # 扩展模块
    ├── chart-render-service/      # 图表生成服务
    ├── foggy-benchmark-spider2/   # Spider2 基准测试
    ├── foggy-dataset-client/      # 数据集客户端 SDK
    ├── foggy-dataset-model-mongo/ # MongoDB 模型支持
    ├── foggy-dataset-mongo/       # MongoDB 查询层
    └── foggy-fsscript-client/     # FSScript 客户端工具
```

### 核心模块

| 模块 | 说明 |
|------|------|
| **foggy-dataset-model** | 语义层引擎 - TM/QM 建模、DSL 查询执行 |
| **foggy-dataset-mcp** | MCP 服务端 - AI 助手集成 |
| **foggy-dataset** | 数据库抽象 - MySQL、PostgreSQL、SQL Server、SQLite |
| **foggy-fsscript** | 脚本引擎 - TM/QM 文件的 JavaScript 语法 |
| **foggy-dataset-demo** | 示例项目 - 电商数据模型 |

### 扩展插件

| 扩展 | 用途 |
|------|------|
| **chart-render-service** | 从查询结果生成图表 |
| **foggy-dataset-mongo** | MongoDB 支持（NoSQL） |
| **foggy-benchmark-spider2** | Spider2 基准测试（Text-to-SQL 评估） |

---

## 📚 文档

### 📘 快速开始指南
- [简介](docs-site/zh/mcp/guide/introduction.md) - 什么是 Foggy MCP
- [Docker 部署](docs-site/zh/quick-start/docker-setup.md) - 一键部署
- [第一次查询](docs-site/zh/quick-start/first-query.md) - 运行第一个 AI 查询

### 📗 核心概念
- [TM/QM 建模](docs-site/zh/dataset-model/guide/introduction.md) - 构建语义层
- [TM 语法手册](docs-site/zh/dataset-model/tm-qm/tm-syntax.md) - 表模型参考
- [QM 语法手册](docs-site/zh/dataset-model/tm-qm/qm-syntax.md) - 查询模型参考
- [DSL 查询 API](docs-site/zh/dataset-model/api/query-api.md) - JSON 查询参考

### 📙 FSScript 引擎
- [为什么用 FSScript](docs-site/zh/fsscript/guide/why-fsscript.md) - 使用场景
- [语法指南](docs-site/zh/fsscript/syntax/variables.md) - 语言参考
- [Spring Boot 集成](docs-site/zh/fsscript/java/spring-boot.md) - Java 集成

### 📕 MCP 集成
- [Claude Desktop 配置](docs-site/zh/mcp/integration/claude-desktop.md)
- [Cursor 集成](docs-site/zh/mcp/integration/cursor.md)
- [MCP 工具参考](docs-site/zh/mcp/tools/overview.md)
- [API 使用](docs-site/zh/mcp/integration/api.md)

### 🌐 完整文档站点
**访问：[https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/)**

---

## 🎯 使用场景

### 📊 商业智能
- **即席查询** - 业务用户用自然语言提问
- **多维分析** - 按维度分组、聚合度量
- **KPI 仪表盘** - 使用计算字段跟踪指标

### 🔍 数据分析平台
- **自助分析** - 非技术用户无需 SQL 即可查询数据
- **动态过滤** - 无需了解 Schema 即可灵活设置条件
- **数据探索** - AI 帮助发现洞察

### 🏢 企业数据网关
- **统一数据访问** - 跨多个数据库的单一语义层
- **访问控制** - 基于角色的字段级权限
- **审计日志** - 跟踪所有数据访问

### 🤖 AI 智能体开发
- **RAG 系统** - 为 AI 推理检索业务数据
- **聊天机器人** - 从数据库回答业务问题
- **工作流自动化** - AI 驱动的数据操作

---

## 🛠️ 开发

### 前置要求
- **Java 17+**
- **Maven 3.6+**
- **Docker**（可选，用于演示）

### 本地构建

```bash
# 构建所有模块
mvn clean install

# 运行 MCP 服务
cd foggy-dataset-mcp
mvn spring-boot:run
```

### IDE 配置
查看 [IDE 开发指南](docs-site/zh/mcp/guide/quick-start.md) 了解 IntelliJ IDEA / VS Code 配置。

---

## 🤝 贡献

我们欢迎贡献！请：

1. Fork 本仓库
2. 创建特性分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 开启 Pull Request

---

## 📄 许可证

[Apache License 2.0](LICENSE)

---

## 🌟 Star 历史

如果您觉得这个项目有用，请在 GitHub 上给我们一个 ⭐️！

[![Star History Chart](https://api.star-history.com/svg?repos=foggy-projects/foggy-data-mcp-bridge&type=Date)](https://star-history.com/#foggy-projects/foggy-data-mcp-bridge&Date)

---

## 📞 支持与社区

- **GitHub Issues**：[报告问题或请求功能](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues)
- **文档站点**：[完整文档](https://foggy-projects.github.io/foggy-data-mcp-bridge/zh/)
- **讨论区**：[加入讨论](https://github.com/foggy-projects/foggy-data-mcp-bridge/discussions)

---

**用 ❤️ 为 AI + 数据社区构建**
