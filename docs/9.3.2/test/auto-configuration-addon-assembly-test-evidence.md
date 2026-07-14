---
doc_role: test_evidence
doc_purpose: Record reproducible verification evidence for the completed 9.3.2 delivery.
version: 9.3.2
target: auto-configuration-addon-assembly
status: reviewed
result: passed
recorded_by: Codex
recorded_at: 2026-07-13
---

# 9.3.2 测试证据

## 执行约束

- 所有最终 Maven 证据串行执行，避免多个进程竞争共享 target。
- ApplicationContextRunner 和 Launcher mock smoke 不连接真实 Mongo、Vector 或 Redis。
- skipTests package 只作为编译和装配证据，不作为测试通过证据。
- 工作区的 9.3.1 未提交变更全程保留。

## 修复前失败契约

修复前源码盘点确认以下契约不成立：

- McpLauncherApplication 显式扫描 com.foggyframework。
- DbModelAutoConfiguration 和 DatasetMcpAutoConfiguration 使用宽 ComponentScan。
- model-mongo factories 注册了错误的 dataset Mongo 自动配置。
- Mongo/Vector model Loader 可通过 Service 或基础包扫描无条件发现。
- Vector 两个 Addon 缺 Boot 3 imports，Cache 仅有旧 factories。
- 多个模块同时存在 imports 和 EnableAutoConfiguration factories。

AutoConfigurationBoundaryContractTest、AutoConfigurationRegistrationUniquenessTest 及各 Addon ContextRunner 将上述问题固化为回归契约。修复前未归档不可变 Surefire 红色报告；最终绿色报告和源码差异可复核该契约。

## 自动配置切片矩阵

| 模块/测试 | 覆盖 | 结果 |
|---|---|---|
| DataSetMongoAutoConfigurationContextTest | 关闭、缺连接 Bean、缺 Mongo 类、条件齐备、用户 Loader back-off、imports | 6/6 |
| MongoModelAutoConfigurationContractTest | 无 Service、imports、关闭、缺类、正向、back-off | 6/6 |
| DataSetVectorAutoConfigurationContextTest | 两级开关、缺 VectorStore 类、缺核心 Bean、正向、back-off、imports | 7/7 |
| VectorModelAutoConfigurationContractTest | 无 Service、imports、关闭、缺 Milvus、缺 WebFlux、正向、back-off | 7/7 |
| QueryCacheAutoConfigurationContextTest | enabled、Redis/Caffeine 缺类、provider/builder/back-off、web API、AspectJ eviction | 14/14 |
| GraphqlAddonAutoConfigurationTest | 关闭、正向、back-off、converter 门禁、缺 Servlet | 5/5 |
| CloudStorageAutoConfigurationTest | 未选择 provider、四类云 SDK 缺失 | 5/5 |
| DataViewerAutoConfigurationContextTest | 缺 Mongo、关闭、缺 QueryFacade 时无部分装配 | 3/3 |
| GlobalNamespaceFallbackRiskDiagnosticTest | 风险码和配置绑定 | 2/2 |

核心 Addon 切片总计 40/40；其他 Addon 与诊断 15/15。所有切片 0 failure、0 error，且未建立外部连接。

可复跑命令示例：

    mvn -pl addons/foggy-dataset-mongo -am -Dtest=DataSetMongoAutoConfigurationContextTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl addons/foggy-dataset-model-mongo -am -Dtest=MongoModelAutoConfigurationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl addons/foggy-dataset-vector -am -Dtest=DataSetVectorAutoConfigurationContextTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl addons/foggy-dataset-model-vector -am -Dtest=VectorModelAutoConfigurationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl addons/foggy-dataset-model-cache -am -Dtest=QueryCacheAutoConfigurationContextTest -Dsurefire.failIfNoSpecifiedTests=false test

相关报告：

- addons/foggy-dataset-mongo/target/surefire-reports
- addons/foggy-dataset-model-mongo/target/surefire-reports
- addons/foggy-dataset-vector/target/surefire-reports
- addons/foggy-dataset-model-vector/target/surefire-reports
- addons/foggy-dataset-model-cache/target/surefire-reports
- addons/foggy-dataset-graphql/target/surefire-reports
- addons/foggy-chart-storage-cloud/target/surefire-reports
- addons/foggy-data-viewer/target/surefire-reports

## 包外、单/全 Addon 与 Launcher

串行执行：

    mvn -pl foggy-mcp-launcher -am -Dtest=AutoConfigurationBoundaryContractTest,AutoConfigurationRegistrationUniquenessTest,OutsidePackageCoreAutoConfigurationSmokeTest,FullAddonAutoConfigurationAssemblyTest,LauncherDefaultRouteIsolationSmokeTest,LauncherExplicitTestRoutesSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test

结果：9/9 tests，0 failure/error；21/21 reactor modules success。

覆盖内容：

- AutoConfigurationBoundaryContractTest 3/3：Launcher 无根扫描，model/MCP 无宽 ComponentScan。
- AutoConfigurationRegistrationUniquenessTest 2/2：17 个入口 imports exact-once，旧 factories 不含这些入口。
- OutsidePackageCoreAutoConfigurationSmokeTest 1/1：io.foggytest 包外应用通过 EnableAutoConfiguration 发现 core。
- FullAddonAutoConfigurationAssemblyTest 1/1：Mongo、Vector、Cache、GraphQL、PreAgg 联合装配，各关键 Bean 恰好一个，无外部连接、循环依赖或重复 Bean。
- LauncherDefaultRouteIsolationSmokeTest 1/1：四个测试/开发 Bean 默认不存在；/dev/tables、/semantic/v3/test/...、/test/identity 均 404。
- LauncherExplicitTestRoutesSmokeTest 1/1：显式开启后 Bean 各一个；/dev/tables 和 /test/identity 可达，semantic test mapping 存在。

## 9.3.1 基线定向回归

| 回归 | 命令摘要 | 结果 |
|---|---|---|
| Cache 专项 | fingerprint、encoder、Caffeine/Redis provider、双数据源隔离 | 86/86 |
| Model 定向 | datasource resolution、auto-config、step ordering、PreAgg→L2 | 27/27 |
| PreAgg→L2 | PreAggregationL2CacheIntegrationTest | 1/1 |
| MCP Controller | DevToolsControllerIsolationTest | 5/5 |
| Semantic Controller | SemanticServiceV3TestControllerIsolationTest | 2/2 |

执行命令：

    mvn -pl addons/foggy-dataset-model-cache -am '-P!multi-db' -Dtest=QueryFingerprintBuilderTest,QueryFingerprintTest,StableCanonicalEncoderTest,CaffeineQueryCacheProviderTest,CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest,RedisQueryCacheProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl foggy-dataset-model '-P!multi-db' -Dtest=TableModelLoaderManagerImplDataSourceResolutionTest,DbModelAutoConfigurationTest,DataSetResultStepExecutorOrderingTest,PreAggregationL2CacheIntegrationTest test
    mvn -pl foggy-dataset-model -am '-P!multi-db' -Dtest=PreAggregationL2CacheIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl foggy-dataset-mcp -am '-P!multi-db' -Dtest=DevToolsControllerIsolationTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -pl foggy-dataset-model '-P!multi-db' -Dtest=SemanticServiceV3TestControllerIsolationTest test

## SQLite 全量与真实数据库

SQLite：

    mvn -pl foggy-dataset-model '-P!multi-db' test

结果：3294 tests，0 failures，0 errors，3 skipped。相比 9.3.1 的 3289 基线新增 5 个本轮相关测试。

真实数据库容器均 healthy：

- MySQL 5.7
- PostgreSQL 15
- SQL Server 2022

串行执行：

    mvn test -pl foggy-dataset-model '-P!multi-db' -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=docker
    mvn test -pl foggy-dataset-model '-P!multi-db' -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=postgres
    mvn test -pl foggy-dataset-model '-P!multi-db' -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=sqlserver

结果：MySQL 5.7、PostgreSQL 15、SQL Server 2022 各 18/18，均 0 failure/error。

## 根 Reactor 与发布物审计

串行执行：

    mvn '-P!multi-db' -DskipTests package

结果：25/25 modules success，耗时约 3 分 20 秒。该结果仅证明编译和装配，不替代前述测试。

发布物审计：

- 所有本地 main JAR 的 AutoConfiguration.imports 和 spring.factories 已逐一读取。
- 未发现 packaged EnableAutoConfiguration 条目。
- 仅 foggy-dataset-client 和 foggy-fsscript-client 保留 ApplicationContextInitializer factories。
- Launcher fat JAR 中 12 个本地嵌套模块 JAR 与本次根 package 产物 checksum 12/12 一致。
- git diff --check 通过，仅有既有 CRLF warning，无 whitespace error。

## 证据结论

- blocker/high: none。
- test decision: passed；覆盖审计对次要 Addon 的直接切片粒度给出 ready-with-gaps。
- 风险：Cloud、DataViewer 和部分次要 Addon 的独立正向/back-off 切片粒度弱于 Mongo、Vector、Cache；imports 唯一性、全 class path、Launcher 和根 package 已覆盖其发布级共存风险。
