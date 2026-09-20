---
name: vitest-integration-testing
description: 编写或排查本仓库的 Vitest 组件/API 集成测试与测试环境。仅需要普通源码修改时不触发完整覆盖率流程。
license: Complete terms in LICENSE.txt
---

# Vitest 集成测试

先确定要证明的行为、失败场景和目标 package 的现有测试命令/配置。复用邻近测试与 fixture，执行受影响用例；不为小改新增只复述实现的测试。

## 环境与范围

- 区分组件/API 合同测试与真实外部集成。单测 mock 外部副作用；真实集成使用已授权的测试端点和 fixture，不以 mock 通过声称外部链路通过。
- 测试间不共享可变状态。数据库/共享本机服务不是天然可丢弃；setup/reset/清理脚本仅可用于已确认的专用 fixture。
- 按目标 vitest/package/CI 的现行门禁执行覆盖率；本技能不额外施加通用 80%/75% 指标，也不削弱已配置的门槛。仅在任务或门禁需要时运行覆盖率报告。
- 测试运行过且通过才能报告通过。已有无关失败说明影响；本任务造成的失败修复后复验。通过后仅因新改动、失败或未解风险扩测。

## 按需资源

- 组件测试：[示例](examples/component_testing.test.ts)。
- API 合同：[示例](examples/api_testing.test.ts)。
- 跨组件/完整场景：[示例](examples/e2e_testing.test.ts)，先核实它是否覆盖真实边界。
- Mock 隔离：[模式](examples/mock_patterns.test.ts)。
- API/Testing Library 用法：[Vitest](references/vitest-api.md)、[Testing Library](references/testing-library-cheatsheet.md)。
- 已有 `scripts/run_tests_with_coverage.sh` / `scripts/setup_test_db.js` 只有目标包、环境和任务适用时使用，先检查参数与副作用。

## 定向执行

使用目标 package 已配置的脚本；需要选择文件时可用 `vitest run path/to/affected.test.ts`。排查时缩小文件/用例，修复后运行直接受影响场景。不提交临时 only/skip 来隐藏失败。
