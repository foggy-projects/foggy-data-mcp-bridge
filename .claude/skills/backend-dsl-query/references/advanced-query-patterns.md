# 高级查询模式

## 模式 7：去重计数查询 (COUNTD)

```java
public List<Map<String, Object>> getDistinctCustomersByCategory() {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("FactSalesQueryModel");

    CalculatedFieldDef uvField = new CalculatedFieldDef(
        "uv", "独立客户数", "COUNTD(customer$id)"
    );
    queryRequest.setCalculatedFields(List.of(uvField));

    queryRequest.setColumns(List.of("product$categoryName", "uv"));
    queryRequest.setGroupBy(List.of(
        new GroupRequestDef("product$categoryName")
    ));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

## 模式 8：窗口函数查询（排名）

```java
public List<Map<String, Object>> getSalesWithRank() {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("FactSalesQueryModel");

    CalculatedFieldDef rankField = new CalculatedFieldDef();
    rankField.setName("salesRank");
    rankField.setCaption("品类销售排名");
    rankField.setExpression("RANK()");
    rankField.setPartitionBy(List.of("product$categoryName"));
    rankField.setWindowOrderBy(List.of(
        new WindowOrderDef("salesAmount", "desc")
    ));
    queryRequest.setCalculatedFields(List.of(rankField));

    queryRequest.setColumns(List.of(
        "product$categoryName", "product$caption",
        "salesAmount", "salesRank"
    ));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

## 模式 9：移动平均（窗口帧）

```java
public List<Map<String, Object>> getSalesWithMovingAvg() {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("FactSalesQueryModel");

    CalculatedFieldDef ma7Field = new CalculatedFieldDef();
    ma7Field.setName("ma7");
    ma7Field.setCaption("7日移动平均");
    ma7Field.setExpression("AVG(salesAmount)");
    ma7Field.setPartitionBy(List.of("product$caption"));
    ma7Field.setWindowOrderBy(List.of(
        new WindowOrderDef("salesDate$caption", "asc")
    ));
    ma7Field.setWindowFrame("ROWS BETWEEN 6 PRECEDING AND CURRENT ROW");
    queryRequest.setCalculatedFields(List.of(ma7Field));

    queryRequest.setColumns(List.of(
        "product$caption", "salesDate$caption",
        "salesAmount", "ma7"
    ));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

## 模式 10：引用 QM 预定义字段

QM 中已预定义的计算字段（如 `profitRate`、`salesRank`、`ma7`）可直接引用，无需在代码中定义：

```java
public List<Map<String, Object>> getSalesAnalytics() {
    DbQueryRequestDef queryRequest = new DbQueryRequestDef();
    queryRequest.setQueryModel("FactSalesQueryModel");

    // 直接引用 QM 预定义字段
    queryRequest.setColumns(List.of(
        "product$categoryName", "salesAmount",
        "profitRate", "salesRank", "ma7"
    ));

    return queryFacade.queryModelData(
        PagingRequest.buildPagingRequest(queryRequest, 100)
    ).getItems();
}
```

## CalculatedFieldDef 窗口属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `partitionBy` | `List<String>` | 窗口分区字段 |
| `windowOrderBy` | `List<WindowOrderDef>` | 窗口排序 |
| `windowFrame` | `String` | 窗口帧（如 `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW`） |

`WindowOrderDef(String field, String dir)` — dir 取值 `"asc"` 或 `"desc"`
