# 向量数据库演示数据初始化脚本

本目录包含向量数据库的初始化数据。

## 数据说明

向量数据库用于存储查询模板和指导文档，支持语义相似度检索。

### 预置数据示例

1. **销售查询模板**
   - 最近一周各品牌销售情况
   - 本月销售数据统计
   - 销售趋势分析

2. **库存查询模板**
   - 当前库存不足的商品
   - 库存周转率分析
   - 滞销商品统计

3. **查询指导文档**
   - 销售数据查询指南
   - 库存管理查询指南
   - 财务报表查询指南

## 初始化方式

向量数据库的初始化通过应用程序启动时自动完成，无需手动执行 SQL 脚本。

相关代码位于：
- `foggy-dataset-vector/src/main/java/.../QueryTemplateInitializer.java`

## 手动添加数据

如需手动添加查询模板，可以通过以下方式：

```java
@Autowired
private QueryTemplateStoreService templateStore;

// 保存 DSL 模板
templateStore.saveDslTemplate(
    "各品牌销售额统计",
    "FactSalesQueryModel",
    dslTemplate,
    List.of("销售", "品牌")
);

// 保存指导文档
templateStore.saveGuideDocument(
    "销售数据查询指南",
    "FactSalesQueryModel",
    guideMarkdown,
    List.of("销售", "指南")
);
```

注意：保存模板功能暂不对外提供，仅供内部使用。
