---
doc_role: version_execution_index
version: 9.3.5
status: ready / speed-forward
entry_gate: 9.3.4-owner-carried-forward
first_execution_gate: Gate-0-classification-debt-migration-closed
validation_mode: affected-tests
final_acceptance: deferred-to-9.4.0-integrated-acceptance
recorded_at: 2026-07-24
---

# 9.3.5 引擎阶段与公共 API

## 当前决定

9.3.5 已按 repository owner 的 speed-forward 决定开放实施。9.3.4 使用既有证据
carry forward，不再等待 replacement Step 7、final authority pointer 或独立 version signoff。
Gate 0 已通过受影响测试关闭，当前按切片继续公共 API 和内部端口收敛。

## 当前进度

- Gate 0 已选择 Option A 并关闭：7 个真实 DB suite / 12 个 node 已迁入 Failsafe
  `mysql57-it` lane。
- 默认 Unit datasource 已恢复为 hermetic H2；专用 MySQL profile 缺少配置时 fail closed。
- fresh 受治理 MySQL 5.7 run 为 7 reports / 12 nodes / F0E0S0。
- 稳定 `QueryFacade` request/result DTO 已建立；model controller、Data Viewer 和 GraphQL
  执行入口已改用 DTO-only facade，旧 9 方法 facade 保留 deprecated 兼容转发层。
- context/SQL generation 已下沉 `InternalQueryExecutionPort`，managed relation 已下沉
  `ManagedRelationExecutionPort`；semantic 只依赖组合 advanced port，pivot 只依赖 managed-relation
  窄口，稳定 facade 未新增引擎类型。
- MCP `ToolConfigLoader` 的未调用 QM 扫描已删除；model discovery/catalog 的 Spring 生产构造器
  不再注入 `QueryModelLoader`，统一消费共享 catalog authority。旧构造签名保留一个兼容周期并
  标记 deprecated。
- runtime/MCP 目录校验已统一下沉到 `DetachedModelValidationFactory/Session`；临时 bundle、
  script cache 和 catalog 均为请求级隔离状态，不再把校验资源注册到 live context。Runtime 旧 loader
  构造签名保留 deprecated 兼容转发，MCP 生产校验路径不再直接依赖 live loader。
- controller/runtime/addon bypass 盘点已关闭：MCP model discovery/catalog 只消费
  `SemanticModelCatalogReadPort`，legacy loader 行为收口在 model 模块适配器；外部模块已无直接
  `loadJdbcQueryModel/getJdbcQueryModel` 调用，残留 `QueryModelLoader` 引用仅为 deprecated 构造签名。
- runtime/native Compose 已统一改用 DTO-only `ComposeExecutionPort`；engine mode、context、authority
  resolver、sandbox/schema/compiler 异常映射均封装在 model 默认适配器，runtime compose controller、
  runner 与 fsscript CTE bridge 不再暴露 `engine.compose` 或 `SemanticQueryServiceV3` 类型；默认端口
  通过 `@ConditionalOnMissingBean` 提供，保留宿主替换能力。
- Pivot pipeline、non-additive rollup 和本地 outer-cache invalidation 已改用
  `PivotRollupExecutionPort/PivotOuterCacheEvictionPort`；rollup SQL 通过 engine-neutral DTO 桥接，
  Pivot 主实现不再直接依赖 `semantic.service` 或 `engine.compose`。旧 `SemanticQueryServiceV3`
  继续提供兼容桥接，不改变 governed SQL、raw execution 或 serial fallback 行为。
- semantic request context 内部改为持有 engine-neutral `DomainTransportPlanSpec`；semantic SQL
  编排只消费中立视图，具体 Pivot plan、builder、renderer 和 ext-data key 均保持原行为。旧
  `getDomainTransportPlans()/withDomainTransportPlans(...)` 源码与 JVM 签名保留一个兼容周期，
  避免现有链式调用方发生无过渡期破坏。
- DTO DSL round-trip、公共 API shape、namespace 继承/显式 default、两个 addon context/入口测试
  及既有 `QueryExecutionPhase` 回归均通过；internal-port compatibility/semantic/pivot 定向回归
  33/33 通过，MCP catalog/tool configuration 定向回归 55/55 通过；detached validation 受影响
  reactor 编译通过，模型自动配置 9/9、MCP 校验 5/5、Runtime 校验/刷新兼容 6/6 通过；catalog
  read-port 切片受影响测试 54/54 通过；Compose port 切片 reactor 编译通过，model 端口/适配器/
  自动配置 16/16、runtime runner/HTTP/CTE bridge/依赖边界 17/17 通过；Pivot semantic-port
  切片 reactor 编译通过，边界/cache/pipeline validation/自动配置 36/36、non-additive
  UNION ALL SQLite 集成路径 1/1 通过；domain transport contract/renderer/rollup 41/41、
  大域 transport/diagnostics/SQL parity SQLite 集成路径 3/3 通过。
- 当前执行切片：拆解 compose compiler/planner 对完整 `SemanticQueryServiceV3` 的剩余依赖，
  保持 per-request authority 与字段表达式解析能力不降级。

## 已确认目标

- 沿用 `QueryExecutionPhase`，不再造与 `QueryStageType` 混淆的第二套阶段枚举。
- 外部查询入口收敛到稳定的 QueryFacade request/result DTO；context、JDBC engine、managed relation
  下沉为 internal/advanced port。
- 清点并消除未批准的 controller/runtime/addon 直接 loader 或模型查询绕过。
- 通过 port 拆解 pivot/compose/semantic 的反向依赖，并按
  planner/compiler/executor/catalog/adapters 组织实现。

## 推进顺序

1. 关闭 Gate 0：将已知 DB consumers 迁入受治理 DB lane，或去除外部 DB 依赖，并删除临时例外。
2. 收敛 QueryFacade request/result DTO 和外部调用入口。
3. 下沉 internal/advanced ports，清除 controller/runtime/addon bypass。
4. 拆解 pivot/compose/semantic 反向依赖和过大的实现边界。

每个切片运行受影响模块的编译、单元测试、必要集成测试；涉及公共签名或依赖方向时，
加跑对应 compatibility/architecture tests。测试通过即进入下一切片，不等待大型 authority。

## 基线材料

- [只读代码与 API 基线](code-inventory.md)
- [Gate 0 分类债务决策包](gate-0-classification-decision-record.md)
- [Bypass 分类关闭记录](bypass-classification-record.md)
- 版本路线图：[9.3.1 → 9.4.0 迭代顺序评审](../9.3.1/roadmap-9.3.1-to-9.4.0.md)
- Gate 0 债务：[Unit MySQL 5.7 fixture 分类迁移](../9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md)
- Canonical 交付契约：
  [9.3.4 → 9.4.0 Speed-Forward](../9.4.0/workitems/FEATURE-v934-v940-speed-forward.md)

## 开发完成条件

classification debt 关闭；无未批准 bypass、无新增包循环；公共 API compatibility test、
阶段轨迹回归和受影响测试通过。达到这些条件后直接进入 9.4.0，不单独启动版本级大型验收。
