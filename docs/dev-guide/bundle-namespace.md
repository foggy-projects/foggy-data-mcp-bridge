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

新接入建议优先使用 `foggy-runtime-api` 的 `/api/v1/bundles`，并通过 `foggy.runtime-api.auth-code` 为管理写操作配置授权码。

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
