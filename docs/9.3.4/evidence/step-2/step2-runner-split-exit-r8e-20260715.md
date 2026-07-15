---
doc_role: step-exit-evidence
doc_purpose: Record the signal-safe authoritative Surefire/Failsafe Step 2 execution against the confirmed r8e successor.
version: 9.3.4
step: 2
status: passed
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 2 runner split exit evidence — r8e

## Decision

`PASS / Step 2 exit satisfied`。confirmed r8e successor、Surefire Unit authority 与
Failsafe Integration authority 均完整执行并以 durable `completed / exit=0 / passed`
结束；两个 runner 的 execution/report sets 互斥且并集 exact 覆盖 confirmed Step 2
positive inventory：

- positive executions：`724 = 677 Surefire + 47 Failsafe`；
- structural reports：`59 = 55 Surefire + 4 Failsafe`；
- raw reports：`783 = 732 Surefire + 51 Failsafe`；
- testcase nodes：`5,205 = 4,890 Surefire + 315 Failsafe`；
- failures/errors/skipped：`0/0/0`；
- report mutation probes：两条 authority 各 `20/20 passed`；
- successor mutation probes：`33/33 passed`；
- source before/after：均为
  `12749d1fb9d37af04b8a3dd80ac49ea0fcc177309edcc0c49645a2c2c19a1a53`。

Step 2 不消费 `46` 个 confirmed Step 3 deferred Failsafe executions；它们仍为
`deferred-to-step3`，不能由本记录提前标 pass。

Evidence provenance：

- git head：`9f5428d8d15d08457d2d2d57296256178c224f5d`；
- successor raw root：
  `target/v934-step2-successor/runs/step2-candidate-r8e-20260715/`；
- Unit raw root：`target/v934-step2-unit/runs/step2-unit-r8e-20260715/`；
- Integration raw root：
  `target/v934-step2-integration/runs/step2-it-r8e-20260715/`；
- Step 2 不生成 evidence archive；archive/root digest 属于 Step 5/7。当前三条 run root
  分别由 summary、durable status、final/canonical manifest 与 raw artifact hashes 封存。

## Confirmed successor basis

Authority runs 均绑定 `step2-candidate-r8e-20260715` 的 confirmed generation：

| Evidence | SHA-256 |
|---|---|
| confirmed freeze | `44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6` |
| confirmed manifest | `4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919` |
| confirmed summary | `f6b80aa5f48c6f32aaa99336823dd00d183d75a096767c74f7de2c21c1ac4b75` |
| Step 2 required execution set | `42a9467cdbcfbed5ed54d0bdfa276d92daa7fa2c83795cd13a21df931d0fc1d0` |
| structural report set | `dd885e54c1ef3dc01d2a4cb2b364f6333b78d362eb7f5b9a7d32968516809e0f` |
| Step 3 deferred set | `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601` |
| runner contract | `a5a7364fe75af2668c2c85989295b1448834dc526604175f7614f93d30e0376a` |

Successor inventory 为 `532 sources / 820 discovery rows / 770 positive executions /
59 structural reports / 519 predecessor edges / 2,395 classpath entries`。双路复核、
三类实际信号探针和 r8d splice rejection 见
[step2-successor-r8e-independent-review-20260715.md](step2-successor-r8e-independent-review-20260715.md)。

## Surefire Unit authority

Run：`step2-unit-r8e-20260715`，时间
`2026-07-15T00:07:23Z` 至 `2026-07-15T00:17:44Z`。

| Field | Value |
|---|---|
| runner / variant | `surefire / unit` |
| execution / structural / raw | `677 / 55 / 732` |
| tests / testcase nodes | `4,890 / 4,890` |
| failures / errors / skipped | `0 / 0 / 0` |
| outer marker | `d36ebf8fc1f8b1bedcea3335ec80a229703655ace1497a97a2923255bf96840d` |
| final report manifest | `b66d06023a5bf2192c32551d0e273314f295928c3ef744fbd778a0009a07f869` |
| run status | `d66eb17f3010a32cd82b85471540db146d72ddf9a4833f9523575a2fcf8b3a11` |
| summary | `55e9e8b67301aa24a743dfa56fd1f2c01ca9bd94c3889f11093c281d6ef2565a` |
| run log | `21f0c63f6f5d864e1f3809545c80ee0de8ff63682763bc98f2fb050578c8ac96` |
| report negatives | `e38ad3f408e385b6ba0ea36aa303e7b8ed4b20b7b6b8059f283b624d28241756`；`20/20` |

## Failsafe Integration authority

Run：`step2-it-r8e-20260715`，时间
`2026-07-15T00:17:55Z` 至 `2026-07-15T00:28:08Z`。

| Field | Value |
|---|---|
| runner | `failsafe` |
| variants | `caffeine-sqlite, hermetic, sqlite-broad, sqlite-harness, sqlite-lifecycle, sqlite-refresh` |
| execution / structural / raw | `47 / 4 / 51` |
| tests / testcase nodes | `315 / 315` |
| failures / errors / skipped | `0 / 0 / 0` |
| outer marker | `c28ef90959eb6c3d5f41c66dec4ad88ced1b3f826e6ecb9291e0cb737f3ec5b5` |
| final report manifest | `351606801eeddb7e1be3b8eaeb6b20d468a3262c70fe7d036b4af971dd8dbb5c` |
| run status | `4942b0f99d6c3d049e72ebba97ad30bb1c4951e9212dad4be54d310a2bf42b68` |
| summary | `0ee6c45907a9b37b4dda726bb7ba38030a57503a40e52995f26d397ba0610e83` |
| run log | `f22861e9deca33c98b6319071887324f14c95696d3cf2974f3078c8e6cc736da` |
| report negatives | `e38ad3f408e385b6ba0ea36aa303e7b8ed4b20b7b6b8059f283b624d28241756`；`20/20` |

Variant exact results：

| Variant | Executions | Structural | Tests | F/E/S |
|---|---:|---:|---:|---:|
| `caffeine-sqlite` | 1 | 0 | 2 | 0/0/0 |
| `hermetic` | 1 | 0 | 3 | 0/0/0 |
| `sqlite-broad` | 42 | 4 | 303 | 0/0/0 |
| `sqlite-harness` | 1 | 0 | 1 | 0/0/0 |
| `sqlite-lifecycle` | 1 | 0 | 4 | 0/0/0 |
| `sqlite-refresh` | 1 | 0 | 2 | 0/0/0 |

## Signal fail-closed and exclusions

- shared runner helper 将 `INT/TERM/HUP` 精确映射为 `130/143/129`；动态注入后
  process/durable exit 一致，status=`failed` 且 summary absent；
- success path 先 ignore 三类信号，再移除 `EXIT` trap；finalizer 屏蔽重入；
- Unit、Integration 与 successor 共用 authority exclusive lock、publish CAS 与
  schema-2 signal contract；
- report verifier 拒绝 cross-run splice、partial testcase、positive-zero、structural-
  nonzero、stale/missing/extra XML 与 source/successor identity 漂移；
- `step2-candidate-r8d-20260715`、`step2-unit-r8-20260715`、
  `step2-it-r8-20260715` 因 signal fail-open 作废，禁止拼入当前 authority；
- 本次未启动 MongoDB/Redis/PostgreSQL/MySQL/SQL Server 等 Step 3 external fixtures，
  对应 `46` executions 仍由 Step 3 负责。

## Exit

Step 2 的 runner split、35 个受控 rename、typed structural reports、actual Unit/IT、
predecessor mapping、source immutability、negative probes 与 signal-safe durable status
全部满足 contract。Step 2=`passed`，Step 3=`ready`；9.3.4 仍为 `in-progress`，9.3.5
仍为 `queued`。
