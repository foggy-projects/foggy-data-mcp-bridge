---
review_type: git-safe-diagnostic-capsule-independent-review
version: 1
step: 4
run_id: step4-coverage-20260720-diagnostic-r40
tested_commit: 6b92451294f3a324ada156a9c088756d711c790c
source_sha256: caca16f6cd17e8fa2195dc2e355013d8da536cd0d0c123644b3ea14f18f5d5cb
candidate_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-threshold-candidate-20260720.json
candidate_sha256: 28d42a09a5da68a326c2effc3fad95da0b1f3b3bd794dd1814fb505388e259cb
attestation_sha256: 9fbd58fa82cf8fee1a2cc65d2ad2133c9cef4e26d4eafd22edaa2c7ddb980c39
capsule_archive_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-portable-capsule.tar.gz
capsule_archive_sha256: 6fa51912862c7787efeaa49e029af80821af04bb486f822c4d5088e795ce0637
capsule_manifest_path: docs/9.3.4/evidence/step-4/step4-coverage-20260720-diagnostic-r40-portable-capsule.manifest.json
capsule_manifest_sha256: a82409581842b6d69d7f58681f0d62717482cfaa5291f3b3bd0ecb4261c87eb1
reviewer: codex-independent-reviewer-b
reviewed_at: 2026-07-20T10:16:03Z
decision: verify-git-safe-capsule
status: passed
B: 0
H: 0
M: 0
L: 0
---

# r40 portable capsule — independent review B

The capsule verifier and materializer independently passed with the expected
run, Cdiag, and source seal. The candidate verification also passed and its
identity agrees with the attestation binding.

The retained allowlist is exactly `evidence/`,
`evidence/diagnostic-attestation.json`, and `evidence/jacoco.xml`; execution
bytes, runtime closure, and unstructured output are excluded. In an empty
temporary directory, the materialized attestation and XML reproduced the
archive and manifest byte-for-byte. The rebuilt archive, manifest, and member
digests equal the front-matter values.

Decision: **PASS** with B/H/M/L = 0/0/0/0 and no mandatory action. This
review authorizes only Cfreeze materialization for this Cdiag; it is neither
formal nor Step 5 or version-signoff authorization.
