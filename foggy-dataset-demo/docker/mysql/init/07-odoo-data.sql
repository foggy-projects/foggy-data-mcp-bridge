-- ============================================
-- Odoo 样本数据 (MySQL)
-- 文件: 07-odoo-data.sql
-- ============================================

SET NAMES utf8mb4;

-- ========== 基础维度 ==========

INSERT INTO `res_currency` (`id`, `name`) VALUES
(1, 'CNY'), (2, 'USD'), (3, 'EUR');

INSERT INTO `res_country` (`id`, `name`, `code`) VALUES
(1, 'China', 'CN'), (2, 'United States', 'US'), (3, 'Germany', 'DE');

INSERT INTO `res_country_state` (`id`, `name`, `code`, `country_id`) VALUES
(1, 'Zhejiang', 'ZJ', 1), (2, 'Shanghai', 'SH', 1), (3, 'Beijing', 'BJ', 1);

INSERT INTO `res_company` (`id`, `name`, `parent_id`, `currency_id`, `country_id`, `city`, `active`) VALUES
(1, 'My Company (Shanghai)', NULL, 1, 1, 'Shanghai', 1),
(2, 'My Company (Beijing)',  1,    1, 1, 'Beijing', 1),
(3, 'Overseas Branch',       1,    2, 2, 'New York', 1);

INSERT INTO `res_users` (`id`, `login`, `company_id`) VALUES
(1, 'admin', 1),
(2, 'sales_mgr', 1),
(3, 'sales_rep_sh', 1),
(4, 'sales_rep_bj', 2),
(5, 'purchaser', 1),
(6, 'accountant', 1);

INSERT INTO `crm_team` (`id`, `name`) VALUES
(1, 'Direct Sales'),
(2, 'Online Sales'),
(3, 'Enterprise Sales');

INSERT INTO `res_partner` (`id`, `name`, `display_name`, `type`, `is_company`, `city`, `country_id`, `state_id`, `company_id`, `user_id`, `team_id`, `customer_rank`, `supplier_rank`, `email`, `phone`, `credit_limit`, `active`) VALUES
(1, 'Azure Interior', 'Azure Interior', 'contact', 1, 'Shanghai', 1, 2, 1, 3, 1, 1, 0, 'azure@test.com', '021-55551234', 50000, 1),
(2, 'Deco Addict', 'Deco Addict', 'contact', 1, 'Beijing', 1, 3, 2, 4, 1, 1, 0, 'deco@test.com', '010-88881234', 80000, 1),
(3, 'Gemini Furniture', 'Gemini Furniture', 'contact', 1, 'Hangzhou', 1, 1, 1, NULL, 2, 1, 0, 'gemini@test.com', NULL, 30000, 1),
(4, 'Ready Mat', 'Ready Mat', 'contact', 1, 'New York', 2, NULL, 3, NULL, 3, 1, 0, 'readymat@test.com', '+1-555-0100', 100000, 1),
(5, 'Wood Corner', 'Wood Corner', 'contact', 1, 'Shanghai', 1, 2, 1, NULL, NULL, 0, 1, 'woodcorner@test.com', '021-33331234', 0, 1),
(6, 'Steel Supplier Co', 'Steel Supplier Co', 'contact', 1, 'Hangzhou', 1, 1, 1, NULL, NULL, 0, 1, 'steel@test.com', NULL, 0, 1);

-- ========== 销售模块 ==========

INSERT INTO `product_pricelist` (`id`, `name`) VALUES
(1, 'Public Pricelist'), (2, 'VIP Pricelist');

INSERT INTO `stock_warehouse` (`id`, `name`, `company_id`) VALUES
(1, 'WH/Shanghai', 1), (2, 'WH/Beijing', 2);

INSERT INTO `product_template` (`id`, `name`, `type`, `categ_id`, `list_price`) VALUES
(1, 'Office Chair', 'consu', 1, 299.00),
(2, 'Desk Combo', 'consu', 1, 599.00),
(3, 'Cabinet with Doors', 'consu', 1, 450.00),
(4, 'Large Desk', 'consu', 1, 1299.00),
(5, 'Storage Box', 'consu', 2, 49.00);

INSERT INTO `product_product` (`id`, `product_tmpl_id`, `default_code`, `barcode`, `active`) VALUES
(1, 1, 'FURN_0001', '5600000000001', 1),
(2, 2, 'FURN_0002', '5600000000002', 1),
(3, 3, 'FURN_0003', '5600000000003', 1),
(4, 4, 'FURN_0004', '5600000000004', 1),
(5, 5, 'FURN_0005', '5600000000005', 1);

INSERT INTO `uom_uom` (`id`, `name`) VALUES
(1, 'Units'), (2, 'Dozens'), (3, 'kg');

INSERT INTO `sale_order` (`id`, `name`, `state`, `partner_id`, `user_id`, `company_id`, `team_id`, `pricelist_id`, `warehouse_id`, `date_order`, `invoice_status`, `amount_untaxed`, `amount_tax`, `amount_total`) VALUES
(1, 'S00001', 'sale',   1, 3, 1, 1, 1, 1, '2025-01-15 10:00:00', 'invoiced',   2990.00, 388.70, 3378.70),
(2, 'S00002', 'sale',   2, 4, 2, 1, 1, 2, '2025-01-20 14:30:00', 'to invoice', 5990.00, 778.70, 6768.70),
(3, 'S00003', 'sale',   3, 3, 1, 2, 1, 1, '2025-02-01 09:00:00', 'invoiced',   1299.00, 168.87, 1467.87),
(4, 'S00004', 'draft',  4, 2, 3, 3, 2, 1, '2025-02-10 11:00:00', 'no',          898.00, 116.74,  1014.74),
(5, 'S00005', 'cancel', 1, 3, 1, 1, 1, 1, '2025-02-15 16:00:00', 'no',          599.00,  77.87,   676.87),
(6, 'S00006', 'sale',   2, 4, 2, 1, 1, 2, '2025-03-01 08:30:00', 'to invoice',  450.00,  58.50,   508.50),
(7, 'S00007', 'done',   3, 3, 1, 2, 1, 1, '2025-03-05 13:00:00', 'invoiced',   3888.00, 505.44, 4393.44),
(8, 'S00008', 'sale',   1, 3, 1, 1, 1, 1, '2025-03-10 10:00:00', 'to invoice',  245.00,  31.85,   276.85);

INSERT INTO `sale_order_line` (`id`, `order_id`, `product_id`, `product_template_id`, `product_uom`, `salesman_id`, `company_id`, `name`, `product_uom_qty`, `qty_delivered`, `qty_invoiced`, `price_unit`, `discount`, `price_subtotal`, `price_tax`, `price_total`) VALUES
(1,  1, 1, 1, 1, 3, 1, 'Office Chair',        10, 10, 10, 299.00, 0,  2990.00, 388.70, 3378.70),
(2,  2, 2, 2, 1, 4, 2, 'Desk Combo',          10, 10,  0, 599.00, 0,  5990.00, 778.70, 6768.70),
(3,  3, 4, 4, 1, 3, 1, 'Large Desk',           1,  1,  1,1299.00, 0,  1299.00, 168.87, 1467.87),
(4,  4, 1, 1, 1, 2, 3, 'Office Chair',         2,  0,  0, 299.00, 0,   598.00,  77.74,  675.74),
(5,  4, 5, 5, 1, 2, 3, 'Storage Box',          6,  0,  0,  49.00, 0,   294.00,  38.22,  332.22),
(6,  5, 2, 2, 1, 3, 1, 'Desk Combo (cancelled)', 1, 0, 0, 599.00, 0,  599.00,  77.87,  676.87),
(7,  6, 3, 3, 1, 4, 2, 'Cabinet with Doors',   1,  1,  0, 450.00, 0,   450.00,  58.50,  508.50),
(8,  7, 4, 4, 1, 3, 1, 'Large Desk',           3,  3,  3,1299.00, 0,  3897.00, 506.61, 4403.61),
(9,  8, 5, 5, 1, 3, 1, 'Storage Box',          5,  3,  0,  49.00, 0,   245.00,  31.85,  276.85);

-- ========== 采购模块 ==========

INSERT INTO `stock_picking_type` (`id`, `name`, `code`, `sequence_code`) VALUES
(1, 'Receipts', 'incoming', 'IN'),
(2, 'Delivery Orders', 'outgoing', 'OUT'),
(3, 'Internal Transfers', 'internal', 'INT'),
(4, 'Manufacturing', 'mrp_operation', 'MO');

INSERT INTO `purchase_order` (`id`, `name`, `state`, `partner_id`, `user_id`, `company_id`, `currency_id`, `picking_type_id`, `date_order`, `date_approve`, `invoice_status`, `amount_untaxed`, `amount_tax`, `amount_total`) VALUES
(1, 'P00001', 'purchase', 5, 5, 1, 1, 1, '2025-01-10 09:00:00', '2025-01-11 10:00:00', 'invoiced', 15000.00, 1950.00, 16950.00),
(2, 'P00002', 'purchase', 6, 5, 1, 1, 1, '2025-02-05 11:00:00', '2025-02-06 09:00:00', 'to invoice', 8500.00, 1105.00, 9605.00),
(3, 'P00003', 'draft',    5, 5, 2, 1, 1, '2025-03-01 14:00:00', NULL,                   'no',         3200.00,  416.00, 3616.00),
(4, 'P00004', 'cancel',   6, 5, 1, 1, 1, '2025-03-05 10:00:00', NULL,                   'no',         1000.00,  130.00, 1130.00);

-- ========== 会计模块 ==========

INSERT INTO `account_journal` (`id`, `name`, `code`, `type`, `company_id`) VALUES
(1, 'Customer Invoices', 'INV', 'sale', 1),
(2, 'Vendor Bills', 'BILL', 'purchase', 1),
(3, 'Bank', 'BNK1', 'bank', 1),
(4, 'Customer Invoices BJ', 'INV', 'sale', 2);

INSERT INTO `account_move` (`id`, `name`, `move_type`, `state`, `partner_id`, `journal_id`, `company_id`, `currency_id`, `invoice_user_id`, `team_id`, `date`, `invoice_date`, `invoice_date_due`, `payment_state`, `invoice_origin`, `amount_untaxed`, `amount_tax`, `amount_total`, `amount_residual`, `amount_untaxed_signed`, `amount_total_signed`, `amount_residual_signed`) VALUES
(1, 'INV/2025/00001', 'out_invoice', 'posted', 1, 1, 1, 1, 3, 1, '2025-01-20', '2025-01-20', '2025-02-19', 'paid',     'S00001', 2990.00, 388.70, 3378.70,    0.00,  2990.00,  3378.70,    0.00),
(2, 'INV/2025/00002', 'out_invoice', 'posted', 3, 1, 1, 1, 3, 2, '2025-02-05', '2025-02-05', '2025-03-07', 'paid',     'S00003', 1299.00, 168.87, 1467.87,    0.00,  1299.00,  1467.87,    0.00),
(3, 'INV/2025/00003', 'out_invoice', 'posted', 2, 4, 2, 1, 4, 1, '2025-03-10', '2025-03-10', '2025-04-09', 'not_paid', 'S00002', 5990.00, 778.70, 6768.70, 6768.70,  5990.00,  6768.70, 6768.70),
(4, 'BILL/2025/0001', 'in_invoice',  'posted', 5, 2, 1, 1, NULL, NULL, '2025-01-15', '2025-01-15', '2025-02-14', 'paid',    'P00001', 15000.00, 1950.00, 16950.00,    0.00, -15000.00, -16950.00,    0.00),
(5, 'BILL/2025/0002', 'in_invoice',  'posted', 6, 2, 1, 1, NULL, NULL, '2025-02-10', '2025-02-10', '2025-03-12', 'not_paid','P00002',  8500.00, 1105.00,  9605.00, 9605.00,  -8500.00,  -9605.00, -9605.00),
(6, 'INV/2025/00004', 'out_invoice', 'posted', 3, 1, 1, 1, 3, 2, '2025-03-08', '2025-03-08', '2025-04-07', 'partial', 'S00007', 3888.00, 505.44, 4393.44, 2000.00,  3888.00,  4393.44, 2000.00),
(7, 'BILL/2025/0003', 'in_invoice',  'posted', 5, 2, 1, 1, NULL, NULL, '2025-03-01', '2025-03-01', '2025-03-31', 'partial', 'P00005',  8500.00, 1110.00,  9610.00, 4610.00,  -8500.00,  -9610.00, -4610.00),
(8, 'BILL/2025/0004', 'in_invoice',  'posted', 6, 2, 1, 1, NULL, NULL, '2025-03-03', '2025-03-03', '2025-04-02', 'paid',    'P00006',  1769.91,  230.09,  2000.00,    0.00,  -1769.91,  -2000.00,     0.00),
(9, 'BILL/2025/0005', 'in_invoice',  'posted', 6, 2, 1, 1, NULL, NULL, '2025-03-04', '2025-03-04', '2025-04-03', 'paid',    'P00007',  1415.93,  184.07,  1600.00,    0.00,  -1415.93,  -1600.00,     0.00),
(10,'RINV/2025/0001','out_refund',   'posted', 1, 1, 1, 1, 3, 1, '2025-03-15', '2025-03-15', '2025-03-15', 'reversed','S00001',  500.00,   65.00,   565.00,    0.00,   -500.00,   -565.00,     0.00),
(11,'RBILL/2025/0001','in_refund',   'posted', 5, 2, 1, 1, NULL, NULL, '2025-03-16', '2025-03-16', '2025-03-16', 'reversed','P00001',  300.00,   39.00,   339.00,    0.00,    300.00,    339.00,     0.00);

INSERT INTO `account_account` (`id`, `name`, `code`, `account_type`, `company_id`) VALUES
(1, 'Accounts Receivable', '1122', 'asset_receivable', 1),
(2, 'Sales Revenue',       '6001', 'income',           1),
(3, 'Accounts Payable',    '2202', 'liability_payable',1),
(4, 'Bank',                '1002', 'asset_cash',       1);

INSERT INTO `account_move_line` (`id`, `move_id`, `account_id`, `journal_id`, `partner_id`, `product_id`, `company_id`, `currency_id`, `move_name`, `name`, `ref`, `parent_state`, `display_type`, `date`, `invoice_date`, `date_maturity`, `matching_number`, `reconciled`, `debit`, `credit`, `balance`, `amount_currency`, `amount_residual`, `quantity`, `price_unit`, `price_subtotal`, `price_total`, `discount`) VALUES
(1, 1, 1, 1, 1, NULL, 1, 1, 'INV/2025/00001', 'Receivable - Azure Interior', 'S00001', 'posted', NULL, '2025-01-20', '2025-01-20', '2025-02-19', 'P', 1, 3378.70,    0.00, 3378.70, 3378.70,    0.00, 0,    0.00,    0.00,    0.00, 0),
(2, 3, 1, 4, 2, NULL, 2, 1, 'INV/2025/00003', 'Receivable - Deco Addict',    'S00002', 'posted', NULL, '2025-03-10', '2025-03-10', '2025-04-09', NULL, 0, 6768.70,    0.00, 6768.70, 6768.70, 6768.70, 0,    0.00,    0.00,    0.00, 0),
(3, 6, 1, 1, 3, NULL, 1, 1, 'INV/2025/00004', 'Receivable - Gemini Furniture','S00007', 'posted', NULL, '2025-03-08', '2025-03-08', '2025-04-07', 'P', 0, 4393.44,    0.00, 4393.44, 4393.44, 2000.00, 0,    0.00,    0.00,    0.00, 0),
(4, 4, 3, 2, 5, NULL, 1, 1, 'BILL/2025/0001', 'Payable - Wood Corner',       'P00001', 'posted', NULL, '2025-01-15', '2025-01-15', '2025-02-14', 'P', 1,    0.00,16950.00,-16950.00,-16950.00,    0.00, 0,    0.00,    0.00,    0.00, 0),
(5, 3, 2, 4, 2, 2,    2, 1, 'INV/2025/00003', 'Desk Combo',                  'S00002', 'posted', 'product', '2025-03-10', '2025-03-10', NULL, NULL, 0, 0.00, 5990.00, -5990.00, -5990.00,    0.00, 10, 599.00, 5990.00, 6768.70, 0),
(6, 5, 3, 2, 6, NULL, 1, 1, 'BILL/2025/0002', 'Payable - Steel Supplier Co', 'P00002', 'posted', NULL, '2025-02-10', '2025-02-10', '2025-03-12', NULL, 0,    0.00, 9605.00, -9605.00, -9605.00, 9605.00, 0,    0.00,    0.00,    0.00, 0),
(7, 7, 3, 2, 5, NULL, 1, 1, 'BILL/2025/0003', 'Payable - Wood Corner Partial','P00005', 'posted', NULL, '2025-03-01', '2025-03-01', '2025-03-31', 'P', 0,    0.00, 9610.00, -9610.00, -9610.00, 4610.00, 0,    0.00,    0.00,    0.00, 0),
(8, 8, 3, 2, 6, NULL, 1, 1, 'BILL/2025/0004', 'Payable - Steel Split A',     'P00006', 'posted', NULL, '2025-03-03', '2025-03-03', '2025-04-02', 'P', 1,    0.00, 2000.00, -2000.00, -2000.00,    0.00, 0,    0.00,    0.00,    0.00, 0),
(9, 9, 3, 2, 6, NULL, 1, 1, 'BILL/2025/0005', 'Payable - Steel Split B',     'P00007', 'posted', NULL, '2025-03-04', '2025-03-04', '2025-04-03', 'P', 1,    0.00, 1600.00, -1600.00, -1600.00,    0.00, 0,    0.00,    0.00,    0.00, 0),
(10,10, 1, 1, 1, NULL, 1, 1, 'RINV/2025/0001', 'Receivable Refund - Azure Interior','S00001', 'posted', NULL, '2025-03-15', '2025-03-15', '2025-03-15', 'R', 1,    0.00,  565.00,  -565.00,  -565.00,    0.00, 0,    0.00,    0.00,    0.00, 0),
(11,11, 3, 2, 5, NULL, 1, 1, 'RBILL/2025/0001','Vendor Refund - Wood Corner','P00001', 'posted', NULL, '2025-03-16', '2025-03-16', '2025-03-16', 'R', 1,  339.00,    0.00,   339.00,   339.00,    0.00, 0,    0.00,    0.00,    0.00, 0);

INSERT INTO `account_payment_method` (`id`, `name`, `code`, `payment_type`) VALUES
(1, 'Manual', 'manual', 'inbound'),
(2, 'Manual', 'manual', 'outbound');

INSERT INTO `account_payment` (`id`, `move_id`, `partner_id`, `currency_id`, `destination_journal_id`, `payment_method_id`, `payment_type`, `partner_type`, `payment_reference`, `is_reconciled`, `is_matched`, `is_internal_transfer`, `amount`, `amount_company_currency_signed`) VALUES
(1, 1, 1, 1, 3, 1, 'inbound',  'customer', 'PAY/2025/00001', 1, 1, 0, 3378.70,  3378.70),
(2, 6, 3, 1, 3, 1, 'inbound',  'customer', 'PAY/2025/00002', 0, 0, 0, 1200.00,  1200.00),
(3, 4, 5, 1, 3, 2, 'outbound', 'supplier', 'PAY/2025/00003', 1, 1, 0,16950.00,-16950.00),
(4, 7, 5, 1, 3, 2, 'outbound', 'supplier', 'PAY/2025/00004', 1, 1, 0, 5000.00, -5000.00),
(5, 8, 6, 1, 3, 2, 'outbound', 'supplier', 'PAY/2025/00005', 1, 1, 0, 3600.00, -3600.00);

INSERT INTO `account_payment_bill_match` (`id`, `payment_id`, `bill_move_id`, `payable_line_id`, `partner_id`, `matched_date`, `match_status`, `matched_amount`) VALUES
(1, 3, 4, 4, 5, '2025-02-10', 'matched', 16950.00),
(2, 4, 7, 7, 5, '2025-03-01', 'matched',  5000.00),
(3, 5, 8, 8, 6, '2025-03-03', 'matched',  2000.00),
(4, 5, 9, 9, 6, '2025-03-03', 'matched',  1600.00);

-- ========== 库存模块 ==========

INSERT INTO `stock_location` (`id`, `complete_name`) VALUES
(1, 'Physical Locations / WH / Stock'),
(2, 'Physical Locations / WH / Input'),
(3, 'Partner Locations / Customers'),
(4, 'Partner Locations / Vendors');

INSERT INTO `stock_picking` (`id`, `name`, `state`, `partner_id`, `picking_type_id`, `location_id`, `location_dest_id`, `user_id`, `company_id`, `origin`, `scheduled_date`, `date_done`) VALUES
(1, 'WH/IN/00001',  'done',      5, 1, 4, 1, 5, 1, 'P00001', '2025-01-12 10:00:00', '2025-01-12 15:00:00'),
(2, 'WH/OUT/00001', 'done',      1, 2, 1, 3, 3, 1, 'S00001', '2025-01-16 08:00:00', '2025-01-17 10:00:00'),
(3, 'WH/OUT/00002', 'assigned',  2, 2, 1, 3, 4, 2, 'S00002', '2025-01-22 08:00:00', NULL),
(4, 'WH/IN/00002',  'done',      6, 1, 4, 1, 5, 1, 'P00002', '2025-02-07 09:00:00', '2025-02-08 11:00:00'),
(5, 'WH/OUT/00003', 'done',      3, 2, 1, 3, 3, 1, 'S00003', '2025-02-02 08:00:00', '2025-02-03 14:00:00'),
(6, 'WH/INT/00001', 'confirmed', NULL, 3, 1, 2, 1, 1, NULL,    '2025-03-01 10:00:00', NULL),
(7, 'WH/OUT/00004', 'done',      3, 2, 1, 3, 3, 1, 'S00007', '2025-03-06 08:00:00', '2025-03-07 15:00:00');

INSERT INTO `purchase_document_flow` (`id`, `purchase_order_id`, `receipt_picking_id`, `bill_move_id`, `vendor_id`, `flow_status`, `receipt_status`, `billing_status`, `payment_state`, `ordered_amount`, `billed_amount`, `bill_residual`) VALUES
(1, 1, 1, 4, 5, 'received_billed_paid', 'done', 'invoiced',   'paid',     16950.00, 16950.00,    0.00),
(2, 2, 4, 5, 6, 'received_billed_open', 'done', 'to invoice', 'not_paid',  9605.00,  9605.00, 9605.00);

INSERT INTO `sale_document_flow` (`id`, `sale_order_id`, `delivery_picking_id`, `invoice_move_id`, `payment_id`, `customer_id`, `flow_status`, `delivery_status`, `invoice_status`, `payment_state`, `ordered_amount`, `invoiced_amount`, `invoice_residual`, `paid_amount`) VALUES
(1, 1, 2, 1, 1, 1, 'delivered_invoiced_paid',    'done',     'invoiced',   'paid',     3378.70, 3378.70,    0.00, 3378.70),
(2, 2, 3, 3, NULL, 2, 'waiting_delivery_open',   'assigned', 'to invoice', 'not_paid', 6768.70, 6768.70, 6768.70,    0.00),
(3, 7, 7, 6, 2, 3, 'delivered_invoiced_partial', 'done',     'invoiced',   'partial',  4393.44, 4393.44, 2000.00, 1200.00);

-- ========== 制造模块 ==========

INSERT INTO `mrp_bom` (`id`, `code`, `type`, `product_id`, `product_qty`, `company_id`) VALUES
(1, 'BOM-FURN-0001', 'normal', 1, 10, 1),
(2, 'BOM-FURN-0002', 'normal', 2, 5, 1),
(3, 'BOM-FURN-0004', 'normal', 4, 2, 1);

INSERT INTO `mrp_production` (`id`, `name`, `state`, `priority`, `origin`, `product_id`, `bom_id`, `product_uom_id`, `user_id`, `company_id`, `picking_type_id`, `location_src_id`, `location_dest_id`, `date_start`, `date_finished`, `date_deadline`, `is_locked`, `consumption`, `product_qty`, `qty_producing`) VALUES
(1, 'MO/2025/00001', 'done',      '0', 'S00001', 1, 1, 1, 1, 1, 4, 1, 1, '2025-01-13 08:00:00', '2025-01-14 17:00:00', '2025-01-15 18:00:00', 1, 'strict',   10, 10),
(2, 'MO/2025/00002', 'progress',  '1', 'S00002', 2, 2, 1, 1, 1, 4, 1, 1, '2025-02-10 09:00:00', NULL,                  '2025-02-18 18:00:00', 1, 'flexible',  5,  3),
(3, 'MO/2025/00003', 'confirmed', '0', 'S00003', 4, 3, 1, 1, 1, 4, 1, 1, '2025-03-05 09:00:00', NULL,                  '2025-03-15 18:00:00', 0, 'warning',   2,  0),
(4, 'MO/2025/00004', 'done',      '0', 'S00007', 4, 3, 1, 1, 1, 4, 1, 1, '2025-03-08 08:00:00', '2025-03-10 16:00:00', '2025-03-11 18:00:00', 1, 'strict',    3,  3),
(5, 'MO/2025/00005', 'to_close',  '1', 'S00008', 5, NULL, 1, 1, 1, 4, 1, 1, '2025-03-12 08:00:00', NULL,                 '2025-03-20 18:00:00', 1, 'flexible', 20, 18),
(6, 'MO/2025/00006', 'cancel',    '0', NULL,     3, NULL, 1, 1, 1, 4, 1, 1, '2025-02-01 08:00:00', NULL,                 '2025-02-10 18:00:00', 0, 'warning',   1,  0);

-- ========== HR 模块 ==========

INSERT INTO `hr_department` (`id`, `name`, `complete_name`, `parent_id`) VALUES
(4, 'Management', 'Management', NULL),
(1, 'Sales', 'Management / Sales', 4),
(2, 'Purchasing', 'Management / Purchasing', 4),
(3, 'Accounting', 'Management / Accounting', 4);

INSERT INTO `hr_job` (`id`, `name`) VALUES
(1, 'Sales Representative'),
(2, 'Sales Manager'),
(3, 'Purchaser'),
(4, 'Accountant'),
(5, 'CEO');

INSERT INTO `hr_work_location` (`id`, `name`) VALUES
(1, 'Office - Shanghai'),
(2, 'Office - Beijing'),
(3, 'Remote');

INSERT INTO `hr_employee` (`id`, `name`, `job_title`, `work_email`, `department_id`, `job_id`, `company_id`, `parent_id`, `work_location_id`, `user_id`, `active`, `gender`, `employee_type`) VALUES
(1, 'Admin User',     'Administrator',       'admin@mycompany.com',    4, 5, 1, NULL, 1, 1, 1, 'male',   'employee'),
(2, 'Zhang Wei',      'Sales Manager',       'zhang.wei@mycompany.com', 1, 2, 1, 1,   1, 2, 1, 'male',   'employee'),
(3, 'Li Na',          'Sales Representative', 'li.na@mycompany.com',    1, 1, 1, 2,   1, 3, 1, 'female', 'employee'),
(4, 'Wang Jun',       'Sales Representative', 'wang.jun@mycompany.com', 1, 1, 2, 2,   2, 4, 1, 'male',   'employee'),
(5, 'Chen Mei',       'Purchaser',           'chen.mei@mycompany.com', 2, 3, 1, 1,   1, 5, 1, 'female', 'employee'),
(6, 'Liu Fang',       'Accountant',          'liu.fang@mycompany.com', 3, 4, 1, 1,   3, 6, 1, 'female', 'employee');
