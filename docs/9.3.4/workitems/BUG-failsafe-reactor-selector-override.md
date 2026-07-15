---
type: bug
bug_source: regression-found
version: 9.3.4
ticket: BUG-934-FAILSAFE-REACTOR-SELECTOR-OVERRIDE
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: root-build
---

# Failsafe reactor selector miss override 被 root POM 字面量吞掉

## Background

Step 2 Integration authority 按 variant 使用 `-pl <owners> -am`，保证被测模块及其上游
依赖都来自当前 reactor。`-Dit.test=<exact selectors>` 也会传给这些上游模块；上游没有
目标测试是合法状态，runner 因此显式传入
`-Dfailsafe.failIfNoSpecifiedTests=false`，最终 exact-set 仍由 report verifier fail-closed。

首个正式运行在 `caffeine-sqlite` 尚未进入 owner 模块前，于 root reactor project 失败：

```bash
scripts/verify-v934-integration.sh step2-it-r1-20260715
```

```text
No tests matching pattern
"com.foggyframework.dataset.db.model.cache.lifecycle.realquery.QueryCacheLifecycleRealQueryIT"
were executed!
```

该 diagnostic run 只有 `cleanup.sentinel` 和 variant marker，没有 summary/final manifest，
不得与后续 authority 拼接。

## Root Cause

root `maven-failsafe-plugin` 将 `<failIfNoSpecifiedTests>true</...>` 写成字面量。在本项目
该 effective Mojo configuration 中，这个字面量优先于对应 user-property 注入；因此
runner 虽传入正确的官方 user property，effective goal 仍保持 fail-closed，并在合法的
上游 selector miss 处提前退出。

## Expected vs Actual

- 期望：默认 Maven 行为仍为 selector miss fail-closed；authority runner 在本次选定的
  `-pl ... -am` reactor 上统一放宽 Maven 自带 selector 检查，随后由 successor
  exact-set verifier 要求 owner 模块的 `47 + 4` reports 一个不少。CLI override 也作用
  于 owner；owner 的 fail-closed 边界在 verifier，不在 Maven 参数层。
- 实际：字面量配置使 override 无效，首个上游无匹配模块即终止，47 个 Step 2 Failsafe
  execution 无法形成同轮证据。

## Test Strategy

1. 保留 `step2-it-r1-20260715` 的稳定 RED 与“无 summary/final”事实。
2. root property 默认值保持 `true`，Failsafe configuration 改为引用该 property。
3. 使用同一 `-pl ... -am -Dit.test=... -Dfailsafe.failIfNoSpecifiedTests=false`
   命令验证上游 miss 不再阻断，owner selector 必须产生目标 XML。
4. successor runner contract 固定“default true + overrideable expression”，并用字段级
   负向探针拒绝字面量回退；静态 validator 同时拒绝默认值漂移。
5. 生成并双审 r7 successor；之后重新执行完整 Unit/Integration authority，不拼接 r6。

## Code Inventory

- `pom.xml`
  - 定义默认值 `failsafe.failIfNoSpecifiedTests=true`；
  - Failsafe 参数引用 `${failsafe.failIfNoSpecifiedTests}`。
- `scripts/v934/step2_successor_tool.py`
  - 固定 default 与 overrideable expression，并纳入 r7 successor。
- `scripts/verify-v934-integration.sh`
  - 已传入官方 override；最终 exact-set 继续由 report verifier 执行；
  - run root 与 EXIT trap 初始化后的 GREEN/RED 都落盘 `run.log` 与带
    phase/exit-code 的 `run-status.env`；参数/环境/目录 preflight 与 `SIGKILL` 不在该
    trap 保证范围内。

## Fix Checklist

- [x] 正式 Integration authority 捕获稳定 RED。
- [x] 确认运行没有 summary/final manifest，排除证据拼接。
- [x] 定位 root POM 字面量覆盖 CLI user property。
- [x] POM 改为 default-true、runner-overrideable expression。
- [x] focused reactor selector GREEN。
- [x] Integration runner 补齐失败日志与退出状态持久化。
- [x] r8e successor hash-sealed 双审。
- [x] r8e Unit authority exact-set GREEN。
- [x] r8e Integration authority 6 variants exact-set GREEN。

## References

- `target/v934-step2-integration/runs/step2-it-r1-20260715/`
- `scripts/verify-v934-integration.sh`
- `scripts/v934/successor/step2/step2-required-execution.tsv`
- `docs/9.3.4/evidence/step-2/step2-successor-r8e-independent-review-20260715.md`
- `docs/9.3.4/evidence/step-2/step2-runner-split-exit-r8e-20260715.md`

## Verification

改为 property expression 后，以失败运行完全相同的 caffeine selector 与 `-pl ... -am`
参数重跑，Maven exit `0`；目标 XML 为 `2/0/0/0`，SHA-256：
`cda062fa8c2b7aee4813650a6b67ff5c40a94f0f433c2d3ed12a24e60403ccf5`。
root POM 修正后 SHA-256 为
`d3e771a80829f3ca066d484b6a32304846f54b2d2cf8880420137a958b471679`。

另以 foreground `SIGINT` 诊断验证失败证据 trap（不是覆盖全部信号投递方式的正式
验收 probe）：
`step2-it-run-status-probe-20260715` 在 `test-compile` phase 以 exit `130` 结束，
`run-status.env` 明确记录 `status=failed`；`run.log` / `run-status.env` SHA-256 分别为
`02a69b3dcc1b2ed31ac463cc372e06094722da4a3daf7ee0189667b4c8c8bfce` /
`1ca12023ab84e99e6c2d9260b4ff37e101a672bb1d08cebbb387d7b44f465b2a`。
