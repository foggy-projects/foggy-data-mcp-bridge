---
evidence_type: replacement-coverage-audit
version: 9.3.4
target: BUG-STEP5-PACKAGE-SUBPHASE-RECEIPT
cdiag_commit: dd2ccde8e97c4dfe88e9a06141280a1d747ac737
cfreeze_commit: f7da93c1ad79be2dede5494b99990092ba110071
formal_run_id: step4-coverage-20260720-formal-r43
reviewed_at: 2026-07-21
verdict: pass
critical_gaps: 0
major_gaps: 0
---

# r43 replacement coverage audit

The independent audit passed AC-1 through AC-7 with zero critical and major
gaps. It binds fresh r42 diagnostic evidence to direct-child Cfreeze `f7da…`
and independent r43 formal evidence; no historical r40/r41 result is reused.

Reviewed thresholds are exact observed values: line `54624/76830`, branch
`26112/44870`, twelve frozen critical identities, and only the approved
`NamespaceScope.branch` zero-total N/A. The r43 formal final is bound to
`formal-passed`, exit `0`, 773 required reports, 59 structural reports, and
5707 testcase nodes. Step 4/Step 6 closure, permitted paths, and fresh-source
identity checks passed. This audit is not version signoff.
