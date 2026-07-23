---
evidence_type: threshold-review
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-diagnostic-r34
tested_commit: 72175735a8409116a42afba4d69e5f7ec9fb50fe
candidate_sha256: 67161164713cdb520cc57d4b2cfa6c5f6d43bf6753e5964c9b1514e1317b08bc
reviewer: "Codex /root + Reviewer A + Reviewer B"
confirmation_owner: "Codex /root"
independent_reviewers:
  - "/root/r34_result_audit (Reviewer A)"
  - "/root/r34_candidate_procedure (Reviewer B)"
reviewed_at: 2026-07-19T19:56:02Z
decision: confirm-observed-thresholds
status: reviewed-cfreeze-authorized
formal_status: pending
user_confirmation: not-asserted
B_H_M_L: "0/0/0/0"
---

# Step 4 r34 threshold review

## Decision

`step4-coverage-20260720-diagnostic-r34` is approved as the only reviewed
diagnostic source for the next Step 4 Cfreeze. It ran from the fresh,
non-shallow, clean Cdiag `72175735a8409116a42afba4d69e5f7ec9fb50fe`.
This review confirms the exact r34 observation; it does not constitute formal
acceptance, coverage audit completion, Step 4 feature acceptance, version
signoff, or authorization to begin Steps 5–7, 9.3.5, or 9.4.0.

Reviewer A independently recomputed the candidate and the safe diagnostic
summaries. Reviewer B independently rebuilt and checked the Git-safe capsule
in a new temporary directory. Both reviews returned `APPROVE`, with
blocker/high/medium/low=`0/0/0/0` and mandatory actions=`0`.

## Candidate recomputation

- Public `validate-diagnostic-run` and `verify-threshold-candidate` both
  passed for r34.
- Required evidence is `773+59 structural / 5707 testcase / F0E0S0`:
  Unit=`681+55 / 4941`, Integration=`47+4 / 320`, and Step 3 required=`45 /
  446` (database=`29 / 370`, external=`16 / 76`).
- The excluded Addon companion is `2 / 6 / F0E0S0`; it is not part of the
  required union. Execution provenance is `23` files / `48` sessions.
- Source before/after is byte-identical for the outer and child lanes; the
  Step 3-required inheritance binding is exact. Model external gate passed.
- Runner-owned cleanup closed at `0/0/0`; the database state-negative,
  SQLite, external, and Addon cleanup summaries also closed without residue.
- Aggregate high-water is line=`54624/76830`, branch=`26112/44870`, and
  complexity=`17659/35571`. All meet the r34 governed floor; complexity is a
  separate high-water check because the threshold candidate schema freezes
  line and branch only.
- The critical policy remains `12` classes / `23` applicable metrics / `1`
  structural N/A / below-floor=`0`. Every applicable candidate minimum is
  exactly its r34 observation; `NamespaceScope` branch is the sole approved
  zero-total N/A.

The immutable candidate contains only r34 evidence and the canonical
`diagnostic-pending` predecessor. It contains no historical candidate,
baseline, or prior-run field, so r32/r33 and all historical noncanonical
materials are excluded from this authority.

## Git-safe capsule review

The portable capsule profile is `git-safe-sanitized-attested-v1`. Its canonical
archive SHA-256 is
`9cce912b59822ebef734a37e3d61a847f06e9983fb50dd7a67a668c1493a1cc7`
(658,954 bytes); its manifest SHA-256 is
`a7477cab81887b482dc61dd083dd2359d6eea5c165d31be265020aa8e4e6a1c1`.
The bound diagnostic attestation SHA-256 is
`6e21bf4bc03898f130cd8b6121c0e27704f000b19aa2bf82a821f8ae8b5e4f98`.

Reviewer B's independent rebuild was byte-identical to the canonical archive
and manifest. Capsule verify, an empty-directory materialize, and the
capsule self-test all passed. The manifest contains only the `evidence/`
directory and these two retained payloads:

- `evidence/diagnostic-attestation.json`
- `evidence/jacoco.xml`

No raw execution bytes, logs, container/process/host identities, or runtime
closure data is retained. The three retention classes—runtime closure,
execution bytes, and unstructured output—remain forbidden.

## Cfreeze authorization boundary

This review authorizes exactly one direct-single-parent Cfreeze from
`72175735a8409116a42afba4d69e5f7ec9fb50fe`. The Cfreeze must project the
candidate's exact aggregate and critical thresholds, bind this finalized
review's SHA-256, include the r34 candidate and portable capsule under
`docs/9.3.4/`, and change no non-document path outside the frozen six-path
formalization allowlist.

Fresh formal verification remains mandatory after that commit. This review
does not assert a user confirmation.
