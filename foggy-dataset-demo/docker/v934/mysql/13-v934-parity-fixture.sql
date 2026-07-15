-- Deterministic rows owned by the 9.3.4 five-database QueryFacade parity lane.
INSERT INTO `fact_sales` (
    `order_id`, `order_line_no`, `date_key`, `product_key`, `customer_key`,
    `store_key`, `channel_key`, `promotion_key`, `quantity`, `unit_price`,
    `unit_cost`, `discount_amount`, `sales_amount`, `cost_amount`, `profit_amount`,
    `tax_amount`, `order_status`, `payment_method`
) VALUES
    ('V934_PARITY_SENTINEL', 1, 20240101, 1, 1, 1, 1, 1, 1, 10.00,
     6.00, 0.00, 10.00, 6.00, 4.00, 0.00, 'COMPLETED', 'V934'),
    ('V934_PARITY_SENTINEL', 2, 20240101, 1, 1, 1, 1, 1, 2, 20.00,
     6.00, 0.00, 40.00, 12.00, 28.00, 0.00, 'COMPLETED', 'V934')
ON DUPLICATE KEY UPDATE
    `date_key` = VALUES(`date_key`),
    `product_key` = VALUES(`product_key`),
    `customer_key` = VALUES(`customer_key`),
    `store_key` = VALUES(`store_key`),
    `channel_key` = VALUES(`channel_key`),
    `promotion_key` = VALUES(`promotion_key`),
    `quantity` = VALUES(`quantity`),
    `unit_price` = VALUES(`unit_price`),
    `unit_cost` = VALUES(`unit_cost`),
    `discount_amount` = VALUES(`discount_amount`),
    `sales_amount` = VALUES(`sales_amount`),
    `cost_amount` = VALUES(`cost_amount`),
    `profit_amount` = VALUES(`profit_amount`),
    `tax_amount` = VALUES(`tax_amount`),
    `order_status` = VALUES(`order_status`),
    `payment_method` = VALUES(`payment_method`);
