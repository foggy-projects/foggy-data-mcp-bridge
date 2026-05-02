// 1. 获取 A 级客户列表
const premiumCustomersBase = Query.from("OdooResPartnerModel");
const premiumCustomers = premiumCustomersBase
    .where([{ field: "category_id$caption", op: "contains", value: "A级" }])
    .select(premiumCustomersBase.id, premiumCustomersBase.name);

// 2. 获取未发货的销售订单
const pendingOrdersBase = Query.from("OdooSaleOrderModel");
const pendingOrders = pendingOrdersBase
    .where([{ field: "deliveryStatus", op: "=", value: "pending" }])
    .select(pendingOrdersBase.partnerId, pendingOrdersBase.name, pendingOrdersBase.amountTotal);

// 3. 通过客户 ID 进行 Join
const joined = premiumCustomers.innerJoin(pendingOrders)
    .on(premiumCustomers.id, pendingOrders.partnerId);

// 4. 投影最终结果
const finalPlan = joined.select(
    premiumCustomers.name.as("customer_name"),
    pendingOrders.name.as("order_number"),
    pendingOrders.amountTotal.as("order_amount")
);

return {
    plans: { anomaly_list: finalPlan },
    metadata: { title: "A级客户逾期未发货清单" }
};
