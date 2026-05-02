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

const productAmountUnion = salesByProduct.union(returnsByProduct, { all: true });

return {
    plans: {
        product_amount_union: productAmountUnion
    },
    metadata: {
        title: "Sales and returns union"
    }
};
