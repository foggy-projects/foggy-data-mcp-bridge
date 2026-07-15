# 向量数据库测试配置说明

## 环境准备

### 1. 启动 Milvus 向量数据库

```bash
cd foggy-dataset-demo/docker
docker-compose up -d milvus
```

等待 Milvus 启动完成（约 1-2 分钟）：
```bash
docker-compose logs -f milvus
# 看到 "Milvus Proxy successfully initialized" 表示启动成功
```

### 2. 配置 Embedding API

向量写入需要 Embedding 模型支持，支持以下方式：

#### 方式 1: OpenAI（推荐）
```bash
export OPENAI_API_KEY=sk-xxx
```

#### 方式 2: 阿里云百炼
```bash
export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export OPENAI_API_KEY=sk-xxx
export OPENAI_EMBEDDING_MODEL=text-embedding-v3
```

#### 方式 3: Ollama（本地）
```bash
# 先启动 Ollama 并拉取 embedding 模型
ollama pull nomic-embed-text

export OPENAI_BASE_URL=http://localhost:11434/v1
export OPENAI_API_KEY=ollama
export OPENAI_EMBEDDING_MODEL=nomic-embed-text
```

## 运行测试

### 单元测试（无需外部依赖）
```bash
cd addons/foggy-dataset-vector
mvn test -Dtest=VectorKeyTest,VectorStoreQueryTest,VectorFscriptDataSetModelTest
```

### 集成测试（Step 3 外部矩阵）

`VectorStoreIT` 当前仍由 class-level `@Disabled` 隔离，不能把手工 Maven 绿色结果当作
真实 Milvus 证据。9.3.4 Step 3 会先移除该静态禁用，接入受控 Milvus + embedding
fixture，再以 fresh Failsafe XML 和 skipped=0 作为 required lane 证据。

## 配置文件说明

`application-test.yml` 中的关键配置：

```yaml
spring:
  ai:
    vectorstore:
      milvus:
        client:
          host: ${MILVUS_HOST:localhost}
          port: ${MILVUS_PORT:19530}
        collection-name: query_templates
        embedding-dimension: 1536

    openai:
      api-key: ${OPENAI_API_KEY:sk-xxx}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      embedding:
        options:
          model: ${OPENAI_EMBEDDING_MODEL:text-embedding-ada-002}
```

## 测试数据

测试使用 `foggy-dataset-demo` 中定义的向量模型：
- `vector_demo/model/QueryTemplateVectorModel.tm` - 表模型
- `vector_demo/query/QueryTemplateVectorQueryModel.qm` - 查询模型

## 故障排查

### Milvus 连接失败
```bash
# 检查 Milvus 状态
curl http://localhost:19121/healthz

# 查看日志
docker-compose logs milvus
```

### Embedding 失败
- 检查 API Key 是否正确
- 检查网络是否能访问 API 端点
- 查看日志中的错误信息

### 测试超时
- 首次运行需要创建 Collection，可能较慢
- 增加测试超时时间或等待 Milvus 完全启动
