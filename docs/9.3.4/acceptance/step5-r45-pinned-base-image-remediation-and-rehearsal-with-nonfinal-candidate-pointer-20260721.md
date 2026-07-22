---
acceptance_scope: step5-rehearsal
version: 9.3.4
target: STEP5-R45-PINNED-BASE-IMAGE-REMEDIATION-AND-REHEARSAL-WITH-NONFINAL-CANDIDATE-POINTER
status: consumed-fail-closed
decision: accepted-single-attempt
signed_off_by: foggy-projects
signed_off_at: 2026-07-21
authorization_source: direct-owner-direction
tested_commit: f7da93c1ad79be2dede5494b99990092ba110071
final_authority_pointer_allowed: false
---

# Step 5 r45 bounded owner authorization and outcome

## Authorization provenance

This append-only record durably transcribes the direct owner direction received
before r45 was launched. It is independent of, and does not reopen or amend,
the signed r43/r44 record: r44 had consumed r43's original single rehearsal
opening.

## Authorized boundary

`foggy-projects` approved exactly one r45 action on 2026-07-21:

- make the already frozen runtime base image available in the local Docker
  cache, without changing its identity or any frozen tooling;
- create a fresh full canonical clone bound to exact Cfreeze
  `f7da93c1ad79be2dede5494b99990092ba110071` and run one isolated Step 5
  `rehearsal`;
- permit the runner's success-only, non-final `candidate-run` record if the
  frozen rehearsal contract reaches success.

The authorization never permits Step 5 authority mode, final promotion or
release publication, final-authority pointer publication, Steps 6-7, 9.3.5,
9.4.0, a new runner/tool/Cfreeze change, image replacement, broad cleanup, or
another rehearsal. The frozen runner's internal same-run Step 4 successor is
not a final promotion. A cache observation is not authorization to infer an
`E_IMAGE` root cause.

## Consumption and outcome

The single authorized run
[`step5-rehearsal-20260721-r45`](../evidence/step-5/step5-r45-fail-closed-20260721.md)
ended fail-closed. Its outer candidate, authority-candidate, and final
authority pointers were absent. Therefore the success-only non-final candidate
allowance was never exercised.

This authorization is consumed. No retry, environmental remediation, candidate
creation, pointer action, or downstream version work is authorized by this
record.
