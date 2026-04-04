# P0-metadata 可选模型缺失时错误提示优化-需求

## 基本信息
- 目标版本：`8.1.10.beta`
- 需求等级：`P0`
- 状态：`待处理`
- 责任项目：`foggy-data-mcp-bridge`

## 背景
2026-04-02 在 Odoo 本地分层测试中，`dataset.get_metadata` 构建全量 metadata 时触发失败，外层表现为 metadata JSON 异常，内层定位到：

- `QueryModel`：`OdooMrpProductionQueryModel`
- `Model`：`OdooMrpProductionModel`
- 目标表：`mrp_production`
- 当前错误：`sqlTable is null`

从排查结果看，这类失败不一定是 TM/QM 配置写错，也可能是：

- 对应 Odoo 可选模块未安装，导致物理表不存在
- model-list 仍静态包含该 QM，metadata 全量构建时无差别尝试加载
- 运行环境数据源与模型声明不一致

当前错误信息过于原始，无法让调用方快速判断是“模型配置错误”还是“环境缺模块”。

## 问题定义
当前 `sqlTable is null` 至少有四个缺口：

1. 没指出是哪个 `QM/TM` 失败。
2. 没指出缺的是哪张表。
3. 没指出对应数据源。
4. 没提示这可能是“可选模块未安装”而非代码写坏。

这会直接拉高 Java/Python/Odoo 三侧排障成本，也不利于把问题归到正确责任边界。

## 目标
- 当 metadata 构建或模型加载失败时，错误信息必须包含：
  - `QM` 名称
  - `TM` 名称
  - `dataSourceName`
  - 目标表名
  - 高概率原因提示
- 错误描述应优先面向排障，而不是保留底层空指针式文案。

建议错误文案方向：

```text
加载 OdooMrpProductionQueryModel 失败：未能在数据源 odoo 中找到表 mrp_production。
请检查对应 Odoo 模块 mrp 是否已安装，或确认 TM/QM 的 tableName/sqlTable 配置是否与目标库一致。
```

## 任务拆分

### 1. Java 元数据/模型加载层
- 在模型解析或 SQL 表绑定阶段补齐上下文信息：`qmName`、`tmName`、`dataSourceName`、`tableName`
- 不再直接向上抛出裸 `sqlTable is null`
- 缺表场景统一转成可读异常

### 2. Java gateway 返回层
- 保留并透传上述详细错误，不要在 RPC 包装层被截断成模糊消息
- 若对外仍需通用错误码，错误详情至少进入 `message/details`

### 3. Java 测试
- 增加“目标表不存在”场景的单测或 contract test
- 覆盖 metadata 全量构建与单模型构建两个入口

## 验收标准
- 再次遇到 `mrp_production` 缺失时，错误信息中可直接看到：
  - `OdooMrpProductionQueryModel`
  - `OdooMrpProductionModel`
  - `odoo`
  - `mrp_production`
- 调用方无需翻源码即可判断是否为“未安装模块”类问题
- 不再出现仅有 `sqlTable is null` 的对外暴露文案

## 非目标
- 本条不直接规定是否要在 Java 层跳过可选模型
- 本条不替代环境初始化脚本对 `mrp` 的安装职责定义
- 本条不回退现有 gateway 管理与测试入口改动
