---
type: bug
bug_source: quality-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-SOURCE-CONTEXT-CROSSLINK-GAP
severity: critical
status: in-progress
reproduction_status: confirmed
test_strategy: provenance-negative-test
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 source/context 与 raw exec 重放缺少 canonical cross-link

## Background

pre-r4 实现质量复核发现，`coverage_xml_tool.py` 对 `source-before.tsv` 与
`source-after.tsv` 只检查二者字节相等；没有证明 TSV 自身是
`run-context.json.source_sha256` 指定的 source seal。`exec-manifest.json` 中的
`run_context_sha256/source_sha256/not_before_ns/git_head` 也只做类型/格式校验，未与真实
canonical `run-context.json` 的字节 SHA 和字段逐项相等。

同一复核还发现，XML live/frozen recomputation 会验证 exec manifest 与 aggregate
provenance 的记录，却没有重新打开 canonical `runs/<run-id>/exec/` 下的 23 个 raw
JaCoCo exec。若只信已记录的 manifest hash，不能声称完成 raw exec byte replay。

## Reproduction

修复前可构造以下同步或 splice 变体：

1. 保持 `source-before.tsv == source-after.tsv`，但把二者替换成另一份相同 TSV；validator
   不比较 TSV SHA 与 `run-context.json.source_sha256`；
2. 保留真实 run context，同时独立修改 exec manifest 的
   `run_context_sha256/source_sha256/not_before_ns/git_head`；各字段格式合法时，manifest
   validator 不与 canonical context cross-link；
3. 删除、替换或混入 `runs/<run-id>/exec/` 的 raw exec，而保留 manifest、aggregate exec
   和 provenance；XML observation recomputation 不重新读取 raw 23 bytes。

Hermetic negative fixture 现在固定覆盖 source TSV equal-but-context-mismatch、manifest
四字段分别漂移、expected Git-head splice、raw exec byte/mtime tamper、missing/extra。
focused fast negative 当前为 `63/63`。

## Root Cause

证据链在生成阶段由 `coverage_exec_tool.py` 做过 context 与 raw exec 校验，但 XML
验收阶段把生成期校验结果当成可传递事实，缺少独立 verifier 的 typed cross-link：

- source TSV 的相等关系没有绑定 canonical context/source SHA；
- manifest/provenance 的字段之间相等，没有回到 canonical run-context bytes；
- aggregate provenance 记录 raw exec 的 SHA/size，但 XML verifier 没有重新读取原文件；
- frozen diagnostic 的结果没有明确给出 context 四元组与 raw exec replay scope 的 receipt。

## Expected vs Actual

- Expected：source-before/source-after 必须是 canonical、语义合法、byte-identical 的 source
  inventory，二者 SHA 必须等于 canonical run context 的 `source_sha256`；manifest 的
  context SHA、source SHA、not-before 与 Git HEAD 必须精确等于真实 run context。
- Expected：live 与 frozen replay 都必须保留并逐字节复验 exact 23 raw exec；整个目录缺失、
  部分缺失、额外文件、symlink/type、owner/link/mode、size/mtime、inode stability 或 SHA
  任一不符都 fail closed。不得降级为仅信 sealed manifest 的“byte replay”。
- Actual：修复前只验证记录之间的自洽性，缺少上述 canonical bytes cross-link。

## Test Strategy

`automation_decision=required`：

1. strict reader 校验 `source-before.tsv/source-after.tsv` canonical path、owner、mode、link、
   bounded bytes、TSV header/row schema、sorted unique path 与整体 SHA；
2. source-before/source-after 必须逐字节相同，且两者 SHA 等于
   `run-context.json.source_sha256`；
3. strict read canonical `run-context.json`，manifest 四字段逐项绑定其真实 byte SHA/字段；
4. exec manifest 本身只能位于 canonical run-owned path，拒绝 alternate/cross-run splice；
5. canonical raw exec 目录必须只有 ledger 的 23 个文件；dirfd + nofollow 打开每个文件，
   校验 owner、single link、non-executable/non-world-writable、exact size/mtime/not-before、
   stable inode/stat 与 SHA-256；
6. frozen validation receipt 显式记录 context 四元组与
   `exact-retained-raw-exec-byte-replay`，不提供 sealed-provenance-only fallback；
7. fast negative 至少覆盖 equal TSV/context mismatch、manifest context SHA/source/not-before/
   Git mismatch、expected Git-head splice、raw byte/mtime tamper、missing/extra；
8. identity manifest 刷新后重跑 full contract/validator 与 fresh all-lane diagnostic。

## Fix Checklist

- [x] 确认 source TSV、exec manifest 与 canonical context 的 cross-link 缺口。
- [x] 确认 XML live/frozen 未复验 raw 23 exec bytes。
- [x] 实现 strict source inventory 语义与 before/after/context SHA 绑定。
- [x] 实现 exec manifest canonical path 与 context 四字段 exact binding。
- [x] 实现 live/frozen 共用 exact 23 raw exec byte replay，禁止缺证据降级。
- [x] frozen replay 输出 typed context/raw-exec receipt。
- [x] hermetic positive/negative `63/63`，两个 Python 文件 `py_compile` 通过。
- [x] identity manifest 刷新后 full validator、contract `20/20 + Git 7/7` 与
  XML `63/63` 通过。
- [ ] fresh all-lane diagnostic 证明 retained raw exec replay 通过。
- [x] pre-r4 正式质量闸门复核，open Blocker/High/Medium=`0/0/0`。

## References

- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/v934/step4/coverage_xml_negative_tool.py`
- `scripts/v934/step4/coverage_exec_tool.py`
- `scripts/verify-v934-step4-coverage.sh`
- `docs/9.3.4/quality/step4-diagnostic-ready-implementation-quality.md`
