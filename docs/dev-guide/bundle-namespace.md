# Bundle 和 Namespace 管理

## Namespace 支持（命名空间隔离）

通过 HTTP Header `X-NS` 传递命名空间，支持同一服务下多环境模型隔离。

### 命名空间规则

- 未传或传空字符串：使用默认命名空间
- 传 `dev`：查询 dev 命名空间下的模型（如 `dev:OrderModel`）
- 传 `test`：查询 test 命名空间下的模型（如 `test:OrderModel`）

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

查询入口只需要稳定传递 namespace，不需要额外传递单位开关。

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
