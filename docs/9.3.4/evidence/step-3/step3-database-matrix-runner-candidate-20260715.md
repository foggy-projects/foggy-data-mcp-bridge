---
doc_role: diagnostic_evidence
doc_purpose: Record the run-local Step 3 database matrix runner/collector candidate and its evidence boundary.
version: 9.3.4
step: 3
status: candidate-not-authority
created_at: 2026-07-15
updated_at: 2026-07-15
---

# Step 3 数据库矩阵 runner/collector candidate

## Decision

本批结论为 `candidate-not-authority`。五库 runner、fresh/run-scoped provision、精确
report collector、运行态 cell 绑定和 run-local `verify-final` 已实现；最终冻结字节下的
真实 SQLite cell 为 `5 reports / 50 testcase / F0/E0/S0`。当前主机的四个冻结端口均被
长期诊断容器占用，完整 runner 在任何编译、正向测试或新容器创建前以
`E_PORT_OWNED` fail closed，因此本批没有、也不声称拥有同一 run 的 fresh 五库
`29/370` authority。

Step 3 仍为 `in-progress`。16 个 required external execution、四外库 fresh-storage
实跑、数据库 identity/fixture/provision 动态负向和 1 个 optional LLM disposition 尚未
闭合。

## Frozen Inputs

- Git HEAD：`79047c2d6b8a54521c9d73c70bd52ba44bbc1f99`
- database matrix contract：
  `def0693d31c080858905d662c06638ef9131fd6eebf2e7e7904ea95f5de8b381`
- authority 66-file manifest：
  `de67a7daf7935b7dc9b36c5a93010f4f0c3147ad85b113aabdfd7c2b7d83c555`
- report tool：
  `fc68f1ac46e4636110478efd1c4bc90cfce29f1c5943cd626080bf04d1e68828`
- top runner：
  `c3438faeca51d8a4f2a7c2c69c8f8f10294d914a38ee11b2310c2446a8bf631f`
- protected source hash：
  `1e6c2ce5faea0f0aa32586a3943f451839a45eef0a1e94e9d5c54d52b7113415`
- source amendment：
  `38d92c2250252b0cd4eae296ec6bf4d36081f0968bf6c0a2f3faf039ce32ef0c`
- confirmed Step 2 successor manifest：
  `4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919`

## Implemented Contract

- `database-matrix-contract.json` 精确冻结
  `5 cells / 7 variants / 29 reports / 370 testcase`，并逐 execution key 对齐
  confirmed `deferred-step3.tsv` 的 database
  subset。
- authority 清单覆盖当前 reactor POM、受保护 production/test/resource trees、五库
  profile/fixture、Compose override、runner、collector、provisioner 与 SQLite tool；
  runner 在执行前后重算受保护树，并在编译前删除 active-reactor stale class trees。
- 每个外库 cell 使用唯一 Compose project/container/volume/network，固定 loopback
  endpoint，核验 image ID/ref/repo digest、Compose labels、volume creation time、数据库
  product/version/catalog/schema identity、canonical fixture 两次幂等和 callback 前后不变；
  cleanup 只删除该 project label 所属资源并证明无残留。
- SQLite 使用 run-scoped physical file；JDBC JAR 在执行前后按字节核验，fixture
  before/after 完全一致，最后删除 database、WAL、SHM、journal，并把 symlink/special
  residue 视为失败。
- collector 只接受同一 outer marker 下的 fresh variant marker/raw XML；最终 bundle
  原样复制完整 variant evidence subtree，并对 source manifest、markers、raw reports、
  cells、metrics 的 path/SHA-256/mtime/size、exact file set 和 tree hash 做双向验证。
- runner 日志使用可等待的 named-pipe tee；敏感扫描在同步 flush 后执行，`rg=0/1/>1`
  分别为命中/清洁/扫描失败。INT/TERM/HUP 与 stopped tee 均有超时收口并写 durable
  failed status；summary 精确匹配 contract 的有序 report-negative tuple。

## Formal Static and Synthetic Verification

```text
py_compile                                       PASS
bash -n                                          PASS
contract validate                               5/7/29/370 PASS
protected source hash                           1e6c2ce5... PASS
report/final-bundle negative probes             14/14 PASS
authority SHA-256                               66/66 PASS
test-compile                                    PASS
```

report-negative TSV SHA-256：
`a465cd8c71492714604a15e896b4e10d6d7d67aa55dab80ffe0d8eb964b11371`。
14 个 probe 只覆盖 missing/extra/duplicate/count/outcome/stale/cross-run/source drift/
final raw tamper 等 report/final-bundle 契约；它们不得被描述为完整数据库状态负向。

独立复核另构造完整 synthetic final bundle：baseline 通过；删除 raw、增加额外文件、
篡改 cell cleanup、删除 source manifest 均由 `verify-final` 拒绝。

## Real Run-scoped SQLite Evidence

- run：`sqlite-collector-candidate-r3`
- outer marker SHA-256：
  `f4c783b3da1ac14540f84921ae758273116a93a9328a921d46b5197fd4be1c5f`
- variant marker SHA-256：
  `a9832bc0ba8dc1b925ba62f3afc4f25ee91cc9808f16fda025683854b0996ee9`
- report manifest SHA-256：
  `98f7f87166f6fcfda20e75358a0a43df831c325a5d00e8362a87550d76c0d5e4`
- result：`5 reports / 50 tests / F0/E0/S0`
- testcase split：Pivot parity `23`、MultiDatabase `18`、preflight `1`、cascade
  `7`、QueryFacade parity `1`
- canonical fixture SHA-256：
  `70b1a5d755bd781004cd35abd8d11525a997b857335165e0b0e2754ae38950cf`
- SQLite JDBC JAR before/after：
  `53174d76087bb73cc29db9c02766fb921fd7fc652f7952f3609e0018e3dd5ded`
- resource/verification/cleanup evidence SHA-256：
  `9272103ebd4603cc15bc215c3a9d4bc204a8a37c8c3fa7065143a2b01fc02352` /
  `cd2f65a1bab72b39dc6b4d591f472a187b2e47818e82c6b596f76f0abddb7c00` /
  `05ef24b62b5ff6a76250a42779938118d211064b6615e296330ce49e213a44f6`
- terminal cleanup：`database.sqlite*` residue=`0`

该 run 证明最终 contract 下的 SQLite physical-file、collector freshness/cardinality、
fixture/JAR identity 和 cleanup；它只有一个真实 cell，不能与 synthetic 或长期容器结果
拼接成五库绿色。

## Fail-closed Full-run Attempt

- run：`matrix-port-owned-negative-r3`
- process exit：`1`
- durable status SHA-256：
  `1236e42e8bab3f4d7f440f3ad904fd6300b9b0b045b964c4fe16a7c9065afa1f`
- last phase：`preflight-mysql57`
- stable reason：`E_PORT_OWNED`（`127.0.0.1:13306` 已占用）
- summary：absent
- positive Maven lane / new container：未执行/未创建
- run-owned container/volume/network：`0/0/0`
- preflight cleanup：passed

该失败证明 top runner 不会为了获得绿色而复用或停止长期容器，也不会在不满足 fresh
endpoint 前提时继续执行 SQLite/外库正向 lane。

## Evidence Boundary and Open Risks

1. 当前 final bundle 是 `run-local`，不是可搬迁 archive。独立复核把整棵 run 搬到新
   路径后，SQLite absolute database/JDBC origin coordinate 会触发
   `E_CELL_EVIDENCE`；不保留纳秒 mtime 的普通 copy/ZIP 提取会触发
   `E_EVIDENCE_MARKER`。Step 5 前必须把 origin path 与 verification location 分离，
   并决定可移植 archive 的 freshness/mtime 规则；不得把本 candidate 直接上传后冒充
   immutable evidence。
2. 当前 14/14 是 report/final-bundle negatives；unavailable、wrong kind/major/catalog/
   schema/sentinel、fixture mutation、provision cleanup/signal 等数据库状态负向仍待实现。
3. 四外库必须在冻结端口空闲的 clean host 上以同一 run 实际完成 fresh volume、
   `29/370/F0/E0/S0`、5/5 cleanup 和 sensitive scan，才能形成 database subset authority。
4. Step 2 deferred 的另 16 个 required external execution 仍须按
   Redis→Mongo/DataViewer→MCP/MySQL57→Vector 消费；1 个 LLM execution 保持 reviewed
   optional，不计 required green。
5. Addon DDL/refresh lifecycle 属于独立 workitem，当前候选改动未混入本批，且其
   COUNT/formula/SQLite/default/watermark/TM normalization blocker 尚未解除。

## Next Decision

本 runner/collector candidate 可合入，作为 clean-host full database run 和 external
matrix 的前置实现；Step 3 exit=`not passed`，Step 4 不得开始。下一批优先补数据库状态
负向并消费 16 个 required external execution，同时为可用 clean host 准备同一 commit
的五库 fresh replay。
