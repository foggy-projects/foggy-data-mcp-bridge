# Step 4 r21 Cfreeze implementation quality

- reviewed_at: 2026-07-19
- scope: r21 sealed diagnostic evidence, deterministic portable capsule, immutable threshold
  candidate, two independent reviews, canonical confirmed threshold and Step 4/Step 6 hash cascade
- mode: formal implementation quality gate / pre-Cfreeze
- decision: PASS / ready-for-one-direct-child-Cfreeze-commit
- B/H/M/L: `0/0/0/0`
- open mandatory fixes: `0`
- downstream authorization: Cfreeze commit/push/topology/clean proof, then one fresh formal only

## Review basis

Fresh diagnostic `step4-coverage-20260718-diagnostic-r21` completed on clean/pushed Cdiag
`5121a9c7fe35120c7864de8554e99188f5d1dc87` with required=`773+59/5707/F0E0S0`、Addon=
`2/6/F0E0S0`、exec/session/class identity=`23/48/16,945`、production universe=
`24/2,098`、aggregate line=`54,624/76,830`、branch=`26,111/44,870`、critical=`12`、
positive metrics=`23`、below-floor=`0`、unique structural N/A=`NamespaceScope.branch`、cleanup=
`0/0/0` and sensitive/model gates=`passed`。Source before/after is byte-identical；the outer wrapper
restored the four exact demo DB containers with `runner_rc=0 / restore_rc=0`。

The immutable candidate passed public verification and two independent reviews。Reviewer A
independently recomputed aggregate and critical counters using integer cross multiplication；Reviewer B
independently verified the evidence graph and materialized the capsule into a truly empty directory。
Candidate bytes remained unchanged before and after both reviews。

## Verification

| Check | Result |
|---|---|
| candidate | public verification PASS；SHA-256 `5f789e986d9e79854fe98b8433b684564fb37c17c349cf3e80ba66f18ad98fdc` |
| review evidence | two independent reviews PASS；SHA-256 `8f3ef8a886e9db1806da019ec618ae54ec21c22ae91b4bff358111a23e578cc6` |
| portable capsule | public verify/materialize PASS；archive `7e1b9461373f4f1e1f231b6d39711cd020938fa8182276beee7fe9e39abf0444`；manifest `8c1fcd86ddd046e7a308f5147b4b01aa000fe7880c540f2d73d4302ac13c37a6`；entries=`6,638`；symlink=`0` |
| strict projection | aggregate exact；critical rows `12`；positive metrics `23`；unique N/A `1` |
| aggregate reviewed threshold | line=`54,624/76,830`；branch=`26,111/44,870` |
| threshold | `confirmed`；SHA-256 `87e10431cf0238a9690360609993656ae278606fff22fe657cba9fe41787352a` |
| contract | contract/publication=`formal-ready`；SHA-256 `babdcd887faa766aee2283fa95d885f5b51bbc3af11720e19047085c48c0be1e` |
| Step 4 manifest | SHA-256 `cf788e6380e3bcb322a3c162e99762b954529a5ab79adc1559c354bc15d85712`；`61/61` |
| Step 6 contract/tool | SHA-256 `af9e8f49f1f184f4683ac40132503a93fe63b11efacc364d9ca400d6b685c668` / `cc2717473db04c517aa5383b7dcf751755239f40275155e1e31a249c39f43908` |
| Step 6 manifest/workflows | SHA-256 `a315b1ea10dccfcb3025f8792a62d4f5a14ccf695c387521d27940239da6fb63`；`16/16`；workflows=`4` |
| fail-closed regression | coverage contract=`27`、Git identity=`22`、frozen replay=`12`、capsule=`8`、CI=`86` cases all PASS；r21 XML negatives=`124` PASS |
| diff hygiene | `git diff --check` PASS；machine delta is the six required exact paths；other paths are under `docs/9.3.4/**` |

## Implementation closure review

- No production、test、POM、runner、workflow、coverage floor、critical set、exclusion or release asset
  changed in this delta。
- The machine delta is exactly the required formalization closure：threshold exact projection，the two
  coverage publication-state fields，Step 4 manifest，Step 6 upstream-manifest binding and its tool and
  manifest hash cascade。
- Candidate remains immutable `review-required`；canonical threshold contains only the candidate exact
  projection plus review binding。No float-derived minimum、denominator rescaling or duplicate authority exists。
- The portable capsule is deterministic across two builds and rejects any source or negative-fixture
  symlink with `E_SYMLINK`。The manifest records an empty omission set，so replay closure is explicit。
- No new runtime branch、API、dependency cycle、large method or repeated implementation was introduced。
  The large JSON delta is reviewed contract data，not new execution complexity。

## Findings

Blocker/High/Medium/Low=`0/0/0/0`。Fresh formal is mandatory downstream work，not an
implementation-quality finding。Any formal counter below the confirmed exact threshold、source or
provenance drift、test failure、negative-probe failure or cleanup residue must fail closed；the threshold
may not be lowered to accommodate it。

## Decision and boundary

The current allowlisted delta may be committed exactly once as the direct-single-parent child of
`5121a9c7fe35120c7864de8554e99188f5d1dc87`。After commit，run official formal-delta、frozen
diagnostic and topology validation，push and prove clean `HEAD == origin/codex/v934-release-authority`
before starting fresh formal。This quality gate authorizes only that Cfreeze and one fresh formal；it
does not authorize post-formal quality、coverage audit、acceptance、Step 4 exit or release publication。
