# Foggy Dataset Vector

向量数据库支持模块，让 AI 可以使用标准 DSL 语法查询向量数据库。

## 功能特性

- 支持通过 TM/QM 文件定义向量数据模型
- AI 使用标准 DSL 语法查询向量数据库
- 基于 Spring AI VectorStore
- 支持 Milvus/Pinecone/Weaviate 等多种向量库

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-vector</artifactId>
</dependency>
```

### 2. 配置向量数据库

```yaml
spring:
  ai:
    vectorstore:
      enabled: true
      milvus:
        client:
          host: localhost
          port: 19530
        collection-name: query_templates
        embedding-dimension: 1536
```

### 3. 定义 TM 文件

创建 `QueryTemplateVectorModel.tm`:

```javascript
export function buildQuery(params) {
    const sliceConditions = params.slice || [];
    const queryCondition = sliceConditions.find(s => s.name === 'query');
    return queryCondition ? queryCondition.value : '';
}

export const topK = 10;
export const threshold = 0.7;
```

### 4. AI 使用 DSL 查询

AI 可以使用标准 DSL 语法查询向量数据库：

```json
{
  "model": "QueryTemplateVectorModel",
  "slice": [
    {
      "name": "query",
      "type": "=",
      "value": "最近一周各品牌销售情况"
    }
  ],
  "limit": 5
}
```

## 架构说明

```
foggy-dataset-vector/
├── VectorKey.java                    # 向量查询 Key
├── VectorModel.java                  # 向量模型接口
├── support/
│   └── VectorFscriptDataSetModel.java  # 向量数据集模型实现
├── funs/
│   └── VectorFileFsscriptLoader.java   # Fsscript 加载器
└── DataSetVectorAutoConfiguration.java # 自动配置
```

## 与 MongoDB 对比

| 特性 | MongoDB | Vector |
|------|---------|--------|
| 查询方式 | find/aggregate | similarity search |
| TM/QM 支持 | ✅ | ✅ |
| DSL 语法 | ✅ | ✅ |
| 自动配置 | ✅ | ✅ |

## License

Apache License 2.0
