const salesByProduct = dsl({
    model: "FactSalesQueryModel",
    columns: ["product$id", "salesAmount"],
    groupBy: ["product$id"]
});

const returnsByProduct = dsl({
    model: "FactReturnQueryModel",
    columns: ["product$id", "returnAmount"],
    groupBy: ["product$id"]
});

const joined = salesByProduct.join(returnsByProduct, "inner", [
    { left: "product$id", op: "=", right: "product$id" }
]);

const salesAndReturns = dsl({
    source: joined,
    columns: ["product$id", "salesAmount", "returnAmount"]
});

return {
    plans: {
        sales_return_by_product: salesAndReturns
    },
    metadata: {
        title: "Sales and returns by product"
    }
};
