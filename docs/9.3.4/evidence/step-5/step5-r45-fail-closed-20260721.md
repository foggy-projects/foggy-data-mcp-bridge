---
evidence_type: step5-rehearsal-fail-closed
version: 9.3.4
run_id: step5-rehearsal-20260721-r45
mode: rehearsal
tested_commit: f7da93c1ad79be2dede5494b99990092ba110071
authorization_record: docs/9.3.4/acceptance/step5-r45-pinned-base-image-remediation-and-rehearsal-with-nonfinal-candidate-pointer-20260721.md
environment_precondition: frozen-runtime-base-image-cache-ready
phase: package-tested-tree
exit_code: 1
outer_status: failed
receipt_operation: package
receipt_subphase: package-image
receipt_error_code: E_IMAGE
receipt_tool_exit_code: 1
candidate_generated: false
authority_candidate_pointer_updated: false
final_authority_pointer_updated: false
tracked_source_changed: false
status: fail-closed
---

# Step 5 r45 fail-closed result after authorized cache preparation

The fresh full canonical rehearsal was bound to exact Cfreeze
`f7da93c1ad79be2dede5494b99990092ba110071`. Before it started, the frozen
runtime base-image cache precondition was verified ready. That is an
environmental fact only; no image identity or raw Docker output is retained.

The outer five-field receipt ended `failed / exit=1` at
`package-tested-tree`. Because that exact phase permits it, the regular,
non-symlink nine-field package sidecar was independently validated with the
controlled receipt verifier. It classified the controlled failure as
`package-image / E_IMAGE` with package tool exit `1`.

The run made no outer candidate, authority-candidate, or final-authority
pointer, and did not alter tracked source. No raw package, Maven, Docker,
XML, image, container, OCI, or other execution output was read or retained.

r44 and r45 have the same bounded classification, while r45 had the frozen
base-image cache precondition ready. This shows only that the authorized cache
preparation did not change the observed bounded classification. `E_IMAGE`
remains intentionally broad: this record neither identifies nor rules out a
specific root cause, and it authorizes no further remediation or retry.
