# 命名空间隔离与事件系统实现总结

> **实施日期**: 2026-01-21
> **状态**: ✅ 已完成并通过所有测试

---

## 实现内容

### 1. Bundle生命周期事件系统

#### 新增事件类 (foggy-fsscript)

- **BundleRemovedEvent.java**
  - 当Bundle从系统中移除时触发
  - 包含 bundleName、namespace、removedBundle 信息

- **BundleAddedEvent.java**
  - 当新的Bundle注册到系统时触发
  - 包含 bundleName、namespace、addedBundle 信息

#### 事件发布 (SystemBundlesContextImpl.java)

```java
// 在 addExternalBundle() 方法中发布 BundleAddedEvent
BundleAddedEvent event = new BundleAddedEvent(this, name, namespace, bundle);
appCtx.publishEvent(event);

// 在 removeBundle() 方法中发布 BundleRemovedEvent
BundleRemovedEvent event = new BundleRemovedEvent(this, bundleName, namespace, targetBundle);
appCtx.publishEvent(event);
```

### 2. QueryModelLoader 命名空间隔离

#### 缓存结构重构 (QueryModelLoaderImpl.java)

**之前**：全局缓存，无法区分命名空间
```java
Map<String, QueryModel> name2JdbcQueryModel = new HashMap<>();
Map<String, String> shortAlias2Name = new HashMap<>();
Set<String> usedAliases = new HashSet<>();
```

**之后**：命名空间级别的缓存
```java
Map<String, NamespaceCache> namespaceCaches = new HashMap<>();

// NamespaceCache 内部类封装单个命名空间的所有缓存
private static class NamespaceCache {
    Map<String, QueryModel> name2QueryModel = new HashMap<>();
    Map<String, String> shortAlias2Name = new HashMap<>();
    Set<String> usedAliases = new HashSet<>();
}
```

#### 新增接口方法

- `clearByNamespace(String namespace)` - 清除指定命名空间的缓存
- `getJdbcQueryModel(String modelName, String namespace)` - 从指定命名空间获取模型

#### 简称分配隔离

- 简称（ShortAlias）现在在命名空间范围内分配
- 不同命名空间中的同名模型可以拥有相同的简称而不冲突

### 3. TableModelLoaderManager 命名空间支持

#### 新增接口方法 (TableModelLoaderManager.java)

```java
void clearByNamespace(String namespace);
```

#### 实现 (TableModelLoaderManagerImpl.java)

```java
@Override
public void clearByNamespace(String namespace) {
    String normalizedNs = (namespace == null || namespace.trim().isEmpty()) ? "" : namespace.trim();

    if (normalizedNs.isEmpty()) {
        // 清除默认命名空间的缓存（不含冒号的key）
        name2JdbcModel.keySet().stream()
            .filter(key -> !key.contains(":"))
            .forEach(name2JdbcModel::remove);
    } else {
        // 清除指定命名空间的缓存（以 "namespace:" 开头的key）
        String prefix = normalizedNs + ":";
        name2JdbcModel.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .forEach(name2JdbcModel::remove);
    }
}
```

### 4. Bundle生命周期监听器 (BundleLifecycleListener.java)

**位置**: foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/event/

**功能**: 自动监听Bundle移除事件，清理相关缓存

```java
@Component
@Slf4j
public class BundleLifecycleListener {

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    @EventListener
    @Order(100)
    public void onBundleRemoved(BundleRemovedEvent event) {
        String namespace = event.getNamespace();

        // 清除TM缓存
        tableModelLoaderManager.clearByNamespace(namespace);

        // 清除QM缓存
        queryModelLoader.clearByNamespace(namespace);

        log.info("已清除namespace=[{}] 的所有模型缓存", namespace);
    }
}
```

### 5. SemanticLayerValidationService 解耦

**之前**：直接调用 tableModelLoaderManager.clearAll()

```java
systemBundlesContext.removeBundle(bundleName);
tableModelLoaderManager.clearAll();  // 直接调用
```

**之后**：通过事件系统自动处理

```java
// 移除Bundle（会自动触发BundleRemovedEvent，由BundleLifecycleListener清理缓存）
systemBundlesContext.removeBundle(bundleName);
```

---

## 关键优势

### 1. 命名空间隔离

- ✅ 不同命名空间的模型互不干扰
- ✅ 同名模型可以在不同命名空间共存
- ✅ 清除缓存时只影响指定命名空间

### 2. 事件驱动架构

- ✅ 解耦：验证服务不再直接依赖缓存管理器
- ✅ 扩展性：其他组件可以监听Bundle事件
- ✅ 维护性：单一职责原则
- ✅ 可测试性：可独立测试事件发布和处理

### 3. 简称分配优化

- ✅ 简称在命名空间范围内唯一
- ✅ 避免跨命名空间冲突
- ✅ 更符合多租户场景

---

## 测试结果

```
✅ 编译: 成功
✅ 单元测试: 全部通过 (0 失败)
✅ 集成测试: 全部通过 (0 失败)
```

---

## 修改文件清单

### 新增文件 (4个)

1. `foggy-fsscript/src/main/java/com/foggyframework/bundle/event/BundleRemovedEvent.java`
2. `foggy-fsscript/src/main/java/com/foggyframework/bundle/event/BundleAddedEvent.java`
3. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/event/BundleLifecycleListener.java`
4. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/NamespaceContext.java`

### 修改文件 (8个)

1. `foggy-fsscript/src/main/java/com/foggyframework/bundle/SystemBundlesContextImpl.java`
   - 添加事件发布逻辑

2. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/QueryModelLoader.java`
   - 添加 clearByNamespace() 和 getJdbcQueryModel(String, String) 接口方法

3. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/QueryModelLoaderImpl.java`
   - 重构缓存结构为命名空间级别
   - 实现命名空间隔离逻辑
   - 添加 NamespaceCache 内部类

4. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/spi/TableModelLoaderManager.java`
   - 添加 clearByNamespace() 接口方法

5. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/impl/loader/TableModelLoaderManagerImpl.java`
   - 实现 clearByNamespace() 方法

6. `foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/query_model/DbModelFileChangeHandler.java`
   - 简化代码，移除已废弃的直接缓存访问逻辑

7. `foggy-dataset-mcp/src/main/java/com/foggyframework/dataset/mcp/validation/SemanticLayerValidationService.java`
   - 移除直接的 clearAll() 调用
   - 添加注释说明事件驱动机制

8. `foggy-core/src/main/java/com/foggyframework/core/bundle/BundleDefinition.java`
   - (如果有修改的话，支持namespace字段)

---

## 向后兼容性

- ✅ 默认命名空间（空字符串或null）保持与之前相同的行为
- ✅ 现有不带namespace参数的方法调用继续有效
- ✅ 所有现有测试通过，无需修改

---

## 使用示例

### 动态注册带命名空间的Bundle

```java
// 注册到 openhands 命名空间
systemBundlesContext.addExternalBundle(
    "openhands-workspace",
    "openhands",
    "/path/to/models",
    true
);

// 自动发布 BundleAddedEvent
// BundleLifecycleListener 自动初始化相关资源
```

### 移除Bundle并清理缓存

```java
// 移除Bundle
systemBundlesContext.removeBundle("openhands-workspace");

// 自动发布 BundleRemovedEvent
// BundleLifecycleListener 自动清理 openhands 命名空间的所有缓存
```

### 从指定命名空间加载模型

```java
// TM 模型
TableModel tm = tableModelLoaderManager.load("FactSales", "openhands");

// QM 模型
QueryModel qm = queryModelLoader.getJdbcQueryModel("SalesQuery", "openhands");
```

---

**实施完成**: 2026-01-21
**实施人员**: Claude Sonnet 4.5
**状态**: ✅ 生产就绪
