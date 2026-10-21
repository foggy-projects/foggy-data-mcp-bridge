---
doc_type: publication-evidence
intended_for: maintainers
purpose: Record the member policy fixes, Query Bean registration verification and publication from the canonical main checkout.
date: 2026-10-21
---

# Launcher 0.1.31 与私有 Maven 发布

本轮修复提交 `883f21151a951b9475489eb4244629a2bcb516af` 已推送 main。
主目录 `D:/workspace/foggy-data-mcp/foggy-data-mcp-bridge` 已切回 main，后续统一在该目录开发；
原 `releases/engine-9.3.0` 工作区保留为 detached HEAD，只保留历史源码和测试证据。
目录约定已写入根 `CLAUDE.md`。

## 发布内容与兼容性

- [Launcher 0.1.31](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.31)：沿用 prerelease 渠道，manifest 固定上述修复提交，dirty=false。
- 私有 Maven：42 个坐标，版本仍为 `9.3.0-SNAPSHOT`，排除独立交付的 Launcher 和构建用 coverage-report。
- Engine 精确快照：`9.3.0-20261021.070108-8`；Viewer 精确快照：`9.3.0-20261021.070108-7`。

成员权限按请求实际依赖应用子维度及中间层级约束，无法安全映射时拒绝；
修复表达式字段权限、最终权限守卫、层级 JOIN、Viewer 完整字段绑定和模型刷新一致性。
detached 校验按目录身份隔离，普通 open 不再隐式替换同名目录，显式 replacement 保留。
字段 name/alias 合同、公共 Model SPI v2 和既有返回封装保持兼容。

`MemberBindingService` 由 Engine 的 `DbModelAutoConfiguration.@Import` 显式注册，
不依赖业务应用扫描 Foggy 包。Viewer 保留 QueryFacade-only 应用启动能力；
缺少真实绑定能力时成员请求明确失败。

## 测试及发布复验

修复阶段的完整结果见[工作项](../9.5/9.5.5/workitems/BUG-member-policy-and-detached-identity.md)：
Windows/Linux 全量各 5,983 项，Linux 无跳过；Windows 的 11 项平台跳过逐项由 Linux 覆盖。
SQLite、MySQL 5.7/8、PostgreSQL、SQL Server 门禁通过，服务 IT 覆盖 47/47 类。
补充 Query 启动及相关回归 24 项全部通过。

本次从主目录正式打包重新执行 5,906 项 Java 测试，零失败/错误，11 项 Windows 平台跳过；
其中两个 Query 启动测试通过，覆盖默认 SQLite 和 memory Viewer。
计数以本次构建日志的模块汇总为准，不把工作目录中既存报告加到本次结果。
Analytics Console 12 项测试、类型检查及生产构建通过；私有 Maven 构建另执行 Runtime Console
38 项测试、类型检查与生产构建。Maven 上传阶段复用已完成的 Java 验证，未重复执行 Java 测试。

Launcher 默认模式、Console opt-in 和 16 组字段绑定制品测试通过。
新增 11 组成员权限、层级操作、Viewer 刷新与 detached 身份场景在待发布及重新下载的 JAR 上通过。
六个发行资产重新下载后，与本地包、GitHub digest 和 SHA256SUMS 一致。

42 个 Maven 坐标的远端 POM/JAR 与构建输出逐字节、SHA-1 核对通过。
独立 Maven 消费项目解析 71 项 classpath，Engine/Viewer 精确快照的摘要均与发布记录相同。
发布的 Engine/Viewer 与 Launcher 内嵌对应模块全部 class 字节一致；
使用下载的 Maven JAR 重放两个 Query 应用上下文测试，均成功注册 Bean 并完成真实成员查询。
本机安装的两个 SNAPSHOT 别名也与已发布构件相同。

Launcher JAR SHA-256：`e3f69911f4e09d776071925770a50300205b06870aee0ad5f14903f7bb71d788`。

## 消费与证据

本次发布同时更新本机 Maven 安装别名，替换此前承达候选构建误用的本地中间 SNAPSHOT。
其他机器可使用 `-U` 更新远端快照；有自行安装的旧 SNAPSHOT 时仍需核对实际依赖字节，
不能只凭 `9.3.0-SNAPSHOT` 版本字符串判断。需要可复现构建时固定上述精确时间戳版本。
既有承达候选归档不会随仓库发布改变，应重新构建并核对实际依赖及应用启动门禁。
本轮未重新部署或启停客户服务。

本次仅执行模块级 clean，原工作区的 108 份测试摘要文件 SHA-256 均未变化，原始日志仍保留。
发布、下载复验及额外备份位于
`D:/workspace/foggy-data-mcp/releases/member-policy-release-evidence/`，
最终汇总 `publication-final-verification.json`；含临时运行配置的原始证据不提交到开源仓库。
