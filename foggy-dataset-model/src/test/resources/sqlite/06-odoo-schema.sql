-- ============================================
-- Odoo 核心表结构 (SQLite 测试用最小集)
-- 文件: 06-odoo-schema.sql
-- 说明: 仅包含 TM 模型引用的列，足够验证模型加载和 ForcedFilter 集成
-- ============================================

-- ========== 基础维度表 ==========

DROP TABLE IF EXISTS res_company;
CREATE TABLE res_company (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    parent_id   INTEGER,
    currency_id INTEGER,
    country_id  INTEGER,
    street      TEXT,
    city        TEXT,
    zip         TEXT,
    email       TEXT,
    phone       TEXT,
    website     TEXT,
    vat         TEXT,
    active      INTEGER DEFAULT 1,
    create_date TEXT DEFAULT (datetime('now'))
);

DROP TABLE IF EXISTS res_currency;
CREATE TABLE res_currency (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

DROP TABLE IF EXISTS res_country;
CREATE TABLE res_country (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    code TEXT
);

DROP TABLE IF EXISTS res_country_state;
CREATE TABLE res_country_state (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    code       TEXT,
    country_id INTEGER
);

DROP TABLE IF EXISTS res_users;
CREATE TABLE res_users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    login      TEXT NOT NULL,
    company_id INTEGER,
    partner_id INTEGER
);

DROP TABLE IF EXISTS res_partner;
CREATE TABLE res_partner (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    display_name  TEXT,
    type          TEXT DEFAULT 'contact',
    parent_id     INTEGER,
    company_id    INTEGER,
    user_id       INTEGER,
    team_id       INTEGER,
    country_id    INTEGER,
    state_id      INTEGER,
    email         TEXT,
    phone         TEXT,
    mobile        TEXT,
    street        TEXT,
    city          TEXT,
    zip           TEXT,
    website       TEXT,
    vat           TEXT,
    ref           TEXT,
    lang          TEXT DEFAULT 'en_US',
    is_company    INTEGER DEFAULT 0,
    active        INTEGER DEFAULT 1,
    customer_rank INTEGER DEFAULT 0,
    supplier_rank INTEGER DEFAULT 0,
    credit_limit  REAL DEFAULT 0,
    create_date   TEXT DEFAULT (datetime('now')),
    write_date    TEXT DEFAULT (datetime('now'))
);

DROP TABLE IF EXISTS crm_team;
CREATE TABLE crm_team (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

-- ========== 销售模块 ==========

DROP TABLE IF EXISTS product_pricelist;
CREATE TABLE product_pricelist (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

DROP TABLE IF EXISTS stock_warehouse;
CREATE TABLE stock_warehouse (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    company_id INTEGER
);

DROP TABLE IF EXISTS sale_order;
CREATE TABLE sale_order (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT NOT NULL,
    state            TEXT DEFAULT 'draft',
    partner_id       INTEGER,
    user_id          INTEGER,
    company_id       INTEGER,
    team_id          INTEGER,
    pricelist_id     INTEGER,
    warehouse_id     INTEGER,
    date_order       TEXT,
    commitment_date  TEXT,
    invoice_status   TEXT DEFAULT 'no',
    client_order_ref TEXT,
    origin           TEXT,
    note             TEXT,
    amount_untaxed   REAL DEFAULT 0,
    amount_tax       REAL DEFAULT 0,
    amount_total     REAL DEFAULT 0,
    currency_rate    REAL DEFAULT 1.0,
    create_date      TEXT DEFAULT (datetime('now')),
    write_date       TEXT DEFAULT (datetime('now'))
);

DROP TABLE IF EXISTS product_template;
CREATE TABLE product_template (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    type       TEXT DEFAULT 'consu',
    categ_id   INTEGER,
    list_price REAL DEFAULT 0
);

DROP TABLE IF EXISTS product_product;
CREATE TABLE product_product (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    product_tmpl_id     INTEGER,
    default_code        TEXT,
    barcode             TEXT,
    active              INTEGER DEFAULT 1
);

DROP TABLE IF EXISTS uom_uom;
CREATE TABLE uom_uom (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

DROP TABLE IF EXISTS sale_order_line;
CREATE TABLE sale_order_line (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id            INTEGER NOT NULL,
    product_id          INTEGER,
    product_template_id INTEGER,
    product_uom         INTEGER,
    salesman_id         INTEGER,
    company_id          INTEGER,
    name                TEXT,
    sequence            INTEGER DEFAULT 10,
    invoice_status      TEXT DEFAULT 'no',
    product_uom_qty     REAL DEFAULT 0,
    qty_delivered       REAL DEFAULT 0,
    qty_invoiced        REAL DEFAULT 0,
    qty_to_invoice      REAL DEFAULT 0,
    price_unit          REAL DEFAULT 0,
    discount            REAL DEFAULT 0,
    price_subtotal      REAL DEFAULT 0,
    price_tax           REAL DEFAULT 0,
    price_total         REAL DEFAULT 0,
    create_date         TEXT DEFAULT (datetime('now'))
);

-- ========== 采购模块 ==========

DROP TABLE IF EXISTS stock_picking_type;
CREATE TABLE stock_picking_type (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    code          TEXT,
    sequence_code TEXT
);

DROP TABLE IF EXISTS purchase_order;
CREATE TABLE purchase_order (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    state           TEXT DEFAULT 'draft',
    partner_id      INTEGER,
    user_id         INTEGER,
    company_id      INTEGER,
    currency_id     INTEGER,
    picking_type_id INTEGER,
    date_order      TEXT,
    date_approve    TEXT,
    date_planned    TEXT,
    invoice_status  TEXT DEFAULT 'no',
    origin          TEXT,
    notes           TEXT,
    amount_untaxed  REAL DEFAULT 0,
    amount_tax      REAL DEFAULT 0,
    amount_total    REAL DEFAULT 0,
    create_date     TEXT DEFAULT (datetime('now')),
    write_date      TEXT DEFAULT (datetime('now'))
);

-- ========== 会计模块 ==========

DROP TABLE IF EXISTS account_journal;
CREATE TABLE account_journal (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    code       TEXT,
    type       TEXT,
    company_id INTEGER
);

DROP TABLE IF EXISTS account_move;
CREATE TABLE account_move (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    name                  TEXT NOT NULL,
    move_type             TEXT DEFAULT 'entry',
    state                 TEXT DEFAULT 'draft',
    partner_id            INTEGER,
    journal_id            INTEGER,
    company_id            INTEGER,
    currency_id           INTEGER,
    invoice_user_id       INTEGER,
    team_id               INTEGER,
    date                  TEXT,
    invoice_date          TEXT,
    invoice_date_due      TEXT,
    payment_state         TEXT DEFAULT 'not_paid',
    ref                   TEXT,
    invoice_origin        TEXT,
    narration             TEXT,
    amount_untaxed        REAL DEFAULT 0,
    amount_tax            REAL DEFAULT 0,
    amount_total          REAL DEFAULT 0,
    amount_residual       REAL DEFAULT 0,
    amount_untaxed_signed REAL DEFAULT 0,
    amount_total_signed   REAL DEFAULT 0,
    amount_residual_signed REAL DEFAULT 0,
    create_date           TEXT DEFAULT (datetime('now')),
    write_date            TEXT DEFAULT (datetime('now'))
);

-- ========== 库存模块 ==========

DROP TABLE IF EXISTS stock_location;
CREATE TABLE stock_location (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    complete_name TEXT NOT NULL
);

DROP TABLE IF EXISTS stock_picking;
CREATE TABLE stock_picking (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT NOT NULL,
    state            TEXT DEFAULT 'draft',
    partner_id       INTEGER,
    picking_type_id  INTEGER,
    location_id      INTEGER,
    location_dest_id INTEGER,
    user_id          INTEGER,
    company_id       INTEGER,
    origin           TEXT,
    scheduled_date   TEXT,
    date_deadline    TEXT,
    date_done        TEXT,
    priority         TEXT DEFAULT '0',
    note             TEXT,
    create_date      TEXT DEFAULT (datetime('now')),
    write_date       TEXT DEFAULT (datetime('now'))
);

-- ========== HR 模块 ==========

DROP TABLE IF EXISTS hr_department;
CREATE TABLE hr_department (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    complete_name TEXT
);

DROP TABLE IF EXISTS hr_job;
CREATE TABLE hr_job (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

DROP TABLE IF EXISTS hr_work_location;
CREATE TABLE hr_work_location (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

DROP TABLE IF EXISTS hr_employee;
CREATE TABLE hr_employee (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    name             TEXT NOT NULL,
    job_title        TEXT,
    work_email       TEXT,
    work_phone       TEXT,
    mobile_phone     TEXT,
    department_id    INTEGER,
    job_id           INTEGER,
    company_id       INTEGER,
    parent_id        INTEGER,
    work_location_id INTEGER,
    user_id          INTEGER,
    active           INTEGER DEFAULT 1,
    gender           TEXT,
    marital          TEXT,
    employee_type    TEXT DEFAULT 'employee',
    create_date      TEXT DEFAULT (datetime('now')),
    write_date       TEXT DEFAULT (datetime('now'))
);
