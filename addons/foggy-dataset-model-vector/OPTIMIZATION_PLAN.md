# Vector 模块优化迭代计划

> 版本：v2.0
> 状态：待审核
> 基于：当前 v1.0 实现 + 讨论反馈

---

## 一、核心优化：元数据自动发现

### 1.1 现状问题

当前 TM 文件需要手动配置向量字段的元数据：

```javascript
// 当前：手动配置，容易与实际集合不一致
{
    column: 'embedding',
    type: 'VECTOR',
    dimensions: 1536,     // 手动指定
    metric: 'cosine',     // 手动指定
}
```

**问题**：
- `dimensions`、`metric`、`indexType` 与 Milvus 集合定义重复
- 配置不一致时会导致运行时错误
- 新增/修改集合需要同步更新 TM 文件

### 1.2 优化方案

新增 `MilvusMetadataService` 类，自动从 Milvus 获取集合元数据：

```java
/**
 * Milvus 元数据服务
 */
public class MilvusMetadataService {

    /**
     * 获取集合的向量字段元数据
     */
    public VectorFieldMetadata getVectorFieldMetadata(String collectionName, String fieldName) {
        // 1. describeCollection -> 获取 schema、dimension
        // 2. describeIndex -> 获取 indexType、metricType
        return VectorFieldMetadata.builder()
            .fieldName(fieldName)
            .dimension(dimension)
            .indexType(indexType)
            .metricType(metricType)
            .build();
    }

    /**
     * 自动发现集合中的向量字段
     */
    public List<VectorFieldMetadata> discoverVectorFields(String collectionName) {
        // 遍历 schema，找出所有 FloatVector/BinaryVector 字段
    }
}
```

### 1.3 TM 语法简化

**简化后的 TM 定义**：

```javascript
export const model = {
    name: 'DocumentSearchModel',
    caption: '文档搜索',
    tableName: 'documents',      // Milvus collection 名称
    type: 'vector',              // 标识向量模型

    properties: [
        { column: 'doc_id', name: 'docId', caption: '文档ID', type: 'TEXT' },
        { column: 'title', name: 'title', caption: '标题', type: 'TEXT' },
        { column: 'content', name: 'content', caption: '内容', type: 'TEXT' },
        { column: 'category', name: 'category', caption: '分类', type: 'TEXT' },

        // 向量字段：仅需声明 type: 'VECTOR'，元数据自动从 Milvus 获取
        { column: 'embedding', name: 'embedding', caption: '文档向量', type: 'VECTOR' }
    ]
};
```

**向量字段识别逻辑**：
1. `type: 'vector'` → 使用向量模型加载器
2. 遍历 properties 找 `type: 'VECTOR'` → 确定向量字段
3. 连接 Milvus → 自动获取 dimension、metric、indexType

**可选覆盖**（仅当需要时）：
```javascript
// 手动覆盖自动发现的值
{ column: 'embedding', type: 'VECTOR', dimensions: 1024 }
```

---

## 二、VectorFieldMetadata 数据结构

新增元数据类：

```java
@Data
@Builder
public class VectorFieldMetadata {
    /**
     * 字段名
     */
    private String fieldName;

    /**
     * 向量维度
     */
    private int dimension;

    /**
     * 索引类型: IVF_FLAT, IVF_SQ8, HNSW, etc.
     */
    private String indexType;

    /**
     * 距离度量: COSINE, L2, IP
     */
    private String metricType;

    /**
     * 向量数据类型: FloatVector, BinaryVector
     */
    private String vectorType;

    /**
     * 索引参数（如 nlist, M, efConstruction 等）
     */
    private Map<String, Object> indexParams;

    /**
     * 是否已建立索引
     */
    private boolean indexed;
}
```

---

## 三、VectorDbConfig 优化

### 3.1 新增配置项

```java
@Data
@Builder
public class VectorDbConfig {
    // ... 现有字段 ...

    /**
     * 是否启用元数据自动发现
     */
    @Builder.Default
    private boolean autoDiscovery = true;

    /**
     * 连接池大小
     */
    @Builder.Default
    private int poolSize = 5;

    /**
     * 连接池最大等待时间（毫秒）
     */
    @Builder.Default
    private long poolMaxWaitMs = 5000;
}
```

### 3.2 Spring 配置

```yaml
foggy:
  vector:
    host: localhost
    port: 19530
    auto-discovery: true
    pool-size: 5
    embedding:
      type: openai
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model: text-embedding-3-small
      # dimensions 不再需要配置，从 Milvus 自动获取
```

---

## 四、DSL 查询语法优化

### 4.1 当前语法（保持兼容）

```javascript
slice: [
    {
        field: "embedding",
        op: "similar",
        value: {
            text: "销售报告",
            topK: 10,
            minScore: 0.7
        }
    }
]
```

### 4.2 新增高级特性

#### 4.2.1 分组搜索（Group By）

```javascript
slice: [
    {
        field: "embedding",
        op: "similar",
        value: {
            text: "技术文档",
            topK: 10,
            groupBy: "category",    // 按分类分组
            groupSize: 2            // 每组返回2条
        }
    }
]
```

**实现**：使用 Milvus 的 `SearchReq.groupByFieldName()`

#### 4.2.2 范围搜索（Radius Search）

```javascript
slice: [
    {
        field: "embedding",
        op: "similar",
        value: {
            text: "销售报告",
            radius: 0.5,           // 距离半径
            rangeFilter: 0.8       // 外部边界过滤
        }
    }
]
```

**实现**：使用 Milvus 的 `SearchReq.searchParams()` 中的 radius 参数

#### 4.2.3 混合搜索（Hybrid Search）

```javascript
slice: [
    {
        field: "embedding",
        op: "hybrid",              // 混合搜索操作符
        value: {
            text: "销售报告",       // 用于向量搜索的文本
            keyword: "Q1 2024",    // 关键词搜索
            vectorWeight: 0.7,     // 向量搜索权重
            keywordWeight: 0.3,    // 关键词搜索权重
            topK: 10
        }
    }
]
```

**实现方案**：
- 使用 Milvus 2.4+ 的 `HybridSearch` API
- 结合 `AnnSearchReq`（向量）和 `filter`（关键词）
- 使用 `RRFRanker` 或 `WeightedRanker` 进行结果融合

```java
// 实现示例
public List<Map<String, Object>> executeHybridSearch(HybridSearchParams params) {
    // 1. 构建向量搜索请求
    AnnSearchReq vectorSearch = AnnSearchReq.builder()
        .vectorFieldName(vectorFieldName)
        .vectors(Collections.singletonList(new FloatVec(queryVector)))
        .topK(params.getTopK())
        .build();

    // 2. 构建关键词过滤（使用 Milvus 的全文搜索或 filter）
    AnnSearchReq keywordSearch = AnnSearchReq.builder()
        .vectorFieldName(vectorFieldName)
        .vectors(Collections.singletonList(new FloatVec(queryVector)))
        .filter(buildKeywordFilter(params.getKeyword()))
        .topK(params.getTopK())
        .build();

    // 3. 混合搜索请求
    HybridSearchReq hybridReq = HybridSearchReq.builder()
        .collectionName(collectionName)
        .searchRequests(Arrays.asList(vectorSearch, keywordSearch))
        .ranker(new WeightedRanker(params.getVectorWeight(), params.getKeywordWeight()))
        .topK(params.getTopK())
        .outFields(outputFields)
        .build();

    return milvusClient.hybridSearch(hybridReq);
}
```

---

## 五、错误处理增强

### 5.1 错误码定义

```java
public enum VectorErrorCode {
    // 连接错误
    MILVUS_CONNECTION_FAILED("VEC_001", "无法连接到 Milvus 服务"),
    MILVUS_AUTH_FAILED("VEC_002", "Milvus 认证失败"),

    // 集合错误
    COLLECTION_NOT_FOUND("VEC_101", "集合不存在: {0}"),
    COLLECTION_NOT_LOADED("VEC_102", "集合未加载到内存: {0}"),

    // 向量字段错误
    VECTOR_FIELD_NOT_FOUND("VEC_201", "未找到向量字段: {0}"),
    DIMENSION_MISMATCH("VEC_202", "向量维度不匹配，期望 {0}，实际 {1}"),

    // Embedding 错误
    EMBEDDING_SERVICE_ERROR("VEC_301", "Embedding 服务调用失败: {0}"),
    EMBEDDING_EMPTY_RESULT("VEC_302", "Embedding 服务返回空结果"),

    // 查询错误
    INVALID_VECTOR_QUERY("VEC_401", "无效的向量查询条件"),
    MULTIPLE_VECTOR_SEARCH("VEC_402", "仅支持单个向量搜索条件"),
    HYBRID_SEARCH_NOT_SUPPORTED("VEC_403", "当前 Milvus 版本不支持混合搜索");
}
```

### 5.2 异常类

```java
public class VectorQueryException extends RuntimeException {
    private final VectorErrorCode errorCode;
    private final Object[] args;

    public String getFormattedMessage() {
        return MessageFormat.format(errorCode.getMessage(), args);
    }
}
```

---

## 六、连接池

使用 Apache Commons Pool2 实现 Milvus 客户端连接池：

### 6.1 依赖

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>2.12.0</version>
</dependency>
```

### 6.2 实现

```java
/**
 * Milvus 客户端工厂
 */
public class MilvusClientFactory extends BasePooledObjectFactory<MilvusClientV2> {

    private final VectorDbConfig config;

    @Override
    public MilvusClientV2 create() {
        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri(String.format("http://%s:%d", config.getHost(), config.getPort()))
            .connectTimeoutMs(config.getConnectTimeoutMs())
            .build();
        return new MilvusClientV2(connectConfig);
    }

    @Override
    public PooledObject<MilvusClientV2> wrap(MilvusClientV2 client) {
        return new DefaultPooledObject<>(client);
    }

    @Override
    public void destroyObject(PooledObject<MilvusClientV2> p) {
        p.getObject().close();
    }

    @Override
    public boolean validateObject(PooledObject<MilvusClientV2> p) {
        // 可选：验证连接有效性
        return true;
    }
}

/**
 * Milvus 客户端池
 */
public class MilvusClientPool implements AutoCloseable {

    private final GenericObjectPool<MilvusClientV2> pool;

    public MilvusClientPool(VectorDbConfig config) {
        MilvusClientFactory factory = new MilvusClientFactory(config);

        GenericObjectPoolConfig<MilvusClientV2> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.getPoolSize());
        poolConfig.setMaxIdle(config.getPoolSize());
        poolConfig.setMinIdle(1);
        poolConfig.setMaxWait(Duration.ofMillis(config.getPoolMaxWaitMs()));
        poolConfig.setTestOnBorrow(false);

        this.pool = new GenericObjectPool<>(factory, poolConfig);
    }

    public MilvusClientV2 borrowClient() throws Exception {
        return pool.borrowObject();
    }

    public void returnClient(MilvusClientV2 client) {
        pool.returnObject(client);
    }

    @Override
    public void close() {
        pool.close();
    }
}
```

### 6.3 使用方式

```java
// 在 VectorModelQueryEngine 中使用
public List<Map<String, Object>> executeSearch(int offset, int limit) {
    MilvusClientV2 client = null;
    try {
        client = clientPool.borrowClient();
        // 执行搜索...
        return results;
    } finally {
        if (client != null) {
            clientPool.returnClient(client);
        }
    }
}
```

---

## 七、实现计划

### Phase 1: 元数据自动发现（核心）

| 任务 | 描述 | 文件 |
|------|------|------|
| 1.1 | 新增 `VectorFieldMetadata` 类 | impl/vector/VectorFieldMetadata.java |
| 1.2 | 新增 `MilvusMetadataService` | impl/vector/MilvusMetadataService.java |
| 1.3 | 修改 `TmVectorModelLoaderImpl` | 集成元数据自动发现 |
| 1.4 | 修改 `VectorDbConfig` | 添加 autoDiscovery、poolSize 配置 |
| 1.5 | 更新单元测试 | 测试元数据获取 |

### Phase 2: 连接池

| 任务 | 描述 | 文件 |
|------|------|------|
| 2.1 | 添加 commons-pool2 依赖 | pom.xml |
| 2.2 | 实现 `MilvusClientFactory` | impl/vector/MilvusClientFactory.java |
| 2.3 | 实现 `MilvusClientPool` | impl/vector/MilvusClientPool.java |
| 2.4 | 修改 `VectorModelQueryEngine` | 使用连接池 |

### Phase 3: DSL 增强

| 任务 | 描述 | 文件 |
|------|------|------|
| 3.1 | 支持 `groupBy` 参数 | VectorModelQueryEngine.java |
| 3.2 | 支持 `radius` 范围搜索 | VectorModelQueryEngine.java |
| 3.3 | 实现 `hybrid` 混合搜索 | VectorModelQueryEngine.java |
| 3.4 | 更新集成测试 | VectorIT.java |

### Phase 4: 错误处理

| 任务 | 描述 | 文件 |
|------|------|------|
| 4.1 | 新增 `VectorErrorCode` 枚举 | VectorErrorCode.java |
| 4.2 | 新增 `VectorQueryException` | VectorQueryException.java |
| 4.3 | 更新各类错误处理 | 各相关文件 |

---

## 八、兼容性说明

### 8.1 向后兼容

- 现有 TM 文件中的 `dimensions`、`metric` 配置仍然有效
- 如果 TM 中指定了这些值，将优先使用（覆盖自动发现）
- 现有 DSL 查询语法（`similar` 操作符）完全兼容

### 8.2 配置优先级

```
TM 文件显式配置 > Milvus 自动发现 > Spring 配置默认值
```

### 8.3 降级策略

当 Milvus 元数据服务不可用时：
1. 记录警告日志
2. 使用 TM 文件或 Spring 配置中的值
3. 如果都没有，抛出明确错误（VEC_201）

---

## 九、示例：优化后的完整 TM

```javascript
/**
 * 文档搜索向量模型 - v2.0
 *
 * 特性：
 * - 向量字段元数据自动发现
 * - 简化配置，无需指定 dimension/metric
 */
export const model = {
    name: 'DocumentSearchModel',
    caption: '文档搜索',
    tableName: 'documents',
    type: 'vector',

    properties: [
        { column: 'doc_id', name: 'docId', caption: '文档ID', type: 'TEXT' },
        { column: 'title', name: 'title', caption: '标题', type: 'TEXT' },
        { column: 'content', name: 'content', caption: '内容', type: 'TEXT' },
        { column: 'category', name: 'category', caption: '分类', type: 'TEXT' },

        // 向量字段 - 仅声明类型，元数据自动获取
        { column: 'embedding', name: 'embedding', caption: '文档向量', type: 'VECTOR' }
    ]
};
```

---

## 十、DSL 完整示例

### 10.1 基础相似度搜索

```javascript
{
    slice: [
        { field: "embedding", op: "similar", value: { text: "销售报告", topK: 10 } }
    ]
}
```

### 10.2 带过滤的搜索

```javascript
{
    slice: [
        { field: "embedding", op: "similar", value: { text: "技术文档", topK: 10, minScore: 0.7 } },
        { field: "category", op: "=", value: "technical" }
    ]
}
```

### 10.3 分组搜索

```javascript
{
    slice: [
        {
            field: "embedding",
            op: "similar",
            value: {
                text: "产品介绍",
                topK: 20,
                groupBy: "category",
                groupSize: 3
            }
        }
    ]
}
```

### 10.4 范围搜索

```javascript
{
    slice: [
        {
            field: "embedding",
            op: "similar",
            value: {
                text: "用户反馈",
                radius: 0.5,
                rangeFilter: 0.8
            }
        }
    ]
}
```

### 10.5 混合搜索

```javascript
{
    slice: [
        {
            field: "embedding",
            op: "hybrid",
            value: {
                text: "销售业绩",
                keyword: "2024 Q1",
                vectorWeight: 0.7,
                keywordWeight: 0.3,
                topK: 10
            }
        }
    ]
}
```

---

请审核此优化计划，确认后我将开始实施。
