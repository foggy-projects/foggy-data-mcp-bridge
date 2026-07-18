---
type: bug
bug_source: diagnostic-found
version: 9.3.4
ticket: BUG-934-STEP4-UNIT-MYSQL57-FINAL-MYSQLD-HANDOFF-READINESS-RACE
severity: blocker
status: closed
closed_at: 2026-07-18
closure_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: final-mysqld-handoff-readiness-and-lifecycle-forensics
automation_decision: required
owner: step4-unit-authority/step4-coverage-tooling
---

# Step 4 Unit MySQL final mysqld handoff readiness race

## Background

fresh diagnostic r17 在 Unit lifecycle 的第三个 `signal-hup` probe 于 callback ready 前退出：child=
`unit-mysql57-49a76dbc70c3bd98`，cell=`fixture-first-apply / exit 1`。cleanup 正确完成，但 canonical
lifecycle receipt、正常 fixture、Unit Maven 与后续 coverage evidence 均未产生。

immutable failure evidence：

- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r17-unit-mysql57-final-mysqld-handoff-fail-closed-20260717.md`

## Expected vs Actual

- Expected：run-owned MySQL health 只能在官方 entrypoint 完成 temporary-server 初始化并把 PID 1
  handoff 给 final `mysqld` 后变为 healthy。
- Expected：Unit lifecycle unexpected failure 必须保留 run-owned diagnostics，而不是只留下 coarse
  phase 与 rc。
- Actual：stock `mysqladmin ping` 在 PID1 仍为 `docker-entrypoi` 时已连续返回 healthy；父 provisioner
  可提前进入首次 fixture apply。
- Actual：r17 撞中 temporary -> final server 切换窗口，provisioner rc=1；stdout/stderr 被丢弃，不能
  追溯具体 mysql subcommand/errno。

## Root Cause

root cause 已由 fresh runtime RED 确认：unfixed authority Compose 在 samples `15..40` 均出现
`health=healthy / PID1=docker-entrypoi`，sample `41` 才成为 `health=healthy / PID1=mysqld`。现有
watermark readiness 也不能关闭该窗口，因为四条 marker 在最后 init SQL 结束和 entrypoint handoff 前
已经可见。

这是 Step 4 test-evidence infrastructure regression，不是 production model/runtime regression。单次
focused lifecycle control 5/5 通过只说明竞态非必现，不能覆盖 RED 或关闭 BUG。

## Fix Strategy

1. 不改冻结 Step 3 provisioner；通过 Step 4 successor declared amendment 精确更新 run-owned authority
   Compose。
2. MySQL5.7 与 MySQL8 的 authority healthcheck 均要求 `/proc/1/comm` exact 为 `mysqld`，并保留原
   `mysqladmin ping`；provisioner 后续 identity/watermark gate 不放宽。
3. amendment 的 parent/successor hash、database authority manifest、database/required successor
   contract、overlay contract/tool 与顶层 Step 4 manifest 全部重新封印；Step 3 历史 manifest 不改。
4. lifecycle provisioner stdout/stderr 写入 probe run root 的 no-clobber combined log；success 删除，
   unexpected failure 保留，原 typed `FixtureError` code（如 `E_LIFECYCLE`/`E_CLEANUP`）不被吞并，
   同时给出 diagnostics path；不扩成功 receipt v1 schema。
5. 先完成 premature-healthy RED/修复后 GREEN，再以多个唯一 run ID 运行真实
   INT/TERM/HUP/callback-failure/leader-kill `5/5`，随后完整 Unit replacement 与 fresh diagnostic。

## Regression Test Decision

`automation_decision=required`。真实 lifecycle `5/5` 已是 Unit authority 的常驻自动化子门禁；本次修复
强化其前置 health contract，并用确定性的 PID1/health RED-GREEN 证明不是靠重复运行掩盖偶发失败。
任何 focused 绿色都不能替代 fresh all-lane diagnostic/formal。

## Fix Checklist

- [x] r17 failure、success-only absence、cleanup 与 DB restoration 已封存。
- [x] cleanup PASS 与 lifecycle probe PASS 已明确区分。
- [x] focused control r1=`5/5`，标记为 non-authority。
- [x] premature healthy RED 已直接重现：samples `15..40` 为 healthy/entrypoint，`41` 才 final mysqld。
- [x] authority Compose 对 MySQL5.7/8 增加 final PID1 + ping health gate。
- [x] MySQL57 对称 GREEN 首次 healthy 即为 final mysqld，premature healthy=`0`。
- [x] unexpected lifecycle provisioner diagnostics 改为 failure-retained no-clobber log。
- [x] successor overlay/contract/manifests 更新后 positive PASS；overlay negative=`12/12`、fixture
  negative=`36/36`、coverage contract negatives=`27 + source/Git 22 + replay 12`。
- [x] MySQL8 对称 handoff GREEN：首次 healthy 即 `PID1=mysqld`，premature healthy=`0`。
- [x] 三个唯一 post-fix lifecycle run 均 `5/5`、合计 `15/15`；receipt SHA-256=
  `8a3900678a718a2df5b604854ad43a5622273128b94f18413ddc6a5979fdf5f2`、
  `eb41ee0675c6c2677de708390125e1095178772e4849aece332be3eb89bb6da4`、
  `dca7439a316899c064f231411ee4a48e052c0e1b1f28d25da5c8041ca8ddd48a`。
- [x] diagnostics 加固的 penultimate 字节 lifecycle=`5/5`，receipt SHA-256=
  `159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`；成功 probe logs
  absent，run-owned residue=`0/0/0`、demo restore=`0/0`；随后 callback OSError/日志存在性诊断
  又有加固，该 receipt 不冒充 latest current bytes。
- [x] latest current bytes lifecycle final `5/5`：run=
  `step4-unit-lifecycle-handoff-current-20260717-r2`，receipt SHA-256=
  `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7`；successful logs absent，
  demo exact restore=`runner_rc=0 / restore_rc=0` 且 healthy/listening。
- [x] 当前静态 receipts：overlay=`12/12` / `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06`；
  Unit fixture=`36/36` / `a5620aa80ac122a9489b14f8fc5352bf685c61e2fcd2426fdadfd36fb882212d`；
  coverage=`27 + source/Git 22 + replay 12` / current r2
  `0f8f5c7bbd6b8fcf18363f979b9948bd396b39b436f94b30c1ca697204fc6856`。
- [x] 完整 Unit replacement=`681+55/4941/F0E0S0`，fixture/receipt/cleanup 全部通过。
- [x] 正式 remediation implementation quality=`PASS / 0/0/0/0`；记录：
  `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`。
- [x] replacement Cdiag commit/push/clean。
- [x] fresh diagnostic -> review -> direct-child Cfreeze -> fresh formal。
- [x] final quality -> coverage audit -> acceptance。

## Closure Scope

本 BUG 在 fresh formal 前保持 `in-progress`；后续 clean/pushed replacement、完整 Unit、formal-r4、
coverage audit 与 feature acceptance 已全部通过，因此关闭。r17 永久保留为 failed/excluded
evidence；threshold 不得降低、coverage exclusion 不得扩大。Step 5 现为 `ready / not-started`。

## References

- `foggy-dataset-demo/docker/docker-compose-v934-authority.yml`
- `scripts/v934/step3/provision-database-cell.sh`
- `scripts/v934/step4/unit_mysql_fixture_tool.py`
- `scripts/v934/step4/successor/declared-amendments.tsv`
- `scripts/v934/step4/successor/overlay-contract.json`
- `scripts/v934/step4/successor/overlay_tool.py`
- `scripts/verify-v934-unit.sh`
- `docs/9.3.4/workitems/BUG-step4-unit-hidden-mysql-environment-dependency.md`
- `docs/9.3.4/workitems/BUG-step4-query-model-join-graph-double-check-coverage-race.md`
- `docs/9.3.4/quality/step4-diagnostic-r17-recovery-implementation-quality.md`
