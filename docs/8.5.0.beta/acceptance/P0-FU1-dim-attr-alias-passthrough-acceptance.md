---
acceptance_scope: feature
version: 8.5.0.beta
target: P0-FU1-dim-attr-alias-passthrough
status: signed-off
decision: accepted
signed_off_by: Codex
signed_off_at: 2026-04-28
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 6
doc_role: feature-acceptance
doc_purpose: Java FU-1 dim$attr user alias passthrough signoff
---

# Feature Acceptance

## Background

8.4.0.beta P0-v13 裸 dimension 收紧签收时，A1-3 `dim$attr AS userAlias` 被 deferred 为 FU-1：Java SQL gen 层仍拒绝维度属性 plain alias，而 Python `v1.7` T4 已支持用户 alias 覆盖 TM dimension caption/alias。

本次 8.5.0.beta FU-1 目标是让 Java semantic query / Compose F4 两条路径都接受 `dim$attr AS userAlias`，并在结果列名中保留用户 alias。

## Acceptance Basis

- `docs/8.5.0.beta/P0-FU1-dim-attr-alias-passthrough-需求.md`
- `docs/8.5.0.beta/P0-FU1-dim-attr-alias-passthrough-progress.md`
- `docs/8.4.0.beta/acceptance/P0-v13-bare-dimension-tightening-acceptance.md`
- Python parity reference: `foggy-data-mcp-bridge-python` v1.7 T4 / commit `59176f2`

## Checklist

- [x] A1 · `product$caption AS userAlias` semantic query 成功，响应列名为 `userAlias`
- [x] A2 · F4 `{field:"product$id", as:"productId"}` 成功，真实 SQL 结果列名为 `productId`
- [x] A3 · `foggy-dataset-model` sqlite lane 全绿，0 regression
- [x] A4 · 原 F4 dim suffix rejection 测试已翻转并通过
- [x] A5 · F4 `f4PlainAliasMetadataIsolation` 未退化
- [x] A6 · Java 行为与 Python `v1.7` T4 对齐

## Evidence

- Code · `InlineExpressionPreprocessStep.trySynthesizePlainAlias` 删除 `COLUMN_DIMENSION_ALIAS_NOT_SUPPORTED` 分支，允许单级 `dim$attr` 进入 PLAIN_ALIAS calc-field 合成路径。
- Test · `StrictBareDimensionRejectionTest.t4_userAliasOverridesTmCaptionOnDimAttr` 从 disabled 改为执行断言，验证 `product$caption AS userAlias` 返回 `userAlias`。
- Test · `F4ColumnObjectIntegrationTest.f4PlainAliasOnDimensionSuffixUsesUserAlias` 验证 `{field:"product$id", as:"productId"}` 与手写 SQL 结果一致。
- Regression · `mvn -pl foggy-dataset-model "-Dtest=StrictBareDimensionRejectionTest,F4ColumnObjectIntegrationTest" test` → 23 tests / 0 failures / 0 errors / 0 skipped。
- Regression · `mvn -pl foggy-dataset-model test` → 1857 tests / 0 failures / 0 errors / 1 skipped。
- Scope guard · `product AS p` 仍由 T2 验证为 `COLUMN_FIELD_NOT_FOUND` + dim-aware hint，FU-2 行为未被放宽。

## Failed Items

None.

## Risks / Open Items

- blocking_items: none
- open_risks: none
- follow_up_required: no

## Final Decision

Decision: `accepted`.

FU-1 的目标行为、F4 镜像路径、既有裸 dim 拒绝路径和模块级 sqlite lane 均已验证通过。该功能可以关闭 8.4.0.beta P0-v13 acceptance 中的 A1-3 / FU-1 缺口。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex
- signed_off_at: 2026-04-28
- acceptance_record: `docs/8.5.0.beta/acceptance/P0-FU1-dim-attr-alias-passthrough-acceptance.md`
- blocking_items: none
- follow_up_required: no
