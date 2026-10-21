---
doc_type: publication-evidence
intended_for: maintainers
purpose: Record Launcher 0.1.33 and private Maven publication for the Compose follow-up fixes and verify their consumers.
date: 2026-10-21
---

# Launcher 0.1.33 与 Compose 后续修复发布

本轮代码来自 `main` 提交 `0cae461a80d9bc93ecfa41b27278b358f8354e20`，构建时工作区干净。
Launcher 使用 prerelease 渠道；私有 Maven 快照发布为 `9.3.0-SNAPSHOT`。

## 修复范围

本轮收敛 Compose 组合计划中的八类错误：右嵌套 UNION 分组与重复行、仅排序的 UNION 分支、
默认路径 UNION 列数校验、透传派生关系的输出 schema、派生 offset-only 分页、嵌套 G10 JOIN
provenance、SQL Server 派生 ORDER BY，以及 `FETCH NEXT 0`。保留 UNION 按位置匹配列、输入分页
与最终分页独立、既有未知字段 fail-closed 的合同。Launcher 内嵌 Engine 自动配置继续注册
`MemberBindingService`。

## Launcher 与 Maven 发布

- [Launcher 0.1.33 prerelease](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.33) 已发布；tag 指向本轮源提交。
- 六个资产均从 GitHub 下载；下载包和发布 staging 包逐字节一致，五项 SHA256SUMS 校验通过。manifest 标记 `releaseReady=true`、`dirty=false`，source commit 与 tag 对应。
- Launcher JAR 大小 85,976,962 bytes，SHA-256：`1975e09bc642c97374533af824ba3bef5fbf987aacba566b53469777a2d21c40`。
- 私有 Maven `foggy-snapshots` 发布并回读 42 个坐标。所有 POM/JAR 与本地部署输出逐字节相等，仓库 SHA-1 校验通过；排除独立发行的 Launcher 与 coverage-report。
- Engine 快照为 `9.3.0-20261021.121816-10`，Viewer 为 `9.3.0-20261021.121816-9`，Runtime API 为 `9.3.0-20261021.121816-9`。
- Launcher 内嵌 Engine 与 Maven Engine 的 1,163 个 ZIP 文件项内容逐项一致；两个完整 JAR 的容器 SHA 不同。已从下载的 Launcher 内嵌 Engine 检查 Boot 自动配置 imports、`DbModelAutoConfiguration` 常量引用及 `MemberBindingService.class`。

## 验证

- 发布 reactor Maven 测试合计 11,914 项，0 失败、0 错误、22 项跳过；release 构建、Analytics Console 类型检查/构建和 FAP question delivery gate 均通过。
- 修复回归矩阵的 SQLite、MySQL 5.7、MySQL 8、PostgreSQL、SQL Server 分别通过 423、232、262、262、232 项，共 1,411 项，零失败、错误或跳过。
- Launcher 默认 profile 与 Analytics Console opt-in 启动冒烟通过；16 项字段绑定、权限、pivot、元数据刷新检查通过。
- 下载的 Launcher 通过 19 项 Compose 制品门禁，覆盖 validate/preview/execute、JOIN 绑定、基础/派生分页、UNION 和零 limit 权限。
- 使用 Launcher 0.1.33 与 CLI 0.1.25 重放原始 #150/#151 Chinook 请求：四个正向场景逐行匹配 SQLite 基线；输入 limit=5 返回客户 1—5，无分页 59 行、47 个匹配客户、总额 477.53；#150 无效 JOIN 只执行 validate/preview，未执行 query。
- 独立消费项目只依靠 Spring Boot 自动配置，无 Foggy 包扫描或显式 Bean import；SQLite、内存两组测试均启动成功，断言 `MemberBindingService` 唯一 Bean，并真实执行成员查询（2/2）。
- 承达 Query 消费端的 Runtime API / Analytics 配置启动测试 5/5 通过。Query app classpath 中 Engine、Viewer、Runtime API 快照 JAR 摘要与刚下载的私有 Maven 工件相同。

独立消费测试显式添加 `reactor-core` 测试依赖，以满足 Viewer MCP 方法签名的可选 Reactor 类型；这仅调整本地验证项目，不改变已发布源码或 POM。
承达工作区原有未提交改动未触碰；只运行 Query app 指定测试。

## 证据

可复验日志、下载件、发布包与机器可读摘要保存在：
`D:/workspace/foggy-data-mcp/releases/compose-followup-0.1.33/`。
重点摘要为 `launcher-test-summary.json`、`download-verification.json`、
`private-maven/snapshot-verification.json`、`private-maven/consumer-context-result.json`、
`tms-consumer-verification.json`、`downloaded-compose-smoke/summary.json` 和
`published-chinook/chinook-retry/summary.json`。发布和 Maven 原始日志不提交仓库。
