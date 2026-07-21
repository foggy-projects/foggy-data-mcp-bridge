---
evidence_type: formal-pass
version: 9.3.4
step: 4
run_id: step4-coverage-20260720-formal-r43
tested_commit: f7da93c1ad79be2dede5494b99990092ba110071
cdiag_commit: dd2ccde8e97c4dfe88e9a06141280a1d747ac737
source_sha256: f3d54c45a2e0e101c6f8c67a88c4b9adea5ea330d60061b8bf91e57d1944a271
coverage_gate_sha256: 36e29c5ca9d5bd778bb7160b172f097894f8bb25f2eda9bb5bda23a7e5b3d098
candidate_manifest_sha256: 612465093aed3f2bba01f74d4d76671a11b7389c208f4ddf3c7b789bb00d6f9c
final_manifest_sha256: b90ef00dd1908329e4f12ede1b20cc7618e4fd9ce903252db8f411babea75cad
status: formal-passed
exit_code: 0
---

# Step 4 formal r43 pass

r43 is the fresh formal run of the sole direct-child Cfreeze
`f7da93c1ad79be2dede5494b99990092ba110071`, whose only parent is Cdiag
`dd2ccde8e97c4dfe88e9a06141280a1d747ac737`. Its structured terminal status
is `formal-passed` with exit code `0`; source-before and source-after bind the
same SHA-256 listed above.

The formal summary confirms `threshold_status=confirmed`, required reports
`773`, structural reports `59`, testcase nodes `5707`, Addon reports/cases
`2/6`, and `model_external_gate=passed`. Independent controlled verification
of the coverage gate, candidate, final artifact, and frozen diagnostic replay
passed. This is formal evidence only: it does not publish a release, update a
final authority pointer, or sign off version 9.3.4.
