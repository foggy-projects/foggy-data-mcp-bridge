# Foggy Dataset Model 文档

Foggy Dataset Model 是一个声明式数据模型定义框架，通过 `.jm`（JDBC Model）和 `.qm`（Query Model）文件定义数据模型，自动生成 SQL 查询，支持多维度分析、聚合计算等复杂场景。

## 核心概念

| 概念 | 说明 |
|------|------|
| **JM (JDBC Model)** | 数据模型定义，描述表结构、维度、度量 |
| **QM (Query Model)** | 查询模型定义，描述可查询的列和列组 |
| **维度 (Dimension)** | 分析的角度，如时间、商品、客户 |
| **度量 (Measure)** | 可聚合的数值，如金额、数量 |
| **属性 (Property)** | 模型的字段定义 |

## 支持的数据库

- MySQL 5.7+
- PostgreSQL 12+
- SQL Server 2012+
- SQLite 3.30+

## 文档导航

### 入门
- [快速入门](quick-start.md) - 5 分钟创建第一个数据模型

### 核心指南 (`guide/`)
- [JM/QM 语法手册](guide/JM-QM-Syntax-Manual.md) - 完整的模型定义语法参考
- [API 参考](guide/API-Reference.md) - HTTP API 接口文档
- [父子维度](guide/Parent-Child-Dimension.md) - 层级结构维度（组织架构、商品分类等）

### 权限控制 (`security/`)
- [数据权限控制 - DataSetResultStep](security/Authorization-Control.md) - Java 代码方式，动态条件注入、结果脱敏
- [数据权限控制 - accesses](security/QueryModel-Accesses-Control.md) - QM 配置方式，SQL 层面行级过滤

### 开发者文档 (`dev/`)
- [测试数据模型](dev/TEST_DATA_MODEL.md) - 电商星型模型设计（用于测试）
- [多数据库测试](dev/MULTI_DATABASE_TESTING.md) - Docker 环境测试指南

## 目录结构

```
docs/
├── README.md              # 本文件（文档导航）
├── quick-start.md         # 快速入门
│
├── guide/                 # 核心指南
│   ├── JM-QM-Syntax-Manual.md
│   ├── API-Reference.md
│   └── Parent-Child-Dimension.md
│
├── security/              # 权限控制
│   ├── Authorization-Control.md
│   └── QueryModel-Accesses-Control.md
│
└── dev/                   # 开发者文档
    ├── TEST_DATA_MODEL.md
    └── MULTI_DATABASE_TESTING.md
```

## 示例项目

项目包含完整的电商数据模型示例：

```
src/test/resources/foggy/templates/ecommerce/
├── model/           # JM 模型文件
│   ├── DimDateModel.jm
│   ├── DimProductModel.jm
│   ├── DimCustomerModel.jm
│   ├── FactSalesModel.jm
│   └── ...
└── query/           # QM 查询模型文件
    ├── FactSalesQueryModel.qm
    ├── OrderPaymentJoinQueryModel.qm
    └── ...
```

## 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    查询请求 (QueryRequest)                │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                  查询引擎 (QueryEngine)                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ QM 查询模型  │  │ JM 数据模型  │  │  公式服务        │  │
│  │ (.qm 文件)  │  │ (.jm 文件)  │  │ (FormulaService)│  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                      SQL 生成                            │
│         SELECT ... FROM ... JOIN ... WHERE ...          │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    数据库执行                            │
│            MySQL / PostgreSQL / ...                     │
└─────────────────────────────────────────────────────────┘
```

## 许可证

Apache License 2.0
