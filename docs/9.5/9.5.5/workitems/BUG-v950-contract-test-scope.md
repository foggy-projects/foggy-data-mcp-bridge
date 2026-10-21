---
doc_type: workitem
intended_for: maintainers
purpose: Separate frozen 9.5.0 authority validation from current-main script unit tests without weakening release evidence checks.
---

# v950 历史合同测试作用域与跨平台夹具

状态：VERIFIED。本项在 #150/#151 的 Launcher 0.1.32 / Maven 发布完成后独立处理，基线 `3598df5d`。

## 分析与合同

9.5.0 的已签收候选为 `03af64da7330d70d3364c9e92992710a2ed3a111`。
其合同固定 31 模块、32 项目、9.1.0.beta Launcher 边界、semantic 63 和数据库 370 项。
当前 main 已有 43 模块，制品版本为 9.3.0-SNAPSHOT；不能只把模块数改成 43，
更不能将本次 Compose 全量测试冒充历史 authority 证据。

现有单元测试 `test_live_contract_matches_current_reactor` 错把 main 当作历史候选，
导致后续正常增添模块后必然失败。Windows 另有两个夹具问题：篡改归档测试依赖 `/dev/shm`，
但该用例应在候选 marker 校验处拒绝；Git changed_paths 的断言错误使用本机路径分隔符。

## 方案与影响面

1. 使用已签收候选 root POM 的冻结 modules 摘录作为正向测试夹具，记录来源；不从当前合同动态生成期望模块列表。
2. 保留 validate-contract 严格匹配模块顺序、模块 POM 存在及 provisioner 摘要；补模块增加、缺失、重排、缺失 POM、摘要篡改拒绝测试。
3. CLI 的 reactor mismatch 信息说明历史作用域和实际模块数，保留原错误前缀及失败退出码。
4. 候选篡改负向测试使用普通临时目录并断言准确拒绝原因；另测同文件系统拒绝，Linux 实测真正跨文件系统成功提取。
5. Git 路径断言统一使用 POSIX 格式。新增 scripts/v950 使用说明，区分工具单元测试与正式 authority。

不修改冻结 JSON、旧签收文档、历史测试计数、生产查询/权限链、Maven 依赖或发布脚本。
不创建未经版本决策批准的新 authority 合同，不运行旧脚本的根 clean，不重新发布 Launcher。
只修改 Python 工具诊断、测试及当前迭代文档；独立提交推送 main。

## 验证

证据目录：`target/v950-contract-scope-fix/`。保存修复前 Windows/Linux 结果；修复后运行双平台
v950 全套单元、真实 Linux 跨文件系统提取、current main 严格拒绝及相邻发布/字段绑定脚本回归。
对比冻结合同、生产 shell 与历史签收摘要，确认未被改写；复核原 Compose 测试证据仍保留。

## 结果（2026-10-21）

- 修复前：Windows 14 项中 1 失败/2 错误；Linux 14 项中 1 错误。均保留原日志。
- 修复后：Windows/Linux v950 全套各 20 项通过，0 失败/错误/跳过。覆盖 frozen fixture 正向验证、模块集合/顺序、缺失 POM、摘要篡改及 CLI 拒绝，原 evidence/reuse/finalizer 测试继续通过。
- Linux 真实 `/tmp` → `/dev/shm` 提取成功，receipt 的 `cross_filesystem=true`；同文件系统以及候选 marker 篡改均执行准确拒绝断言，失败时不生成成功 receipt。
- 在主目录运行 `validate-contract` 返回退出码 1，明确报告历史 31 模块与实际 43 模块不匹配；这是预期的严格拒绝，不再混同工具单元测试失败。
- 相邻回归：Linux 发布脚本 38 项、字段绑定门禁 6 项通过；Windows 字段绑定门禁 6 项通过。
- 发布脚本相邻回归有两次换行环境失败，均保留日志。部分 shell 在 Git 中保存 CRLF，最终在隔离源码归档中将 75 个 shell 文件转换为 LF 后运行；主目录文件未改写。
- 冻结 JSON 摘要仍为 `282d06d79456406224d7810400c11c030f31f10e7d17a2fb0848486f926cd9e0`；root POM、生产 authority shell、provisioner、历史签收及索引共 6 个保护文件均与基线一致。
- 仅修改诊断、测试夹具与文档，未改变 Java 产品代码或构建输入，因此复用刚完成的引擎全量/多库验收，不重复运行 Java 全量或重新发布。

本次修复的是历史工具测试的作用域与平台夹具；没有重跑或重新签收 9.5.0 全量 authority，
也没有创建针对当前 main 的新版本 authority。使用入口见 [scripts/v950/README.md](../../../../scripts/v950/README.md)。
