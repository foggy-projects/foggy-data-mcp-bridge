---
type: bug
bug_source: test-governance-found
version: 9.3.4
ticket: BUG-934-STEP3-MONGO-JDBC-DIALECT
severity: major
status: closed
reproduction_status: confirmed
test_strategy: integration-test
automation_decision: required
owner: addons/foggy-dataset-model-mongo/foggy-dataset-model
---

# Mongo model loader 隐式依赖默认 JDBC dialect

## Symptom

Fresh Mongo diagnostic `external-mongo-dev-r1` 把默认 datasource 指向不可达端口，以验证
Mongo external lane 不会借用长期 MySQL。DataViewer `3/3` 通过，但 model-mongo 的 27 个
节点得到 `21 errors`：

```text
TmMongoModelLoaderImpl.load
  -> LoaderSupport.loadQueryObject
  -> DbUtils.getDialect
  -> DataSource.getConnection
```

Durable run status 为 `failed / last_phase=variant-mongo6`，无 summary/candidate，Mongo
container 与两个 named volume residue 均为 `0`。该 diagnostic 不能作为绿色证据。

## Root Cause

`TmMongoModelLoaderImpl` 为 Mongo TM 生成兼容性的 fake `viewSql`，随后复用 JDBC
`LoaderSupport.loadQueryObject`。后者会从 default datasource 读取 SQL dialect，即使真正
的数据读取和聚合只发生在 MongoDB。Mongo-only 测试 classpath 因而仍需要一个可连接的
JDBC datasource。

## Step 3 Unblock

9.3.4 Mongo runner 显式提供 run-local in-memory SQLite dialect guard，并在
`foggy-dataset-model-mongo` 的 test classpath 添加 SQLite JDBC。它只支持 loader 的 metadata
路径，不读取共享 MySQL，不改变 Mongo 查询、fixture 或结果断言：

```text
jdbc:sqlite:file:v934_<run-scope>?mode=memory&cache=shared
maximum-pool-size=1
connection-init-sql=CREATE TABLE IF NOT EXISTS dual(dummy INTEGER)
```

Mongo positive evidence仍必须来自 digest-pinned fresh Mongo、两个 run-scoped database、
exact `4 reports / 30 testcase / F0/E0/S0` 与零 Docker residue。SQLite guard 不能被描述为
产品级解耦修复。

## Required Regression

- [x] 不可达 JDBC diagnostic 必须失败且不能产生绿色 candidate
- [x] 失败 diagnostic 必须清理 Mongo container 与两个显式 volume
- [x] Mongo test classpath 显式声明 SQLite JDBC，不借用其他模块的 test dependency
- [x] run-local SQLite guard 下执行 exact Mongo/DataViewer `4/30/F0/E0/S0`

## Long-term Production Follow-up

后续应让 Mongo loader 直接从 TM 属性构建 `QueryObject`/列 metadata，或为非 JDBC loader
提供不解析 SQL dialect 的公共路径。该生产解耦不在 9.3.4 测试 authority 中通过 SQLite
workaround 冒充完成，也不能放宽 catalog/model build 的 fail-closed 行为。

- [ ] 无 default JDBC datasource 的 Mongo-only application context 回归
- [ ] Mongo loader 不再为列 metadata 建立 JDBC connection

上述两项是生产解耦长期项，不属于 9.3.4 Step 3 required external lane 的关闭条件。
若实现需要公共 loader/SPI 边界变化，应转入 9.3.5 或 9.4.0，而不是扩大本版本范围。

## Closure

9.3.4 的解阻范围以 run-local SQLite metadata guard、不可达 JDBC fail-closed diagnostic、
fresh Mongo 6/DataViewer `4 reports / 30 testcase / F0/E0/S0`、双 volume 清理和 shared
external single-outer replay闭合。该 workaround 不读取长期 MySQL，也不改变 Mongo 查询
或结果断言，因此本 BUG 的 Step 3 test-governance scope 关闭；上面的生产解耦事项继续
显式开放，但不冒充已修复。

## References

- `addons/foggy-dataset-model-mongo/src/main/java/com/foggyframework/dataset/db/model/impl/mongo/TmMongoModelLoaderImpl.java`
- `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/LoaderSupport.java`
- `scripts/verify-v934-external-mongo.sh`
- `docs/9.3.4/workitems/BUG-step3-external-matrix-gaps.md`
- `docs/9.3.4/evidence/step-3/step3-external-mongo-runner-candidate-20260715.md`
