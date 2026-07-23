---
doc_type: release-authority-evidence
version: 9.3.4
step: 4
status: PASSED_READY_FOR_SIGNOFF
governance_date: 2026-07-23
run_id: step4-v934-fsscript-lifecycle-dual-seal-formal-tmux-recovery-20260723-r10
git_head: 5c57e537603004d47be936f74e92f873de5fe431
cplan_sha: b411e8bffd6026db39700f52252b3b911c69530d
activation_sha: aa7217d6add2c6ae425227f055b96bf9388e88d2
---

# Step 4 FSScript lifecycle dual-seal formal tmux recovery r10

## Verdict

The one r10 invocation authorized by the tmux-owner recovery Cplan completed naturally and reached
the canonical terminal verdict:

`[v934-step4-coverage] FORMAL PASS run=step4-v934-fsscript-lifecycle-dual-seal-formal-tmux-recovery-20260723-r10 exec=23/48 reports=774/59/5709 addon=2/6 acceptance=final`

The tmux pane was retained after exit and reported `pane_dead=1`, `dead_status=0`. The canonical
`run-status.env` independently records `last_phase=completed`, `exit_code=0` and
`status=formal-passed`. Candidate and final manifests both pass the public artifact verifier; final
verification was bound to the canonical run status.

This closes the Step4 formal-authority gap created by the externally interrupted r8 and r9 runs.
It does not itself accept the release or replace the required Step5-7 governed executions. The
successor work item is `READY_FOR_SIGNOFF`, not `ACCEPTED`.

## Governance and immutable input

- Predecessor r9 failure record: `f217f456516f4ba73cf6dd7a90ba4a0c8cad9331`.
- Approved Cplan: `b411e8bffd6026db39700f52252b3b911c69530d`, a clean direct child of the
  predecessor record.
- Activation: `aa7217d6add2c6ae425227f055b96bf9388e88d2`, a clean direct child of the
  Cplan and pushed before execution.
- Immutable Cfreeze / execution HEAD: `5c57e537603004d47be936f74e92f873de5fe431`.
- Direct parent Cdiag: `462d64acf0865f44582b2b1245a9b4c771aad4cd`.
- Fresh detached clone:
  `/tmp/foggy-v934-formal-tmux-recovery-20260723-r10.fIiAk0`.
- Run started at `2026-07-23T02:39:54Z` and finished at `2026-07-23T03:43:28Z`
  (`2026-07-23 10:39:54` to `11:43:28`, Asia/Shanghai).
- Source inventory before and after:
  `21b9bddf5f5a120b8058ddaab5a9c0088ae2f72a9e67668d17a095bb391ca01a`;
  the TSV files are byte-identical.
- Coverage contract:
  `5107aad524da7f309acc939c2e1698dfe10e88822749806fdbf441c8ae9412be`.
- Reviewed threshold:
  `55b84fa64e178ce77a1331da66373d47d3a40880685e4bc1a3191f6c9c216635`.
- Formalization delta:
  `4f46f78bdb5e67d4694fa51fdef0aab146906e80303338822cc4c1f55cfb4e8b`.
- Step4 manifest:
  `56c1b6c832bcfb7e1e4ea9b208a86bffe8ddec799c5ae6162459f3cba31415d6`.
- Step6 contract/tool/manifest seals remained respectively
  `89ffd6cb1b1ef99b958dce29bb92eddc907fd551aab2054222cccf8f15bed46a`,
  `0a0ef47a684243a55e01b6b87756b63197efa5291c16020e076ee674097f0707` and
  `8ad6c774c9abda6d01c6505c41854be4635273287fbe128609c0e0fa01ab7e56`.

The execution clone remained clean and detached at exact Cfreeze. No source, product, test, POM,
runner, manifest, threshold, exclusion, selector, fork, skip, test order, API or SPI byte changed.

## tmux ownership and observer boundary

- Isolated control directory:
  `/tmp/foggy-v934-formal-tmux-control-20260723-r10.6VaMiY` (created mode `0700`).
- Isolated socket: `runner.sock`; session/window/pane:
  `v934-formal-r10:formal.%0`.
- Pane main process identity at launch: PID/PGID/SID `677327`, TTY `/dev/pts/6`.
- Pane command used shell `exec` to start the single canonical formal invocation; no retry,
  timeout, wrapper exit translation or second runner was present.
- `remain-on-exit=on` retained the exact pane terminal state: `dead_status=0`, dead time
  `2026-07-23 11:43:28 +08:00`.
- After launch, observation was limited to `list-panes`, `capture-pane` and read-only
  filesystem/process/container/port inspection. There was no attach, `send-keys`, `pipe-pane`,
  respawn, continuation, run-root edit or execution-state mutation.
- After the dead pane, receipts and cleanup were captured; only then was the isolated tmux server
  closed. No matching runner process remained.

This ownership boundary demonstrates that terminating a Codex process or its original PTY does not
terminate the detached runner. It does not protect against a host reboot or an explicit tmux server
kill; those conditions remain fail-closed.

## Required execution results

| Gate | Result |
|---|---|
| Unit | PASS |
| Integration | PASS |
| Step3 required | PASS, `45 reports / 446 testcases`, F/E/S=0 |
| Database matrix | PASS, 29 reports / 370 testcase nodes, state-negative `18/18` |
| External matrix | PASS, 16 reports / 76 testcase nodes |
| Required report inventory | PASS, `774 positive + 59 structural / 5709 testcases`, F/E/S=0 |
| Addon companion | PASS, `2 reports / 6 testcases`, F/E/S=0 |
| JaCoCo inputs | PASS, 23 exec files / 48 sessions / 16,950 class records |
| Class universe | PASS, 24 production modules / 2,098 classes |
| Coverage | PASS, 24 groups / 48 sessions / 12 critical classes / below-floor=0 |
| Model gate | PASS, bundle line/branch minimum `0.77/0.62`, semantic scale `1.00/1.00` |
| Negative matrices | PASS, coverage-exec `17`, coverage-XML `13`, report inventory `30/30` |
| Source integrity | PASS, before and after source inventories byte-identical |
| Sensitive scan | PASS, 5 credential-shaped patterns over evidence text formats |
| Resource cleanup | PASS, container/volume/network residue `0/0/0` |
| Acceptance | Candidate VALID, final VALID, canonical run status `formal-passed` |

## Canonical receipt digests

| Receipt | SHA-256 |
|---|---|
| `run-status.env` | `7b759c19335caf49d536413282d34fbbdab5b2c58f96675619eab0dfbe223dd3` |
| `summary.env` | `85e03d9656a833b9592e8d3eb9ff1defe6bf214bcd2038f7ae4db1c0348ee52b` |
| `coverage-gate.json` | `e2c810a76dc5ed9bce3c949f3dd67d37fc01ca87300ddc6e4bf77d7106d10db3` |
| `candidate-manifest.json` | `9fce69b809a0fd7ad2ddf844a0f953f1bf747ab90a79b30d1a83780dc7e524ef` |
| `final-manifest.json` | `5943e323f1441f60219483339203b0f5f860d6db448006fa255524f9c6914ffc` |
| `toolchain-receipt.json` | `0f2fa39e2d4a953bd58ad3cb64a83082a769d8f6eceeabb0d51070effb8b124c` |
| `child-lifecycle.json` | `38057cedef8e24cad5a6c8e2964db0913deaa6fdb09cbf3979127a4d1b1b9232` |
| `report-inventory.json` | `dc07273fe10a10fffa63c7f2634f4fdc5404b752d5aad6e2213396419c19cabe` |
| `exec-manifest.json` | `7d668813d9f3b66d60ea5e0711179c2859bf9aa555d6b1971da2c0ed820cdeec` |
| `coverage-observation.json` | `2d996e824432016e1427d37aa09e3851f6f69b88b5df8ee18fbb24d506befb85` |
| aggregate exec | `3e477f76e62c718b6c318913420de1db1cb159113334ea932b367b99a4481ad6` |
| aggregate XML | `73d24552a8dff8ef7be1565c09cc7705ed1e8c4258def6ba482c695e57c17e09` |
| `cleanup.env` | `5524f0c68afb6ff1d13358f8fa3d2d19f7c1270454f2dfed1d2fe72cfe334ff1` |
| `sensitive-scan.env` | `b4f3df2931ee6b0b1e529d0e267242eb0b2d3b5e5ab71498b1bdbbab98674701` |
| `model-gate.env` | `deab21ef726b775abf75e5e307b05caf78671cce5d0de63fffa276595f6a00da` |

The final manifest binds the aggregate exec/XML, report and aggregate provenance, class universe,
child lifecycle, source before/after, formalization delta, model, sensitive and cleanup receipts.
The confirmed frozen diagnostic replay passed and remained bound to Cdiag, Cfreeze and the reviewed
threshold/capsule identities.

## Residue and protected workspace

- Post-run inspection found no matching runner/test process and no run-labeled container, volume or
  network.
- Ports `13306`, `13308`, `15432` and `11433` were free.
- The run-owned tmux server was stopped only after the dead pane and all receipt identities were
  captured.
- The original workspace remains at
  `9743f97d9d935d5e26311b78c158755bca51f17a`; it is behind the remote governance branch and still
  contains only the user's protected `docs/9.3.5/README.md` modification and untracked
  `docs/9.3.5/workitems/`. They were neither modified nor included.
- r8 and r9 runtime bytes remain permanently `FAILED_EXCLUDED`; no file, report, exec, candidate or
  partial child result from those runs was reused or spliced into r10.

## Disposition

- Step4 formal authority for the FSScript lifecycle successor is complete.
- This work item may enter independent signoff as `READY_FOR_SIGNOFF`.
- `can_enter_step5=yes`, subject to the controlling release work item's exact command, fresh-run and
  fail-closed requirements.
- Step6 and Step7 remain sequentially gated by their own controlling-item predecessors; this record
  does not pre-authorize them or perform release signoff.
