---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MONGO-SENSITIVE-PROBE-SELF-MATCH
severity: major
status: in-progress
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: scripts/v934/step3
---

# Mongo sensitive negative probe evidence self-match

## Symptom

正式候选 `external-mongo-candidate-ef0dae38-r1` 已通过 exact
`4 reports / 30 testcase / F0/E0/S0`、Mongo fixture、数据库与 Docker 清理、source seal
和 `12/12` report negatives，随后在 `sensitive-scan` 阶段被拒绝。失败候选不生成
`summary.env` 或 `candidate-manifest.json`，Docker residue 为 `0/0`。

## Root Cause

短凭据探针把 bearer pattern 从最少 8 字符收紧为任意非空 token，以证明单字符 token 也会
被拒绝；但 durable negative evidence 仍使用标签 `bearer`。扫描器因而把
`bearer<TAB>passed` 自己识别为 bearer credential，`sensitive-scan.matches` 只包含
`negative/sensitive-probes.tsv`。这证明扫描 fail closed 生效，但该运行不能成为绿色候选。

## Fix

将 durable probe 标签改为不带凭据语义的 `auth-header`。探针输入仍是
`Authorization: Bearer x`，检测强度和 `6/6` 数量不变；全 run-owned evidence scan 仍使用
原来的五组敏感模式，不增加排除路径。

## Required Regression

- [x] 原始正式候选因 probe evidence self-match fail closed
- [x] `sensitive-scan.matches` 精确指向 `negative/sensitive-probes.tsv`
- [ ] 六个短凭据 fixture 全部被扫描器检出
- [ ] durable probe evidence 自身不匹配敏感模式
- [ ] exact Mongo candidate 通过全目录 sensitive scan 并生成可复核 manifest

## References

- `scripts/verify-v934-external-mongo.sh`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
