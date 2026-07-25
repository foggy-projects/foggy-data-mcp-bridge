# P0 Add-on 多 Bundle 原子注册与来源治理

## 状态

- 目标版本：`9.1.0.beta`
- 源码基线：`origin/main@3b1c7249ba75b3bab54cb0f898ea1c198e5303d4`
- 状态：`READY_FOR_SIGNOFF`
- 上游调用方：TMS `query-cloud-service`
- 决策日期：2026-07-24

## 目标

为 Foggy Runtime 提供通用 Add-on TM/QM Bundle 生命周期能力，使同一 namespace
可以安全承载多个 Bundle，也可以为 Add-on 使用独立 namespace。能力不得包含任何
TMS 或 `tms-connect-tracking` 项目硬编码。

## 冻结契约

### Bundle 与 namespace

1. Bundle 名称在运行时全局唯一；重复 add 必须稳定失败。
2. 一个 namespace 允许注册多个 Bundle。
3. 同一 namespace 内 TM/QM 的规范模型名必须唯一。
4. 不允许“后注册静默覆盖”；冲突必须在候选目录发布前失败。
5. Bundle 升级只替换自身来源，不能删除或改变同 namespace 的其他 Bundle。
6. Bundle 卸载只移除自身来源模型。
7. namespace 仅是模型和数据源路由边界，不承担租户隔离。

### 生命周期与原子性

1. Runtime API 支持 validate、add、replace、refresh、describe、remove。
2. add/replace/remove 必须先构造并校验不可见候选目录，成功后一次性发布。
3. 任一模型解析、冲突检测或数据源绑定校验失败时：
   - 新 Bundle 不可见；
   - 原 Bundle 和原 Catalog 保持可用；
   - 不残留半注册模型、watcher、来源 revision 或 registry 记录。
4. replace 失败时不允许依赖“先删后补”的尽力恢复作为正确性保障。
5. remove 失败时保留旧 Bundle 与旧 Catalog。
6. Runtime registry 只在引擎提交成功后持久化；持久化失败需要回滚运行态提交，
   或返回明确的不可恢复错误并阻止成功响应。

### 模型冲突与来源

1. 模型唯一键为 `namespace + model kind + canonical model name`。
2. TM、QM 分别检测同 kind 重名；现有跨 kind 引用语义保持不变。
3. 每个 Catalog 模型记录来源：
   - `bundleName`
   - `namespace`
   - `resource identity/path`
4. list/describe 能返回模型所属 Bundle；未能确定来源时明确标记，而不是伪造。
5. 冲突诊断至少包含 namespace、模型名、已有 Bundle、候选 Bundle。

### namespace 与数据源

1. 引擎允许调用方配置或动态维护 namespace 到受控数据源绑定。
2. 未知 namespace 和缺失数据源绑定必须 fail closed，不回退到默认 namespace。
3. 默认 namespace 的既有行为保持兼容。
4. Bundle API 不接受 JDBC URL、密码、token、Secret 或 Provider 凭据。

### 稳定错误

至少提供以下稳定类别：

- `BUNDLE_ALREADY_EXISTS`
- `BUNDLE_NAME_CONFLICT`
- `BUNDLE_NOT_MANAGED`
- `BUNDLE_SOURCE_INVALID`
- `BUNDLE_MODEL_CONFLICT`
- `BUNDLE_VALIDATION_FAILED`
- `BUNDLE_REPLACE_FAILED`
- `BUNDLE_REMOVE_FAILED`
- `NAMESPACE_NOT_FOUND`
- `DATASOURCE_BINDING_NOT_CURRENT`
- `CATALOG_BUILD_FAILED`
- `REGISTRY_PERSIST_FAILED`

错误响应继续使用既有 Runtime envelope，不破坏已有 API 路径和字段。

## 兼容性

- 保持 `SystemBundlesContext.addExternalBundle/removeBundle` 源码兼容。
- 保持已有配置 Bundle、默认 namespace、frontend-meta、direct query、members 行为。
- 未使用 Runtime API 的应用无需迁移。
- 已有单 Bundle namespace 行为是多 Bundle 能力的子集。
- 不改变身份认证、租户上下文和数据源凭据体系。

## 实现边界

- `foggy-fsscript`：Bundle 候选来源、事务性 mutation、资源来源定位。
- `foggy-dataset-model`：候选 Catalog、模型冲突检测、来源记录、原子发布。
- `foggy-runtime-api`：validate/add/replace/remove 编排、registry 一致性、稳定错误。
- TMS 平台负责 Add-on manifest、允许的数据源别名和 StaffSessionToken；不进入本仓库。

## 验收

1. 同 namespace 注册两个无冲突 Bundle，模型均可发现、describe 和查询。
2. 候选 Bundle 与已有 Bundle 模型重名时失败，已有模型与 Catalog identity 不变。
3. replace 只替换目标 Bundle；语法错误或冲突时旧版本保持可用。
4. remove 只删除目标 Bundle 模型；其他 Bundle 保持可用。
5. 独立 namespace 绑定有效数据源后可查询；未知 namespace 不回退。
6. list/describe 可定位模型来源 Bundle。
7. 生命周期失败不留下 Bundle、Catalog、watcher、registry 半提交状态。
8. 既有 Bundle、模型目录、Runtime API 和 namespace 测试继续通过。

## 验证预算

- 必跑：相关模块单元测试与 Runtime API 集成测试。
- 必跑：真实 TM/QM 资源的同 namespace 多 Bundle、冲突、replace、remove 测试。
- 必跑：`mvn -pl foggy-runtime-api -am test` 的相关测试链。
- 不在本次默认预算内：全部数据库方言、全仓长时测试、发布签名链；需要单独授权。

## Implementation Result

- 实现：
  - `SystemBundlesContext` 增加源码兼容的原子
    `replaceExternalBundle`，add/replace/remove 在 catalog refresh 失败时
    回滚 bundle source、definition、watcher/cache 与 source revision。
  - `RuntimeBundleAdmissionService` 对候选目录执行完整 TM/QM validate，并按
    `namespace + TM/QM kind + modelName` 预检冲突；禁止后注册静默覆盖。
  - Runtime Bundle API 的 add/replace/remove 与 registry 持久化协同回滚，
    replace 不再采用先删后加，且禁止改变 namespace。
  - TM/QM catalog 记录 `bundleName`、`namespace`、`resourceIdentity`；
    semantic model list 和 describe 返回来源信息。
  - 运行态内部 admission service 不再依赖开启 Runtime Controller，便于
    query-cloud 在管理 API 默认关闭时复用候选校验。
- 稳定错误：
  - `BUNDLE_NAME_CONFLICT`、`BUNDLE_ALREADY_EXISTS`、
    `BUNDLE_MODEL_CONFLICT`、`BUNDLE_SOURCE_INVALID`、
    `BUNDLE_VALIDATION_FAILED`、`BUNDLE_NAMESPACE_CHANGE_UNSUPPORTED`、
    `BUNDLE_ADD_FAILED`、`BUNDLE_REPLACE_FAILED`、
    `BUNDLE_REMOVE_FAILED`、`BUNDLE_REGISTRY_PERSIST_FAILED`、
    `BUNDLE_ROLLBACK_FAILED`。
- 自动化证据：
  - `foggy-fsscript`、`foggy-dataset-model`、`foggy-runtime-api` focused
    suite：32 tests，0 failures。
  - 其中 SQLite 集成测试以真实 TM/QM 验证同 namespace 两个 bundle
    同时查询、来源定位、replace 只更新自身、remove 后兄弟 bundle 继续
    可查询。
  - namespace/datasource/request resolver focused suite：21 tests，
    0 failures；验证 `X-NS` 优先、默认 namespace 仅在请求未声明时使用，
    以及未知 namespace 不启用 datasource 默认回退。
  - `mvn -pl foggy-runtime-api -am -Dmaven.test.skip=true install`：
    8 个 reactor module 构建并安装成功，供 query-cloud 组合验证。
- 兼容性：
  - 保持 `addExternalBundle/removeBundle` 现有调用兼容。
  - 未开启 Runtime API 的应用无需迁移；内部 admission service 可独立使用。
  - 配置 bundle 和 runtime-managed bundle 所有权分离，Runtime API 不允许
    修改 unmanaged/configured bundle。
- 残余发布约束：
  - 本能力在既有版本号 `9.1.0.beta` 上实现，发布时
    `foggy-fsscript`、`foggy-dataset-model`、`foggy-runtime-api` 必须来自
    同一次构建，禁止部署节点混用旧的同版本 JAR。
