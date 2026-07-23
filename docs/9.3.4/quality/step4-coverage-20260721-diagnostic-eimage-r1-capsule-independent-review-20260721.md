---
evidence_type: independent-git-safe-capsule-review
version: 1
step: 4
run_id: step4-coverage-20260721-diagnostic-eimage-r1
tested_commit_binding: verified-without-identifier-disclosure
source_seal_binding: verified-without-digest-disclosure
tracked_source_entries: 4212
capsule_archive_path: docs/9.3.4/evidence/step-4/step4-coverage-20260721-diagnostic-eimage-r1-portable-capsule.tar.gz
capsule_manifest_path: docs/9.3.4/evidence/step-4/step4-coverage-20260721-diagnostic-eimage-r1-portable-capsule.manifest.json
capsule_archive_binding: verified-without-digest-disclosure
capsule_manifest_status: sealed
archive_member_count: 3
reviewer: codex-independent-reviewer-b
reviewed_at: 2026-07-21T09:09:01Z
verdict: pass
P0: 0
P1: 0
P2: 0
---

# eimage-r1 Git-safe capsule independent review

## 范围

本次仅独立审阅 `step4-coverage-20260721-diagnostic-eimage-r1` 的 portable
capsule 与其 manifest。审阅未涵盖阈值候选、Cfreeze、正式验证、发布指针或版本签收。

## 结果

`frozen_diagnostic_capsule_tool.py verify` 在预期 run ID 下通过。该安全验证器确认
archive/manifest 绑定、gzip 与 tar framing、固定成员顺序和元数据、成员摘要绑定、
canonical attestation，以及 attestation 与 JaCoCo XML 的绑定；验证器内部仅在隔离临时
目录中处理白名单成员。

manifest 状态为 `sealed`，archive 只有 3 个允许成员：证据目录、受限诊断 attestation
和 JaCoCo XML。其 retention 明确禁止 runtime closure、execution bytes 与
unstructured output。未发现额外成员、链接、运行时元数据或非白名单保留内容。

独立的 Git-safe 重算确认：capsule 绑定当前已提交的测试提交，且按受控 tracked-source
payload 算法重算的 4212 个源条目与 capsule source seal 一致。比较只记录通过状态和
计数，不披露提交标识或摘要值。

审阅过程中没有手动解包或读取 XML、原始执行文件、日志或其他运行时内容；未运行
Docker、Maven 或全量 gate。

## 结论

该 capsule 的 Git-safe 封装和可移植性证据通过独立复核，P0/P1/P2 均为 0。本结论不构成
formal validation、candidate promotion 或版本签收。
