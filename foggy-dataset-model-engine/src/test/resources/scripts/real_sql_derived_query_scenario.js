const salesByCategory = dsl({
    model: "FactSalesQueryModel",
    columns: ["product$categoryName", "salesAmount"],
    groupBy: ["product$categoryName"]
});

const highValueCategories = dsl({
    source: salesByCategory,
    columns: ["product$categoryName", "salesAmount"],
    slice: [{ field: "salesAmount", op: ">", value: 1000 }]
});

return {
    plans: {
        high_value_categories: highValueCategories
    },
    metadata: {
        title: "High value categories"
    }
};
