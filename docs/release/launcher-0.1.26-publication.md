---
doc_type: evidence
intended_for: maintainers
purpose: Record Launcher 0.1.26 and private Maven publication of the issue 145 sorting fix.
date: 2026-10-20
---

# Launcher 0.1.26 与私有 Maven 发布

用户明确授权 main 提交/推送、更新并关闭 #145、重新发布 Launcher 和私有 Maven。

- 修复源码提交：`8c8e461b73224d8faddbc7573e550cb549901f05`，已推送 `origin/main`。
- [Issue #145](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/145) 已补充修复、兼容性和测试结果并关闭。
- [Launcher 0.1.26](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.26)
  已发布，沿用 prerelease 渠道；annotated tag 指向上述源码提交。
- Launcher SHA-256：`051639bf738633cc63c2e4acfb5a4e91f0a3782c45bdcf767876abd465c7ff40`；六个 GitHub 资产的服务端 SHA-256 与本地文件全部一致。
- 私有 Nexus `foggy-snapshots` 已发布 `com.foggysource:*:9.3.0-SNAPSHOT`，42 个坐标。
  引擎时间戳版本：`9.3.0-20261020.103830-3`。未改变开发版本号。
- 私有发布排除 `foggy-mcp-launcher` 与 `build-support/foggy-coverage-report`，Launcher 按独立 GitHub 渠道交付。

## 发布验证

标准 `runtime_launcher_publish.py` 完成 clean/package、Console 重建、Function delivery gate、
JAR 内嵌资产校验、默认/Console opt-in 两组运行时 smoke、六资产远端 digest 校验。
正式打包额外运行 5,777 项 Java 测试，0 失败/错误，11 项 Windows 平台条件跳过。
前序完整回归包括 Windows/Linux 各 5,856 项、五数据库、47 个 IT 类、571 项前端和 26 项浏览器；
详见[统一排序 workitem](../9.5/9.5.5/workitems/REFACTOR-order-by-field-binding.md)。

正式发行 JAR 再次执行原始 Chinook v1.4.5 的 validate/execute/explain，
119 个 artist 的顺序与独立 SQLite 查询一致；降序分页、total 和非法排序拒绝通过。
夹具 SHA-256 与 #145 原始数据一致。

私有库上传使用 Maven deploy plugin 3.1.4 与显式 snapshot repository，覆盖独立 parent 的模块。
打包复用已验证代码和正式重建的前端资源，不重复执行已通过的测试。
发布后从正常 HTTPS 仓库端点下载 42 个坐标的 POM/JAR，内容与本地输出完全一致，
仓库 SHA-1 校验全部通过，并记录下载内容 SHA-256。
发行 JAR 与私有库引擎的 1,143 个 class 文件逐项相同，包含本次三个新增排序实现类。

## 本地证据保留说明

发布脚本的第二条 Maven 命令包含整个受影响 reactor 的 `clean`，实际会清理根 `target`，
与 runbook 描述的仅 artifact-owning modules clean 不一致。首轮被中断，未产生可发布产物；
随后将证据和日志移出清理目录，再完整执行标准门禁发布。
在首次 clean 前未先移出全部临时证据，是本次操作的疏漏，部分忽略目录中的报告副本已删除。

Linux 全量、常规 IT 和六条外部服务报告保留并重新核验；Windows 的 883 份模块报告重新收集，
仍为 5,856 项、0 失败/错误、11 平台跳过，其中包含正式打包的 5,777 项重跑。
原五数据库矩阵 XML、专项报告副本和原始汇总文件未完整保留；已执行的通过结果记录在修复提交
`8c8e461b` 的 workitem 和 issue 评论中。恢复后的 `target/order-binding/final-summary.json`
明确标识为依据已提交记录与保留报告重建的 receipt，不冒充原始执行汇总。

发布日志和下载核验在 `D:/workspace/foggy-data-mcp/releases/issue145-release-evidence/`；
精简 receipt 副本在 `target/order-binding/publication/`。这些目录均不提交真实凭据或运行配置。

后续已按用户授权修复 [Launcher clean 范围](../9.5/9.5.5/workitems/BUG-launcher-clean-scope.md)：
保留两个产物模块的 scoped clean，移除受影响 reactor package 命令中的 clean；真实 Maven 回归通过。
