# Step 4 diagnostic-r17 handoff recovery implementation quality

- reviewed_at: 2026-07-17
- scope: diagnostic-r17 immutable Unit failure, MySQL final-server handoff readiness remediation,
  lifecycle failure diagnostics, successor seal chain and authority-document writeback
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-replacement-Cdiag-commit-and-fresh-diagnostic
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one replacement Cdiag commit/push and one fresh diagnostic only

## Review Basis

clean/pushed Cdiag `316a71f753827f8f34063b0eb0669271f696c5ee` 上的 fresh
`step4-coverage-20260717-diagnostic-r17` 在 outer `child-unit / exit 1`、Unit
`unit-mysql57-lifecycle-negative / exit 1` 正确 fail closed。第三个 HUP probe 在
`fixture-first-apply`、callback ready 前退出；canonical lifecycle receipt、正常 Unit fixture、
Surefire XML/JaCoCo exec 与全部后续 success-only artifact absent。r17 已永久标记为
`excluded/non-reusable`，cleanup JSON 只证明资源回收，不是 lifecycle 或 full Unit PASS。

runtime RED 直接捕获原 authority healthcheck 在 PID 1 仍为 `docker-entrypoi` 时提前 healthy；
final `mysqld` handoff 更晚发生。remediation 只修改 Step 4 run-owned authority override：MySQL 5.7
与 MySQL 8 都必须同时满足 PID 1=`mysqld` 和原 `mysqladmin ping`，之后既有 business identity、
schema/sentinel 与四个 watermark gate 不放宽。frozen Step 3 provisioner、production Java、public API、
POM、coverage floor/critical set/threshold/exclusion 与成功 receipt v1 schema 均未修改。

Unit lifecycle launcher 现在把 combined stdout/stderr 写入 probe-local no-clobber `0600` 文件；受控
failure 保留原 typed `FixtureError` code 并附 `diagnostics=<path>`。日志只有在 expected terminal
state、finalizer 和 cleanup 全部通过后才删除；失败日志保留，但在正式引用前仍须通过 sensitive
scan。

## Verification

| Check | Result |
|---|---|
| r17 failure boundary | immutable/excluded/non-reusable；success-only artifact absent；outer restore=`runner_rc=1 / restore_rc=0` |
| MySQL 5.7 RED | samples `15..40`=`healthy / docker-entrypoi`，sample `41` 才为 final `mysqld` |
| MySQL 5.7 / MySQL 8 GREEN | first healthy 均为 PID 1=`mysqld`，premature healthy=`0` |
| post-fix lifecycle | 三个唯一旧修复字节 run=`15/15` PASS |
| final current-byte lifecycle | `step4-unit-lifecycle-handoff-current-20260717-r2`=`5/5`；exit=`130/143/129/17/137`；每项 residue=`0/0/0`、port free |
| final lifecycle receipt | SHA-256 `e3bad41ad9ec634c1702ffe20f4c0ddbff3050a227956e2ba9054a11f6b606c7` |
| lifecycle diagnostics | successful `provisioner.log` count=`0`；focused mode/enrichment check PASS；demo restore=`0/0` |
| Unit fixture tool | SHA-256 `9be62daaf7a3d2d873c7647078c0bf798ab25c491a163e90960d4143965be5be`；Python compile PASS |
| Unit static negatives | `36/36`；SHA-256 `a5620aa80ac122a9489b14f8fc5352bf685c61e2fcd2426fdadfd36fb882212d` |
| successor overlay | `parents=3 / contracts=4 / amendments=22 / bindings=9 / required=45/446 / Addon=2/6` PASS |
| overlay negatives | `12/12`；SHA-256 `cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06` |
| coverage contract negatives | mutation=`27/27`、source/Git=`22/22`、replay=`12/12`；SHA-256 `0f8f5c7bbd6b8fcf18363f979b9948bd396b39b436f94b30c1ca697204fc6856` |
| full coverage contract | diagnostic/diagnostic-pending；required=`773+59/5707`；exec/session=`23/48`；manifest=`60` |
| Step 4 manifest | `60/60` PASS；SHA-256 `bffc93fabbfac080bd83d5541dc273d29cbf0500cb8d8b21f2cc8017a8e35d92` |
| successor manifest | `14/14` PASS；SHA-256 `6cafd484432f7627eb2a3e1d8c86fbfafae1aa6cb2b63aed9acc1fd0f60bbf94` |
| Step 3 freeze | Step 3 diff=`0`；historical manifest SHA-256 `38248a6788d2c16b315f955fb0cd8fac00d4847eb20629a33e837e1411864aed` |
| production/API/build boundary | production/test Java diff=`0`；POM diff=`0`；coverage contract/threshold diff=`0` |
| database restoration | original MySQL57/MySQL8/PostgreSQL15/SQLServer2022 IDs running/healthy；ports `13306/13308/15432/11433` listening |
| documentation/state | 10 authority docs、r17 evidence/new BUG 与 4 个关联 BUG一致；links/AST PASS |
| diff hygiene | `git diff --check` PASS |

三个旧修复字节 lifecycle receipt SHA-256 分别为：

1. `8a3900678a718a2df5b604854ad43a5622273128b94f18413ddc6a5979fdf5f2`
2. `eb41ee0675c6c2677de708390125e1095178772e4849aece332be3eb89bb6da4`
3. `dca7439a316899c064f231411ee4a48e052c0e1b1f28d25da5c8041ca8ddd48a`

penultimate diagnostics 字节的 `5/5` receipt=
`159fbe80595933e29f05f13c0f1d82e9b65d7f947dddec253477f0b3f3876799`；callback OSError 与成功
日志存在性诊断加固后已由上述 final r2 current-byte receipt 取代。

dirty worktree 上尝试的 full Unit authority 在 source seal 预检因 untracked evidence 正确 fail closed，
没有执行 Maven；它不计行为 PASS。完整 Unit、QueryModel all-lane、23 exec/48 session 与 aggregate
只能由提交/push/clean 后的 replacement Cdiag fresh diagnostic 证明。

## Findings

### Blocker / High

首轮文档复核发现一项 High：authority progress 的 current-risk 段曾停留在 historical
r16/Cfreeze。现已由 formal-r3 与 r17 superseding boundary 更新为
`diagnostic-ready/diagnostic-pending` recovery 链；独立复核转为 PASS。最终 open Blocker/High=`0/0`。

### Medium

首轮复核发现的两项 Medium 已全部关闭：

1. 成功日志曾早于 finalizer/cleanup 删除；现已改为终态清理成功后才删除。
2. contract 曾把所有 failure 统一描述为 `E_LIFECYCLE`；现与实现一致，保留根因 typed code
   （例如 cleanup=`E_CLEANUP`）并附 diagnostics path。

独立代码、机器封印链和文档复核均转为 PASS。

### Low

无。

最终合并 B/H/M/L=`0/0/0/0`，open mandatory fixes=`0`。

## Documentation and State Review

README、roadmap、requirement、contract、module responsibility、implementation plan、progress、test plan、
acceptance evidence plan、code inventory、r17 failure evidence、新 handoff BUG 与 QueryModel/hidden
MySQL/lifecycle static/database successor 关联 BUG 已统一为：Steps 1–3 passed；r17 immutable
fail-closed/excluded；handoff focused remediation verified；machine=`diagnostic-ready/diagnostic-pending`；
full Unit 与 fresh diagnostic pending。Step 5、coverage audit、acceptance 与 9.3.5 保持关闭。

## Decision

当前 delta 可且仅可一次提交并 push 为 replacement Cdiag。提交后必须证明 clean
`HEAD == origin/main` 和 source identity，再以唯一新 run ID 执行 fresh diagnostic。该 gate 不放行
threshold candidate、Cfreeze、formal、coverage audit、acceptance、Step 5 或 9.3.5；fresh diagnostic
失败时必须封存并继续 fail closed。
