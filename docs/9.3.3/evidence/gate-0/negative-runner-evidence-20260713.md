---
doc_role: gate-negative-evidence
doc_purpose: Record expected Surefire/Failsafe failure evidence for missing and zero-test lifecycle suites.
version: 9.3.3
status: expected-negative-pass
recorded_at: 2026-07-13T18:05:24+0800
experience: N/A
---

# Gate 0 Runner 负向证据

## 文档作用

- doc_type: test-evidence
- intended_for: execution agent / coverage auditor / signoff owner
- purpose: 证明 lifecycle owning module 的缺失指定类和 0-test suite 都会非零退出。

## Result

| Runner | Scenario | Exit | Failure contract | Log SHA-256 |
|---|---|---:|---|---|
| Surefire 3.5.3 | `DefinitelyMissingV933UnitTest` | 1 | no specified tests | `6e4a7750b2a76865b902b89ed5b90955cec173ce093b9b41813c6f6b369dd547` |
| Failsafe 3.5.3 | `DefinitelyMissingV933IT` | 1 | no specified tests | `b14af90586840df64f7a5a4ff8ed39224cd59d83c30837edd802a312e2f306d7` |
| Surefire 3.5.3 | lifecycle include set contains 0 tests | 1 | no tests executed | `4c2824cca76de35117c6d86b27eb9a317acfef1458c7dc16d463334fd2867382` |
| Failsafe 3.5.3 | lifecycle include set contains 0 ITs | 1 | no tests executed | `330846c6ec1db4fa3ceb11c359c92ec79a79e0f82875e8eaf3b26a9b739791e3` |

The zero-suite rows were captured before the probe classes were added and
remain the direct `failIfNoTests` proof. The missing-class rows were rerun by
the final unified gate after the current reactor artifacts were installed.

Final generated logs:

- `target/v933-entry-gate/runs/20260713T104955Z-959834/negative-missing-unit/maven.log`
- `target/v933-entry-gate/runs/20260713T104955Z-959834/negative-missing-it/maven.log`
- `target/v933-entry-gate/zero-unit/maven.log`
- `target/v933-entry-gate/zero-it/maven.log`

所有命令显式使用 `-P'!multi-db,model-lifecycle'`，且只运行 owning `foggy-dataset-model`，因此失败原因不是 helper module 0-test。日志未记录数据库凭据。

## Decision

- result: expected-negative-pass
- missing unit class fails: yes
- missing IT class fails: yes
- zero unit suite fails: yes
- zero IT suite fails: yes
- positive suite evidence: passed；`gate-0-run-20260713.md`
- reactor owning-report assertion: passed with fresh run marker and exact runner execution counts
