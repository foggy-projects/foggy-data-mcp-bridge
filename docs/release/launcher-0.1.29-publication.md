---
doc_type: publication-evidence
intended_for: maintainers
purpose: Record Launcher 0.1.29 and private Maven publication of compiled field bindings and immutable dependency graph reuse.
date: 2026-10-21
---

# Launcher 0.1.29 与私有 Maven 发布

字段投影与依赖图优化已由 `a3693d0a812129f51966ef9c18247b0094a4f7f7` 推送到 main，并以该提交构建发布。

Launcher 0.1.29 已作为 prerelease 发布：[foggy-runtime-launcher-v0.1.29](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.29)。
六个发行资产上传完成并通过发布脚本摘要核验。Launcher JAR SHA-256：
`f02ee6fabced558543b7da11c7b97da0469aaab5b0a32ff23d03a42b1eaf8595`。

正式打包的 5842 项 Java 测试零失败、零错误，11 项 Windows 平台跳过；同一提交的 Linux 全量验收为 5921 项，零失败、零错误、零跳过。三组发行运行冒烟均通过，新增字段绑定制品检查 13 项全部通过，覆盖权限、排序、pivot、元数据、模型刷新及刷新后重新计算权限依赖。实际制品测试进程已退出。

私有 Nexus 发布了 42 个 Maven 坐标，snapshot engine 版本为 `9.3.0-20261021.012208-6`。逐项下载验证了 POM/JAR 字节和仓库 SHA-1。引擎 JAR SHA-256：
`a37c4c66c0dcfe58a8cc2aae8d4dddc375c7beb69bb8fa6c941fa923547b643f`。独立消费项目解析 62 项 classpath，获取的引擎 JAR 与新快照摘要一致。Launcher 内嵌的 1150 个引擎 class 与 Maven JAR 逐项相同。

0.1.29 包含 0.1.28 的字段名称/权限修复，以及这次 projection 编译与依赖图复用优化。接口合同和公共 Model SPI v2 保持不变。私有 snapshot 消费方使用 Maven `-U` 更新。

完整回归范围、四库矩阵和兼容边界见[优化工作项](../9.5/9.5.5/workitems/OPT-compiled-field-bindings.md)。发布、消费端和摘要比较证据保存在
`D:/workspace/foggy-data-mcp/releases/compiled-field-bindings-release-evidence/`；包含临时运行配置的原始 Maven 日志不提交。
