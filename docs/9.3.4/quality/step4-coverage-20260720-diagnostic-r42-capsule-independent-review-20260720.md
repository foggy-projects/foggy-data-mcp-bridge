---
evidence_type: independent-git-safe-capsule-review
version: 1
step: 4
run_id: step4-coverage-20260720-diagnostic-r42
tested_commit: dd2ccde8e97c4dfe88e9a06141280a1d747ac737
source_sha256: dc87cb0e44080fa7af7113674275ef36d449dca1a8b27ffc2a402a0387bf0d0c
attestation_sha256: 8f2a2c0332f5cc7f12801b383e5441c5d9302b864f8cf976995853a69cb88556
capsule_archive_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-portable-capsule.tar.gz
capsule_archive_sha256: 394a393224aabe31f5b9708bcc9c6874dfae23fb390291ec5d862c85123fc662
capsule_manifest_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r42-portable-capsule.manifest.json
capsule_manifest_sha256: 3b275bbb7deaf1098f00e81f2999129132f4048f2bd1c301be154ada413c3682
reviewer: codex-independent-reviewer-b
reviewed_at: 2026-07-20T17:14:11Z
verdict: pass
B: 0
H: 0
M: 0
L: 0
---

# r42 Git-safe capsule independent review

Reviewer B independently verified and materialized the versioned r42
capsule into a fresh empty directory. The exact member and retention checks
passed, as did the attestation, candidate, run, commit, source, and XML
hash-binding checks.

The capsule retains only its evidence directory, hash-safe attestation, and
the JaCoCo XML member. Runtime closure, execution bytes, and unstructured
output are all explicitly forbidden. No archive, XML, execution output, or
other raw runtime content was reviewed directly. This review does not claim
formal validation or version signoff.
