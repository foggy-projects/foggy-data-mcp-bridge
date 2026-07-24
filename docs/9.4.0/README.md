---
doc_role: version_execution_index
version: 9.4.0
status: SIGNED_OFF / accepted-with-risks
entry_gate: 9.3.5-development-complete
validation_mode: affected-tests-then-one-integrated-acceptance
recorded_at: 2026-07-24
---

# 9.4.0 SPI v2 与模块化

## 当前决定

9.4.0 在 9.3.5 开发目标完成后立即开始，不等待独立的 9.3.5 version acceptance。
实施按小切片推进，每个切片通过受影响测试即可继续；只在全部 9.4.0 开发目标完成后
安排一次跨版本整体验收。

9.3.5 与 9.4.0 开发目标均已完成。候选已基于最新 `origin/main`
`20ef23e8d3e00f05c9864f2ec1bd3bd2785fbf6b` 完成 review 和跨版本整体验收，结论为
`ACCEPTED_WITH_RISKS`。风险来自同一实施会话内的非独立 review，以及 owner 明确排除的
Step 5/Step 7 authority、semantic/portable replay、source seal 和 GitHub CI；不存在核心功能阻断。
首个 9.4.0 切片建立
`foggy-dataset-model-api`：稳定 `QueryFacade`/DTO 保持原包名与 JVM 名称物理迁入，旧
`foggy-dataset-model` 通过 Maven 依赖继续提供传递兼容；同时新增最小 provider identity/capability
契约，且 API 主代码 compile classpath 为零第三方依赖。

当前切片证据：API 单元/边界测试 4/4、旧聚合 facade compatibility/boundary 测试 3/3，受影响的
12-module reactor package 成功；API 与旧聚合 JAR 不含重复 facade class。

第二个切片已经建立纯 JDK `foggy-dataset-model-core` provider catalog。provider descriptor 在发现时
快照化；重复 identity、缺失 provider、未声明 capability 均显式 fail closed。API+core tests 8/8，
core compile dependency tree 仅含 API，旧聚合及其 9-module upstream reactor package 成功。

第三个切片已经建立 `foggy-dataset-model-jdbc` v2 adapter。`QueryBackendProvider` 是独立于基础
`BackendProvider` 的单方法角色；core 类型化解析会拒绝只宣称 QUERY capability、却未实现该角色的
provider。JDBC adapter 包装稳定 facade 并支持显式 dialect identity。API+core+JDBC tests 11/11，
依赖树为 `jdbc -> core -> api`，旧聚合及其 10-module upstream reactor package 成功。

第四个切片已经建立 `foggy-dataset-model-starter`。auto-configuration 在稳定 `QueryFacade` 存在时
装配默认 JDBC provider，并从全部有序 provider 构建 fail-closed catalog；其他 backend 可与 JDBC
共存，同名 `jdbcQueryBackendProvider` 可显式覆盖默认 adapter，用户 catalog 继续 back off。
API+core+JDBC+starter tests 18/18，旧聚合 `DbModelAutoConfigurationTest` 11/11；starter compile
依赖树为 `starter -> jdbc -> core -> api` 加 Spring Boot auto-configuration，不含旧聚合或 web。

第五个切片已经建立 `foggy-dataset-model-web`，目标五模块全部存在。web 只依赖 core/API 与最小
Spring MVC/Jackson 边界，不依赖 starter、JDBC adapter 或旧聚合；只在 servlet application、catalog
存在且 `foggy.model.backends.web.enabled=true` 时暴露只读 `/foggy/model/backends`。返回值来自
catalog 的 discovery-time descriptor snapshot，按 backend identity/capability 稳定排序。API/core/web
tests 14/14、旧聚合 context tests 11/11，web dependency tree 与 classpath boundary tests 均确认未
引入 JDBC/legacy implementation。

第六个切片已经建立独立 `foggy-dataset-model-tck`，并将 query-cache addon 的实际迁移面发布为
`query-cache` backend 的 `CACHE_INVALIDATION` 小角色。TCK 复用 provider identity、不可变 capability、
typed catalog resolution 与 duplicate/missing/unsupported fail-closed 契约；cache adapter 只桥接既有
`QueryCacheProvider.evict/evictAll`，不虚报 model load、query、namespace 或 atomic refresh 能力。
API/core/TCK tests 12/12，cache TCK、自动配置及既有 context tests 23/23；TCK 与 starter 在 addon 中
均为 test scope，未扩大 cache 运行时依赖边界。

第七个切片完成 launcher 装配与制品边界复证。全 addon auto-configuration smoke、注册唯一性和
outside-package smoke 合计 4/4；launcher 生产包包含兼容聚合及 API/core/JDBC/starter/web，不包含
测试构件 TCK，也不把 test-scope query-cache addon 带入运行时 JAR。SPI v2 迁移说明同步冻结了一个
兼容周期、provider 实现步骤、fail-closed 错误语义与各消费场景的目标 Maven 坐标。

最终候选窄范围复证：六个新模块 tests 26/26、旧 facade compatibility/boundary tests 6/6、cache
TCK/context tests 23/23、launcher smoke 4/4；目标模块 compile dependency tree 为
`starter -> jdbc -> core -> api`、`web -> core -> api`、`tck -> core -> api`，无反向边。
launcher production package 再次成功并确认 TCK/cache 未进入运行时 JAR。

最终跨版本整体验收于 2026-07-24 完成：

- 根 reactor `mvn clean verify -DskipITs ...` 的 32 个模块全部成功，耗时 6 分 11 秒；
- Gate 0 缺失配置按预期 fail-closed，受控 MySQL 5.7 lane 为 7 份报告、12 个测试，F0/E0/S0；
- 依赖树复证 `core -> api`、`jdbc -> core -> api`、`starter -> jdbc -> core -> api`、
  `web/tck -> core -> api`，无目标模块反向生产依赖；
- launcher 运行时 JAR 包含兼容聚合及 API/core/JDBC/starter/web，不包含 TCK/cache；
- 验收过程未执行 `mvn install`，未接入 CI，未 tag、release 或 publish。

正式记录见 [9.4.0 version signoff](acceptance/version-signoff.md)。

## 开工依赖

1. 9.3.4 已由 owner carry forward；
2. 9.3.5 完成公共 API 瘦身、外部入口收敛和去环，相关测试通过；
3. 9.3.2 的 auto-configuration/back-off 边界继续保留，并在受影响 starter tests 中复证。

## 已确认目标模块顺序

物理建立顺序为 `model-api → model-core → model-jdbc → model-starter → model-web`。运行时依赖图为
`starter → jdbc → core → api` 与 `web → core → api` 两条单向分支，避免 web 反向引入 JDBC。

旧 `foggy-dataset-model` 只在兼容期保留为聚合/转发层。后续不得先物理拆分再定义 API，或让
`model-api` 引入 Spring、JDBC、implementation 或 web 依赖。

## 基线材料

- [模块与 SPI 静态盘点](code-inventory.md)
- [Model SPI v2 迁移说明](model-spi-v2-migration.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- 9.3.5 前置：[引擎阶段与公共 API 基线](../9.3.5/README.md)
- Canonical 交付契约：
  [9.3.4 → 9.4.0 Speed-Forward](workitems/FEATURE-v934-v940-speed-forward.md)

## 开发与最终准出

开发阶段要求 Maven 依赖单向无环，`model-api` 无 Spring/JDBC/impl/web 依赖；每个切片只运行
受影响 reactor/module、compatibility、TCK、starter context 或 launcher smoke。

全部目标完成后形成 `READY_FOR_SIGNOFF` 候选，再执行一次跨版本整体验收。该验收已完成并签署
`ACCEPTED_WITH_RISKS`，且未复活 9.3.4 Step 5/Step 7 authority、portable replay pointer 或
GitHub CI。
