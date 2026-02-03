# 向量模型

向量模型用于与 Milvus 等向量数据库集成，支持语义相似度检索。

## 使用场景

- **文档检索**：基于内容语义搜索文档
- **商品推荐**：查找相似商品
- **图像检索**：查找相似图片
- **知识库**：FAQ 问答匹配

## 基本结构

```javascript
export const model = {
    name: 'DocumentSearchModel',
    caption: '文档检索模型',
    type: 'vector',                    // ✅ 指定为向量模型
    tableName: 'documents',            // Milvus 集合名称
    idColumn: 'doc_id',                // 主键字段（可选）

    properties: [
        { column: 'doc_id', caption: '文档ID', type: 'BIGINT' },
        { column: 'title', caption: '标题', type: 'STRING' },
        { column: 'content', caption: '内容', type: 'STRING' },
        { column: 'category', caption: '分类', type: 'STRING' },
        { column: 'embedding', caption: '向量', type: 'VECTOR' }  // ✅ 向量字段
    ],

    measures: []  // 向量模型通常没有度量
};
```

## 关键配置

| 字段 | 说明 |
|------|------|
| `type: 'vector'` | 指定模型类型为向量模型（必填） |
| `tableName` | Milvus 集合名称（必填） |
| `type: 'VECTOR'` | 属性类型为向量字段（必填） |

## 向量字段类型

```javascript
properties: [
    {
        column: 'embedding',
        caption: '文档向量',
        type: 'VECTOR',           // ✅ 向量类型
        description: '文档内容的向量表示，用于语义检索'
    }
]
```

## 向量字段元数据

向量字段的以下信息会自动从 Milvus 获取，无需在 TM 中配置：

- **dimension**：向量维度（如 768、1536）
- **indexType**：索引类型（如 IVF_FLAT、HNSW）
- **metricType**：度量类型（如 L2、IP、COSINE）

## 向量模型限制

### ❌ 不支持的特性

1. **不支持维度关联（dimensions）**
   ```javascript
   dimensions: []  // 向量模型不支持 JOIN
   ```

2. **不支持聚合度量**
   ```javascript
   measures: []  // 向量模型通常没有度量
   ```

3. **不支持普通 WHERE 条件组合向量检索**
   - 向量检索使用专用操作符

### ✅ 支持的特性

1. **标量字段筛选**
   ```javascript
   properties: [
       { column: 'category', caption: '分类', type: 'STRING' },
       { column: 'create_time', caption: '创建时间', type: 'DATETIME' }
   ]
   ```

2. **元数据存储**
   - 可以存储文档标题、摘要、分类等元数据

## 查询语法

### 向量相似度检索

使用 `similar` 操作符：

```json
{
    "columns": ["doc_id", "title", "content", "_score"],
    "filters": [
        {
            "column": "embedding",
            "op": "similar",
            "value": [0.1, 0.2, 0.3, ...]  // 查询向量
        }
    ],
    "limit": 10
}
```

### 混合检索（向量 + 标量筛选）

使用 `hybrid` 操作符：

```json
{
    "columns": ["doc_id", "title", "category", "_score"],
    "filters": [
        {
            "column": "embedding",
            "op": "hybrid",
            "value": [0.1, 0.2, 0.3, ...],
            "metadata": {
                "category": "技术文档"  // 标量筛选条件
            }
        }
    ],
    "limit": 10
}
```

### 查询结果

返回结果包含 `_score` 字段表示相似度：

```json
{
    "data": [
        {
            "doc_id": 123,
            "title": "向量数据库介绍",
            "category": "技术文档",
            "_score": 0.95  // 相似度分数
        },
        {
            "doc_id": 456,
            "title": "Milvus 使用指南",
            "category": "技术文档",
            "_score": 0.87
        }
    ]
}
```

## 向量检索操作符

| 操作符 | 说明 | 示例 |
|-------|------|------|
| `similar` | 纯向量相似度检索 | 查找与给定向量最相似的文档 |
| `hybrid` | 混合检索（向量 + 标量） | 在特定分类下查找相似文档 |

## 完整示例

### 文档检索模型

```javascript
/**
 * 文档检索向量模型
 * @description 用于基于内容语义的文档检索
 */
export const model = {
    name: 'DocumentSearchModel',
    caption: '文档检索',
    description: '支持语义搜索的文档库',
    type: 'vector',
    tableName: 'knowledge_base_documents',
    idColumn: 'doc_id',

    properties: [
        {
            column: 'doc_id',
            caption: '文档ID',
            type: 'BIGINT'
        },
        {
            column: 'title',
            caption: '文档标题',
            type: 'STRING',
            description: '文档的标题或摘要'
        },
        {
            column: 'content',
            caption: '文档内容',
            type: 'STRING',
            description: '文档的完整文本内容'
        },
        {
            column: 'category',
            caption: '文档分类',
            type: 'STRING',
            description: '文档所属类别'
        },
        {
            column: 'tags',
            caption: '标签',
            type: 'STRING',
            description: '文档标签，逗号分隔'
        },
        {
            column: 'create_time',
            caption: '创建时间',
            type: 'DATETIME'
        },
        {
            column: 'embedding',
            caption: '文档向量',
            type: 'VECTOR',
            description: '文档内容的向量表示，用于语义相似度检索'
        }
    ],

    measures: []
};
```

### 商品推荐模型

```javascript
/**
 * 商品推荐向量模型
 * @description 基于商品特征的相似商品推荐
 */
export const model = {
    name: 'ProductRecommendModel',
    caption: '商品推荐',
    type: 'vector',
    tableName: 'product_embeddings',
    idColumn: 'product_id',

    properties: [
        {
            column: 'product_id',
            caption: '商品ID',
            type: 'STRING'
        },
        {
            column: 'product_name',
            caption: '商品名称',
            type: 'STRING'
        },
        {
            column: 'category',
            caption: '品类',
            type: 'STRING'
        },
        {
            column: 'brand',
            caption: '品牌',
            type: 'STRING'
        },
        {
            column: 'price',
            caption: '价格',
            type: 'MONEY'
        },
        {
            column: 'features_vector',
            caption: '特征向量',
            type: 'VECTOR',
            description: '商品特征的向量表示，包含品类、品牌、属性等综合信息'
        }
    ],

    measures: []
};
```

## Milvus 配置

### Spring Boot 配置

```yaml
spring:
  data:
    milvus:
      host: localhost
      port: 19530
      database: default
```

### 创建 Milvus 集合

向量模型的集合结构应与 TM 定义一致：

```python
from pymilvus import Collection, CollectionSchema, FieldSchema, DataType

# 定义字段
fields = [
    FieldSchema(name="doc_id", dtype=DataType.INT64, is_primary=True),
    FieldSchema(name="title", dtype=DataType.VARCHAR, max_length=512),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=4096),
    FieldSchema(name="category", dtype=DataType.VARCHAR, max_length=64),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768)
]

# 创建集合
schema = CollectionSchema(fields, "Document search collection")
collection = Collection("documents", schema)

# 创建向量索引
index_params = {
    "metric_type": "COSINE",
    "index_type": "HNSW",
    "params": {"M": 16, "efConstruction": 200}
}
collection.create_index("embedding", index_params)
```

## 最佳实践

1. **选择合适的向量维度**
   - 小数据集（<10万）：256-512 维
   - 中等数据集（10万-100万）：512-768 维
   - 大数据集（>100万）：768-1536 维

2. **选择合适的索引类型**
   - **HNSW**：召回率高，查询速度快（推荐）
   - **IVF_FLAT**：平衡精度和速度
   - **IVF_PQ**：节省内存，适合大规模数据

3. **选择合适的度量类型**
   - **COSINE**：余弦相似度（推荐，适合大部分场景）
   - **IP**：内积（适合归一化向量）
   - **L2**：欧式距离

4. **元数据设计**
   - 保留必要的元数据字段（标题、分类等）
   - 支持混合检索（向量 + 标量筛选）

5. **性能优化**
   - 定期压缩集合（compact）
   - 合理设置 `nprobe` 参数平衡速度和精度
   - 使用分区加速查询

## 注意事项

1. 向量字段不能为空
2. 向量维度必须与 Milvus 集合定义一致
3. 向量检索结果默认按相似度降序排序
4. `_score` 字段范围 0-1，值越大表示越相似（COSINE/IP）
5. 混合检索时，标量筛选在向量检索前执行

## 参考资料

- [Milvus 官方文档](https://milvus.io/docs)
- [向量数据库最佳实践](https://milvus.io/docs/performance_tuning.md)
