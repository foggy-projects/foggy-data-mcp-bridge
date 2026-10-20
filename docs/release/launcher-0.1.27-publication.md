---
doc_type: evidence
intended_for: maintainers
purpose: Record Launcher 0.1.27 and private Maven publication of the issue 146 naming fix.
date: 2026-10-20
---

# Launcher 0.1.27 与私有 Maven 发布

用户明确授权 main 提交/推送、更新并关闭 #146、重新发布 Launcher 和私有 Maven。

- 修复源码提交：`40309be1126a4b09417c56e1a3eaa127cc07f6cf`，已推送 origin/main；#146 已更新修复、兼容性与测试结果并关闭。
- [Launcher 0.1.27](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.27) 已发布，沿用 prerelease 渠道；annotated tag 指向该修复提交。
- Launcher SHA-256：`e3eb39bf97ad99193193f585c9d2f7914501dc5af7886227bfdeaf11f1d0f095`；六个 GitHub 资产的服务端摘要与本地文件全部一致。
- 私有 Nexus foggy-snapshots 已发布 42 个 `com.foggysource:*:9.3.0-SNAPSHOT` 坐标；
  引擎时间戳版本为 `9.3.0-20261020.140714-4`，未改变开发版本号。
- 私有发布排除 foggy-mcp-launcher 与 build-support/foggy-coverage-report；Launcher 独立通过 GitHub 交付。

## 验证

正式 runtime_launcher_publish.py 完成两个产物模块的 scoped clean、受影响 reactor package、
Console 重建、Function delivery gate、内嵌资源检查、默认及 Console opt-in 两组运行时 smoke，
并在公开 release 前后核验六资产摘要。正式打包额外运行 5,803 项 Java 测试，零失败/错误，
11 项 Windows 平台跳过；Console 12 项单元、类型检查、生产构建通过。

前序专项 177 项、Windows/Linux 全量各 5,882 项、五数据库、47 个 IT 类、571 项前端、
26 项浏览器、36 项发布脚本测试详见[修复工作项](../9.5/9.5.5/workitems/BUG-146-qm-field-names.md)。
Linux 补齐 Windows 平台跳过。历史 v9.5.0 冻结合同的 1 项既有错误单独保留，
当前 main 合同通过；在线 LLM 分支未启用。不同线路存在重复覆盖，不相加作为独立测试总数。

正式发行 JAR 重放 issue 原始 Chinook 模型与 DSL：JSON/Markdown、validate/execute/explain、
旧维度路径和旧返回键均通过。新旧查询都返回 Iron Maiden / 34 / 33.66，与独立 SQLite SQL 一致。

私有发布使用 Maven deploy plugin 3.1.4、显式 snapshot repository 和 deployAtEnd，
复用已验证的源码及前端构建，不重复执行已通过测试。发布后下载全部 42 个坐标的 POM/JAR，
与本地输出逐字节一致，仓库 SHA-1 全部通过并记录 SHA-256。
私有引擎与 Launcher 内嵌引擎的 1,144 个 class 完全相同，包含新增 QmColumnNaming。

## 证据与升级

本轮发布没有执行 root/reactor clean，96 份已记录结果 JSON 的哈希前后相同。
原专项与全量证据保留在 target/issue146-tests/ 和 target/issue146-full/。
发布日志、命令、smoke 结果、Chinook 复测、GitHub 摘要和 Nexus 下载核验保存在
`D:/workspace/foggy-data-mcp/releases/issue146-release-evidence/`；不将含运行配置的原始日志上传公开仓库。

升级后按[命名合同](../dev-guide/qm-field-names.md)刷新受影响模型并失效旧查询结果缓存。
私有 Maven 消费方可用 -U 更新 snapshot；固定列集合的调用应显式指定 columns。
