// 1. 基础查询：按部门聚合订单总额和单数
const salesBase = Query.from("OdooSaleOrderModel");
const deptSales = salesBase
    .groupBy(salesBase.teamId$caption)
    .select(
        salesBase.teamId$caption,
        salesBase.amountTotal.sum().as("total_sales"),
        salesBase.id.count().as("order_count")
    );

// 2. 派生查询：基于前一步的结果过滤高销售额部门
const highValueDepts = deptSales
    .where([{ field: "total_sales", op: ">", value: 50000 }])
    .select(
        deptSales.teamId$caption,
        deptSales.total_sales,
        deptSales.order_count
    );

return {
    plans: {
        high_value_departments: highValueDepts
    },
    metadata: {
        title: "高销量部门筛选"
    }
};
