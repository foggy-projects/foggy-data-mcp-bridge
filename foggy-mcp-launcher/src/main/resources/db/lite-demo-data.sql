INSERT INTO dim_channel (channel_id, channel_name, channel_type, platform, status) VALUES
('CHN001', 'Tmall Flagship Store', 'ONLINE', 'Tmall', 'ACTIVE'),
('CHN002', 'JD Self-operated Store', 'ONLINE', 'JD', 'ACTIVE'),
('CHN003', 'Offline Direct Store', 'OFFLINE', 'Offline', 'ACTIVE');

INSERT INTO dim_promotion (promotion_id, promotion_name, promotion_type, discount_rate, start_date, end_date, status) VALUES
('PRM001', 'No Promotion', NULL, 1.00, '2024-01-01', '2099-12-31', 'ACTIVE'),
('PRM002', 'New Year 10 Percent Off', 'DISCOUNT', 0.90, '2024-01-01', '2024-01-15', 'ACTIVE'),
('PRM003', 'Member Day', 'DISCOUNT', 0.88, '2024-01-01', '2024-12-31', 'ACTIVE');

INSERT INTO dim_store (store_id, store_name, store_type, province, city, district, address, manager_name, open_date, area_sqm, status) VALUES
('STR00001', 'Hangzhou West Lake Store', 'DIRECT', 'Zhejiang', 'Hangzhou', 'Xihu', 'No. 1 Xihu Road', 'Manager A', '2020-01-15', 350.00, 'ACTIVE'),
('STR00002', 'Nanjing Xuanwu Store', 'DIRECT', 'Jiangsu', 'Nanjing', 'Xuanwu', 'No. 2 Xuanwu Road', 'Manager B', '2020-03-20', 420.00, 'ACTIVE'),
('STR00003', 'Shanghai Pudong Store', 'FRANCHISE', 'Shanghai', 'Shanghai', 'Pudong', 'No. 3 Pudong Road', 'Manager C', '2021-01-05', 500.00, 'ACTIVE');

INSERT INTO dim_customer (customer_id, customer_name, customer_type, gender, age_group, province, city, district, register_date, member_level, status) VALUES
('CUS000001', 'Customer 1', 'VIP', 'M', '25-34', 'Zhejiang', 'Hangzhou', 'Xihu', '2020-01-10', 'DIAMOND', 'ACTIVE'),
('CUS000002', 'Customer 2', 'VIP', 'F', '25-34', 'Jiangsu', 'Nanjing', 'Xuanwu', '2020-02-15', 'PLATINUM', 'ACTIVE'),
('CUS000003', 'Customer 3', 'NORMAL', 'M', '35-44', 'Shanghai', 'Shanghai', 'Pudong', '2021-01-05', 'SILVER', 'ACTIVE'),
('CUS000004', 'Customer 4', 'NEW', 'F', '18-24', 'Zhejiang', 'Hangzhou', 'Xihu', '2023-02-15', 'BRONZE', 'ACTIVE');

INSERT INTO dim_date (date_key, full_date, year, quarter, month, month_name, week_of_year, day_of_month, day_of_week, day_name, is_weekend, is_holiday, fiscal_year, fiscal_quarter) VALUES
(20240101, '2024-01-01', 2024, 1, 1, 'January', 1, 1, 1, 'Monday', 0, 1, 2024, 1),
(20240102, '2024-01-02', 2024, 1, 1, 'January', 1, 2, 2, 'Tuesday', 0, 0, 2024, 1),
(20240103, '2024-01-03', 2024, 1, 1, 'January', 1, 3, 3, 'Wednesday', 0, 0, 2024, 1),
(20240104, '2024-01-04', 2024, 1, 1, 'January', 1, 4, 4, 'Thursday', 0, 0, 2024, 1);

INSERT INTO fact_order (order_id, date_key, customer_key, store_key, channel_key, promotion_key, total_quantity, total_amount, discount_amount, freight_amount, pay_amount, order_status, payment_status, order_time) VALUES
('ORD-LITE-0001', 20240101, 1, 1, 1, 2, 2, 10998.00, 1099.80, 0.00, 9898.20, 'COMPLETED', 'PAID', '2024-01-01 10:30:00'),
('ORD-LITE-0002', 20240101, 2, 2, 2, 1, 1, 4599.00, 0.00, 0.00, 4599.00, 'COMPLETED', 'PAID', '2024-01-01 14:20:00'),
('ORD-LITE-0003', 20240102, 3, 3, 1, 1, 3, 1298.00, 0.00, 0.00, 1298.00, 'PROCESSING', 'PARTIAL', '2024-01-02 09:15:00'),
('ORD-LITE-0004', 20240103, 4, 1, 3, 3, 1, 1299.00, 155.88, 0.00, 1143.12, 'PENDING', 'UNPAID', '2024-01-03 15:30:00'),
('ORD-LITE-0005', 20240104, 1, 2, 1, 1, 1, 19.90, 0.00, 10.00, 29.90, 'CANCELLED', 'UNPAID', '2024-01-04 09:00:00');

INSERT INTO crm_lead (lead_id, created_at, lead_source, converted_opportunity_id, converted_order_id) VALUES
('CRM-LITE-001', '2024-01-01 09:00:00', 'WEB', 'OPP-LITE-001', 'ORD-LITE-0001'),
('CRM-LITE-002', '2024-01-01 11:00:00', 'WEB', 'OPP-LITE-002', NULL),
('CRM-LITE-003', '2024-01-02 10:00:00', 'APP', 'OPP-LITE-003', 'ORD-LITE-0004'),
('CRM-LITE-004', '2024-01-03 15:00:00', 'APP', NULL, NULL),
('CRM-LITE-005', '2024-01-04 08:30:00', 'PHONE', 'OPP-LITE-005', 'ORD-LITE-0005'),
('CRM-LITE-006', '2024-01-04 16:00:00', 'PHONE', NULL, NULL);

INSERT INTO customer_order_lifecycle
(customer_id, customer_name, first_order_date, first_order_month, first_order_channel, order_count, lifetime_amount,
 repurchase_30d_flag, repurchase_60d_flag, repurchase_90d_flag)
VALUES
('CUS000001', 'Customer 1', '2024-01-01', '2024-01', 'ONLINE', 2, 11017.90, 1, 1, 1),
('CUS000002', 'Customer 2', '2024-01-01', '2024-01', 'ONLINE', 1, 4599.00, 0, 0, 0),
('CUS000003', 'Customer 3', '2024-01-02', '2024-01', 'ONLINE', 1, 1298.00, 0, 0, 0),
('CUS000004', 'Customer 4', '2024-01-03', '2024-01', 'OFFLINE', 1, 1299.00, 0, 0, 0);
