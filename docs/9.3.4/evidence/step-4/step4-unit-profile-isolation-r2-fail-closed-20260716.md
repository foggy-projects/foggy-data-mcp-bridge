---
evidence_type: failed-unit-remediation-diagnostic
version: 9.3.4
step: 4
run_id: step4-unit-fixture-quality-20260716-r2
tested_commit: a603f839a98d99b2d7beb8379f76b4d85539328c
status: failed
failure_class: unit-datasource-profile-isolation
decision: excluded-from-step4-exit
recorded_at: 2026-07-16
---

# Step 4 Unit profile isolation r2 fail-closed evidence

## Decision

`step4-unit-fixture-quality-20260716-r2` 是 run-owned MySQL 5.7 remediation 的第二次
完整 Unit authority 尝试，不是 Step 4 all-lane r8。该 run 在 clean commit
`a603f839a98d99b2d7beb8379f76b4d85539328c` 上建立 source seal，并先完成真实 fixture
lifecycle negatives=`5/5`；随后主 fixture 成功启动，但唯一 Surefire reactor Maven 调用在
`foggy-dataset-model` 以 `3,115 tests / 0 failures / 631 errors / 0 skipped` 失败。

首个共同根因为：callback 通过 JVM 级 `-Dspring.datasource.*` 把 MySQL URL/credential
覆盖到完整 reactor，而 `foggy-dataset-model` 的默认 `sqlite` profile 仍保留
`org.sqlite.JDBC`。Spring 因此组合出 SQLite driver + MySQL URL，并明确报错：

`Driver org.sqlite.JDBC claims to not accept jdbcUrl, jdbc:mysql://127.0.0.1:13306/...`

r2 正确 fail closed，没有发布 Unit final report manifest、Unit summary、fixture manifest、
source-after 或任何 Step 4 aggregate/threshold 结果。其不完整 XML/exec、lifecycle receipt 和
fixture before-only evidence 不得与其他 run 拼接；r2 的决定是
`excluded/non-reusable`。

## Immutable result

- run：`step4-unit-fixture-quality-20260716-r2`；
- tested commit：`a603f839a98d99b2d7beb8379f76b4d85539328c`；
- started/finished：`2026-07-16T15:07:16Z`—`2026-07-16T15:14:47Z`；
- source-before：`3,981` files，SHA-256=
  `087d074f3497aff3fe305806f82b4d62ff41cdd4d3b26556e58f034138b14c2c`；
- source-after：absent；outer last phase=`unit-mysql57-provision`，exit=`1`；
- lifecycle negatives：`5/5`，覆盖 INT/TERM/HUP、callback failure、leader-kill fallback；
- Surefire failure：`foggy-dataset-model=3,115/F0E631S0`；
- Unit final report manifest/summary、fixture manifest、after snapshot、connection receipt：absent；
- child fixture=`unit-mysql57-90da4977dc197f81`，project=
  `v934db-mysql57-4a37a6c06f3b`；
- child cleanup：container/volume/network=`0/0/0`，`13306=free`；
- evidence window 结束后，原 demo MySQL exact container
  `0c50bf7e8684950ee1f9c3c257d3b2a8ba1ace8fbc85f2aa57fd35ff0fe5c166`
  已按原 identity 恢复为 `running/healthy`。

关键 artifact SHA-256：

- `run-context.json`：
  `68417a7a5db65381b589f36c1944581e2b594f966bc6acdf1345e47445655c93`；
- `run-status.env`：
  `d19d94bcd319df771f80a831027ce16a8b7eafc5ecbbdc8acb8a133d5a759ff0`；
- `run.log`：
  `00a7ab17ade617427462c1e14f00be67590522368f5e3a2e807b82be41920ee4`；
- `mysql57-fixture-lifecycle-negative.json`：
  `6fe6615616cf8b153c8cf97e1e7c1a9fff565be258e847a962f93517431810ca`；
- child `status.env` / `cleanup.env`：
  `9e18f0484ec60523b9ade76bb544f0dcb1c7c1b61489bac32ee2040a6dac635c` /
  `fd7cce8822d8b8b70ba1e6c1301327c981dfaeba0b758b99d7e67a99c59cfaa5`；
- child `unit-fixture-status.env`：
  `24ecfce6b50ababe8afe9d6f7e5a411642ad6687de34707c4dc3e88917bf704a`；
- Unit before-only schema snapshot：
  `93a9a8d51c8e8188173ce905965293adbd163e2d1e21c12d2f1f8637bbe4da0d`。

## Root cause and minimal repair

run-owned database lifecycle、image/database/port identity、schema setup 与 cleanup 并未失败。
缺陷位于 datasource 适配边界：完整 24-module reactor 需要 fresh 执行，但只有
`foggy-dataset/src/test/resources/application.yml` 声明已知 MySQL consumer。把通用
`spring.datasource.*` 作为整条 Maven 命令的 system property 会覆盖其他模块自己的
SQLite/profile 语义，因而不能作为 full-lane replacement 的可信实现。

最小修复保持唯一 Maven invocation 和完整 Unit replacement 不变：

1. `foggy-dataset` test resource 的 datasource 与 `spring.test` 三元组改为
   `V934_UNIT_MYSQL57_URL/USERNAME/PASSWORD` placeholder，并保留原默认值，普通本地测试
   兼容不变；
2. callback 只在受控 Maven 子进程环境中注入这三个专用 key，移除全部全局
   `-Dspring.datasource.*` / `-Dspring.test.*`；其他模块的 SQLite 和显式 profile 不受影响；
3. outer Unit runner 与 callback 双层拒绝 ambient 三个专用 key、Spring/custom key 的
   underscore/dotted/hyphen 变体、Maven/JVM datasource override，以及 `@argfile`、
   `VMOptionsFile`、`javaagent/agentlib/agentpath` 间接注入；机器契约同时要求三个 key 只被
   这一个 tracked adapter 消费；
4. adapter path/hash 进入 fixture contract、fixture manifest binding、top exact manifest 与
   successor declared amendment；consumer inventory 使用 scrubbed Git environment 枚举
   `HEAD` tree 并设置 no-replace object；adapter 漂移、ambient key、global Spring override
   与 option indirection 均有 fail-closed negative；
5. schema before/after 由 root control connection 读取；`init_connect` receipt 收集全部
   Unit Maven observation window 内的 non-super connection：root 先 configure
   `init_connect`，Maven 返回后由同一 root batch 先 disable 再按 `connection_id` SELECT；
   receipt 保存有序 `connection_id + observed user`，要求非空且全部属于 run-owned
   `v934_unit`。callback 返回后的 provisioner `foggy` 控制面连接位于该窗口外。

该修复不放宽 ambient listener 禁令，不复用 demo 数据库，不拆分 Maven invocation，也不改变
`681 positive + 55 structural / 4,941 testcase`、`23 exec / 48 sessions` 或 Step 2
identity/cardinality 的结构边界。

## Static closure after repair

以下只证明修复后的静态/fail-closed 契约闭合，不是正式实现质量、fresh Unit 或 r8 通过：

- Unit negatives=`36/36`：原 fixture/manifest=`20/20`、connection receipt typed=`7/7`、
  atomic publisher=`3/3`、profile isolation=`6/6`；negative receipt schema tamper=`4/4`；
- real lifecycle=`5/5`；report inventory negatives=`30/30`；
- top exact manifest=`60/60` / SHA-256=
  `6056a930a1d0deec59767ffc0239485ae42b4067e343c0d68e3f899c3440e587`；
- successor exact manifest=`14/14` / SHA-256=
  `acb580e92a72eb407f31f5d6f9a8139a3509f3a0bfbf58537922465f4086a112`；
- coverage contract diagnostic/formal SHA-256=
  `c062219a6335ae41330c6d5924d6fce60941c5d168b361081fbb41df77428477` /
  `341991d6b5a15d19cdb9e0de70a8cc6ace29480227596c413c07e6bf7fdbc73d`；
- coverage tool SHA-256=
  `27afd37350fa7f1646fba4be59791ec6bdec94fe57e0cdfecc2a08e0f43f2f18`；
- fixture contract/tool SHA-256=
  `7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a` /
  `cc19390ce6c0cfb307b7632dbe4e25540b1e4d49d11ec1512739f6724646d345`；
- Unit runner SHA-256=
  `45536c0a969731f6b7c87acecdb225b13a8a0fca45a9a04c9cdfb2173fc60c66`；
- declared amendments=`18` / SHA-256=
  `8e21b8527f290061361ef0b8fbf084d51b2536ef479e1f70e45488f996090bfc`；
- overlay contract/tool SHA-256=
  `84d09bfc333bb40d8ef830979734933717555845cebe9943f70ff7087a9a482d` /
  `1fea2816504519b7e7f1dc6839744ee943a9a4bf3feb783375e21e935da63d31`；
- datasource adapter SHA-256=
  `9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256`。

## Next gate

- [x] r2 failure、source seal、root cause、cleanup 与 demo restoration 已封存；
- [x] profile-scoped adapter、ambient double rejection、restricted exclusive receipt 与静态
      negative 已实现并复验；
- [ ] fresh Unit remediation r3 通过完整 `681+55 / 4,941 / F0E0S0`；
- [ ] 正式 remediation implementation quality 通过；
- [ ] commit/push 并证明 clean `HEAD == origin/main`；
- [ ] fresh Step 4 all-lane r8、threshold freeze、formal 与最终质量通过。

当前 `can_enter_coverage_audit=no`。fresh Unit r3、正式 remediation quality、commit/push、
fresh r8 均 pending；Step 5 保持关闭。
