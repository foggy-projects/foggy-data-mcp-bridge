// 1. 获取销售订单金额，按日期汇总
const salesBase = Query.from("OdooSaleOrderModel");
const sales = salesBase
    .groupBy(salesBase.dateOrder$caption)
    .select(
        salesBase.dateOrder$caption.as("date"),
        salesBase.amountTotal.sum().as("sales_amount")
    );

// 2. 获取采购订单金额，按日期汇总
const purchasesBase = Query.from("OdooPurchaseOrderModel");
const purchases = purchasesBase
    .groupBy(purchasesBase.dateOrder$caption)
    .select(
        purchasesBase.dateOrder$caption.as("date"),
        purchasesBase.amountTotal.sum().as("purchase_amount")
    );

// 3. 将销售和采购汇总数据进行 Union All
// 注意：需要确保两边的列结构对齐。
const salesAligned = sales.select(
    sales.date,
    sales.sales_amount.as("amount")
);

const purchasesAligned = purchases.select(
    purchases.date,
    purchases.purchase_amount.as("amount")
);

const mergedPlan = salesAligned.union(purchasesAligned, { all: true });

return {
    plans: {
        cashflow_overview: mergedPlan
    },
    metadata: {
        title: "本月销售与采购现金流对比"
    }
};
