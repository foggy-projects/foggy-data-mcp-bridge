# 简介

Foggy Dataset 是一个嵌入式语义层框架，基于类javascript语法fsscript实现声明式数据建模，深度集成spring，使得前端或上游可以使用DSL语言(Foggy Dataset的DSL采用JSON技术)的方式安全地访问数据库。

## 特性

- **TM (Table Model)**: 将数据库表映射为表格数据模型
- **QM (Query Model)**: 通过将多个TM表格模型进行join，生成查询模型，并植入权限控制，为前端或上游提供数据查询服务
- **DSL**: 通过JSON格式查询数据，简单易上手
- **多数据库支持**: MySQL、PostgreSQL、SQL Server、SQLite、mongo

## 快速链接

- [快速开始](./quick-start.md)
- [核心概念](./concepts.md)
- [TM 语法手册](../tm-qm/tm-syntax.md)
