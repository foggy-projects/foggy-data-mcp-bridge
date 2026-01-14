# Foggy Dataset Demo - Vector Database

向量数据库演示配置和示例。

## 启动 Milvus 向量数据库

```bash
cd foggy-dataset-demo/docker

# 启动 Milvus 及其依赖（etcd, minio）
docker-compose up -d milvus

# 查看日志
docker-compose logs -f milvus

# 检查健康状态
docker-compose ps
```

## 访问端口

- **Milvus**: `localhost:19530` (gRPC)
- **Milvus Metrics**: `localhost:19121` (HTTP)
- **MinIO Console**: `localhost:9001` (用户名/密码: minioadmin/minioadmin)

## 配置 Spring AI

在 `application.yml` 中配置：

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
        index-type: HNSW
        metric-type: COSINE
```

## 使用示例

### 1. AI 使用 DSL 查询向量数据库

```json
{
  "model": "QueryTemplateVectorQueryModel",
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

### 2. 返回结果

```json
{
  "items": [
    {
      "id": "doc1",
      "content": "最近一周各品牌销售情况查询模板",
      "template_type": "dsl",
      "model_name": "FactSalesQueryModel",
      "similarity": 0.95,
      "tags": ["销售", "品牌"]
    }
  ],
  "total": 5
}
```

## TM/QM 文件说明

- **QueryTemplateVectorModel.tm**: 表模型，定义向量存储的字段和查询逻辑
- **QueryTemplateVectorQueryModel.qm**: 查询模型，提供查询接口配置

## 停止服务

```bash
# 停止 Milvus
docker-compose stop milvus etcd minio

# 删除数据（慎用）
docker-compose down -v
```

## 故障排查

### Milvus 启动失败

1. 检查 etcd 和 minio 是否正常运行
2. 查看日志：`docker-compose logs milvus`
3. 确保端口 19530 未被占用

### 连接失败

1. 确认 Milvus 健康检查通过：`curl http://localhost:19121/healthz`
2. 检查防火墙设置
3. 验证 Spring AI 配置正确

## 参考资料

- [Milvus 官方文档](https://milvus.io/docs)
- [Spring AI Milvus](https://docs.spring.io/spring-ai/reference/api/vectordbs/milvus.html)
