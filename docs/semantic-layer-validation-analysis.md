# OpenHands 语义层验证集成方案

## 一、需求分析

### 1.1 核心需求
- 使用 OpenHands 编写语义层文件（TM/QM 模型）
- 将编写完成的文件夹传递给 foggy-dataset-model 进行验证
- 获取验证结果反馈给 OpenHands

### 1.2 典型使用场景
```
OpenHands (编写TM/QM) → 保存到目录 → foggy-dataset-model (验证) → 返回验证结果
```

---

## 二、可行性分析 ✅ **完全可行**

### 2.1 系统现有能力

#### ✅ 已支持外部 Bundle 加载
系统已实现 `ExternalBundleDefinition` 和 `ExternalBundleProperties`，支持从文件系统动态加载模型。

**配置示例**：
```yaml
foggy:
  bundle:
    external:
      enabled: true
      bundles:
        - name: openhands-models
          namespace: dev
          path: /data/openhands-workspace/models
          watch: true
```

**目录结构**：
```
/data/openhands-workspace/models/
├── model/
│   ├── ProductModel.tm
│   ├── SalesModel.tm
│   └── CustomerModel.tm
├── query/
│   ├── ProductQuery.qm
│   └── SalesQuery.qm
└── dicts.fsscript
```

#### ✅ 已有验证机制
- **QmValidationOnStartup**: 启动时验证所有 QM 文件
- **TableModelLoaderManager**: TM 加载器，内置验证逻辑
- **QueryModelLoader**: QM 加载器，内置验证逻辑

#### ✅ 已有错误处理
- 加载失败时抛出详细异常信息
- 包含行号、字段名、错误原因等详细信息

---

## 三、实施方案（三选一）

### 方案 1：配置文件方式（最简单） ⭐ **推荐用于开发环境**

#### 优点
- 无需编码，配置即可使用
- 支持文件监听，自动重载
- 适合开发调试

#### 实施步骤
1. **OpenHands 配置工作目录**
   ```bash
   # OpenHands 工作目录
   WORKSPACE=/data/openhands-workspace
   ```

2. **foggy-dataset-mcp 配置外部 Bundle**
   ```yaml
   # application.yml
   foggy:
     bundle:
       external:
         enabled: true
         bundles:
           - name: openhands-workspace
             namespace: openhands
             path: ${OPENHANDS_WORKSPACE:/data/openhands-workspace}/models
             watch: true
     dataset:
       validate-on-startup: true  # 启动时验证
   ```

3. **OpenHands 编写完成后触发验证**
   ```bash
   # 方式1: 重启服务（触发启动验证）
   curl -X POST http://localhost:7108/actuator/refresh

   # 方式2: 调用验证接口（需要实现方案2）
   curl -X POST http://localhost:7108/api/validate/namespace/openhands
   ```

#### 缺点
- 需要手动触发验证（或重启服务）
- 无法动态注册/卸载

---

### 方案 2：REST API + MCP Tool（最灵活） ⭐ **推荐用于生产环境**

#### 优点
- OpenHands 可通过 MCP Tool 调用验证
- 动态注册/卸载 Bundle
- 获取实时验证结果
- 支持增量验证

#### 架构设计

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────────┐
│  OpenHands  │  MCP    │  foggy-dataset-  │  调用   │  TableModel     │
│             │────────>│  mcp             │────────>│  Loader         │
│             │  Tool   │                  │         │  Manager        │
└─────────────┘         └──────────────────┘         └─────────────────┘
                                │
                                │ 返回验证结果
                                ▼
                        ┌──────────────────┐
                        │  ValidationResult│
                        │  - success       │
                        │  - errors[]      │
                        │  - warnings[]    │
                        └──────────────────┘
```

#### 需要实现的组件

##### 1. REST API Controller
```java
@RestController
@RequestMapping("/api/semantic-layer")
public class SemanticLayerValidationController {

    /**
     * 注册外部语义层目录
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResult> registerExternalBundle(
        @RequestBody RegisterRequest request
    ) {
        // request.path: 外部目录路径
        // request.namespace: 命名空间
        // request.watch: 是否监听文件变化
    }

    /**
     * 验证指定命名空间的所有模型
     */
    @PostMapping("/validate/{namespace}")
    public ResponseEntity<ValidationResult> validateNamespace(
        @PathVariable String namespace
    ) {
        // 验证所有 TM 和 QM 文件
        // 返回详细的验证结果
    }

    /**
     * 验证单个模型文件
     */
    @PostMapping("/validate/{namespace}/{modelName}")
    public ResponseEntity<ValidationResult> validateModel(
        @PathVariable String namespace,
        @PathVariable String modelName,
        @RequestParam(required = false) String type  // tm 或 qm
    ) {
        // 验证单个文件
    }

    /**
     * 卸载外部 Bundle
     */
    @DeleteMapping("/unregister/{namespace}")
    public ResponseEntity<Void> unregisterBundle(
        @PathVariable String namespace
    ) {
        // 卸载指定命名空间的 Bundle
    }

    /**
     * 列出所有已注册的外部 Bundle
     */
    @GetMapping("/bundles")
    public ResponseEntity<List<BundleInfo>> listBundles() {
        // 返回所有外部 Bundle 信息
    }
}
```

##### 2. MCP Tool
```java
@Component
public class SemanticLayerValidationTool implements McpTool {

    @Override
    public String getName() {
        return "semantic_layer.validate";
    }

    @Override
    public String getDescription() {
        return "验证语义层模型文件（TM/QM）的正确性";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "path", Map.of(
                    "type", "string",
                    "description", "语义层文件夹路径"
                ),
                "namespace", Map.of(
                    "type", "string",
                    "description", "命名空间（默认：openhands）",
                    "default", "openhands"
                ),
                "watch", Map.of(
                    "type", "boolean",
                    "description", "是否监听文件变化",
                    "default", false
                )
            ),
            "required", List.of("path")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String path = (String) arguments.get("path");
        String namespace = (String) arguments.getOrDefault("namespace", "openhands");
        boolean watch = (Boolean) arguments.getOrDefault("watch", false);

        // 1. 注册外部 Bundle
        // 2. 验证所有 TM/QM 文件
        // 3. 返回详细的验证结果

        return Map.of(
            "success", true,
            "namespace", namespace,
            "totalFiles", 10,
            "validFiles", 8,
            "errors", List.of(
                Map.of(
                    "file", "ProductModel.tm",
                    "line", 15,
                    "message", "未找到字段：product_id"
                )
            ),
            "warnings", List.of(
                Map.of(
                    "file", "SalesQuery.qm",
                    "message", "建议添加索引字段"
                )
            )
        );
    }
}
```

##### 3. 验证服务
```java
@Service
public class SemanticLayerValidationService {

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    /**
     * 动态注册外部 Bundle
     */
    public void registerExternalBundle(String name, String namespace, String path, boolean watch) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
            name, namespace, path, watch
        );

        // 注册到系统
        systemBundlesContext.registerExternalBundle(definition);
    }

    /**
     * 验证指定命名空间下的所有模型
     */
    public ValidationResult validateNamespace(String namespace) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationWarning> warnings = new ArrayList<>();

        // 1. 查找所有 TM 文件
        List<BundleResource> tmFiles = findTmFiles(namespace);
        for (BundleResource tmFile : tmFiles) {
            try {
                String modelName = extractModelName(tmFile);
                tableModelLoaderManager.load(modelName, namespace);
            } catch (Exception e) {
                errors.add(new ValidationError(tmFile, e));
            }
        }

        // 2. 查找所有 QM 文件
        List<BundleResource> qmFiles = findQmFiles(namespace);
        for (BundleResource qmFile : qmFiles) {
            try {
                queryModelLoader.loadJdbcQueryModel(qmFile);
            } catch (Exception e) {
                errors.add(new ValidationError(qmFile, e));
            }
        }

        return new ValidationResult(
            errors.isEmpty(),
            tmFiles.size() + qmFiles.size(),
            errors,
            warnings
        );
    }

    /**
     * 卸载外部 Bundle
     */
    public void unregisterBundle(String namespace) {
        systemBundlesContext.unregisterBundle(namespace);
        tableModelLoaderManager.clearAll();  // 清除缓存
    }
}
```

#### OpenHands 使用示例

```python
# OpenHands 编写完 TM/QM 文件后
result = mcp_client.call_tool(
    "semantic_layer.validate",
    {
        "path": "/workspace/models",
        "namespace": "openhands",
        "watch": False
    }
)

if result["success"]:
    if result["errors"]:
        print(f"验证失败，发现 {len(result['errors'])} 个错误：")
        for error in result["errors"]:
            print(f"  {error['file']}:{error['line']} - {error['message']}")
    else:
        print(f"验证成功！共 {result['validFiles']} 个文件")
else:
    print(f"验证异常: {result['message']}")
```

---

### 方案 3：文件上传方式（最独立）

#### 优点
- 无需共享文件系统
- 支持远程调用
- 适合云环境

#### 实施步骤
1. **实现文件上传接口**
   ```java
   @PostMapping("/upload-and-validate")
   public ResponseEntity<ValidationResult> uploadAndValidate(
       @RequestParam("file") MultipartFile zipFile,
       @RequestParam("namespace") String namespace
   ) {
       // 1. 解压 zip 到临时目录
       // 2. 注册临时 Bundle
       // 3. 验证
       // 4. 清理临时文件
   }
   ```

2. **OpenHands 打包上传**
   ```bash
   cd /workspace/models
   zip -r models.zip .
   curl -F "file=@models.zip" \
        -F "namespace=openhands" \
        http://localhost:7108/api/semantic-layer/upload-and-validate
   ```

#### 缺点
- 需要压缩/解压
- 临时文件管理复杂
- 性能开销较大

---

## 四、推荐方案对比

| 特性 | 方案1（配置） | 方案2（API+Tool） | 方案3（上传） |
|------|--------------|------------------|--------------|
| 实施难度 | ⭐ 最简单 | ⭐⭐ 中等 | ⭐⭐⭐ 较复杂 |
| 灵活性 | ⭐⭐ 低 | ⭐⭐⭐ 高 | ⭐⭐⭐ 高 |
| 性能 | ⭐⭐⭐ 最优 | ⭐⭐⭐ 优 | ⭐⭐ 一般 |
| OpenHands集成 | ⭐⭐ 需脚本 | ⭐⭐⭐ MCP Tool | ⭐⭐ HTTP API |
| 文件监听 | ✅ 支持 | ✅ 支持 | ❌ 不支持 |
| 动态管理 | ❌ | ✅ | ✅ |
| 共享文件系统 | 需要 | 需要 | 不需要 |

---

## 五、最终推荐

### 🎯 短期方案（1-2天实施）
**方案1（配置文件）+ 简化的验证接口**

**实施清单**：
1. ✅ 配置外部 Bundle（0.5天）
2. 🆕 实现 `/api/validate/{namespace}` 接口（1天）
3. 🆕 提供 Shell 脚本供 OpenHands 调用（0.5天）

**优点**：
- 快速上线，立即可用
- 代码改动最小
- 风险低

---

### 🎯 长期方案（3-5天实施）
**方案2（完整 REST API + MCP Tool）**

**实施清单**：
1. 🆕 实现 `SemanticLayerValidationController`（1.5天）
2. 🆕 实现 `SemanticLayerValidationService`（1.5天）
3. 🆕 实现 `SemanticLayerValidationTool`（1天）
4. ✅ 单元测试和集成测试（1天）

**优点**：
- 功能完善，可扩展
- OpenHands 直接通过 MCP 调用
- 支持动态管理

---

## 六、技术细节补充

### 6.1 验证结果格式
```json
{
  "success": true,
  "namespace": "openhands",
  "totalFiles": 12,
  "validFiles": 10,
  "invalidFiles": 2,
  "errors": [
    {
      "file": "ProductModel.tm",
      "type": "TM",
      "line": 15,
      "column": 8,
      "severity": "ERROR",
      "code": "FIELD_NOT_FOUND",
      "message": "维度引用的字段 'product_id' 在表中不存在",
      "suggestion": "请检查 tableName 配置或添加该字段"
    }
  ],
  "warnings": [
    {
      "file": "SalesQuery.qm",
      "type": "QM",
      "severity": "WARNING",
      "code": "MISSING_INDEX",
      "message": "查询涉及大量数据，建议添加索引字段",
      "suggestion": "在 defaultSort 中添加索引字段"
    }
  ],
  "timestamp": "2026-01-21T20:00:00Z"
}
```

### 6.2 SystemBundlesContext 扩展需求
需要添加以下方法（如果不存在）：

```java
public interface SystemBundlesContext {
    /**
     * 动态注册外部 Bundle
     */
    void registerExternalBundle(ExternalBundleDefinition definition);

    /**
     * 卸载指定命名空间的 Bundle
     */
    void unregisterBundle(String namespace);

    /**
     * 获取指定命名空间的 Bundle
     */
    Bundle getBundleByNamespace(String namespace);
}
```

### 6.3 错误信息国际化
验证错误信息应支持中英文：

```properties
# i18n/messages.properties
validation.field.not.found=Field ''{0}'' not found in table ''{1}''
validation.dimension.invalid=Invalid dimension definition: {0}
validation.measure.aggregation.invalid=Invalid aggregation type ''{0}'' for measure ''{1}''

# i18n/messages_zh_CN.properties
validation.field.not.found=字段 ''{0}'' 在表 ''{1}'' 中不存在
validation.dimension.invalid=无效的维度定义: {0}
validation.measure.aggregation.invalid=度量 ''{1}'' 的聚合类型 ''{0}'' 无效
```

---

## 七、风险评估

| 风险项 | 等级 | 缓解措施 |
|--------|------|---------|
| 外部文件路径安全 | 🟡 中 | 路径白名单验证，禁止 `../` |
| 并发注册冲突 | 🟡 中 | 添加分布式锁或命名空间互斥 |
| 大文件加载性能 | 🟢 低 | 异步验证 + 进度反馈 |
| 文件监听资源占用 | 🟢 低 | 生产环境禁用 watch |

---

## 八、开发计划（方案2）

### Phase 1: 核心验证服务（2天）
- [ ] 实现 `SemanticLayerValidationService`
- [ ] 扩展 `SystemBundlesContext`（如需要）
- [ ] 单元测试

### Phase 2: REST API（1天）
- [ ] 实现 `SemanticLayerValidationController`
- [ ] API 文档（Swagger）
- [ ] 集成测试

### Phase 3: MCP Tool（1天）
- [ ] 实现 `SemanticLayerValidationTool`
- [ ] 工具注册和权限配置
- [ ] E2E 测试

### Phase 4: 文档和示例（1天）
- [ ] OpenHands 集成文档
- [ ] 示例代码和脚本
- [ ] 部署指南

---

## 九、示例代码片段

### 动态注册 Bundle
```java
// 注册外部 Bundle
ExternalBundleDefinition definition = new ExternalBundleDefinition(
    "openhands-workspace",
    "openhands",
    "/data/openhands-workspace/models",
    true  // 启用文件监听
);

systemBundlesContext.registerExternalBundle(definition);
```

### 验证单个模型
```java
try {
    // 加载 TM 模型
    TableModel model = tableModelLoaderManager.load("ProductModel", "openhands");

    // 如果加载成功，说明验证通过
    return ValidationResult.success(model);

} catch (Exception e) {
    // 解析异常信息
    return ValidationResult.error(
        "ProductModel.tm",
        extractLineNumber(e),
        e.getMessage()
    );
}
```

---

## 十、结论

✅ **需求完全可行**

推荐采用 **方案2（REST API + MCP Tool）**：
- 短期：先实现核心验证接口
- 长期：完善 MCP Tool 和动态管理功能

**预计开发时间**：3-5 天
**技术风险**：低
**用户体验**：优秀（OpenHands 可直接通过 MCP Tool 调用）

是否开始实施？如果确认，我可以立即开始编写代码。
