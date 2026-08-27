CREATE TABLE smoke_orders (
    order_id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL
);

INSERT INTO smoke_orders (order_id, tenant_id, amount)
VALUES (1, 'tenant-smoke', 125.50),
       (2, 'tenant-other', 900.00);