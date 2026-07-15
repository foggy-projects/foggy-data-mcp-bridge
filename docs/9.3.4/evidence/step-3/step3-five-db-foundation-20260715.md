---
doc_role: implementation_evidence
doc_purpose: Record the diagnostic foundation for the 9.3.4 Step 3 five-database identity and QueryFacade parity contract.
version: 9.3.4
step: 3
status: diagnostic-passed
authority_status: not-authority
created_at: 2026-07-15
---

# Step 3 五数据库 Identity / Parity 基础证据

## Decision

五库基础实现已达到 `diagnostic-passed`：SQLite、MySQL 5.7、MySQL 8、PostgreSQL
15、SQL Server 2022 均完成一个 preflight testcase 和一个
QueryFacade/native parity testcase，合计 `10/F0/E0/S0`。

本记录不是 Step 3 exit，也不是 release authority。29 个数据库 execution、16 个
required external execution、required negative probes、run-owned raw archive 与 exact
report collector 尚未完成，因此 Step 3 保持 `in-progress`。

## Confirmed Input

- predecessor commit：`a0f3a2db83365951a08b65f36765abf2920c6369`
- Step 2 deferred manifest SHA-256：
  `89190db9370fe3117d5316c72835efffa577d132d39cef9b5d9ae3558afa7601`
- deferred inventory：`46 = 45 required + 1 optional LLM`
- database contract：`scripts/v934/database-contract.tsv`

## Implementation

- 保持 9.3.3 共用 `docker-compose.yml` 的 tag/container contract 不变；新增
  `docker-compose-v934.yml` 覆盖层，把 MySQL 5.7、MySQL 8、PostgreSQL 15、SQL
  Server 2022 固定到冻结 OCI image ID/digest。当前 fixture manager 校验实际
  container `.Image`；最终 Step 3 runner 还必须显式加载覆盖层，不能只读取 compose
  文本。
- 五库建立同构 `v934_test_sentinel(sentinel_key, sentinel_value)`；canonical
  `v934_test_sentinel|contract_version|9.3.4\n` SHA-256 为
  `cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191`。
- 五库建立两行幂等 `V934_PARITY_SENTINEL` fixture；外库 fixture 移入
  `docker/v934/`，只由 Step 3 脚本显式加载；SQLite 仅在显式
  `sqlite,v934-sqlite` profile 中加载。通用 init/profile 不受污染。QueryFacade 与
  native oracle 均精确筛选该 order id 和互斥 `order_line_no={1,2}`。
- preflight 增加 MySQL 8 与 SQL Server 2022；MySQL 5.7 精确校验
  `5.7.44-log`，SQLite 精确校验 engine/driver version 与 shared-cache JDBC URL；
  PostgreSQL/SQL Server 物理 endpoint 统一为 `127.0.0.1`；SQL Server 要求显式
  `databaseName` 与 active catalog 相等。OCI image 与 SQLite JAR byte SHA 仍由最终
  runner 负责。
- QueryFacade parity 增加 MySQL 8 与 SQL Server 2022；SQL Server native oracle 使用
  `TOP`，不复用 `LIMIT`。
- `v934.expectedDatabase` 为 Step 3 owner；仅 v933 property 时保留三库旧查询、旧
  sentinel 口径与 `V933_REAL_QUERY_DB` probe；v934 property 才启用五库 sentinel、固定
  fixture 与 `V934_REAL_QUERY_DB`。两者同时存在但值不同会 fail closed。

## Reproduction Findings

首个 MySQL 8 preflight 在修复前稳定执行 `1/F1/E0/S0`：正确连接
`8.0.44@127.0.0.1:13308/foggy_test` 后仍报
`Unsupported v933.expectedDatabase: mysql8`。扩展五库枚举后，同一 lane
`1/F0/E0/S0`。

首个 MySQL 8 QueryFacade parity 随后稳定执行 `1/F1/E0/S0`，暴露旧持久卷只有
`order_line_no=1`、没有 lane 2。没有放宽非空断言；加入确定性两行 fixture 并将
oracle 收窄到该 fixture 后，同一 lane `1/F0/E0/S0`。

四个 external DB 的 parity fixture 在第二次幂等 apply 前后保持逐库精确哈希相等
（哈希输入含 `sales_key` 与全部受保护业务列）：

| DB | Before SHA-256 | After SHA-256 |
|---|---|---|
| mysql57 | `7e5ec588ddd0242384da6c5789d05673003b0218a2b75c0cd32e72a913803d87` | same |
| mysql8 | `660053fcbba236b503b1414f2a936674416450bfc4a334ed64e77d0599c38abd` | same |
| postgres15 | `bb10d8f8e26100e6c9329654d15b73e2f74566f6488bd9e976fa15cbcaebd84e` | same |
| sqlserver2022 | `210268b0817c7ae5f67ee1540781218f6aede659e8521efa7e5c4030c1938ed2` | same |

SQLite 每个 Spring context 由 versioned init script 创建同一两行 fixture；final runner
仍需把其 context 内 before/after snapshot 写入 run-owned evidence。

本次 diagnostic 对应的 17 个实现/fixture/compatibility 文件逐文件 SHA-256 记录在
`step3-five-db-foundation-source-sha256.txt`，该 manifest SHA-256 为
`2b0506b945e76a71a35c0bd934b995fd506cbf3d88d627f60e89e40dcaa7a634`。SQLite runtime
JAR diagnostic SHA-256 为冻结值
`53174d76087bb73cc29db9c02766fb921fd7fc652f7952f3609e0018e3dd5ded`。

## Raw XML Digest Ledger

正向 XML 当前位于本地 ignored
`foggy-dataset-model/target/v934-step3-diagnostic/foundation-r3/`，负向 XML 位于
`foggy-dataset-model/target/v934-step3-diagnostic/negative-r2/`；当前分别保留 `10` 与
`3` 份，只用于本阶段实现核验。它们不随提交归档，clean 后不可恢复；最终 Step 3
runner 必须重新执行并产 run-owned archive，不能把本表当作 authority raw artifact。

| Kind | DB | Tests/F/E/S | XML SHA-256 |
|---|---|---:|---|
| preflight | sqlite | 1/0/0/0 | `a571553259a4d034306c177abb9d307d84849fe15148b80a948fce8b57933f6d` |
| preflight | mysql57 | 1/0/0/0 | `17e7d2917c691db18f5c0a8c401ec52e2515b7224789201c7d2f30b1fb1354fb` |
| preflight | mysql8 | 1/0/0/0 | `d3062d995741236c54a535a986874188487123d7af7bfef6cdb506a2aad1e81e` |
| preflight | postgres15 | 1/0/0/0 | `b364a1decdb76dc2cd0457fabf07e95db81047efa7d53d534d354f2f84d414b1` |
| preflight | sqlserver2022 | 1/0/0/0 | `d2548264cb5d51b7ca85ef9793bdbd5cca1becad3c80e38c759a8c6ed80586f0` |
| QueryFacade parity | sqlite | 1/0/0/0 | `da0e012516bcf7fbd47549fb37b69d8323fdd8837ec53f5dd666b1e017656372` |
| QueryFacade parity | mysql57 | 1/0/0/0 | `2107493f06fbf95a7f92e5f360049665cecc1e63f8449cc08444cb05b0bb181d` |
| QueryFacade parity | mysql8 | 1/0/0/0 | `c5bc1b26100a2c5258b05b02fc48e11bbc81fecfe21290ea4153a42d33b519d3` |
| QueryFacade parity | postgres15 | 1/0/0/0 | `9947431365edd2e22cbd4ce55cdae301d6828a0f8dd6d009734f6e60cfbed67b` |
| QueryFacade parity | sqlserver2022 | 1/0/0/0 | `31973094e38d481cbad910876a827c8de5280612eb6e889a45341e79cdb2970b` |

## Diagnostic Negative Proof

| Probe | Expected result | Observed | XML SHA-256 |
|---|---|---|---|
| conflicting `v934=sqlite` / `v933=mysql57` | non-zero；拒绝双 owner 漂移 | exit 1；1/F1/E0/S0 | `bacaccdb565c2cf97796ec202fdc180d8d5486e7cf9629cfb7ce8960d60c6990` |
| MySQL 5.7 endpoint declared as `mysql8` | non-zero；拒绝错误 major/endpoint | exit 1；1/F1/E0/S0 | `5063d555d5ef70d96f868bff85b5bdee1df09f5ebf3f9dd7566b03dd726e930d` |
| SQLite `cache=private` declared as required SQLite | non-zero；拒绝错误 cache mode | exit 1；1/F1/E0/S0 | `5542045399c311ccdc7a73c3e3b3df4c7599611a148bb79984e3440ea1ac4075` |

这些项只证明当前 identity seam 生效；Step 3 final 仍需由版本化 runner 执行完整的
unavailable、wrong coordinate/catalog/schema/sentinel、fixture mutation、missing/stale XML
负向集合。

## v9.3.3 Compatibility Replay

清理所有 `V934_PARITY_SENTINEL` 行及 v934 sentinel 表后，共享库恢复冻结基线：
MySQL 5.7=`110317`、PostgreSQL 15=`17384`、SQL Server 2022=`5940`，三库 v934
row count 均为 `0`。

- v933-only preflight：SQLite/MySQL57/PostgreSQL15 合计 `3/F0/E0/S0`；XML SHA-256
  分别为 `503ec98d1c32f7a9b19e65300fd6ddfb67edc3f393978b750ed53741fa9defcd`、
  `169828215c991b4b211aa4b731b71ffa7dd070d74283b3ce01056393d8c7db60`、
  `ca34688bccd0880a6ebcf52ef646c24ab831b6ebff3112bee3837e389b96d0ae`。
- v934 compatibility wrapper 保持 historical `verify-v933-batch6-real-query.sh` 字节
  不变（SHA-256
  `0f560482112cd1241c54b279198d38130c59f76e100fa32fecfeb61be50ae403`），只为上游
  reactor selector miss 注入两项 Maven override。replacement run
  `v934-step3-compat-r5-20260715`：`11 tests / 6 reports / F0/E0/S0`；旧 probe 精确保持
  SQLite `8/2`、MySQL57 `25/25`、PostgreSQL15 `25/25`；summary SHA-256
  `09000e0c35f7a3b0033d52970c52bbbdbc1dce477bb33e35dababb42a2d32883`，
  `SHA256SUMS` SHA-256
  `87005e0b3a8cecfa27c5e4f29d6f653e13a21046ea6f3b3ec344ca598dd5259c`。
- 该重放同时确认并修复 Step 2 引入的 historical directed-runner selector regression，
  详见 `BUG-step2-v933-real-query-runner-compatibility.md`。

## Verification Commands

```bash
mvn -q -P\!multi-db,\!model-lifecycle,\!query-cache-real-query \
  -pl foggy-dataset-model -am -DskipUnitTests=true -DskipITs=true test-compile

# 对五个 kind/profile 分别执行；runner 实现后由数据驱动 wrapper 替代手工循环。
mvn -q -pl foggy-dataset-model \
  -Dit.test=com.foggyframework.dataset.db.model.lifecycle.gate.RequiredDatabasePreflightIT \
  -Dspring.profiles.active=<profile> -Dv934.expectedDatabase=<kind> \
  -DskipUnitTests=true -DskipITs=false \
  failsafe:integration-test failsafe:verify

mvn -q -pl foggy-dataset-model \
  -Dit.test=com.foggyframework.dataset.db.model.lifecycle.realquery.RequiredDatabaseQueryFacadeParityIT \
  -Dspring.profiles.active=<profile> -Dv934.expectedDatabase=<kind> \
  -DskipUnitTests=true -DskipITs=false \
  failsafe:integration-test failsafe:verify
```

SQLite v934 lane 必须使用 `spring.profiles.active=sqlite,v934-sqlite`；四个外库 diagnostic
fixture 由下列脚本显式管理：

```bash
scripts/v934/step3/manage-database-foundation-fixtures.sh apply all
scripts/v934/step3/manage-database-foundation-fixtures.sh clean all
scripts/v934/step3/verify-v933-real-query-compat.sh <fresh-run-id>
```

## Remaining Step 3 Blockers

- `PivotSqlParityIT` / `PivotCascadeGenerateSqlParityIT` required assumption skips；其中
  pre-aggregation case 已确认会生成不存在的 `pa.product$categoryName` / `pa.salesAmount`
  却因未执行 SQL 而伪绿，须先修真实 relation/oracle；
- `MultiDatabaseQueryTest` 空 fixture/unsupported capability early-return；
- PostgreSQL/SQL Server 缺同构 preagg schema/data，MySQL preagg 物理列与模型契约不一致；
- 29 个 DB execution 的 exact runner/report collector、fresh/run-scoped volume、SQLite JAR
  byte SHA 与 fixture before/after manifest；长期 demo named volume 不能成为 authority；
- Redis、Mongo/DataViewer、MCP/MySQL 5.7、Vector 的 16 required execution；
- required negative probes；optional LLM 的 reviewed disposition。

Work item：`docs/9.3.4/workitems/BUG-step3-database-contract-gaps.md`。
