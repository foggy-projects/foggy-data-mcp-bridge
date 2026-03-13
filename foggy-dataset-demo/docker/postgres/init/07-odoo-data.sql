-- ============================================
-- Odoo 样本数据 (PostgreSQL)
-- 文件: 07-odoo-data.sql
-- ============================================

-- ========== 基础维度 ==========

INSERT INTO res_currency (id, name) VALUES
(1, 'CNY'), (2, 'USD'), (3, 'EUR');

INSERT INTO res_country (id, name, code) VALUES
(1, 'China', 'CN'), (2, 'United States', 'US'), (3, 'Germany', 'DE');

INSERT INTO res_country_state (id, name, code, country_id) VALUES
(1, 'Zhejiang', 'ZJ', 1), (2, 'Shanghai', 'SH', 1), (3, 'Beijing', 'BJ', 1);

INSERT INTO res_company (id, name, parent_id, currency_id, country_id, city, active) VALUES
(1, 'My Company (Shanghai)', NULL, 1, 1, 'Shanghai', 1),
(2, 'My Company (Beijing)',  1,    1, 1, 'Beijing', 1),
(3, 'Overseas Branch',       1,    2, 2, 'New York', 1);
SELECT setval('res_company_id_seq', 3);

INSERT INTO res_users (id, login, company_id) VALUES
(1, 'admin', 1),
(2, 'sales_mgr', 1),
(3, 'sales_rep_sh', 1),
(4, 'sales_rep_bj', 2),
(5, 'purchaser', 1),
(6, 'accountant', 1);
SELECT setval('res_users_id_seq', 6);

INSERT INTO crm_team (id, name) VALUES
(1, 'Direct Sales'),
(2, 'Online Sales'),
(3, 'Enterprise Sales');
SELECT setval('crm_team_id_seq', 3);

INSERT INTO res_partner (id, name, display_name, type, is_company, city, country_id, state_id, company_id, user_id, team_id, customer_rank, supplier_rank, email, phone, credit_limit, active) VALUES
(1, 'Azure Interior', 'Azure Interior', 'contact', 1, 'Shanghai', 1, 2, 1, 3, 1, 1, 0, 'azure@test.com', '021-55551234', 50000, 1),
(2, 'Deco Addict', 'Deco Addict', 'contact', 1, 'Beijing', 1, 3, 2, 4, 1, 1, 0, 'deco@test.com', '010-88881234', 80000, 1),
(3, 'Gemini Furniture', 'Gemini Furniture', 'contact', 1, 'Hangzhou', 1, 1, 1, NULL, 2, 1, 0, 'gemini@test.com', NULL, 30000, 1),
(4, 'Ready Mat', 'Ready Mat', 'contact', 1, 'New York', 2, NULL, 3, NULL, 3, 1, 0, 'readymat@test.com', '+1-555-0100', 100000, 1),
(5, 'Wood Corner', 'Wood Corner', 'contact', 1, 'Shanghai', 1, 2, 1, NULL, NULL, 0, 1, 'woodcorner@test.com', '021-33331234', 0, 1),
(6, 'Steel Supplier Co', 'Steel Supplier Co', 'contact', 1, 'Hangzhou', 1, 1, 1, NULL, NULL, 0, 1, 'steel@test.com', NULL, 0, 1);
SELECT setval('res_partner_id_seq', 6);

-- ========== 销售模块 ==========

INSERT INTO product_pricelist (id, name) VALUES
(1, 'Public Pricelist'), (2, 'VIP Pricelist');
SELECT setval('product_pricelist_id_seq', 2);

INSERT INTO stock_warehouse (id, name, company_id) VALUES
(1, 'WH/Shanghai', 1), (2, 'WH/Beijing', 2);
SELECT setval('stock_warehouse_id_seq', 2);

INSERT INTO product_template (id, name, type, categ_id, list_price) VALUES
(1, 'Office Chair', 'consu', 1, 299.00),
(2, 'Desk Combo', 'consu', 1, 599.00),
(3, 'Cabinet with Doors', 'consu', 1, 450.00),
(4, 'Large Desk', 'consu', 1, 1299.00),
(5, 'Storage Box', 'consu', 2, 49.00);
SELECT setval('product_template_id_seq', 5);

INSERT INTO product_product (id, product_tmpl_id, default_code, barcode, active) VALUES
(1, 1, 'FURN_0001', '5600000000001', 1),
(2, 2, 'FURN_0002', '5600000000002', 1),
(3, 3, 'FURN_0003', '5600000000003', 1),
(4, 4, 'FURN_0004', '5600000000004', 1),
(5, 5, 'FURN_0005', '5600000000005', 1);
SELECT setval('product_product_id_seq', 5);

INSERT INTO uom_uom (id, name) VALUES
(1, 'Units'), (2, 'Dozens'), (3, 'kg');
SELECT setval('uom_uom_id_seq', 3);

INSERT INTO sale_order (id, name, state, partner_id, user_id, company_id, team_id, pricelist_id, warehouse_id, date_order, invoice_status, amount_untaxed, amount_tax, amount_total) VALUES
(1, 'S00001', 'sale',   1, 3, 1, 1, 1, 1, '2025-01-15 10:00:00', 'invoiced',   2990.00, 388.70, 3378.70),
(2, 'S00002', 'sale',   2, 4, 2, 1, 1, 2, '2025-01-20 14:30:00', 'to invoice', 5990.00, 778.70, 6768.70),
(3, 'S00003', 'sale',   3, 3, 1, 2, 1, 1, '2025-02-01 09:00:00', 'invoiced',   1299.00, 168.87, 1467.87),
(4, 'S00004', 'draft',  4, 2, 3, 3, 2, 1, '2025-02-10 11:00:00', 'no',          898.00, 116.74,  1014.74),
(5, 'S00005', 'cancel', 1, 3, 1, 1, 1, 1, '2025-02-15 16:00:00', 'no',          599.00,  77.87,   676.87),
(6, 'S00006', 'sale',   2, 4, 2, 1, 1, 2, '2025-03-01 08:30:00', 'to invoice',  450.00,  58.50,   508.50),
(7, 'S00007', 'done',   3, 3, 1, 2, 1, 1, '2025-03-05 13:00:00', 'invoiced',   3888.00, 505.44, 4393.44),
(8, 'S00008', 'sale',   1, 3, 1, 1, 1, 1, '2025-03-10 10:00:00', 'to invoice',  245.00,  31.85,   276.85);
SELECT setval('sale_order_id_seq', 8);

INSERT INTO sale_order_line (id, order_id, product_id, product_template_id, product_uom, salesman_id, company_id, name, product_uom_qty, qty_delivered, qty_invoiced, price_unit, discount, price_subtotal, price_tax, price_total) VALUES
(1,  1, 1, 1, 1, 3, 1, 'Office Chair',        10, 10, 10, 299.00, 0,  2990.00, 388.70, 3378.70),
(2,  2, 2, 2, 1, 4, 2, 'Desk Combo',          10, 10,  0, 599.00, 0,  5990.00, 778.70, 6768.70),
(3,  3, 4, 4, 1, 3, 1, 'Large Desk',           1,  1,  1,1299.00, 0,  1299.00, 168.87, 1467.87),
(4,  4, 1, 1, 1, 2, 3, 'Office Chair',         2,  0,  0, 299.00, 0,   598.00,  77.74,  675.74),
(5,  4, 5, 5, 1, 2, 3, 'Storage Box',          6,  0,  0,  49.00, 0,   294.00,  38.22,  332.22),
(6,  5, 2, 2, 1, 3, 1, 'Desk Combo (cancelled)', 1, 0, 0, 599.00, 0,  599.00,  77.87,  676.87),
(7,  6, 3, 3, 1, 4, 2, 'Cabinet with Doors',   1,  1,  0, 450.00, 0,   450.00,  58.50,  508.50),
(8,  7, 4, 4, 1, 3, 1, 'Large Desk',           3,  3,  3,1299.00, 0,  3897.00, 506.61, 4403.61),
(9,  8, 5, 5, 1, 3, 1, 'Storage Box',          5,  3,  0,  49.00, 0,   245.00,  31.85,  276.85);
SELECT setval('sale_order_line_id_seq', 9);

-- ========== 采购模块 ==========

INSERT INTO stock_picking_type (id, name, code, sequence_code) VALUES
(1, 'Receipts', 'incoming', 'IN'),
(2, 'Delivery Orders', 'outgoing', 'OUT'),
(3, 'Internal Transfers', 'internal', 'INT');
SELECT setval('stock_picking_type_id_seq', 3);

INSERT INTO purchase_order (id, name, state, partner_id, user_id, company_id, currency_id, picking_type_id, date_order, date_approve, invoice_status, amount_untaxed, amount_tax, amount_total) VALUES
(1, 'P00001', 'purchase', 5, 5, 1, 1, 1, '2025-01-10 09:00:00', '2025-01-11 10:00:00', 'invoiced', 15000.00, 1950.00, 16950.00),
(2, 'P00002', 'purchase', 6, 5, 1, 1, 1, '2025-02-05 11:00:00', '2025-02-06 09:00:00', 'to invoice', 8500.00, 1105.00, 9605.00),
(3, 'P00003', 'draft',    5, 5, 2, 1, 1, '2025-03-01 14:00:00', NULL,                   'no',         3200.00,  416.00, 3616.00),
(4, 'P00004', 'cancel',   6, 5, 1, 1, 1, '2025-03-05 10:00:00', NULL,                   'no',         1000.00,  130.00, 1130.00);
SELECT setval('purchase_order_id_seq', 4);

-- ========== 会计模块 ==========

INSERT INTO account_journal (id, name, code, type, company_id) VALUES
(1, 'Customer Invoices', 'INV', 'sale', 1),
(2, 'Vendor Bills', 'BILL', 'purchase', 1),
(3, 'Bank', 'BNK1', 'bank', 1),
(4, 'Customer Invoices BJ', 'INV', 'sale', 2);
SELECT setval('account_journal_id_seq', 4);

INSERT INTO account_move (id, name, move_type, state, partner_id, journal_id, company_id, currency_id, invoice_user_id, team_id, date, invoice_date, invoice_date_due, payment_state, invoice_origin, amount_untaxed, amount_tax, amount_total, amount_residual, amount_untaxed_signed, amount_total_signed, amount_residual_signed) VALUES
(1, 'INV/2025/00001', 'out_invoice', 'posted', 1, 1, 1, 1, 3, 1, '2025-01-20', '2025-01-20', '2025-02-19', 'paid',     'S00001', 2990.00, 388.70, 3378.70,    0.00,  2990.00,  3378.70,    0.00),
(2, 'INV/2025/00002', 'out_invoice', 'posted', 3, 1, 1, 1, 3, 2, '2025-02-05', '2025-02-05', '2025-03-07', 'paid',     'S00003', 1299.00, 168.87, 1467.87,    0.00,  1299.00,  1467.87,    0.00),
(3, 'INV/2025/00003', 'out_invoice', 'posted', 2, 4, 2, 1, 4, 1, '2025-03-10', '2025-03-10', '2025-04-09', 'not_paid', 'S00002', 5990.00, 778.70, 6768.70, 6768.70,  5990.00,  6768.70, 6768.70),
(4, 'BILL/2025/0001', 'in_invoice',  'posted', 5, 2, 1, 1, NULL, NULL, '2025-01-15', '2025-01-15', '2025-02-14', 'paid',    'P00001', 15000.00, 1950.00, 16950.00,    0.00, -15000.00, -16950.00,    0.00),
(5, 'BILL/2025/0002', 'in_invoice',  'draft',  6, 2, 1, 1, NULL, NULL, '2025-02-10', '2025-02-10', '2025-03-12', 'not_paid','P00002',  8500.00, 1105.00,  9605.00, 9605.00,  -8500.00,  -9605.00, -9605.00),
(6, 'INV/2025/00004', 'out_invoice', 'posted', 3, 1, 1, 1, 3, 2, '2025-03-08', '2025-03-08', '2025-04-07', 'partial', 'S00007', 3888.00, 505.44, 4393.44, 2000.00,  3888.00,  4393.44, 2000.00);
SELECT setval('account_move_id_seq', 6);

-- ========== 库存模块 ==========

INSERT INTO stock_location (id, complete_name) VALUES
(1, 'Physical Locations / WH / Stock'),
(2, 'Physical Locations / WH / Input'),
(3, 'Partner Locations / Customers'),
(4, 'Partner Locations / Vendors');
SELECT setval('stock_location_id_seq', 4);

INSERT INTO stock_picking (id, name, state, partner_id, picking_type_id, location_id, location_dest_id, user_id, company_id, origin, scheduled_date, date_done) VALUES
(1, 'WH/IN/00001',  'done',      5, 1, 4, 1, 5, 1, 'P00001', '2025-01-12 10:00:00', '2025-01-12 15:00:00'),
(2, 'WH/OUT/00001', 'done',      1, 2, 1, 3, 3, 1, 'S00001', '2025-01-16 08:00:00', '2025-01-17 10:00:00'),
(3, 'WH/OUT/00002', 'assigned',  2, 2, 1, 3, 4, 2, 'S00002', '2025-01-22 08:00:00', NULL),
(4, 'WH/IN/00002',  'done',      6, 1, 4, 1, 5, 1, 'P00002', '2025-02-07 09:00:00', '2025-02-08 11:00:00'),
(5, 'WH/OUT/00003', 'done',      3, 2, 1, 3, 3, 1, 'S00003', '2025-02-02 08:00:00', '2025-02-03 14:00:00'),
(6, 'WH/INT/00001', 'confirmed', NULL, 3, 1, 2, 1, 1, NULL,    '2025-03-01 10:00:00', NULL);
SELECT setval('stock_picking_id_seq', 6);

-- ========== HR 模块 ==========

INSERT INTO hr_department (id, name, complete_name, parent_id) VALUES
(4, 'Management', 'Management', NULL),
(1, 'Sales', 'Management / Sales', 4),
(2, 'Purchasing', 'Management / Purchasing', 4),
(3, 'Accounting', 'Management / Accounting', 4);
SELECT setval('hr_department_id_seq', 4);

INSERT INTO hr_job (id, name) VALUES
(1, 'Sales Representative'),
(2, 'Sales Manager'),
(3, 'Purchaser'),
(4, 'Accountant'),
(5, 'CEO');
SELECT setval('hr_job_id_seq', 5);

INSERT INTO hr_work_location (id, name) VALUES
(1, 'Office - Shanghai'),
(2, 'Office - Beijing'),
(3, 'Remote');
SELECT setval('hr_work_location_id_seq', 3);

INSERT INTO hr_employee (id, name, job_title, work_email, department_id, job_id, company_id, parent_id, work_location_id, user_id, active, gender, employee_type) VALUES
(1, 'Admin User',     'Administrator',       'admin@mycompany.com',    4, 5, 1, NULL, 1, 1, 1, 'male',   'employee'),
(2, 'Zhang Wei',      'Sales Manager',       'zhang.wei@mycompany.com', 1, 2, 1, 1,   1, 2, 1, 'male',   'employee'),
(3, 'Li Na',          'Sales Representative', 'li.na@mycompany.com',    1, 1, 1, 2,   1, 3, 1, 'female', 'employee'),
(4, 'Wang Jun',       'Sales Representative', 'wang.jun@mycompany.com', 1, 1, 2, 2,   2, 4, 1, 'male',   'employee'),
(5, 'Chen Mei',       'Purchaser',           'chen.mei@mycompany.com', 2, 3, 1, 1,   1, 5, 1, 'female', 'employee'),
(6, 'Liu Fang',       'Accountant',          'liu.fang@mycompany.com', 3, 4, 1, 1,   3, 6, 1, 'female', 'employee');
SELECT setval('hr_employee_id_seq', 6);
