# Step 4 formal-r7 portability recovery implementation quality

- reviewed_at: 2026-07-19
- scope: formal-r7 failure sealing, repo-contained CALCULATE catalog, authority preflight, diagnostic recovery
- decision: pass / ready-for-Cdiag
- B/H/M/L: `0/0/0/0`
- open mandatory implementation fixes: `0`
- downstream authorization: one Cdiag commit/push, isolated proofs, then one fresh diagnostic-r25 only

## Review basis

formal-r7 在 clean fresh clone 上通过完整 Unit 后，于 Integration 唯一失败
`CalculateMvpIT.parityCatalogCasesStayExecutable`。独立审计确认 bridge HEAD 从未包含该 catalog；
早期 diagnostic 读取了工作区父目录的同名 tracked 文件。r7 的 fail-closed、success-only absence、
source、cleanup、process residue、sensitive scan 与四个 exact demo DB restore 均符合失败态契约。

修复只新增 exact repo-local catalog，并让 Step 4 authority 在 Docker/tests 前验证 tracked `100644`
Git blob 与 raw SHA。Java production/test source、POM、testcase、selector、report cardinality、floor、
critical set、exclusion 均不改。runner 改动后的 raw-byte seal 和 executable-stream seal 已同步，
Step4→Step6 hash cascade完整闭合。

## Verification

| Check | Result |
|---|---|
| formal-r7 immutability | `failed / child-integration / exit 1 / excluded`；candidate/final absent |
| failure capsule | `9 entries / 10303 bytes`；manifest `c7dbf3a5…102ae`；Base64 解码后 raw Failsafe byte-exact + per-entry provenance |
| catalog identity | `3407 bytes / 9 cases / blob d7879a6a… / SHA f52eba37…`；two-source byte-exact |
| Java test identity | `CalculateMvpIT.java=21d8d817…`；14 nodes unchanged |
| focused regression | `14 / F0 E0 S0`；named parity testcase PASS |
| untracked negative | authority rc=1 before run root/Docker/tests，exact tracked input required |
| runner seals | raw=`cf3979fc…f109e82`；executable=`f3d35874…66a16a`；full lifecycle negative PASS |
| Step 4 machine | manifest=`77cc8269…2919a1 / 61/61`；contract=`diagnostic-ready`；threshold=`diagnostic-pending` |
| Step 6 machine | manifest=`afb51627…0cb9b / 16/16`；workflow validator PASS |
| contract/overlay | full contract PASS；successor overlay=`3/4/35 + 45/446 + Addon 2/6` |
| generic negatives | coverage contract `27 + Git/source 22 + replay 12`；coverage XML `124`；lifecycle/overlay PASS |
| hygiene | JSON/Bash/diff check PASS；capsule sensitive hits=`0` |
| independent final review | `PASS / B/H/M/L 0/0/0/0 / mandatory fixes 0` |

## Decision boundary

该质量结论只授权把 catalog、runner/seal/hash closure、failure evidence与current-state文档一起提交为
新的 Cdiag。提交后必须证明 catalog 在 HEAD/index 中为 exact `100644`，分支 push/clean exact；再在
无父/兄弟 catalog 的隔离 clone 执行 focused method、完整 14 nodes 与 missing/tamper/外部诱饵负例。

只有上述证明通过，才可运行 fresh all-lane diagnostic-r25。r24/Cfreeze/formal-r7 继续 immutable；
candidate、new Cfreeze、formal-r8、post-gates、Step5与9.3.5均未开放。
