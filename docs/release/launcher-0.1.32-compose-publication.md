---
doc_type: publication-evidence
intended_for: maintainers
purpose: Record issues 150/151 publication, exact artifacts and verification evidence.
date: 2026-10-21
---

# Launcher 0.1.32 与 Compose 修复发布

修复提交 `1f883a81ce39a7c9e2a63244326db93a150e5c9c` 已推送 main，
#150、#151 已补充修复及测试说明并关闭。开发继续使用主目录 main。

## 内容与兼容性

- 基础计划 limit/start 在权限、过滤、聚合和排序后、JOIN/UNION 消费前生效；输入分页与最终分页保持独立。
- JOIN 连接键按实际输出字段绑定，覆盖 QM name/alias、请求 AS、聚合及派生输出；三个 Runtime 模式在 SQL 执行前拒绝无效字段。
- 修复普通查询 SQL Server 请求别名与默认排序重复；保留表达式、表实例和结果阶段身份。
- RuntimeEnvelope、CLI payload、Model SPI v2 保持兼容；诊断新增内部 Compose 错误码和阶段。
- 保留既有窗口能力限制：MySQL 5.7 窗口函数及 SQL Server Compose 窗口结果阶段 CTE 提升明确拒绝。

方案和完整验收见[工作项](../9.5/9.5.5/workitems/BUG-150-151-compose-binding-pagination.md)。
专项 598 项、Windows/Linux 全量各 6,021 项、五数据库 1,336 项、服务 IT 490 项通过。
Linux 无跳过，Windows 11 个平台跳过已逐项由 Linux 覆盖；IT 源码类覆盖 47/47。

## 发布与制品复验

- [Launcher 0.1.32](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.32) 沿用 prerelease 渠道，manifest 固定修复提交且 dirty=false。
- 私有 Maven 发布 42 个坐标，版本 `9.3.0-SNAPSHOT`；排除独立交付 Launcher 和 coverage-report。
- Engine 精确快照 `9.3.0-20261021.103645-9`；Runtime API、Viewer 精确快照 `9.3.0-20261021.103645-8`。
- 正式打包重新执行 5,942 项 Java 测试，零失败/错误，11 项 Windows 平台跳过。Analytics Console 12 项、Runtime Console 38 项测试及各自类型检查、生产构建通过。
- 默认启动、Console opt-in、16 组字段绑定/权限/pivot/刷新检查、13 组 Compose 实际行集检查通过。
- GitHub 六个发行资产重新下载，与本地包、GitHub digest 和 SHA256SUMS 一致。
- 下载的 JAR 通过 CLI 0.1.25 重放 12 组 Compose 场景的三个模式（36 次 CLI 调用），并通过普通查询分页对照，共 13 组；逐次比对 CLI 与 HTTP 返回。CLI 无代码或协议变化，本轮不另发版本。
- 42 个 Maven 坐标的远端 POM/JAR 与构建输出逐字节和 SHA-1 核对通过；独立消费项目解析 71 项 classpath，精确快照摘要匹配。
- Launcher 与 Maven 共有的 28 个模块、2,642 个 class 字节一致。下载的 Engine/Viewer 重放两个 Query 上下文测试通过，确认 `MemberBindingService` 自动配置注册；本地安装别名也与发布构件相同。

Launcher JAR SHA-256：`48f9ba6829a9b9435def9754339aecf766c014b1f928d3e9877bdb0a07fd637e`。
私有 Maven 上传复用已完成的 Java 验证，不重复运行 Java 测试。

## 证据与后续边界

源码与冻结全量测试快照一致（文档更新、换行及构建产物另列）；保留的 6,509 份原始证据摘要均未变化。
仅执行发布脚本规定的模块级 clean，不运行根 clean。
本地发布和下载证据位于 `D:/workspace/foggy-data-mcp/releases/compose-150-151-release-evidence/`；
原始测试证据仍在主目录 `target/compose-150-151-fix/` 与 `target/compose-150-151-audit/`。
含临时配置的原始证据不提交公开仓库。

历史 v950 冻结合同的 31 模块与 main 的 43 模块冲突，是独立基线失败；
本轮 Launcher 发布流程不调用该门禁。本次发布不改变历史合同或宣称 v950 验收通过，后续独立修复。
