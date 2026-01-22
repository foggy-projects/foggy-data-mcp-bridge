# 语义层验证服务 - OpenHands 对接文档

> **功能**: 验证 TM/QM 文件的正确性
> **版本**: 1.0
> **服务地址**: http://localhost:7108

---

## 调用方式

### 方式1: REST API（推荐）

```bash
curl -X POST http://localhost:7108/api/semantic-layer/validate \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/path/to/models",
    "namespace": "openhands"
  }'
```

### 方式2: MCP Tool

```bash
curl -X POST http://localhost:7108/mcp/admin/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/call",
    "params": {
      "name": "semantic_layer.validate",
      "arguments": {
        "path": "/path/to/models",
        "namespace": "openhands"
      }
    }
  }'
```

### 方式3: 配置文件（生产环境）

```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: openhands-workspace
          namespace: openhands
          path: /path/to/models
          watch: true
```

---

## 输入参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `path` | string | 是 | - | 文件夹路径（会递归扫描所有 .tm/.qm 文件） |
| `namespace` | string | 否 | openhands | 命名空间（隔离不同环境） |
| `watch` | boolean | 否 | false | 是否监听文件变化 |
| `clearExisting` | boolean | 否 | true | 是否清除已存在的同名 Bundle |
| `includeStackTrace` | boolean | 否 | false | 是否返回堆栈跟踪 |

---

## 输出结果

### 成功示例

```json
{
  "success": true,
  "namespace": "openhands",
  "totalFiles": 8,
  "validFiles": 8,
  "invalidFiles": 0,
  "errors": [],
  "warnings": [],
  "durationMs": 1234
}
```

### 失败示例

```json
{
  "success": false,
  "namespace": "openhands",
  "totalFiles": 8,
  "validFiles": 6,
  "invalidFiles": 2,
  "errors": [
    {
      "file": "ProductModel.tm",
      "type": "TM",
      "message": "字段 'product_id' 在表中不存在",
      "code": "FIELD_NOT_FOUND",
      "suggestion": "请检查 tableName 配置或添加该字段"
    }
  ],
  "durationMs": 1234
}
```

---

## 验证规则

系统会验证：
- ✅ FSScript 语法正确性
- ✅ TM 模型定义完整性（表名、字段、维度、度量）
- ✅ QM 查询模型正确性（引用的 TM 是否存在）
- ✅ 字段引用有效性（字段是否在表中存在）
- ⚠️ 后续将增加更多范式校验和建议

---

## 常见错误

| 错误代码 | 说明 | 解决方案 |
|----------|------|----------|
| `FIELD_NOT_FOUND` | 字段在表中不存在 | 检查 fieldName 或 tableName 配置 |
| `MODEL_NOT_FOUND` | QM 引用的 TM 不存在 | 确认 TM 文件已创建 |
| `SYNTAX_ERROR` | FSScript 语法错误 | 检查 JavaScript 语法 |

---

## 快速测试

### Python 示例

```python
import requests

def validate_models(path):
    response = requests.post(
        "http://localhost:7108/api/semantic-layer/validate",
        json={"path": path, "namespace": "openhands"}
    )
    result = response.json()

    if result["success"]:
        print(f"✅ 验证通过: {result['validFiles']}/{result['totalFiles']} 文件")
    else:
        print(f"❌ 验证失败: {len(result['errors'])} 个错误")
        for error in result["errors"]:
            print(f"  - {error['file']}: {error['message']}")

    return result

# 使用
result = validate_models("/data/openhands-workspace/models")
```

### cURL 测试

```bash
# 验证
curl -X POST http://localhost:7108/api/semantic-layer/validate \
  -H "Content-Type: application/json" \
  -d '{"path":"/path/to/models","namespace":"openhands"}'

# 健康检查
curl http://localhost:7108/api/semantic-layer/health

# 列出已注册的 Bundle
curl http://localhost:7108/api/semantic-layer/bundles
```

---

## 注意事项

- 文件必须使用 `.tm` 或 `.qm` 后缀
- 系统会递归扫描目录，不限制目录结构
- 相同 namespace 的 Bundle 会被替换
- 验证失败不影响已存在的其他 Bundle

---

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/semantic-layer/validate` | POST | 验证文件夹 |
| `/api/semantic-layer/bundles` | GET | 列出已注册 Bundle |
| `/api/semantic-layer/bundles/{name}` | DELETE | 移除 Bundle |
| `/api/semantic-layer/health` | GET | 健康检查 |

---

## 联系支持

- GitHub: https://github.com/foggy-projects/java-data-mcp-bridge
- Issue: https://github.com/foggy-projects/java-data-mcp-bridge/issues
