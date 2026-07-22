---
acceptance_scope: bug
version: 9.3.4
target: BUG-934-FSSCRIPT-SHARED-DEFAULT-SPRING-DESTROY
status: signed-off
decision: accepted
signed_off_by: independent-release-reviewer
signed_off_at: 2026-07-22
reviewed_by: independent-release-reviewer
blocking_items: []
follow_up_required: no
evidence_count: 8
---

# FSScript shared DEFAULT Spring lifecycle signoff

## Document Purpose

- intended_for: release owner / project root session
- purpose: 对提交 `2f2b2d8bac37ae0c27188816e2476443be269636` 的共享 FSScript runtime 生命周期修复形成独立签收结论。

## Background

- delivery_spec: `docs/9.3.4/workitems/BUG-fsscript-shared-default-spring-destroy.md`
- target_outcome: Spring Context 关闭不再清空当前 `DefaultExpFactory.DEFAULT` 及其共享 `FunTable`，同时保留独立实例和显式 `clear()` 的既有语义。
- signoff_scope: 修复提交、canonical spec、确定性生命周期回归和两个 affected modules；不包含完整 v9.3.4 release-authority。

## Acceptance Basis

- approved delivery spec: 状态 `READY_FOR_SIGNOFF`，无 open question。
- reviewed commit: `2f2b2d8bac37ae0c27188816e2476443be269636`。
- exact parent: `8350ac62edbbe79259229e4b3e059cba7f09650c`。
- remote branch at review: `origin/codex/fsscript-lifecycle-analysis-20260722` 精确指向 reviewed commit。
- changed surface: 两个生产 `destroy()` 方法、一份 lifecycle regression 和 canonical spec，共 4 个路径。
- independent environment: clean detached worktree、JDK 17.0.19、Maven 3.8.7、隔离 Maven local repository。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | Context A/B 共享 runtime 且均可执行 `IIF` | 测试核对相同 factory/table identity 及结果 | lifecycle focused 2/2 | pass |
| AC-2 | 关闭 A 后 B 的 `IIF` 和用户函数仍可用 | 关闭 A 后再次解析两类函数 | lifecycle focused 2/2 | pass |
| AC-3 | 后建 Context C 仍可用 | A 关闭后创建并验证 C | lifecycle focused 2/2 | pass |
| AC-4 | factory/table destroy 均执行但共享表不 clear | destroyCalls 各为 1，clearCalls 各为 0 | lifecycle focused assertions | pass |
| AC-5 | 独立 factory/table 仍 `destroy() -> clear()` | 两类独立实例均验证函数被清除 | lifecycle focused assertions | pass |
| AC-6 | 不改变公共契约和发布治理边界 | 无签名、Bean、POM、coverage、exclusion、skip 或 runOrder diff | changed-path/diff audit | pass |
| AC-7 | launcher 真实触发链和反向全量恢复 | 7/7、6/6、20/20 | independent Maven runs | pass |
| AC-8 | FSScript 普通与反向 lanes 稳定 | 两个 lane 均 403/403 | independent Maven runs | pass |

## Implementation Quality

- scope and changed surface: 补丁只对当前共享对象增加 identity guard；没有展开 Context-local registry 或 immutable builtins 重构。
- maintainability and duplication: factory/table 两条 Spring 销毁路径分别防护，与双 Bean ownership 相符；无测试顺序规避或临时诊断代码。
- error handling and edge cases: `DEFAULT == null` 时非共享 factory/table 仍清理；DEFAULT 被重新绑定后旧实例恢复非共享清理语义。
- contract and compatibility: `clear()` 方法未变；public API/SPI、Bean 名称和实际类型未变。
- repository boundaries: 未修改 POM、CI、coverage floor/exclusion、required selectors、skip/assumption 或 Surefire 配置。

## Evidence Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence | Coverage |
|---|---|---|---|---|---|---|---|---|
| AC-1..AC-5 | critical | 2/2 | Spring Context A/B/C | N/A | N/A | code/diff review | `DefaultExpFactorySpringLifecycleTest` | covered |
| AC-6 | major | N/A | N/A | N/A | N/A | exact four-path diff audit | no forbidden surface changed | covered |
| AC-7 | critical | 6/6, 20/20 | trigger chain 7/7 | N/A | N/A | isolated JAR identity | launcher affected lanes | covered |
| AC-8 | major | 403/403 twice | N/A | N/A | N/A | normal/reverse selection review | FSScript full Surefire | covered |

## Independent Test Record

All commands ran at reviewed commit with failures/errors/skips equal to zero:

- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-fsscript -Dtest=com.foggyframework.fsscript.exp.DefaultExpFactorySpringLifecycleTest test` -> 2 tests.
- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-fsscript test` -> 403 tests.
- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-fsscript -Dtest='*Test' -Dsurefire.runOrder=reversealphabetical test` -> 403 tests.
- current FSScript was installed into the isolated repository; source and repository JARs both had SHA-256 `d38cf19f9060b3c2145a7bc4f0f18deac56fdf03fb39697c27312ed706b93b0e`.
- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-mcp-launcher -Dtest='io.foggytest.autoconfigure.OutsidePackageCoreAutoConfigurationSmokeTest,com.foggyframework.mcp.launcher.DataViewerApiSmokeTest' -Dsurefire.runOrder=reversealphabetical test` -> 7 tests.
- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-mcp-launcher -Dtest=com.foggyframework.mcp.launcher.DataViewerApiSmokeTest test` -> 6 tests.
- `mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-signoff-m2-20260722 -pl foggy-mcp-launcher -Dtest='*Test' -Dsurefire.runOrder=reversealphabetical test` -> 20 tests.

## Failed Items

- none

## Risks / Follow-ups

- 已接受的现状债务：共享动态函数仍为 JVM/classloader 全局作用域；`DEFAULT`/`appCtx` last-writer-wins、并发注册和完整扩展注销不在本补丁范围。
- 上述事项不影响本次缺陷修复的正确性，也不授权在 v9.3.4 中展开方案 B/C。

## Final Decision

- decision: accepted
- rationale: 所有 acceptance criteria 均有独立代码审计和实际运行证据；没有失败、未知证据、范围偏离或阻断风险。
- blocking_items: none
- follow_up_owner_and_due: 方案 B/C 类治理与架构债务由后续版本规划处理，无 v9.3.4 本项 follow-up。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-release-reviewer
- signed_off_at: 2026-07-22
- acceptance_record: `docs/9.3.4/acceptance/BUG-fsscript-shared-default-spring-destroy-signoff-20260722.md`
- blocking_items: none
- follow_up_required: no
