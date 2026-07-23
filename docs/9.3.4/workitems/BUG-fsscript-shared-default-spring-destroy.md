---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-934-FSSCRIPT-SHARED-DEFAULT-SPRING-DESTROY
status: ACCEPTED
canonical: true
execution_mode: ultra
approved_by: repository-owner-via-user-confirmed-solution-a
approved_at: 2026-07-22
open_questions: []
---

# Delivery Spec: FSScript shared DEFAULT Spring destroy lifecycle

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结已确认的受约束方案 A，解除 Spring `ApplicationContext` 对进程共享 FSScript 函数注册表的破坏性销毁，同时保留独立实例的清理语义。
- canonical_path: `docs/9.3.4/workitems/BUG-fsscript-shared-default-spring-destroy.md`

## Goal

- version_goal: 修复 v9.3.4 release-authority required tests 暴露的生产级多 Context 生命周期缺陷，不降低测试、覆盖率或发布门槛。
- target_outcome: 关闭任一使用共享 FSScript runtime 的 Spring Context 后，其他仍存活 Context 及随后新建 Context 仍可使用 `IIF`、其他内置函数和共享动态注册函数。

## Scope

- in_scope:
  - `foggy-fsscript` 中共享 `DefaultExpFactory.DEFAULT`、其共享 `FunTable` 与 Spring Bean 销毁所有权的最小修复。
  - 确定性生命周期回归：同时存活 Context A/B、关闭 A 后 B、随后创建 C、共享函数与独立实例清理。
  - `foggy-mcp-launcher` 中当前真实顺序触发链和 `DataViewerApiSmokeTest` 回归。
- affected_modules: `foggy-fsscript`、`foggy-mcp-launcher`、`docs/9.3.4/workitems`。
- external_dependencies: Spring Framework/Boot 当前依赖版本；测试不依赖 Docker 或外部数据库。

## Non-Goals

- out_of_scope:
  - 方案 B 的 Context-local factory/table 行为变更。
  - 方案 C 的 immutable builtins + Context-local extensions 分层重构。
  - 修复 `DefaultExpFactory.DEFAULT`/`ApplicationContext` last-writer-wins、全局扩展注销、线程安全或完整类加载器隔离。
  - 调整测试执行顺序、fork、Context cache，或自行继续完整 v9.3.4 release-authority/发布签收。
- do_not_touch:
  - public API/SPI 签名、Bean 名称、Bean 类型、现有扩展注册入口。
  - coverage floor、critical set、exclusion、required tests、skip/assumption 和 Surefire run order。
  - 用户原工作区的 `docs/9.3.5/README.md` 与 `docs/9.3.5/workitems/`。
  - 与本缺陷无关的 v9.3.4 runner、历史 evidence 和发布状态。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 采用受约束方案 A | 当前 Spring 自动配置把 JVM/classloader 共享对象注册成 Context-owned Bean；任一 Context 关闭会清空所有 Context 共用的函数表。 | Spring 关闭不得破坏当前共享 `DEFAULT` 或其共享 `FunTable`；必须同时覆盖 factory/table 两条销毁路径。 |
| 独立实例继续可清理 | `DefaultExpFactory`/`FunTable` 的 `DisposableBean` 与显式 `clear()` 是既有实际行为。 | 不得把所有实例的 `destroy()` 统一改成空操作；`new DefaultExpFactory()`、`new FunTable()` 的独立销毁语义必须有回归保护。 |
| 共享 runtime 以当前 identity 识别 | `DEFAULT` 是 public mutable static；固定构造标志会在重新绑定后产生过期所有权，而 Spring 内部 BeanDefinition 抑制机制会增加版本耦合。 | 当前 `DEFAULT` 及其当前 `FunctionSet` 忽略 `destroy()`；显式 `clear()` 保持可用，非共享/已不再是当前默认的实例继续清理。 |
| 保持共享扩展兼容 | 现有 Spring GET/POST、Dataset 扩展和用户通过 `DEFAULT` 注册的函数均进入同一共享表。 | 本补丁不改变注册作用域、Bean override 或扩展查找路径；Context-local registry 留给后续方案 C。 |
| 不以 `@Bean(destroyMethod="")` 单点修复 | 受控实验证明该设置不会抑制 `DisposableBean.destroy()`，且 factory/table 是两个 Bean identity。 | 实现必须由自动化测试证明两个共享 Bean 在 Context 关闭时均未发生破坏性清理。 |
| 不隐藏真实失败 | 反向全模块顺序已稳定复现 exact 4 个 `IIF` 缺失失败。 | 不得改 selector、runOrder、fork、cache、skip 或断言强度。 |

## Acceptance Criteria

- [x] AC-1: Context A 与 B 同时启动时使用同一共享 factory/table，二者均可解析并执行 `IIF`。
- [x] AC-2: 关闭 Context A 后，仍存活的 Context B 继续可执行 `IIF`，且共享用户注册函数未丢失。
- [x] AC-3: A 关闭后新建 Context C，C 可执行 `IIF`，共享 factory/table 未因旧 Context 销毁而被清空。
- [x] AC-4: Spring 关闭共享 runtime 时，factory 与 table 两条 `DisposableBean` 销毁所有权均不会造成 `clear()`；不存在共享表双重清理。
- [x] AC-5: 非共享的独立 `DefaultExpFactory` 和 `FunTable` 仍保持既有 `destroy() -> clear()` 行为。
- [x] AC-6: public API/SPI 签名、Spring Bean 名称/类型、内置函数初始化与动态注册入口不变；无 POM、coverage 或 exclusion 变更。
- [x] AC-7: `OutsidePackageCoreAutoConfigurationSmokeTest` 后运行 `DataViewerApiSmokeTest` 不再出现 4 个 `IIF` 缺失失败；Launcher 反向全量 Surefire 通过。
- [x] AC-8: `foggy-fsscript` 普通与反向 Surefire lanes 通过，且新增回归在独立 fresh JVM 中稳定通过。

## Contract / Data / Security Constraints

- API or event contract: 不修改 public API/SPI、配置项、Bean 名称或 Bean 实际类型；允许且必须改变共享 runtime 在 Spring Context shutdown 时被清空的错误生产语义。
- data and migration: 无数据库、schema、配置或持久数据迁移。
- compatibility and rollback: 现有单 Context、非 Spring parser 和共享 `DEFAULT` 注册路径保持；补丁可由单提交 revert。回滚会恢复多 Context 缺陷。
- permissions and secrets: 不读取或记录凭证；测试仅使用内存 Context/runtime。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1..AC-5 | critical | 新增 `foggy-fsscript` lifecycle regression；先在未修复语义确认 RED，再修复为 GREEN | 精确 Maven 命令、test counts、失败/通过摘要 |
| AC-6 | major | `git diff`、API/Bean/POM/coverage boundary review | changed paths 与边界审查摘要 |
| AC-7 | critical | focused trigger pair、`DataViewerApiSmokeTest`、Launcher reverse full Surefire | 精确命令与 F/E/S 结果 |
| AC-8 | major | `foggy-fsscript` focused、normal full、reverse full Surefire | 精确命令与 F/E/S 结果 |

验证成本与循环控制：

- `<5m` 轻量：新增 lifecycle focused、trigger pair、`DataViewerApiSmokeTest`；每次相关生产或测试逻辑变化后可重跑。
- `5-30m` 中等：`foggy-fsscript` 普通/反向、Launcher 反向全量；在 focused GREEN 且代码边界冻结后各执行一次最终运行。
- `>30m` 或完整 v9.3.4 release-authority：本事项不执行，由原发布收口会话接管。
- 仅当生产/测试输入、selector 或相关依赖变化时使对应证据失效；docs-only 回写不使已完成 Maven 证据失效。
- 若同一中等验证连续两次因非产品环境问题失败，设置 `NEEDS_REPLAN`，不得无限重跑。

## Bug Context

- bug_source: release-authority-regression-found
- severity: critical
- environment: PR #124 / GitHub Actions run 29912487082；PR HEAD `8350ac62edbbe79259229e4b3e059cba7f09650c`（2026-07-22 实时复核仍一致）；本地 JDK/Maven 单 JVM Surefire。
- current_behavior: 任一装配共享 FSScript runtime 的 Context 关闭时，Spring 分别销毁 `DefaultExpFactory.DEFAULT` 与同一内部 `FunTable` Bean，函数表被清空；存活或新建 Context 找不到 `IIF`。
- expected_behavior: Context shutdown 不得销毁 JVM/classloader 共享 runtime；其他 Context 和后续 Context 继续使用已初始化的内置/共享扩展函数。
- reproduction_steps:
  1. `mvn -pl foggy-mcp-launcher -Dtest='*Test' -Dsurefire.runOrder=reversealphabetical test`；
  2. 观察 `DataViewerApiSmokeTest` 6 个测试中 4 个因找不到 `IIF` 失败；
  3. 单独执行 `DataViewerApiSmokeTest` 为 6/0/0/0；
  4. 先执行并关闭 `OutsidePackageCoreAutoConfigurationSmokeTest`，再执行 Data Viewer class，可稳定得到同样 4 个失败。
- reproduction_status: confirmed
- existing_evidence:
  - GitHub run 29912487082 / PR #124 exact failures。
  - 本地 reverse full `20 tests / F4/E0/S0`，focused Data Viewer `6/F0E0S0`。
  - 受控 A/B/C 探针证明 A/B 同一 factory/table identity；关闭 A 后 factory destroy 1、table destroy 1、同一 table clear 2，B 与 C 的内置/用户函数均丢失。
  - `FunTable` 仅保存 `HashMap<String, FunDef>`，无线程、连接、文件等主动关闭资源。
- existing_tests: 当前 `foggy-fsscript` 401 个测试均未覆盖多个 Spring Context 关闭共享 runtime 的语义。
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks:
  - 共享用户函数与 Context 注入的 GET/POST/Dataset `FunDef` 继续保持 JVM/classloader 全局作用域，可能延长对象或类加载器引用；这是现状兼容性选择，不在 v9.3.4 最小补丁中重构。
  - `DefaultExpFactory.DEFAULT` 和 `appCtx` 的多 Context last-writer-wins、`HashMap` 并发访问仍是后续方案 C 的设计债务。
  - 直接对当前共享 `DEFAULT` 或其当前共享表调用 `destroy()` 将不再清空；需要显式清空的非 Spring 调用方仍可调用既有 `clear()`。这是解除共享对象销毁所有权所必需的最小语义变化。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、`FoggyFscriptAutoConfiguration`、`DefaultExpFactory`、`FunTable` 和现有相关测试。
- 优先先建立确定性 RED 生命周期回归，再实施最小修复；诊断探针不得作为生产补丁。
- 在 scope 内自主选择不新增 public API 的实现结构；必须同时处理 factory/table 销毁所有权，且不能依赖测试顺序规避。
- 如需改变 public API/SPI、Bean 名称/类型、扩展作用域、POM、coverage/exclusion 或采用方案 B/C，设置 `NEEDS_REPLAN` 并停止扩展。
- 只执行本事项的 focused/affected lanes；不继续完整 v9.3.4 release-authority、CI 或最终签收。
- 完成后填写 `Implementation Result`，状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - `DefaultExpFactory.destroy()` 仅清理非当前 `DEFAULT` 实例；当前 classloader 共享实例不再因任一 Spring Context 关闭而被清空。
  - `FunTable.destroy()` 同样保护当前 `DEFAULT.getFunctionSet()`，覆盖自动配置把内部函数表作为第二个 `DisposableBean` 注册所形成的双重销毁路径。
  - 显式 `clear()`、独立 factory/table 的 `destroy() -> clear()`、Bean 名称/类型和函数注册入口保持不变。
- changed_paths:
  - `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/DefaultExpFactory.java`
  - `foggy-fsscript/src/main/java/com/foggyframework/fsscript/exp/FunTable.java`
  - `foggy-fsscript/src/test/java/com/foggyframework/fsscript/exp/DefaultExpFactorySpringLifecycleTest.java`
  - `docs/9.3.4/workitems/BUG-fsscript-shared-default-spring-destroy.md`
- tests_and_results:
  - RED（未修改生产语义）：`mvn -pl foggy-fsscript -Dtest=com.foggyframework.fsscript.exp.DefaultExpFactorySpringLifecycleTest test` → 2 tests，E1；关闭 A 后 B 执行 `IIF` 报“未能找到函数 [IIF]”。
  - GREEN focused：同一命令 → 2 tests，F0/E0/S0。
  - FSScript normal：`mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-lifecycle-m2-20260722 -pl foggy-fsscript -DskipTests install` → Surefire 403 tests，F0/E0/S0，BUILD SUCCESS；Failsafe 按命令跳过。
  - FSScript reverse：`mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-lifecycle-m2-20260722 -pl foggy-fsscript -Dtest='*Test' -Dsurefire.runOrder=reversealphabetical test` → 403 tests，F0/E0/S0。
  - Launcher focused trigger：`mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-lifecycle-m2-20260722 -pl foggy-mcp-launcher -Dtest='io.foggytest.autoconfigure.OutsidePackageCoreAutoConfigurationSmokeTest,com.foggyframework.mcp.launcher.DataViewerApiSmokeTest' -Dsurefire.runOrder=reversealphabetical test` → 7 tests，F0/E0/S0。
  - Data Viewer single Context：`mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-lifecycle-m2-20260722 -pl foggy-mcp-launcher -Dtest=com.foggyframework.mcp.launcher.DataViewerApiSmokeTest test` → 6 tests，F0/E0/S0。
  - Launcher reverse full：`mvn -o -Dmaven.repo.local=/tmp/foggy-fsscript-lifecycle-m2-20260722 -pl foggy-mcp-launcher -Dtest='*Test' -Dsurefire.runOrder=reversealphabetical test` → 20 tests，F0/E0/S0。
  - Launcher 使用隔离 Maven local repository，先安装本 worktree 的 FSScript，避免 `-pl foggy-mcp-launcher` 误用用户 `~/.m2` 中的旧 JAR；未修改用户 Maven 缓存。
- manual_or_experience_evidence: N/A
- deviations: none
- residual_risks:
  - 共享动态函数仍为 JVM/classloader 全局作用域，Context 注入函数可能延长对象或类加载器引用；本补丁为兼容性保留现状。
  - `DEFAULT`/`appCtx` 的多 Context last-writer-wins、`HashMap` 并发写和完整扩展注销仍属于后续方案 C 范围。
  - 对当前共享对象直接调用 `destroy()` 不再清空；需要主动重置时继续使用既有显式 `clear()`。
- readiness: READY_FOR_SIGNOFF

## References

- requirement / issue: PR #124；GitHub Actions run 29912487082。
- architecture / glossary: `DefaultExpFactory.DEFAULT`、`FoggyFscriptAutoConfiguration`、`FunTable`。
- related work items: `docs/9.3.4/workitems/FEATURE-v934-release-authority-and-ci.md`、`docs/9.3.4/workitems/DECISION-v934-risk-tiered-release-exit-policy.md`。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-release-reviewer
- signed_off_at: 2026-07-22
- acceptance_record: `docs/9.3.4/acceptance/BUG-fsscript-shared-default-spring-destroy-signoff-20260722.md`
- blocking_items: none
- follow_up_required: no
