---
evidence_type: scoped-threshold-cfreeze-authorization
version: 1
step: 4
run_id: step4-v934-fsscript-lifecycle-dual-seal-diagnostic-20260722-r7
reviewer: codex-governed-cfreeze-review
reviewed_at: 2026-07-22T17:45:28Z
decision: reviewed-cfreeze-authorized
authorization: single-direct-child-only
---

# FSScript lifecycle dual-seal diagnostic r7 — scoped threshold review

## Reviewed inputs

- Fresh diagnostic: passed and independently recomputable from Cdiag
  `462d64acf0865f44582b2b1245a9b4c771aad4cd`.
- Threshold candidate: SHA-256
  `b58e550729cdeedf84287773a5caf5b67b868b2157c6c4f929af64e3aecf8a78`,
  recomputed and verified while remaining `review-required`.
- Git-safe capsule: archive SHA-256
  `c8b99e5891fb96e619374fafad63c05c697245b30cdb06984beb1a885d70736a`,
  sealed and verified for the same run, Cdiag, source and aggregate XML.
- Primary and independent reviews: both passed with P0/P1/P2 = 0/0/0.

## Scoped authorization

The approved dual-seal successor specification authorizes exactly one
direct-single-parent Cfreeze from the tested Cdiag. That Cfreeze may only:

- project this candidate into the existing confirmed threshold schema;
- change the existing coverage contract to `formal-ready`;
- refresh the governed Step 4 and Step 6 hash closure; and
- carry the safe run-owned candidate, capsule and review evidence under
  `docs/9.3.4/`.

The Cfreeze must not modify product/test sources, POMs, runner semantics,
report inventory, coverage floors/exclusions, selector/fork/skip/order,
public API/SPI, Step 5 tooling or later-version paths.

This record does not authorize formal execution until the Cfreeze is
committed, pushed, topology-validated and clean. Any governed formal failure
must be recorded and stop for replan; it must not be retried in place.
