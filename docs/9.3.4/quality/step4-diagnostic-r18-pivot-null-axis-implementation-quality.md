# Step 4 diagnostic-r18 Pivot NULL-axis implementation quality

- reviewed_at: 2026-07-17
- scope: r18 governed high-water gap、Pivot NULL-axis deterministic semantic oracle、successor
  authority/hash-chain rebinding and version-authority document writeback
- mode: formal implementation quality gate / pre-Cdiag
- decision: pass / ready-for-one-replacement-Cdiag-commit-and-fresh-r19
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: one replacement Cdiag commit/push and one fresh r19 diagnostic only

## Review Basis

clean/pushed Cdiag `5be1edaa16c5883cde2f66396ac26a1ae113430b` 上的 fresh
`step4-coverage-20260717-diagnostic-r18` 已完成 full Unit、required lanes、23 exec / 48 sessions、
aggregate replay、source seal、public diagnostic validation、sensitive scan 与 cleanup。r18 是有效、
不可变的 `diagnostic-observed / completed / exit 0` evidence；但 aggregate=
`54622/76830 line`、`26107/44870 branch`，比 r16 independently reviewed high-water
`54624/76830`、`26111/44870` 少 `2 line / 4 branch`。因此 r18 decision 固定为
`threshold-candidate-not-authorized`，candidate 保持 absent，禁止降阈。

逐 class/source-line 差异只涉及未改 production 的
`BaselineRatioCalculator=-2 line/-3 branch` 与 `ResultShaper=-1 branch`。remediation 仅在既有
`PivotSqlParityIT#testBaselineRatioParity` 内加入受控 in-memory NULL-axis input：NULL column
保留为可见 cell 但不进入 first/last baseline domain；NULL row 与 named row 保持独立 group，并在
tree 输出映射为 `__null__`。断言同时证明 ratio、group isolation、tree node 与 NULL-column cells，
不是只为命中 probe。production、public API、POM、runner、coverage floor/critical/threshold/exclusion
均未修改；`@Test` 静态数保持 `23 -> 23`。

## Verification

| Check | Result |
|---|---|
| r18 authority | public `VALID`；required=`773+59/5707/F0E0S0`；Unit=`681+55/4941/F0E0S0`；Addon=`2/6`；exec/session/identity=`23/48/16940` |
| governed high-water | r18/r16=`-2 line/-4 branch`；decision=`threshold-candidate-not-authorized`；r18 candidate absent |
| deterministic source | `PivotSqlParityIT.java` SHA-256=`18ebeedf8d79a84d5ba59ff991aeffeefea73cbb8aa51c6c10444ed64ba6d325` |
| test cardinality | `@Test=23 -> 23`；database variant report identity/nodes=`23` unchanged |
| focused fresh JVM r1 | `1/F0E0S0`；exec SHA-256=`47db4897dc2e4272c1c7eb3143745dbe117c985817b8976d2bca40f378bd9a4d` |
| focused fresh JVM r2 | `1/F0E0S0`；exec SHA-256=`e94e76f1ddf603a8ef6cbd9bca5f0b9a47c19475df2deea68a7f093e04d4edf6` |
| focused fresh JVM r3 | `1/F0E0S0`；exec SHA-256=`7d87ce100faf3e9952ce7a4e4bfeb7175100be154f1f569c4db7af37530cc36c` |
| Baseline probe stability | class ID=`c21d882aa99c2fac`；`79/131` probes；bitmap=`9v5_gPv_H3aEb-g_MBAH_wQ`；`3/3 identical` |
| ResultShaper probe stability | class ID=`d04b34fb9680b047`；`45/139` probes；bitmap=`eoD62P8H6PcNAAAAAAAAACAH`；`3/3 identical` |
| complete owning test class | `PivotSqlParityIT=23/F0E0S0`；XML SHA-256=`b896f8f76cc930b3a5ee16a08b97a5d5ca240a979f0ce0bc16aa1673c555b4b0` |
| compiled test identity | class SHA-256=`e88b8a1f657f505f46c70f9cfdfcb74e0655673d358c8d2f8a6897185402486c`；class/report mtime 均晚于 source |
| protected test tree | `306 files`；tree SHA-256=`d11f9d478b4d4c29e036fd714586663591677f9ea0f833c505cba299523f0ea4` |
| database authority | `7 variants / 29 reports / 370 nodes`；contract SHA-256=`cca2158bb0cdb99d125f9d93b8fb37cbfd589ade059fdd039e7c42dfa3c167e5` |
| Step 3 required successor | `45/446/F0E0S0`；contract SHA-256=`a7ae5fc7aa0f161d99a8b9dadfd67a9c86c1ecb3d3745f32a00dbdd2743453a3` |
| successor overlay | `parents=3 / contracts=4 / amendments=22 / bindings=9 / required=45/446 / Addon=2/6` PASS |
| overlay negatives | `12/12`；SHA-256=`cf98e3306fcf5650ca4aad9475beb0e986b5363d15e4f3d326568753edd92e06` |
| coverage contract negatives | mutation=`27/27`、source/Git=`22/22`、replay=`12/12`；SHA-256=`e6763510d20847cb1f6e68ccc5b7123e5bf9b420b5baf7a965d7f7e73f9200d6` |
| coverage XML negatives | `118/118`；SHA-256=`9fd41898a5799347e83c0cffcc34334186c2b47a537851160a7fffc9cc28a32e` |
| full coverage contract | `diagnostic-ready/diagnostic-pending`；required=`773+59/5707`；exec/session=`23/48`；manifest entries=`60` |
| Step 4 manifest | `60/60` PASS；SHA-256=`812b148b1e41227988a61c8820dbdf7db2204c025a9d33bdb34dc4aa2c981de5` |
| successor manifest | `14/14` PASS；SHA-256=`8cec55116d926998165df6161e6a6f40eb41b77af54915603a5250bd96dbf1e9` |
| production/API/build boundary | production Java/POM/runner/threshold/exclusion diff=`0`；only governed test + successor bindings + docs changed |
| documentation/state | 9 authority docs、r18 governed evidence 与 NULL-axis BUG aligned；candidate/Cfreeze/formal/audit/acceptance/Step 5 closed |
| diff hygiene | `git diff --check` PASS |

focused exec 文件因 session ID/timestamp 不同而 byte SHA 不同；质量结论比较的是 JaCoCo class ID、
probe count 与 decoded probe bitmap，三次目标类均 exact identical。完整 owning test class 证明新增逻辑
未破坏相邻 S1–S23 行为；它仍不替代 clean authority 的 all-lane aggregate。

## Findings

### Blocker

无。

### High

首轮治理复核发现两项 High，均已关闭：

1. acceptance 顶部 Required Materials/Current Step 4 readiness 仍停留在 r17，与文件尾 r18
   superseding boundary 冲突；现已改为 r18 complete/public-valid、candidate blocked、r19 pending。
2. quality 前置 proof 尚未完整回写：当时缺少 owning class 结果，且 3 次 focused bitmap、哈希链与
   checklist 仍显示 pending；现已补齐 `3/3 identical`、`23/F0E0S0`、validators 与完成标记。

fresh governance re-review 将两项关闭，open High=`0`。

### Medium

首轮治理复核发现一项 Medium：README、progress、implementation plan、BUG/evidence checklist 与
acceptance/code inventory 对 hash-chain/focused proof 的时态不一致。所有 current summary、Current
Risks、Post-Gate、Next Action 与 checklist 已统一；fresh re-review 关闭该项，open Medium=`0`。

### Low

无。

独立代码审阅与 fresh 治理 re-review 最终合并 B/H/M/L=`0/0/0/0`，open mandatory fixes=`0`。

## Residual Risk

focused oracle、owning class 与机器契约只能证明本次 NULL-axis path 已确定化，不能证明 replacement
all-lane aggregate。唯一允许的下一证据是 clean/pushed Cdiag 上的 fresh r19；r19 必须完整 PASS、
public-valid 且 line/branch 不低于 r16 high-water。若仍低于高水位，必须封存并继续 fail closed，不得
生成 candidate、降低 threshold、扩大 exclusion 或进入 Cfreeze/formal。

## Decision

当前 delta 可且仅可一次提交并 push 为 replacement Cdiag。提交后必须证明
`HEAD == origin/main` 且 worktree clean，再以唯一 run ID
`step4-coverage-20260717-diagnostic-r19` 执行 fresh diagnostic。该 gate 不放行 threshold candidate、
Cfreeze、formal、post-formal final quality、coverage audit、acceptance、Step 5 或 9.3.5。
