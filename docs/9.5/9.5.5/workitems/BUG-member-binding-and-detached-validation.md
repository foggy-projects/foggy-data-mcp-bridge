---
doc_type: implementation-plan
status: DONE
date: 2026-10-21
intended_for: engine and Runtime maintainers
purpose: Repair issues 148 and 149 while preserving member permissions and detached catalog isolation.
---

# #148 成员绑定与 #149 同目录校验

目标迭代沿用 9.5.5，用户授权直接在 main 修复，先回归再完整 reactor 和多数据库测试。

## 合同与兼容性

- 成员入口绑定源 QM 的查询 name 和真实维度 owner；alias 仍是输出名称，不扩展为通用输入同义词。
  兼容无歧义的维度短名、id/caption 及完整路径。未知或歧义字段明确拒绝，不猜根维度。
- synthetic QM 的 canonical 身份包含目标维度完整路径；同一目标的不同输入形式归一。
  显式 `QM#root` 保留根成员与子树字段的既有查询语义。嵌套成员入口返回子维表的完整目录，
  不能使用事实表 DISTINCT 或从根表投影替代，否则会丢失未被引用的成员。
- 权限仍针对源 QM 的 MEMBER_QUERY 决策。源字段、行谓词、TM/QM memberPermission 和
  deniedColumns 映射到实际目标；无法表达的约束 fail closed，不清空物理列拒绝。
  不缓存身份权限决定，不绕过 QueryFacade 或 catalog 的 slot/name 校验。
- Runtime `/api/v1/members` 使用 RuntimeEnvelope；旧 `/jdbc-model/dimension/v2` 保持 RX。
  同步修改独立 CLI 仓库的 main：优先 Runtime 路由，仅未装配路由的非 Runtime 404 回退旧接口；
  RX 成功需要 code=200 且 data.items 为列表，权限/业务错误不回退，不改变旧服务端包装。
- detached validation 按 namespace + 规范化物理目录识别同源 Bundle，在候选中使用当前源码。
  不修改 live registry、缓存或 generation；不同目录 canonical name 冲突仍拒绝。
  clearExisting 不删除 live Bundle；恢复明确的同源目录诊断，不恢复旧共享模型实例行为。

## 影响面与风险

成员变化涉及 resolver/factory/loader、简单成员入口、模型及字段权限、成员内部 patch、
Runtime 自动装配、元数据消费者和缓存身份。原有根 synthetic、字典、层级、聚合成员过滤需回归。
维度短名歧义应要求完整路径；重复遍历 flattened 维度集合不得产生虚假歧义。

已有客户端需要注意：曾经错误返回父维度的嵌套成员请求将返回正确子维表；确需父维度时应显式
指定父维度。synthetic canonical 名称随真实完整路径归一，缓存仍按既有 catalog/generation 管理。
旧 Launcher 的 CLI 回退仅兼容响应包装，无法修复其引擎中的错误成员数据，仍需升级 Launcher。
源事实行权限无法投影到目标成员表时返回拒绝，不用删掉谓词或查询事实 DISTINCT 的方式“兼容”。

目录变化涉及 models.validate、candidate query 和 authoring workspace 共用的隔离 Bundle view。
资源发现与按名查找必须使用一致候选视图；Windows 路径、file URI、同名不同 namespace、
跨 Bundle 依赖、模型删除和失败后旧 catalog 可用性必须验证。不得按 basename 全局去重。

## 执行与验证

1. 固化真实 TM/QM 和 SQLite 回归，断言目标表的独立 SQL 结果及权限。
2. 统一成员绑定、canonical synthetic 加载及权限映射；补齐 Runtime 自动装配和 HTTP 回归。
3. 修复隔离目录身份，验证已登记目录原样/编辑/clearExisting、真实重名和候选隔离。
4. 专项通过后跑完整 reactor；用独立 MySQL 5.7/8、PostgreSQL、SQL Server 数据库执行相关合同。
   补跑外部服务 IT 和 Linux 平台测试，实际 Launcher 制品验证两条问题链。
5. 原始证据保留在 ignored target/issue-148-149-fix；只提交无敏感信息的摘要。

首次实现验收不包含提交推送、发布或 issue 关闭。2026-10-21 用户追加授权：
提交并推送 Engine/CLI main，更新并关闭 #148/#149，发布 Launcher、CLI 和私有 Maven 快照。
发布结果另记发布证据文档；以下保留首次实现验收时的测试结论。

## 实现边界

- `MemberFieldBinding` 统一实际 owner、完整路径、查询 name 和权限谓词映射。
  synthetic 实例固定编译时的源 QM；权限仍按请求求值，不缓存用户决策。
- 仅 synthetic 的子树字段按完整绑定名校验，避免 `album$artist$caption` 被截成 `album`；
  普通 QM 的维度权限分组保持原合同。隐藏 renamed 列不能通过自动生成的旧字段重新开放。
- synthetic 的 id/caption/property 同样建立物理列映射，保留 deniedColumns 检查。
- 同目录判断使用 namespace + real path，支持 file URI 和规范化路径；资源发现、依赖查找、
  Bundle 名称/包名视图一致。不同目录的真实冲突继续拒绝。
- detached validate 不发布、不改 live Bundle；文件监听器对源编辑的 admission 行为独立存在。
  测试隔离该监听器后验证 candidate 不污染 live，不能据此承诺任意现场编辑都仍可查询旧模型。

## 验收结果（2026-10-21）

Engine 基于 main `4bd67812`，CLI 基于独立仓库 main `be39ead`；本轮未提交、推送或发布。
新增成员绑定回归 31 项，加上 Runtime 自动装配/HTTP/真实 authoring 回归 3 项，
Maven 全量用例由 5921 增至 5955。所有下表验收运行均零失败、零错误。

| 验证 | 执行数 | 跳过 |
|---|---:|---:|
| 成员、权限、Runtime、隔离专项 | 188 | 0 |
| SQLite 字段绑定门禁（含 IT 命名的权限用例） | 362 | 0 |
| Windows Maven 全量 verify | 5955 | 11 |
| Linux Maven 全量 verify | 5955 | 0 |
| MySQL 5.7 / MySQL 8 | 171 / 201 | 0 |
| PostgreSQL / SQL Server | 201 / 171 | 0 |
| 通用查询及生命周期 IT | 410 | 0 |
| Redis / Mongo / MCP | 3 / 31 / 23 | 0 |
| Vector / MySQL 5.7 preagg | 20 / 3 | 0 |
| CLI Python 全量 | 148 | 0 |
| 发布脚本 / 门禁脚本 Python | 38 / 6 | 0 |

四库每条 lane 均执行全部 31 项新成员回归，并检查 JDBC 数据库身份、必需测试类和新报告。
仓库 47/47 个 IT 类均有执行，缺失清单为空。Windows 的 11 项平台跳过已由 Linux 全量覆盖。
MCP 的在线 LLM 评测未启用，验收包含离线配置检查和真实工具调用；前端无源码变更，
Maven 使用已有前端构建资产，本轮不宣称新增独立前端测试结果。

实际 Launcher JAR 通过 17 项检查，包含 CLI、RuntimeEnvelope、旧 RX、隐藏成员权限、
同目录反复校验，以及已有 pivot/窗口/公式/排序/刷新合同。Chinook v1.4.5 独立复现结果：
Artist=275（首项 AC/DC）、Album=347、Track=3503；登记前后、clearExisting 和另一 Namespace
验证均 6/6 成功。运行结束关闭测试 JVM。

- 验收 JAR SHA-256：`2bfb9beabf4c27ec83577d8441dbfa0150f5431fa467e9a403dd57156086cd71`。
- 原始证据：ignored `target/issue-148-149-fix/`，包括 XML、命令、日志、制品响应、源码摘要和最终摘要。
- 最终核对 4300 个非文档快照文件与当前源码一致；制品冒烟脚本和 CLI 源码另有测试版本摘要。
- Linux 工作副本仅对 Shell 脚本做 CRLF→LF 转换；发布脚本的前置环境失败保留原始日志，
  在 LF、Bash、jq 就绪后通过全部 38 项。后四条服务 IT 在源码摘要一致的前提下复用已编译类，
  所有指定测试实际执行，并要求新生成的 XML 报告。
- 未运行根 clean；只清理本轮创建且通过 ownership 校验的容器与网络，保留测试证据。

本轮实现遵循既有 catalog canonical identity、candidate 隔离、QueryFacade 和逐请求权限流程；
未弱化 slot/name 校验、引入旁路 SQL 或修改公共 Model SPI v2。

## 发布完成（2026-10-21）

用户追加授权后的提交、issue 关闭、Launcher 0.1.30 / CLI 0.1.25 发行、私有 Maven 快照及正式制品复验均已完成。
详见[发布证据](../../../release/launcher-0.1.30-cli-0.1.25-publication.md)。
