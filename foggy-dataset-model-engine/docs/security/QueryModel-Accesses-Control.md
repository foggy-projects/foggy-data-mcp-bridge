# QueryModel 权限控制 (accesses)

本文档介绍如何在 QueryModel (.qm) 中使用 `accesses` 配置实现 **SQL 层面的行级权限控制**。

## 概述

`accesses` 是 QueryModel 的一个配置项，用于在 **SQL 生成阶段** 动态添加权限过滤条件。与 `DataSetResultStep` 方式不同，`accesses` 直接修改 SQL WHERE 子句，适合行级数据隔离场景。

### 两种权限控制方式对比

| 特性 | accesses (本文档) | DataSetResultStep |
|------|------------------|-------------------|
| 执行时机 | SQL 生成阶段 | 查询结果返回后 |
| 作用层面 | 修改 SQL WHERE 条件 | 处理结果集 |
| 适用场景 | 行级数据过滤（多租户、部门隔离） | 字段脱敏、结果加工 |
| 性能影响 | 数据库层面过滤，高效 | 内存层面处理 |
| 配置位置 | .qm 文件 | Java 代码 |

## 配置语法

### 基本结构

```javascript
//导入spring bean tokenUtils 的getToken方法
import {getToken} from '@tokenUtils';

export const queryModel = {
    name: 'WhitelistQueryModel',
    model: [{
        name:'WhitelistModel',
        alias:'a'
    }],

    columnGroups: [...],
    orders: [...],

    // 权限控制配置
    accesses: [{
        property: 'clearingTeamId',
        queryBuilder: () => {
            // 在 SQL 生成时动态添加过滤条件
            query.and('a.tenant_id',  getToken().tenantId);
        }
    }]
};
```

### 配置字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dimension` | string | 二选一 | 关联的维度名称 |
| `property` | string | 二选一 | 关联的属性名称 |
| `dimensionDataSql` | function | 否 | 用于查询维度数据的 SQL 函数 |
| `queryBuilder` | function | 是 | 构建权限过滤条件的函数 |

> **注意**：`dimension` 和 `property` 必须二选一，用于指定权限控制作用的字段。
