---
doc_role: formal_execution_evidence
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-formal-r38
tested_cfreeze: 62361688d838ba0a73348900502924decfbeeb68
cdiag_parent: 9743f97d9d935d5e26311b78c158755bca51f17a
status: formal-passed-postformal-evidence-complete
recorded_at: 2026-07-20
---

# Step 4 formal-r38 Addon publication-order revalidation result

## Authority boundary

- Tested Cfreeze: 62361688d838ba0a73348900502924decfbeeb68. Its sole direct parent is
  Cdiag 9743f97d9d935d5e26311b78c158755bca51f17a.
- The formal run used a new clean, non-shallow clone at the tested Cfreeze. The source seal before and after
  is identical: ed51da1b3721380318266614c91a978767fdd7e0d38d2357b3550eb2787187e9.
- step4-coverage-20260720-formal-r38 completed formal-passed / completed / exit=0. It is the sole formal
  authority for the repaired Addon successor runner; r11 and its acceptance remain historical only.

## Safe result summary

- Required union: 773 positive + 59 structural / 5707 testcase / F0E0S0.
- Addon companion: 2 reports / 6 testcase / F0E0S0, outside the required union.
- Exact execution inputs: 23 exec / 48 sessions.
- Aggregate line=54624/76830, branch=26112/44870, complexity=17659/35571. The critical policy has 12
  classes, 23 applicable metrics, one structural N/A, and 24 passed outcomes.
- The independent final-artifact verifier returned ARTIFACT VALID stage=final. Candidate manifest SHA-256 is
  4011516824c1133182a38121aab5fab39544be86735f3710e8f8793d7d8295f6; final manifest SHA-256 is
  5be020978a5032ce2f9a10aeff07b68e1bcdaea998b908e98b1c2c7c3451f11c; gate SHA-256 is
  d6e8b16c1a72b5b08aeddfe23d3bbeee98b1d2abfb598ab25acdd6c849ab2734.
- The final public effective-POM receipt is regular, non-link, non-empty, and mode 0644. Cleanup is
  container/volume/network=0/0/0.

## Addon publication-order result

The reviewed runner SHA-256 is 7c5020dc8af9328a0b70cc7933df3b01b7574bbfd21ecb9458c20c99ac2c9ac8.
In inherited mode it authenticates the canonical parent before publication, clamps the temporary child
mtime to no earlier than the authenticated parent marker, fsyncs and atomically publishes, then rechecks the
parent identity and digest. The r38 structured result confirms child_mtime >= parent_mtime and matching
parent digest/binding. Frozen Step 3 and its E_CROSS_RUN_SPLICE consumer remain unchanged.

## Independent static replay

The following checks passed against the fresh clone after the formal run:

- canonical formal verify-artifact on the final manifest and run status;
- coverage-contract and frozen-diagnostic validation;
- successor overlay validation, including the Step 3/Step 4/Step 6 hash closure;
- Step 6 CI workflow validation.

Raw logs, exec payloads, container identities and runtime report trees remain run-local and are not copied into
Git evidence. This record contains only reproducible identifiers and safe summaries.

## Historical exclusion and owner boundary

The r37 diagnostic establishes the frozen threshold baseline only; it is not formal authority. formal-r11 and
its previously accepted feature record are historical for the changed successor-runner bytes. The r38 Pivot
companion, independent implementation quality, and r38 35+1 revalidation audit are recorded separately.
Downstream authority remains suspended until foggy-projects explicitly re-signs this new runner scope; Step 5,
Steps 6-7, 9.3.5, and 9.4.0 are not opened by this evidence record.
