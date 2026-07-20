---
doc_role: formal_execution_evidence
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-formal-r11
tested_cfreeze: 4b17dbe34ae17168d44377c19ea6637b4c37a200
status: formal-passed-postformal-evidence-complete
recorded_at: 2026-07-20
---

# Step 4 formal-r11 fresh authority result

## Authority boundary

- Tested Cfreeze: `4b17dbe34ae17168d44377c19ea6637b4c37a200`; its sole parent is recovery Cdiag
  `93b3993e41d285300cb6968865da229319dad26d`.
- The run used a new non-shallow, clean clone at the tested Cfreeze, under outer `umask 077`. Docker was
  reachable before start and the governed `13306` listener/map precondition was zero.
- `step4-coverage-20260720-formal-r11` finished `formal-passed / completed / exit=0`. This is a new formal
  execution; r35 supplies only the frozen threshold baseline. No r35 raw exec/XML, r34, r10, or prior
  Cfreeze material was imported as formal evidence.

## Safe result summary

- Required union: `773 positive + 59 structural / 5707 testcase / F0E0S0`.
- Addon companion: `2 reports / 6 testcase / F0E0S0`, outside the required union.
- Exact execution inputs: `23 exec / 48 sessions`.
- Aggregate line=`54624/76830`, branch=`26112/44870`, complexity=`17659/35571`; line and branch equal the
  confirmed r35 minimum, and the coverage gate passed with all 24 critical metric outcomes passed.
- The independent final artifact verifier returned `ARTIFACT VALID stage=final`; final manifest SHA-256 is
  `b93e8c5d17c9961290f44c75d33be94d225323304fa305db6669cb757bdd71fc`.
- The final public effective-POM receipt is a regular non-link, non-empty file at exact mode `0644`.
- Runner cleanup passed with container/volume/network residue=`0/0/0`; after the run, the governed port
  listener/map count was again zero.

## Independent static replay

The following checks passed against the fresh clone after the formal run:

- `coverage_xml_tool.py verify-artifact --mode formal` on the canonical final manifest and run status;
- `coverage_tool.py validate-contract --repo-root .` including frozen-r35 semantic replay;
- `coverage_xml_tool.py validate-frozen-diagnostic`;
- successor overlay validation and Step 6 CI workflow validation.

Raw logs, exec payloads, container identities and runtime report trees remain run-local and are not copied into
Git evidence. This record contains only reproducible identifiers and safe summaries.

## Historical exclusion and acceptance boundary

formal-r10 remains `mechanically-passed / contract-invalid / non-authoritative /
excluded-from-audit-and-acceptance`; its `0600` report-stage receipt is not part of this authority chain.

formal-r11 opened the ordered post-formal work: same-Cfreeze Pivot supplemental evidence, independent final
implementation quality, the defined 35-row replacement coverage audit plus separately mandatory report-stage
receipt gate. Those evidence gates are now passed and recorded in the companion post-formal records.

This formal result still does not itself issue Step 4 feature acceptance. That decision awaits the separately
recorded Step 4-scoped `READY_FOR_SIGNOFF` canonical-delivery-spec prerequisite; until then Step 5, 9.3.5 and
9.4.0 remain closed.
