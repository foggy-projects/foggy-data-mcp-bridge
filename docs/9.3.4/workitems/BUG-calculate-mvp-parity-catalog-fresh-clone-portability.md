---
type: bug
bug_source: formal-authority-found
version: 9.3.4
ticket: BUG-934-CALCULATE-PARITY-CATALOG-PORTABILITY
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: foggy-dataset-model
---

# CALCULATE parity catalog 依赖仓外兄弟目录

## Background

Step 4 `formal-r7` 在真正 fresh clone 的 `sqlite-broad` Failsafe 变体中执行
`CalculateMvpIT.parityCatalogCasesStayExecutable` 时失败。该测试从模块工作目录依次检查
`docs/`、`../docs/`、`../../docs/`，但主仓没有跟踪目标 catalog。

长期工作区中的 `../../docs/v1.5.1/P1-CALCULATE-restricted-mvp-parity-catalog.json`
实际属于仓外父目录；另一份同字节副本属于 Python 兄弟仓。因此普通工作区与 earlier
diagnostic 被环境偶然满足，fresh clone 正确暴露了可移植性缺口。

## Reproduction

- tested commit: `439aea5e4e30b7a9fb50acc51e4898244f479df4`
- run: `step4-coverage-20260719-formal-r7`
- failing variant: `sqlite-broad`
- Maven result: `Tests run: 307, Failures: 1, Errors: 0, Skipped: 0`
- failure: `Cannot locate P1-CALCULATE parity catalog from working directory`
- Unit authority before failure: `681 + 55 / 4941 / F0 E0 S0`
- cleanup: containers/volumes/networks=`0/0/0`; demo DB restore=`4/4 same ID healthy`

## Root Cause

`CalculateMvpIT` 的第二个候选路径本来就面向主仓根目录 `docs/v1.5.1`，但该文件从未纳入
Git。第三个候选路径误命中工作区父目录，使本地测试产生 false green。失败不是 CALCULATE
行为、SQLite 能力或生产代码回归。

两份仓外副本字节一致，SHA-256 均为
`f52eba376e3b2e94c2d03c8f01fcc6d9c3b98623d82938608aeacb90dd03ef60`。

## Fix Boundary

1. 将同一字节的 catalog 跟踪为主仓
   `docs/v1.5.1/P1-CALCULATE-restricted-mvp-parity-catalog.json`。
2. Step 4 authority 在任何测试前验证该路径为 exact tracked `100644` Git blob，并验证
   raw SHA-256；缺失、仓外诱饵、内容漂移均 fail closed。
3. 不修改生产源码、POM、测试方法、测试节点、selector、覆盖率阈值或 skip policy。
4. 在隔离 checkout/fresh clone 中执行该方法和完整 `CalculateMvpIT`，确认不依赖父目录或
   兄弟仓。
5. 由于新增路径不属于 r24 Cfreeze formalization allowlist，r24/Cfreeze/formal-r7 只保留历史；
   必须形成新 Cdiag、fresh diagnostic、candidate/capsule/review/Cfreeze 和 replacement formal。

## Acceptance

- [ ] tracked catalog SHA-256 与已审阅来源 exact。
- [ ] missing/tampered catalog authority preflight fail closed，仓外诱饵不能替代。
- [ ] isolated checkout focused method `1/0/0/0`。
- [ ] isolated checkout complete class `14/0/0/0`。
- [ ] repository parent/sibling catalog 缺失时仍通过。
- [ ] successor source/discovery/report identities无节点漂移。
- [ ] replacement diagnostic/formal authority通过。
