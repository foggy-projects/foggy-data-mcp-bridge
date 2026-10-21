---
doc_type: publication-evidence
intended_for: maintainers
purpose: Record publication of member binding and detached validation fixes for issues 148 and 149.
date: 2026-10-21
---

# Launcher 0.1.30、CLI 0.1.25 与私有 Maven 发布

引擎修复 [23a51c59](https://github.com/foggy-projects/foggy-data-mcp-bridge/commit/23a51c59ac0318dfbe40d1e3835dbb50b3798a19)
和 CLI 修复 [b7538e1](https://github.com/foggy-projects/foggy-runtime-cli/commit/b7538e18afee2e8279a7339d36ba2336e72a9224)
均已推送 main。[#148](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/148) 与
[#149](https://github.com/foggy-projects/foggy-data-mcp-bridge/issues/149) 已补充根因、影响面、兼容性和验收结果，并按 completed 关闭。

- [Launcher 0.1.30](https://github.com/foggy-projects/foggy-data-mcp-bridge/releases/tag/foggy-runtime-launcher-v0.1.30)：沿用 prerelease 渠道；manifest 绑定引擎修复提交，dirty=false、releaseReady=true。
- [CLI 0.1.25](https://github.com/foggy-projects/foggy-runtime-cli/releases/tag/v0.1.25)：发布工作流从 CLI 修复提交构建 wheel、sdist、安装脚本和清单。
- 私有 Nexus：42 个 Maven 坐标发布成功，沿用 `9.3.0-SNAPSHOT`；引擎精确快照为 `9.3.0-20261021.035003-7`。排除独立交付的 Launcher 和 build-only coverage-report。

两组 GitHub Release 均包含六个资产；全部重新下载，与 GitHub digest 和 SHA256SUMS 核对一致。
CLI wheel 内 Python 源码与发布提交一致，在独立虚拟环境安装后输出 `foggy-runtime 0.1.25`。
私有 Maven 逐坐标验证 POM/JAR 字节及远端 SHA-1；独立消费项目解析 62 项 classpath，
下载引擎与发布摘要一致，且 Launcher 内嵌引擎的 1152 个 class 与 Maven 制品逐项相同。

## 测试与制品复验

修复阶段完整方案及结果见[工作项](../9.5/9.5.5/workitems/BUG-member-binding-and-detached-validation.md)：
Windows/Linux 全量各 5955 项，Windows 的 11 项平台跳过由 Linux 覆盖；四数据库 744 项、
服务/通用 IT 490 项（47/47 IT 类）全部通过。

正式 Launcher 打包额外执行 5876 项 Java 测试，零失败/错误，11 项 Windows 平台跳过；
Console 前端 3 个文件、12 项测试通过，同时完成类型检查和生产构建。
默认模式、Console opt-in 运行冒烟以及 16 项字段绑定制品门禁全部通过。

CLI 版本更新后本机全量 148 项通过；[GitHub 发布工作流](https://github.com/foggy-projects/foggy-runtime-cli/actions/runs/39539979197)
为 147 项通过、1 项跳过（未检出相邻 Java Analytics 合同仓库），该合同项已在本机全量执行。
下载后的正式 Launcher JAR 与已安装的正式 CLI wheel 共同通过 17 项检查，涵盖成员接口、
RuntimeEnvelope、旧 RX、隐藏字段权限、同目录重验，以及 pivot、窗口、公式、排序和刷新。

正式发行 JAR 的 Chinook 独立复验：Artist=275（首项 AC/DC）、Album=347、Track=3503；
登记前后同目录验证、clearExisting、新 namespace 均 6/6 成功，Runtime 与旧 RX 成员响应均正确。
测试 JVM 已退出。首次发布复验脚本因 Windows 路径字符串转义失败，修正临时脚本的路径后通过；
未改动产品源码，原失败日志保留。

## 摘要与兼容性

- Launcher JAR SHA-256：`c11af04b990b8a351c0c1330031ca854fe3237ee58c3167724e9d3e5a2ed4374`。
- CLI wheel SHA-256：`162a0b50d2035132bf679b262fcf5f33ba7b50a462bda28a7def7705323ee0ac`。
- 私有引擎 JAR SHA-256：`6ac838bc9d817269c82f31c0670a5ef309e62985e016d4b02e0ed2078580cb31`。

name 仍为查询标识、alias 仍为输出名称；保留 slot/name guard、普通 QM 权限分组与旧 RX 接口。
成员子维度按实际目标表和完整绑定路径解析；权限无法安全映射时拒绝。CLI 仅对明确的旧服务
路由缺失 404 回退 RX，不把权限/业务失败转为成功。
同目录 detached validation 读取当前候选源码并保持 live catalog 隔离，不免除不同目录的真实冲突。
文件监听器对现场编辑的 admission 行为独立存在。公共 Model SPI v2 未变化。

私有 snapshot 消费方可用 Maven `-U` 更新；既有发行资产保留。未改动用户业务运行实例。
本次使用 scoped clean，原有 56 份测试 JSON 与外部备份摘要一致。
原始证据保存在 `D:/workspace/foggy-data-mcp/releases/member-binding-release-evidence/`，
最终汇总为 `publication-final-verification.json`；不提交含临时运行配置的原始日志。
