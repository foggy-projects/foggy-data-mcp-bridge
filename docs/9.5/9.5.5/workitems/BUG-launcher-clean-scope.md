---
doc_type: bug
intended_for: maintainers
purpose: Restrict Launcher packaging clean to artifact-owning modules and retain root/upstream test evidence.
status: LOCALLY_VERIFIED
date: 2026-10-20
---

# Launcher 发布 clean 范围

用户明确授权修复发布脚本、补回归、提交并推送 main；本次不重新发布 Launcher。

## 问题与方案

`maven_build_commands` 先对 Console/Launcher 执行 scoped clean，随后又执行
`-pl foggy-mcp-launcher -am clean package`。第二次 clean 会展开到根 aggregator 和
上游依赖，删除根 target 中的忽略/未跟踪证据及上游测试报告，与 runbook 不一致。

移除第二条命令的 clean。第一条继续只清理 `addons/foggy-analytics-console` 与
`foggy-mcp-launcher`，保证 Console 资源和最终 Launcher 重建；第二条保留 `-am package`，
依赖模块正常参与 reactor 构建。正式、candidate 与 authorized docs-only 模式共用该命令。
不修改测试跳过策略、发布门禁、版本或远端发布接口。

## 回归

- 修正既有命令契约测试，要求 package 命令不含 clean，clean 命令不含 -am；candidate 同样检查。
- 增加真实 Maven/Git 临时 POM reactor：根目录有 Git 跟踪及忽略的二进制证据，上游有测试报告，
  Console/Launcher 有旧产物。实际执行生成的两条 Maven 命令，证明旧产物清理且证据逐字节保留。
- 原代码回归失败：命令仍包含 clean；实际执行后根跟踪报告、忽略证据、上游报告均缺失。
  没有在真实仓库 target 上执行破坏性复现。

## 验证结果

- 修改前：真实 Maven 文件系统回归 1 项失败，分别捕获根跟踪文件、根忽略证据、上游报告丢失的
  3 个 subtest；另一个命令契约断言也失败。
- 修改后：`python -m unittest discover -s scripts/release/tests -p 'test_runtime_launcher_*.py' -v`
  共 29 项通过，0 失败/错误/跳过，包含真实 Maven/Git 临时 reactor 回归和既有发布资产门禁测试。
- `python scripts/current-main/verify_current_main.py` 与 `git diff --check` 通过。
- 测试只在临时夹具中执行 clean；没有用真实仓库 target 验证破坏性场景。
- 不重复发布已有 Launcher 0.1.26，不触发私有 Maven 上传；本次仅修改后续发布流程。

原始 red/green 日志在本地忽略目录 `target/release-clean-regression/`。

