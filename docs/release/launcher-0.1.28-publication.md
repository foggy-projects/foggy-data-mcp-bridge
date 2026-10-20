---
doc_type: evidence
intended_for: maintainers
purpose: Record Launcher 0.1.28 and private Maven publication of field binding and permission dependency fixes.
date: 2026-10-21
---

# Launcher 0.1.28 与私有 Maven 发布

修复已提交并推送 main：5c4f0e01。

Launcher 0.1.28 已发布：https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.28 。六个资产摘要一致；私有 Maven 42 个坐标发布并逐文件下载核验通过，引擎版本为 9.3.0-20261020.170208-5。Launcher 内嵌引擎与私有制品的 1146 个 class 一致。

专项、双平台全量、四数据库及 IT/前端测试结果见 issue 正文和修复工作项。正式打包额外运行 5828 项 Java 测试，0 失败/错误，11 项 Windows 平台跳过已由 Linux 补齐。实际发行 JAR 在 SQLite、SQL Server 上分别通过 12 项查询/权限/聚合/排序/模型刷新检查。

兼容性：name 仍是查询标识，alias 为输出键；原先忽略的 formula alias 生效，schema 可新增 queryName，窗口分区/排序依赖权限收紧。升级需刷新受影响模型并失效旧结果缓存。后续结构优化独立推进，不改变此发行的修复范围。

Launcher SHA-256：`9f191d389dd0e2f9b1e5fc2cb4fcf712549346e363c57a74d771493dc927f71c`。
沿用 prerelease 渠道；annotated tag 指向修复提交。私有 Maven 保持 9.3.0-SNAPSHOT，
排除独立交付的 launcher 和 build-only coverage 模块。

本轮使用 scoped clean，192 份既有测试 JSON 哈希未改变。完整修复与兼容报告见
[工作项](../9.5/9.5.5/workitems/BUG-field-binding-dependencies.md)。

发行包检查使用独立 SQLite/SQL Server 数据库验证 pivot 轴与原生 SQL、TopN、改名 AVG
及非可加总计、公式 alias/AS 的值与粒度、schema、窗口权限与输出、传递依赖、聚合排序、
普通/DISTINCT 重复排序、describe/validate/explain，以及刷新后的新表达式与输出键。
SQL Server 整数 AVG 按独立原生 SQL oracle 验证，不假设与 SQLite 返回小数的类型行为相同。
初次冒烟脚本的鉴权枚举配置和 AVG 预期纠正保留在证据中，未修改生产源码。

发布及下载证据位于 `D:/workspace/foggy-data-mcp/releases/field-binding-release-evidence/`，
包括 snapshot-verification.json、github-asset-verification.json、artifact-sqlite/summary.json、
artifact-sqlserver-verified/summary.json、engine-artifact-comparison.json、evidence-preservation.json。
不将包含临时运行配置的原始日志上传公开仓库。

升级通过隔离运行时的真实模型刷新验证；本轮未接触用户业务运行实例。私有 Maven 消费方
按既有流程使用 -U 更新 snapshot。旧版本 0.1.27 制品保留，可作为部署回退依据。
