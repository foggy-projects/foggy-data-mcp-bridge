# 简介

FSScript 是一个面向 Java 应用的轻量级脚本语言，采用类 JavaScript 语法，专注于简化配置、格式转换、模板开发、AST语法树解析等。

## 设计理念

FSScript **不是执行性能设计**，如果您需要极性的性能，应该使用GraalJS或Groovy，同时它也不适合编写复杂业务，它的场景通常如下：

- **简化配置** - 当 YAML 或 XML 过于复杂时的替代方案
- **模板生成** - 像 JavaScript 一样灵活地生成模板
- **降低门槛** - JSON 主体 + JavaScript 协助，容易上手
- **解析语法** - 做函数权限控制等
- **深度 Spring 集成** - 直接调用 Spring Bean 或集成 Java 接口
- **面向 B 端场景** - 为企业级应用的后台任务、报表、数据处理等场景设计

## 来源

最早它从mondrian项目的mdx语法编译修改而来，用于在动态拼接mdx语句，后来用于拼接SQL语句、模板、JSON格式数据处理(java原生处理这些是一言难尽)



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
