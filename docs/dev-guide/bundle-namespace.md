# Bundle 和 Namespace 管理

## Namespace 支持（命名空间隔离）

通过 HTTP Header `X-NS` 传递命名空间，支持同一服务下多环境模型隔离。

### 命名空间规则

- 未传或传空字符串：默认保持空 namespace；如果配置了 `foggy.dataset.request.default-namespace`，API/MCP 请求入口会先替换为该默认 namespace
- 传 `dev`：查询 dev 命名空间下的模型（如 `dev:OrderModel`）
- 传 `test`：查询 test 命名空间下的模型（如 `test:OrderModel`）

Dataset Native REST API 同时支持 `X-NS` header 和请求 body 顶层 `namespace` 字段。有效 namespace 优先级固定为：

```text
X-NS header > body.namespace > foggy.dataset.request.default-namespace > 空 namespace
```

`body.namespace` 仅指请求 body 顶层字段，不从 `payload.namespace`、compose script 或其他嵌套参数中推断。`X-NS` 与 `body.namespace` 同时存在且不一致时，`X-NS` 优先。

### YAML 配置示例

```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: ecommerce-dev
          namespace: dev        # 开发环境
          path: /data/ecommerce-dev
          watch: true

        - name: ecommerce-test
          namespace: test       # 测试环境
          path: /data/ecommerce-test
          watch: false
```

### 金额语义单位 Namespace 示例

当同一套 TM/QM 需要同时服务两类契约时，建议用不同 namespace 隔离：

- 业务前后台继续使用物理单位，例如数据库保存“分”，查询也按“分”理解。
- AI Agent / MCP / LLM 使用语义单位，例如金额统一按“元”理解。

`ExternalBundleProperties` 只负责把同一路径注册到不同 namespace；dataset-model 通过 `foggy.dataset.semantic-scale` 决定哪些 namespace 禁用 `semanticScaleFactor`。

```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: tms-models-physical
          namespace: tms-biz
          path: /data/tms-models
          watch: true

        - name: tms-models-semantic
          namespace: tms-ai
          path: /data/tms-models
          watch: true

  dataset:
    request:
      default-namespace: tms-ai
    semantic-scale:
      default-enabled: true
      disabled-namespaces:
        - tms-biz
```

在这个配置下：

| Namespace | 使用方 | 金额字段契约 |
|-----------|--------|--------------|
| `tms-ai` | AI Agent / MCP / LLM | 保留 `semanticScaleFactor`，字段值按元 |
| `tms-biz` | 业务前后台 / 既有调用 | 忽略 `semanticScaleFactor`，字段值按分 |

查询入口只需要稳定传递 namespace，不需要额外传递单位开关。如果 AI/MCP 入口不方便显式传 `X-NS: tms-ai`，可以配置 `foggy.dataset.request.default-namespace: tms-ai`，让缺失 namespace 的 API/MCP 请求默认进入 semantic namespace。Dataset Native REST 也可以通过 body 顶层 `namespace` 显式指定命名空间，但系统入口强制注入的 `X-NS` 始终优先。

注意：`request.default-namespace` 是请求入口默认值，不是 bundle 默认 namespace。未配置时，底层 `null` / `""` 仍表示空 namespace，保持兼容。

### Java 配置示例

```java
@EnableFoggyFramework(
    bundleName = "my-models",
    namespace = "dev"
)
public class MyModelsConfig { }
```

## 动态 Bundle 管理

支持运行时动态添加/移除外部 Bundle，无需重启服务。

注意：旧版 `foggy-fsscript` `/api/bundles/**` 管理接口默认不再装配。确需使用旧接口时需显式开启：

```yaml
foggy:
  fsscript:
    bundle-management:
      enabled: true
```

新接入建议优先使用 `foggy-runtime-api` 的 `/api/v1/bundles`，并通过 `foggy.runtime-api.auth-code` 为管理操作配置授权码。

### Spring Boot 宿主接入 Runtime API

`foggy-runtime-api` 可以嵌入既有 Spring Boot 宿主应用，但仅添加 Maven 依赖不会保证 `/api/v1/**` Controller 被注册。当前模块提供 Controller、配置类、拦截器和服务类，没有作为 Spring Boot starter 自动导入宿主扫描范围。

宿主应用的 component scan 根包如果不覆盖 `com.foggyframework.runtime.api`，需要显式导入或扫描：

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "com.foggyframework.runtime.api")
class HostRuntimeApiConfiguration {
}
```

同时配置运行时管理面开关和授权码：

```yaml
foggy:
  runtime-api:
    enabled: true
    security-mode: auth-code
    auth-code: ${FOGGY_RUNTIME_API_AUTH_CODE}
```

宿主嵌入方式适合企业项目校验，因为模型加载会运行在真实 Spring 上下文中，可以访问宿主数据源、项目 Bean、可选 loader、dict/script 依赖和既有 namespace 配置。公共 lite runtime 更适合 demo、样例或不依赖宿主上下文的独立模型。

### Bundle validate/refresh 标志语义

`/api/v1/bundles` 和 `foggy-runtime-cli bundles add --validate --refresh` 接受 `validate` / `refresh` 参数，但 bundle API 本身只负责注册、更新或持久化 bundle 记录，不会在同一个请求里执行模型校验或缓存刷新。响应中的 warning 会提示继续执行模型命令。

推荐使用明确的三步流：

```bash
foggy-runtime --base-url "$RUNTIME_URL" --namespace "$NS" bundles add \
  --name my-models --path /data/models --watch --replace

foggy-runtime --base-url "$RUNTIME_URL" --namespace "$NS" models validate \
  --models-dir /data/models

foggy-runtime --base-url "$RUNTIME_URL" --namespace "$NS" models refresh
```

这样可以把 bundle 注册失败、模型语法/宿主上下文失败、缓存刷新失败分开定位，也避免把校验副作用混进 bundle 更新回滚语义。

### 企业平铺 TM/QM 目录校验

企业项目可以使用标准目录：

```text
models/
  model/*.tm
  query/*.qm
```

也可以使用平铺目录：

```text
models-dir/
  *.tm
  *.qm
  *.fsscript
```

`models validate --models-dir` 会扫描 `**/*.tm` 和 `**/*.qm`。当同一 namespace 下已经加载了同一个目录，Runtime API 会复用已注册 bundle 来校验，避免再次注册临时 bundle 造成同名 TM/QM 重复。若要校验一个尚未加载、但模型名可能和当前 namespace 已有模型冲突的目录，建议使用干净的临时 namespace。

平铺目录中的文件名按完整文件名匹配，例如 `StationModel.tm` 不会再匹配 `FactTaskStationModel.tm`。如果仍出现同名资源冲突，应检查同一 namespace 下是否有多个 bundle 提供了相同文件名。

企业模型如果依赖以下宿主能力，应通过嵌入宿主的 Runtime API 校验，而不是 public lite runtime：

- 宿主 Spring Bean 或项目工具类
- 真实 datasource schema、view、函数或权限上下文
- Mongo / Odoo / 自定义 loader
- dict、script import 或 bundle namespace 约定
- 项目侧默认 namespace、semantic scale 或 datasource binding

### Runtime API 授权码边界

`foggy-runtime-api` 是内部运行时管理面，不是面向客户的权限系统。只要部署环境会把管理操作暴露到可信本机以外的访问范围，应配置授权码：

```yaml
foggy:
  runtime-api:
    enabled: true
    security-mode: auth-code
    auth-code: ${FOGGY_RUNTIME_API_AUTH_CODE}
    auth-scope: mutations
```

请求侧支持 `X-Foggy-Runtime-Code: <code>`，也兼容 `Authorization: Bearer <code>`。使用 `foggy-runtime-cli` 自动化调用时，建议优先通过 `FOGGY_RUNTIME_API_AUTH_CODE` 环境变量传递，避免把授权码直接写入 shell history 或脚本参数。

默认 `auth-scope=mutations` 保持历史调用兼容，保护 Runtime API 管理写操作，例如：

- Bundle 添加、更新、移除
- Datasource 添加、更新、移除、连通性测试
- Namespace 与 Datasource 绑定管理
- 资源保存、模型校验、模型刷新等管理探测入口

默认 scope 下，查询、读取、SQL、Compose 和模型资源读取仍按原有部署与数据权限边界控制。
原始 FSScript 执行属于作者/管理面，始终要求管理凭据。

需要让 auth-code 覆盖全部 `/api/v1/**` 时，显式配置：

```yaml
foggy:
  runtime-api:
    auth-scope: management-all
```

`management-all` 仍不提供客户级权限、用户身份、审计或授权码轮换。查询受保护 QM 时，数据面
`Authorization` 与管理 `X-Foggy-Runtime-Code` 是两个独立凭据，不能互相替代。

### 只读 Artifact Lifecycle Inventory

启用 Runtime API 后，可用 `GET /api/v1/authoring/artifacts/lifecycle` 查看 authoring workspace、published
artifact/attempt 与当前 Bundle registry 的容量、health、引用分类和稳定 blocked reason。该端点属于 authoring
管理面，即使保持默认 `auth-scope=mutations`，也必须通过 `X-Foggy-Runtime-Code` 提交已配置的管理授权码。

inventory 是零写入诊断：缺失 root 不会被初始化，扫描不会 cleanup、repair、quarantine 或修改 mtime/content。
响应不会暴露 configured absolute root、内部 `storeId`、模型内容或凭据。`PROVABLY_UNREACHABLE_CANDIDATE`
只表示当前单进程一致快照中没有可证明引用，不等于允许删除；retention/grace、复核和真正 cleanup 需要独立交付。
遇到 corrupt、foreign、symlink、无法完整验证的中断 temporary/staging 或不完整引用图时，按
`UNKNOWN_PRESERVE` 保留并报告
blocked reason。外部进程写入、shared NFS 和跨 Runtime 协调不在该快照保证内。

### 可选 Production Promotion Mode

跨 Runtime 搬运模型时可显式启用 9.5.4 release package promotion。该能力默认关闭；生产 Runtime
必须同时配置管理授权码，并明确 opt in：

```yaml
foggy:
  runtime-api:
    enabled: true
    security-mode: auth-code
    auth-code: ${FOGGY_RUNTIME_API_AUTH_CODE}
    authoring-workspaces:
      production-promotion-enabled: true
```

启用后 `/api/v1/authoring/releases/import`、workspace `/promote`、`/rollback` 与
`/rollback/recover` 可用，普通 workspace `/publish` 在服务端被禁止。开发 Runtime 可保持默认配置，
从 exact validated workspace 的 `/release-package` 导出 JSON。package 只携带 TM/QM/FSScript 与安全
provenance；导入后 candidate 不可编辑，必须在目标 Runtime 重新 validate/query 后才可 promote。

v1 package 的 SHA-256 用于内容完整性，不是签名或用户身份。持有 management auth-code 并显式选择
target Namespace/Bundle 的操作者是信任根。rollback 只恢复 apply attempt 的直接前一 base，不提供
revision history 或任意历史选择；`ROLLBACK_REQUIRED` 时只能执行 pinned forward recovery。

### 可选 Runtime Web Console

Launcher 默认不包含 Runtime Console。构建时显式启用 Maven profile：

```bash
mvn -B -ntp -pl foggy-mcp-launcher -am -Pruntime-console package -DskipTests
```

运行时必须同时满足以下配置，否则 Console fail closed 并阻止应用启动：

```yaml
foggy:
  runtime-api:
    enabled: true
    security-mode: auth-code
    auth-code: ${FOGGY_RUNTIME_API_AUTH_CODE}
    auth-scope: management-all
  runtime-console:
    enabled: true
```

启动后访问 `http(s)://host[:port][/context]/console/`。Console 与 Runtime API 同源运行，不需要
Node、BFF 或独立前端服务；Node 只用于 Maven 构建静态资源。浏览器端管理 token 只存放在当前
tab 的 `sessionStorage`，不会进入 URL、`localStorage` 或诊断输出。部署方仍应使用 TLS、限制
管理网络入口并自行管理共享 auth-code 的生成和轮换。

如果 standalone `foggy-fsscript` 应用显式开启旧版 `/api/bundles/**` Controller，且没有接入 `foggy-runtime-api` 拦截器，部署侧需要自行保护该旧入口的网络访问边界。

### REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/bundles/list` | GET | 列出所有外部Bundle |
| `/api/bundles/add` | POST | 添加外部Bundle |
| `/api/bundles/remove/{bundleName}` | DELETE | 移除外部Bundle |
| `/api/bundles/exists/{bundleName}` | GET | 检查Bundle是否存在 |

### 添加 Bundle 示例

```bash
curl -X POST http://localhost:8080/api/bundles/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "dynamic-models",
    "namespace": "dev",
    "path": "/data/dynamic-models",
    "watch": true
  }'
```

### 移除 Bundle 示例

```bash
curl -X DELETE http://localhost:8080/api/bundles/remove/dynamic-models
```

### Bundle 配置参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 是 | Bundle 唯一标识 |
| `namespace` | String | 否 | 命名空间（默认为空） |
| `path` | String | 是 | Bundle 文件系统路径 |
| `watch` | Boolean | 否 | 是否监听文件变化（默认 false） |

### 注意事项

- Bundle 名称在全局必须唯一
- 移除 Bundle 不会删除文件系统中的文件
- 启用 `watch` 后，文件变更会自动重新加载
- 同名 Bundle 添加会失败，需先移除
