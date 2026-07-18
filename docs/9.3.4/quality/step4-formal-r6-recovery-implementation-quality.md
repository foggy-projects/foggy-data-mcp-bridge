# Step 4 formal-r6 recovery implementation quality

- reviewed_at: 2026-07-19
- scope: formal-r6 fail-closed analysis, fsmonitor v2 negative-fixture remediation, diagnostic state recovery
- decision: pass / ready-for-new-Cdiag
- B/H/M/L: `0/0/0/0`
- open mandatory implementation fixes: `0`
- downstream authorization: one Cdiag commit/push and one fresh diagnostic only

## Review basis

formal-r6 on clean/pushed Cfreeze `14931b68…` passed formal delta, frozen r22 replay, full contract and
successor overlay, then failed at mandatory `bootstrap-negative` before source seal or any test lane.
The failed run and its success-only absence boundary are sealed separately and cannot be resumed.

Two independent read-only audits agreed that the real main/fresh indexes are ordinary and that the
synthetic hook's newline response violates fsmonitor protocol v2. One audit reproduced the old behavior
as `996 h / 4 H` and the NUL-token behavior as `1000/1000 h`; another observed an unchanged index hash,
persisted `flags=0x200000`, transient first `H`, then `h`.

The implementation delta changes only the synthetic hook response from newline to a fixed NUL token.
It retains both the exact lowercase-flag precondition and the validator rc=2 rejection. There is no
retry, alternate acceptance, production source, public API, POM, runner, floor, exclusion, critical set,
report cardinality or database change.

## Verification

| Check | Result |
|---|---|
| formal-r6 immutability | `failed / bootstrap-negative / exit 1`; summary/toolchain/tests absent; cleanup and DB restore PASS |
| failure capsule | raw artifacts/outer restore=`5 entries / 10412 bytes`; manifest `969beebbaf47db053f5829f43f24a0782e6f6983079407aee56b6160ce0c21de`; exact 4 container IDs healthy |
| independent protocol stress | old=`996/1000 h`; fixed=`1000/1000 h` |
| local focused stress | `100 iterations / 200 cases / passed` |
| five full negative processes | `5/5 passed`; each `27 + 22 + 12` governed cases |
| source-hash fail closed | exact `fsmonitor-valid-index-entry / rc 2 / ordinary H error` in all five receipts |
| pending threshold | `0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96` |
| diagnostic contract | `5f7ba4bf9d6e6b0673065c3f4d004d2ce627cd87dea7a286996d14b06f07d530` |
| Step 4 manifest | `51ff1d262008338b208726fac98f52b916de57660c3459b9df913e57e5344f76`; `61/61` |
| Step 6 contract/tool | `8622edd1…2a3` / `719dcc70…71e4`; workflow validator PASS |
| full contract | `workflow_state=diagnostic / threshold_status=diagnostic-pending / passed` |
| successor overlay | `parents=3 / contracts=4 / amendments=35 / required=45/446 / Addon=2/6 / passed` |
| diff hygiene | `git diff --check` passed |
| independent delta review | PASS / `B/H/M/L 0/0/0/0` / blocking fixes 0 |
| independent state-doc review | initial `0/1/1/1`; all current-state/capsule findings fixed; final `0/0/0/0` |

The pre-Cdiag delta has no blocker/high/medium/low finding. Because the changed negative tool is outside
the prior formalization allowlist, r22 and Cfreeze `14931b68…` are historical only. The next authority
must be a fresh all-lane diagnostic from the new clean/pushed Cdiag, followed by new review/freeze/formal.
