---
doc_type: execution-evidence
version: 9.3.4
step: 5
planned_run_id: step5-local-rehearsal-20260723-no-ci-fullclone-r2
preflight_commit: 22e737e09bb3e283aa21894d3f0b92ef205ff6b9
status: passed
decision: ACTIVATE_ONE_REPLACEMENT_REHEARSAL
created_at: 2026-07-23
---

# Step 5 no-CI full-clone r2 preflight

## Decision

- verdict: `passed / replacement attempt not yet consumed`
- authorized_next_action: 提交 docs-only activation，推送后启动 exactly one
  replacement rehearsal。
- unchanged_scope: no-CI；不启用 Actions，不修改 sealed runner/tool/manifest，
  不进入 Step 7、merge、tag、release 或 publish。

## Clone and Source Identity

- private parent mode: `0700`
- clone kind: full clone；`.git` 为 real directory
- shallow: `false`
- origin: canonical SSH repository
- clean HEAD:
  `22e737e09bb3e283aa21894d3f0b92ef205ff6b9`
- remote branch and PR #124 head: exact match

Direct pre-activation runtime-source receipt：

- command/status: `scan-runtime-source / passed`
- module count: `13`
- file count: `1411`
- bytes: `10757069`
- set SHA-256:
  `22670362fff8f063791129e1e875768d6b1b44286ec8237591f458b5486b07f8`
- receipt SHA-256:
  `5445b2593e5c6f3d929a7bbf2a7dc6ab3eb53cd6ea8c7c4c354d1475847019f9`
- contract SHA-256:
  `382a34e0c3ed81d0d39828bae73cf802b7be41fb0e681c126ead4ad5946f8ec0`

## Frozen Tooling and Focused Checks

- Step 4/5/6 manifests: exact `63/8/16`
- shell syntax: `12` files passed
- Python compile: `21` files passed
- artifact self-test: passed；negative cases=`105`
- package negative: passed；cases=`120`
- pointer negative: passed；cases=`5`

Focused output SHA-256：

- artifact self-test:
  `97e02cd77d42f464b15a22e9f777165777fe82b6b3dd76d0d74eca48331bdcf0`
- package negative:
  `354d99b6b996a4383db666bdcafcde279e2037162b44131426bae5ca6b006137`
- pointer negative:
  `e82ac5538a8b7496b9e924c3568b9805dfb1788fb9c9fe86fd626b22d2562fe8`

An observer-only JSON summary command initially treated numeric `.cases` as
an array and exited `5` after all three tools had completed. Direct schema
validation confirmed each tool's own `status=passed` and exact case count；
this did not start or consume the formal replacement attempt。

## Runtime Base and Environment

- OCI index:
  `sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57`
- linux/amd64 manifest and local image identity:
  `sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52`
- config:
  `sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc`
- required ports: `13306/13307/15432/11433/17017/16379/19530` all free
- candidate pointer: absent
- final authority pointer: absent
- runs root: absent
- competing release runner: none
- run-owned Docker resources: none
- Maven/JVM controls: sanitized launch environment verified
- ambient forbidden Git controls: none
- GitHub Actions: `enabled=false`

## Attempt Boundary

No Step 4, Maven, database, Docker package/image or release runner was
started by this preflight. The replacement attempt remains available exactly
once and becomes consumable only after the activation commit is pushed and
PR #124 points to it。
