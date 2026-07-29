<p align="center">
  <img src="logo.png" alt="Foggy Data MCP Bridge" width="120">
</p>

<h1 align="center">Foggy Data MCP Bridge</h1>

<p align="center">
  面向 AI 数据分析的治理型语义层：通过 MCP 查询业务数据，而不是把数据库 Schema 和原始 SQL 直接交给 AI。
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="https://foggy-projects.github.io/foggy-data-mcp-docs/zh/">完整文档</a> ·
  <a href="https://github.com/foggy-projects/foggy-data-mcp-bridge/releases">版本发布</a> ·
  <a href="https://github.com/foggy-projects/foggy-data-mcp-bridge/issues">问题反馈</a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-blue.svg" alt="Apache 2.0 License"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00.svg" alt="Java 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F.svg" alt="Spring Boot 3.4">
  <img src="https://img.shields.io/badge/MCP-JSON--RPC-7B61FF.svg" alt="MCP JSON-RPC">
</p>

## 项目简介

让大模型直接理解数据库表结构并生成 SQL，会使 AI 与物理表、数据库方言和脆弱的 JOIN
逻辑紧密耦合。Foggy 在 AI 与数据库之间增加语义建模和查询治理层：

```text
AI 客户端
   │  MCP / JSON Query DSL
   ▼
Foggy Runtime
   ├── TM/QM 语义模型
   ├── Namespace 与字段策略
   ├── 查询规划与方言转换
   └── 模型生命周期与 Runtime API
   │
   ▼
MySQL · PostgreSQL · SQL Server · SQLite · 可选 MongoDB
```

AI 面对的是销售额、客户、商品、下单日期等业务概念。Foggy 在运行时将它们解析为受治理的
只读查询，并统一处理关联、聚合和数据库方言。

## 核心能力

- **语义建模**：使用 FSScript 定义可复用的表模型（TM）和查询模型（QM）。
- **原生 MCP 接入**：通过 JSON-RPC 暴露分析、业务和管理工具。
- **结构化 Query DSL**：让 AI 在模型约束内生成查询，而不是提交任意 SQL。
- **运行时治理**：支持 Namespace 隔离、语义字段白名单、物理列黑名单和工具调用审计。
- **模型生命周期**：Bundle 注册、模型校验、原子刷新和 Runtime 能力发现。
- **可扩展后端**：核心支持关系数据库，可选接入 MongoDB、缓存、向量、预聚合、
  GraphQL、Data Viewer 和 Odoo。

## 快速开始

已发布的 Runtime Launcher 是本地开发和体验的最短路径。它需要 Java 17 或更高版本，
默认使用本地 SQLite 数据库。

> Launcher 是开发/测试发行物。对外部署前，请补齐生产认证、网络隔离、数据源治理和运行环境加固。

### 1. 下载并启动 Runtime

下面的命令固定使用 Launcher `0.1.16`。如有新版本，请以
[Releases](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases)
页面为准。

```bash
mkdir foggy-runtime && cd foggy-runtime

curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.16/foggy-runtime-launcher-0.1.16.jar
curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.16/start-foggy-runtime.sh
curl -fLO https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/download/foggy-runtime-launcher-v0.1.16/SHA256SUMS

grep -E 'foggy-runtime-launcher-0.1.16.jar|start-foggy-runtime.sh' SHA256SUMS | sha256sum -c -
chmod +x start-foggy-runtime.sh
./start-foggy-runtime.sh
```

Windows 用户可从同一 Release 下载 `start-foggy-runtime.ps1`，然后在 PowerShell
中运行。默认服务地址为 `http://127.0.0.1:18066`。

### 2. 验证服务

```bash
curl http://127.0.0.1:18066/readyz
curl http://127.0.0.1:18066/api/v1/capabilities
```

`capabilities` 会返回 Runtime API 版本、已启用能力和 `securityMode`。如果部署返回
`auth-code`，调用 Runtime API 时还需提供服务端配置的 auth code。

### 3. 连接 MCP 客户端

连接分析端 JSON-RPC，并通过 `X-NS` 选择 Namespace：

```json
{
  "mcpServers": {
    "foggy-ai-analysis": {
      "url": "http://127.0.0.1:18066/mcp/analyst/rpc",
      "headers": {
        "X-NS": "salesdrop"
      }
    }
  }
}
```

少数 MCP 客户端还要求填写 `"type": "http"`。Runtime 元数据和数据源凭证不应放进
`mcpServers`。

配置客户端前，可以先探测 MCP 端点：

```bash
curl -X POST http://127.0.0.1:18066/mcp/analyst/rpc \
  -H 'Content-Type: application/json' \
  -H 'X-NS: salesdrop' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

### 4. 接入数据源和语义模型

一个可查询的环境还需要：

1. 创建 Namespace，并绑定数据源；
2. 将 TM/QM 模型放入模型 Bundle；
3. 完成模型校验和刷新；
4. MCP 客户端使用相同的 `X-NS` 请求头。

你可以通过
[AI 分析上手指南](https://foggy-projects.github.io/foggy-data-mcp-docs/zh/)
加载内置 SQLite 示例，或接入自己的数据库。编写模型与查询时，可参考
[语义层语法手册](https://foggy-projects.github.io/foggy-data-mcp-docs/zh/whitepaper/v1.0/semantic-layer-syntax-reference.html)
和
[Query DSL 手册](https://foggy-projects.github.io/foggy-data-mcp-docs/zh/whitepaper/v1.0/query-dsl-syntax-reference.html)。

## 查询示例

AI 提交语义字段，不需要拼接物理表 JOIN：

```json
{
  "model": "FactSalesQueryModel",
  "payload": {
    "columns": [
      "product$brand",
      "sum(salesAmount) as totalSalesAmount"
    ],
    "slice": [
      {
        "field": "salesDate$caption",
        "op": "[)",
        "value": ["2026-01-01", "2027-01-01"]
      }
    ],
    "groupBy": ["product$brand"],
    "orderBy": ["-totalSalesAmount"],
    "limit": 10
  },
  "mode": "execute"
}
```

Foggy 会解析模型关系、执行已配置的访问控制、规划聚合、转换目标数据库方言，并返回结构化结果。

## 运行源码 Docker 演示

仓库包含一个基于 MySQL 的电商演示。该方式会构建当前源码，并且需要
OpenAI 兼容服务的 API Key。

```bash
git clone https://github.com/foggy-projects/foggy-data-mcp-bridge.git
cd foggy-data-mcp-bridge/docker/demo
cp .env.example .env
# 编辑 .env，设置 OPENAI_API_KEY；需要时同时修改 OPENAI_BASE_URL。
docker compose up -d --build
curl http://localhost:7108/actuator/health
```

模型、配置、日志和清理命令详见 [Docker 演示指南](docker/demo/README.md)。

## 仓库结构

| 目录 | 职责 |
| --- | --- |
| `foggy-dataset-model-api` | 稳定查询 DTO 与后端 SPI |
| `foggy-dataset-model-core` | Provider Catalog 与 fail-closed 治理 |
| `foggy-dataset-model-engine` | TM/QM 加载、规划、刷新和执行 |
| `foggy-runtime-api` | 数据源、Namespace、Bundle、模型与查询 API |
| `foggy-dataset-mcp` | MCP 工具、发现、分发和审计 |
| `foggy-mcp-launcher` | 可执行 Spring Boot 装配 |
| `foggy-dataset` | JDBC 访问与关系数据库方言 |
| `foggy-fsscript` | 语义模型使用的脚本引擎 |
| `foggy-dataset-demo` | 示例 Schema、数据与语义模型 |
| `addons` | 可选后端与产品集成 |

完整模块边界和生命周期见[当前架构文档](docs/architecture/README.md)。

## 本地开发

前置条件：

- JDK 17+
- Maven 3.9+
- 集成测试环境需要 Docker Compose v2

运行单元测试：

```bash
mvn -B -ntp test -DskipITs
```

开发某个模块时运行聚焦测试：

```bash
mvn -B -ntp -pl foggy-runtime-api,foggy-dataset-mcp -am test -DskipITs
```

集成测试默认不启用，执行时可能需要外部数据库：

```bash
mvn -B -ntp verify -DskipITs=false
```

较大的架构调整请先创建 Issue，再通过范围清晰的 Pull Request 提交。不要提交密钥、连接串，
或包含敏感信息的日志。

## 文档与支持

- [用户文档](https://foggy-projects.github.io/foggy-data-mcp-docs/zh/)
- [架构文档](docs/architecture/README.md)
- [版本发布](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases)
- [Bug 与功能建议](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues)
- [讨论区](https://github.com/foggy-projects/foggy-data-mcp-bridge/discussions)
- [Foggy Odoo Bridge](https://github.com/foggy-projects/foggy-odoo-bridge)

## 许可证

Copyright © Foggy Data MCP Bridge contributors.

本项目基于 [Apache License 2.0](LICENSE) 开源。
