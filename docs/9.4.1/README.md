---
doc_role: version-readme
version: 9.4.1
theme: spi-v2-adoption-hardening
status: READY_FOR_SIGNOFF
recorded_at: 2026-07-24
---

# 9.4.1 SPI v2 Adoption/Hardening

9.4.1 是 9.4.0 Model SPI v2 的无破坏采用与加固版本。它冻结新增 legacy debt，迁移可安全切换的
第一方查询入口，并让已实现的 provider capability 在发现期 fail closed；它不执行 9.5.0 legacy exit。

## 本次结果

- 直接依赖旧 `foggy-dataset-model` 聚合的第一方 POM：`15 -> 14`；Odoo bridge 的冗余直接依赖已移除。
- 聚合之外导入旧 `com.foggyframework.dataset.db.model.*` 的生产 Java 文件：
  `12 modules / 114 files -> 11 modules / 110 files`。
- GraphQL 与 DataViewer 的第一方执行路径直接使用 `QueryFacadeRequest`，不再调用
  `LegacyQueryFacadeAdapter`；原公开 legacy 转换 API 保留。
- 9 个 addon/runtime 自动配置排序点改用精确 JVM 名称 `afterName`，不再编译期导入
  `DbModelAutoConfiguration`。
- repository architecture guard 固定剩余依赖、旧导入 ceiling、deprecated query adapter 和
  direct `model.query()` bypass allowlist。
- catalog 在 discovery 阶段拒绝虚报 `QUERY` 或 `CACHE_INVALIDATION`、但未实现对应小型 port 的 provider。
- JDBC TCK 精确覆盖 `QUERY`；query-cache TCK 精确覆盖 `CACHE_INVALIDATION`。未新增或暗示
  `MODEL_LOAD`、namespace isolation、`ATOMIC_REFRESH` 能力。

## 兼容承诺

- 继续保留旧 `foggy-dataset-model` 聚合、deprecated facade/adapter/高级方法/构造器和旧 SPI。
- 不修改现有 Maven 坐标，不删除或改签名公共 API/SPI。
- 本版本无数据库迁移、权限变化或不可逆操作。
- 9.5.0 是否删除桥接层必须基于本版本盘点和独立 breaking-change 授权重新决定。

## 文档入口

- canonical workitem：`docs/9.4.1/workitems/FEATURE-v941-spi-v2-adoption-hardening.md`
- adoption inventory / provider coverage：`docs/9.4.1/model-spi-v2-adoption.md`
- 正式签收：`docs/9.4.1/acceptance/version-signoff.md`

## 验证边界

本版本只执行受影响 Maven reactor 的 compile、unit、context、compatibility 和 TCK；所有命令使用
`-pl ... -am`，未运行 `mvn install`。Owner 明确排除的 Step 5/Step 7、authority、semantic/portable
replay、source-seal、通用五库矩阵、GitHub CI、tag、release 和 publish 均未运行，签收不得把这些
排除项描述为已验证。
