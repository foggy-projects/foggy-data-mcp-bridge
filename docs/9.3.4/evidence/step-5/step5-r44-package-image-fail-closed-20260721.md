---
evidence_type: step5-rehearsal-fail-closed
version: 9.3.4
run_id: step5-rehearsal-20260721-r44
mode: rehearsal
tested_commit: f7da93c1ad79be2dede5494b99990092ba110071
phase: package-tested-tree
exit_code: 1
outer_status: failed
receipt_operation: package
receipt_subphase: package-image
receipt_error_code: E_IMAGE
receipt_tool_exit_code: 1
candidate_generated: false
final_authority_pointer_updated: false
status: fail-closed
---

# Step 5 r44 package-image fail-closed result

The one owner-authorized fresh rehearsal r44 reached its independent same-run
Step 4 release successor, which completed with `release-passed / exit=0`, and
then stopped at outer phase `package-tested-tree`.

The canonical outer five-field failure receipt records only `failed / exit=1`.
Because that exact outer phase permits it, the fixed nine-field package receipt
was verified and classified the controlled failure as `package-image / E_IMAGE`
with tool exit `1`. No raw package, Maven, Docker, XML, or image output was
read or retained in this record. No candidate and no final authority pointer
were created.

After the failure, the frozen pinned runtime base image was found absent from
the local Docker cache. This is a safe environmental observation, not a causal
claim for `E_IMAGE`, which intentionally covers multiple fail-closed image
boundaries. This record authorizes neither image remediation nor another
rehearsal; either requires a new owner direction.
