# 简介

FSScript 是一个面向 Java 应用的轻量级脚本语言，采用类 JavaScript 语法，专注于简化配置与模板开发。

## 设计理念

FSScript 的设计目标**不是追求执行性能**，而是：

- **简化配置** - 当 YAML 或 XML 过于复杂时的替代方案
- **模板生成** - 像 JavaScript 一样灵活地生成模板
- **降低门槛** - JSON 主体 + JavaScript 协助，容易上手
- **深度 Spring 集成** - 直接调用 Spring Bean 或集成 Java 接口
- **面向 B 端场景** - 为企业级应用的后台任务、报表、数据处理等场景设计

## 与其他方案对比

| 特性 | FSScript | GraalJS | SpEL | Groovy |
|------|----------|---------|------|--------|
| 学习成本 | 低 (JS 子集) | 低 | 中 | 中 |
| Spring Bean 导入 | `import '@bean'` | 需手动绑定 | 需 `#bean` | 需配置 |
| Java 类导入 | `import 'java:...'` | `Java.type()` | 有限支持 | 原生支持 |
| 脚本模块化 | ES6 import/export | 需自实现 | 不支持 | 支持 |
| 开箱即用 | ✅ | ❌ 需集成层 | ✅ | ✅ |
| 执行性能 | 解释执行 | JIT 编译 | 编译执行 | 编译执行 |
| 适用场景 | 配置/模板/规则 | 通用脚本 | 表达式求值 | 通用脚本 |

## 适用场景

- 需要用脚本定义 SQL 模板、动态查询
- 需要灵活配置业务规则但不想引入重量级引擎
- 团队熟悉 JavaScript，希望快速上手

## 核心特性

- **类 JavaScript 语法** - 支持 let/const/var、箭头函数、模板字符串等
- **Spring 深度集成** - 通过 `@beanName` 导入 Spring Bean
- **Java 互操作** - 通过 `java:` 前缀导入 Java 类
- **ES6 模块化** - import/export 语法，支持脚本模块化
- **IDE 友好** - JavaScript 语法被主流 IDE 识别
