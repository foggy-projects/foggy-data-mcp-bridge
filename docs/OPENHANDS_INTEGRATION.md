# 语义层验证服务 - OpenHands对接文档

> **版本**: 1.0 (配置文件方式)
> **状态**: 生产就绪
> **更新日期**: 2026-01-21

---

## 概述

本文档介绍如何将 OpenHands 与 Foggy Dataset Model 的语义层验证服务进行集成。当前版本使用**配置文件方式**进行集成，为最简单、最稳定的方案。

### 功能说明

- ✅ 验证TM（表模型）文件的语法和结构
- ✅ 验证QM（查询模型）文件的正确性
- ✅ 检查字段引用、表结构、度量定义等
- ✅ 返回详细的错误信息和建议
- ✅ 支持文件监听，自动重载

---

## 集成方案

### 方案选择

| 方案 | 状态 | 适用场景 | 实施复杂度 |
|------|------|---------|------------|
| **配置文件方式** | ✅ 当前版本 | 开发环境、测试环境 | ⭐ 简单 |
| MCP Tool | 🚧 下一版本 | AI工具直接调用 | ⭐⭐ 中等 |
| REST API | 🚧 下一版本 | 独立服务、云环境 | ⭐⭐⭐ 较复杂 |

**推荐使用配置文件方式**：最简单、最稳定，适合当前版本。

---

## 快速开始

### 第1步：准备语义层文件目录

OpenHands需要将编写好的语义层文件保存到指定目录结构：

```
/data/openhands-workspace/models/
├── model/                      # TM文件目录
│   ├── ProductModel.tm         # 产品模型
│   ├── SalesModel.tm           # 销售模型
│   └── CustomerModel.tm        # 客户模型
├── query/                      # QM文件目录
│   ├── ProductQuery.qm         # 产品查询模型
│   └── SalesQuery.qm           # 销售查询模型
└── dicts.fsscript              # 字典文件（可选）
```

**注意**：
- 文件必须使用 `.tm` 或 `.qm` 后缀
- TM文件放在 `model/` 目录下
- QM文件放在 `query/` 目录下

---

### 第2步：配置Foggy Dataset MCP服务

在 `application.yml` 中添加外部Bundle配置：

```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: openhands-workspace        # Bundle名称（唯一标识）
          namespace: openhands               # 命名空间（隔离不同环境）
          path: /data/openhands-workspace/models  # 文件目录路径
          watch: true                        # 监听文件变化，自动重载

  dataset:
    validate-on-startup: true                # 启动时验证所有模型
```

**配置说明**：
- `name`: Bundle的唯一标识，建议使用 `openhands-workspace`
- `namespace`: 命名空间，用于隔离不同环境的模型，默认 `openhands`
- `path`: OpenHands保存语义层文件的目录路径
- `watch`: 是否监听文件变化（开发环境建议开启）
- `validate-on-startup`: 启动时验证，及早发现错误

---

### 第3步：Docker部署配置

如果使用Docker部署，需要挂载目录：

```bash
docker run -d \
  -v /host/openhands-workspace:/data/openhands-workspace \
  -e FOGGY_BUNDLE_EXTERNAL_ENABLED=true \
  -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_NAME=openhands-workspace \
  -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_NAMESPACE=openhands \
  -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_PATH=/data/openhands-workspace/models \
  -e FOGGY_BUNDLE_EXTERNAL_BUNDLES_0_WATCH=true \
  -p 7108:7108 \
  foggy-dataset-mcp:latest
```

---

### 第4步：验证配置

#### 4.1 启动服务

```bash
# 启动服务
java -jar foggy-dataset-mcp.jar

# 观察日志，确认Bundle加载成功
# 应该看到类似输出：
# INFO  - External bundle loaded: name=openhands-workspace, namespace=openhands
# INFO  - Found 5 TM files
# INFO  - Found 3 QM files
```

#### 4.2 检查健康状态

```bash
curl http://localhost:7108/api/semantic-layer/health

# 期望输出：
{
  "status": "UP",
  "service": "semantic-layer-validation",
  "timestamp": 1737468000000
}
```

#### 4.3 查看已注册的Bundle

```bash
curl http://localhost:7108/api/semantic-layer/bundles

# 期望输出：
[
  {
    "name": "openhands-workspace",
    "packageName": "external.openhands-workspace"
  }
]
```

---

## 使用方式

### 方式1：启动时自动验证（推荐）

配置 `foggy.dataset.validate-on-startup: true` 后，服务启动时会自动验证所有文件。

**验证失败时**：
```
ERROR - QM 启动校验失败: 2 个文件有错误
ERROR - QM [ProductModel.tm]: 字段 'product_id' 在表中不存在
ERROR - QM [SalesQuery.qm]: 引用的模型 'InvalidModel' 未找到
```

服务会阻止启动，强制修复错误。

---

### 方式2：文件监听自动验证

配置 `watch: true` 后，修改文件会自动触发验证。

**日志示例**：
```
INFO  - File changed: ProductModel.tm
INFO  - Reloading model: ProductModel
INFO  - Model loaded successfully: ProductModel
```

---

### 方式3：调用验证API（当前版本不可用）

**注意**：当前版本暂不支持REST API调用，下一版本将支持。

```bash
# 未来版本支持
curl -X POST http://localhost:7108/api/semantic-layer/validate \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/data/openhands-workspace/models",
    "namespace": "openhands",
    "watch": false
  }'
```

---

## 验证结果说明

### 成功示例

服务启动日志：
```
INFO  - QM 启动校验开始
INFO  - 找到 8 个 QM 文件
INFO  - 校验结果: 成功 8, 失败 0
INFO  - QM 启动校验完成
```

### 失败示例

#### 错误1：字段不存在
```
ERROR - TM文件验证失败: file=ProductModel.tm
ERROR - 字段 'product_id' 在表 'dim_product' 中不存在
```

**原因**：TM文件中定义的维度字段在数据库表中不存在

**解决方案**：
1. 检查 `tableName` 配置是否正确
2. 检查字段名是否拼写正确
3. 确认数据库表确实存在该字段

#### 错误2：引用模型未找到
```
ERROR - QM文件验证失败: file=SalesQuery.qm
ERROR - 引用的模型 'ProductModel' 未找到
```

**原因**：QM文件引用了不存在的TM模型

**解决方案**：
1. 确认TM文件已创建并加载
2. 检查模型名称是否拼写正确
3. 确认TM文件语法正确

#### 错误3：语法错误
```
ERROR - TM文件验证失败: file=CustomerModel.tm
ERROR - Unexpected token 'dimensions' at line 15
```

**原因**：FSScript语法错误

**解决方案**：
1. 检查JavaScript/FSScript语法
2. 确认大括号、引号是否匹配
3. 参考示例文件的格式

---

## OpenHands集成流程

### 推荐工作流程

```
┌─────────────────┐
│  1. OpenHands   │
│  编写TM/QM文件  │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  2. 保存到指定   │
│  目录结构        │
│  /models/model/  │
│  /models/query/  │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  3. Foggy服务   │
│  自动检测文件变化 │
│  (如果watch=true) │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  4. 自动验证     │
│  加载模型        │
└────────┬────────┘
         │
    ┌────┴────┐
    ↓         ↓
┌───────┐ ┌───────┐
│ 成功  │ │ 失败  │
└───────┘ └───┬───┘
              ↓
      ┌──────────────┐
      │ 返回错误信息  │
      │ OpenHands修复 │
      └──────────────┘
```

### 实施步骤

#### Step 1: OpenHands编写完成后保存文件

```python
# OpenHands代码示例
def save_semantic_layer_files(files_dict):
    """
    保存TM/QM文件到指定目录

    files_dict = {
        'ProductModel.tm': '<tm_content>',
        'SalesModel.tm': '<tm_content>',
        'ProductQuery.qm': '<qm_content>'
    }
    """
    base_dir = '/data/openhands-workspace/models'

    for filename, content in files_dict.items():
        if filename.endswith('.tm'):
            target_dir = f'{base_dir}/model'
        elif filename.endswith('.qm'):
            target_dir = f'{base_dir}/query'
        else:
            continue

        os.makedirs(target_dir, exist_ok=True)

        file_path = f'{target_dir}/{filename}'
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)

        print(f'保存文件: {file_path}')
```

#### Step 2: 等待验证结果

如果启用了文件监听（`watch: true`），服务会自动验证。

```python
import time
import requests

def wait_for_validation():
    """
    等待服务验证完成（简单轮询方式）
    """
    # 给服务一些时间来检测文件变化
    time.sleep(2)

    # 检查健康状态
    response = requests.get('http://localhost:7108/api/semantic-layer/health')
    if response.status_code == 200:
        print('验证服务运行正常')
        return True
    else:
        print('验证服务异常')
        return False
```

#### Step 3: 检查日志获取验证结果

```python
import subprocess

def check_validation_logs():
    """
    检查服务日志，查找验证错误
    """
    # 假设使用docker
    result = subprocess.run(
        ['docker', 'logs', 'foggy-dataset-mcp', '--tail', '100'],
        capture_output=True,
        text=True
    )

    logs = result.stdout

    # 查找错误信息
    errors = []
    for line in logs.split('\n'):
        if 'ERROR' in line and ('验证失败' in line or 'validation failed' in line):
            errors.append(line)

    if errors:
        print('发现验证错误：')
        for error in errors:
            print(f'  - {error}')
        return False
    else:
        print('验证通过')
        return True
```

---

## 故障排查

### 问题1：Bundle未加载

**症状**：
```
WARN - Bundle not found: openhands-workspace
```

**解决方案**：
1. 检查 `application.yml` 配置是否正确
2. 确认 `foggy.bundle.external.enabled: true`
3. 检查路径是否存在且可访问
4. 重启服务

### 问题2：文件未被检测到

**症状**：
```
INFO - Found 0 TM files
```

**解决方案**：
1. 确认文件使用 `.tm` 或 `.qm` 后缀
2. 确认文件在正确的目录下（`model/` 或 `query/`）
3. 检查文件权限
4. 如果watch未生效，重启服务

### 问题3：验证失败但无错误信息

**症状**：
服务启动失败，但日志不够详细

**解决方案**：
1. 设置日志级别为DEBUG：
   ```yaml
   logging:
     level:
       com.foggyframework: DEBUG
   ```

2. 重启服务查看详细日志

### 问题4：模型加载后无法使用

**症状**：
验证通过，但查询时找不到模型

**解决方案**：
1. 检查namespace是否正确
2. 确认调用时使用正确的namespace
3. 清除缓存后重试：
   ```bash
   curl -X POST http://localhost:7108/api/cache/clear
   ```

---

## 配置参考

### 完整配置示例

```yaml
# application.yml

server:
  port: 7108

foggy:
  # 外部Bundle配置
  bundle:
    external:
      enabled: true
      bundles:
        # OpenHands工作空间
        - name: openhands-workspace
          namespace: openhands
          path: /data/openhands-workspace/models
          watch: true

        # 可配置多个Bundle
        - name: custom-models
          namespace: custom
          path: /data/custom-models
          watch: false

  # 数据集配置
  dataset:
    # 启动时验证
    validate-on-startup: true

    # 数据源配置（示例）
    datasource:
      default:
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://localhost:3306/foggy_demo
        username: root
        password: ${DB_PASSWORD:password}

# 日志配置
logging:
  level:
    root: INFO
    com.foggyframework: INFO
    com.foggyframework.dataset.mcp.validation: DEBUG  # 验证服务详细日志
```

---

## API参考（未来版本）

当前版本仅支持配置文件方式，以下API在下一版本将可用：

### POST /api/semantic-layer/validate

验证语义层文件夹

**请求**：
```json
{
  "path": "/data/openhands-workspace/models",
  "namespace": "openhands",
  "watch": false,
  "clearExisting": true,
  "includeStackTrace": false
}
```

**响应（成功）**：
```json
{
  "success": true,
  "namespace": "openhands",
  "totalFiles": 8,
  "validFiles": 8,
  "invalidFiles": 0,
  "errors": [],
  "warnings": [],
  "timestamp": "2026-01-21T20:00:00Z",
  "durationMs": 1234
}
```

**响应（失败）**：
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
      "line": 15,
      "column": 8,
      "severity": "ERROR",
      "code": "FIELD_NOT_FOUND",
      "message": "字段 'product_id' 在表中不存在",
      "suggestion": "请检查 tableName 配置或添加该字段"
    }
  ],
  "warnings": [],
  "timestamp": "2026-01-21T20:00:00Z",
  "durationMs": 1234
}
```

---

## 常见问题

### Q1: 如何知道验证是否成功？

**A**: 检查以下几点：
1. 服务启动日志中有 "QM 启动校验完成"
2. 没有ERROR级别的日志
3. 健康检查返回 `{"status": "UP"}`

### Q2: 验证失败后如何修复？

**A**:
1. 查看ERROR日志，找到具体错误
2. 根据错误信息修改TM/QM文件
3. 保存文件（如果watch=true会自动重载）
4. 或重启服务重新验证

### Q3: 如何在生产环境使用？

**A**:
1. 生产环境建议关闭watch（`watch: false`）
2. 使用CI/CD流程部署前验证
3. 配置监控告警
4. 定期备份语义层文件

### Q4: 支持哪些数据库？

**A**: 支持所有Foggy Dataset支持的数据库：
- MySQL 5.7+
- PostgreSQL 12+
- SQL Server 2012+
- SQLite 3.30+
- MongoDB（需addon模块）

---

## 下一步计划

### 版本 1.1（计划中）

- ✅ REST API直接调用
- ✅ MCP Tool支持
- ✅ 动态Bundle注册/卸载
- ✅ 更详细的错误信息和建议
- ✅ 验证结果持久化
- ✅ 验证报告导出

### 版本 1.2（规划中）

- 增量验证（仅验证修改的文件）
- 验证性能优化
- Web UI管理界面
- Webhook通知

---

## 联系支持

如遇问题，请联系：

- **技术支持**: [技术支持渠道]
- **文档**: https://foggy-framework.com/docs
- **GitHub**: https://github.com/foggy-projects/java-data-mcp-bridge
- **Issue**: https://github.com/foggy-projects/java-data-mcp-bridge/issues

---

## 附录

### A. TM文件示例

```javascript
export const model = {
    name: 'ProductModel',
    tableName: 'dim_product',
    caption: '产品模型',

    dimensions: [
        {
            name: 'product',
            caption: '产品',
            fieldName: 'product_id',
            properties: [
                { name: 'name', caption: '产品名称', fieldName: 'product_name' },
                { name: 'category', caption: '分类', fieldName: 'category_name' }
            ]
        }
    ],

    measures: [
        {
            name: 'price',
            caption: '价格',
            fieldName: 'price',
            aggregation: 'AVG'
        }
    ]
};
```

### B. QM文件示例

```javascript
export const model = {
    name: 'ProductQuery',
    caption: '产品查询',
    modelName: 'ProductModel',

    defaultColumns: ['product', 'product.name', 'price'],
    defaultSort: [{ column: 'product', direction: 'ASC' }]
};
```

### C. 目录结构完整示例

```
/data/openhands-workspace/
└── models/
    ├── model/
    │   ├── ProductModel.tm
    │   ├── SalesModel.tm
    │   ├── CustomerModel.tm
    │   └── OrderModel.tm
    ├── query/
    │   ├── ProductQuery.qm
    │   ├── SalesQuery.qm
    │   └── CustomerQuery.qm
    └── dicts.fsscript
```

---

**文档版本**: 1.0
**生成时间**: 2026-01-21
**维护者**: Foggy Framework Team
