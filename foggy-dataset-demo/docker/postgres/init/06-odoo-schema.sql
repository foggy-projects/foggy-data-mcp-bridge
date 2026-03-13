-- ============================================
-- Odoo 核心表结构 (PostgreSQL)
-- 文件: 06-odoo-schema.sql
-- ============================================

-- ========== 基础维度表 ==========

DROP TABLE IF EXISTS res_company CASCADE;
CREATE TABLE res_company (
    id          SERIAL,
    name        VARCHAR(200) NOT NULL,
    parent_id   INT,
    currency_id INT,
    country_id  INT,
    street      VARCHAR(200),
    city        VARCHAR(100),
    zip         VARCHAR(20),
    email       VARCHAR(200),
    phone       VARCHAR(50),
    website     VARCHAR(200),
    vat         VARCHAR(50),
    active      SMALLINT DEFAULT 1,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS res_currency CASCADE;
CREATE TABLE res_currency (
    id   SERIAL,
    name VARCHAR(10) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS res_country CASCADE;
CREATE TABLE res_country (
    id   SERIAL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(10),
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS res_country_state CASCADE;
CREATE TABLE res_country_state (
    id         SERIAL,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(10),
    country_id INT,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS res_users CASCADE;
CREATE TABLE res_users (
    id         SERIAL,
    login      VARCHAR(100) NOT NULL,
    company_id INT,
    partner_id INT,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS res_partner CASCADE;
CREATE TABLE res_partner (
    id            SERIAL,
    name          VARCHAR(200) NOT NULL,
    display_name  VARCHAR(200),
    type          VARCHAR(20) DEFAULT 'contact',
    parent_id     INT,
    company_id    INT,
    user_id       INT,
    team_id       INT,
    country_id    INT,
    state_id      INT,
    email         VARCHAR(200),
    phone         VARCHAR(50),
    mobile        VARCHAR(50),
    street        VARCHAR(200),
    city          VARCHAR(100),
    zip           VARCHAR(20),
    website       VARCHAR(200),
    vat           VARCHAR(50),
    ref           VARCHAR(50),
    lang          VARCHAR(10) DEFAULT 'en_US',
    is_company    SMALLINT DEFAULT 0,
    active        SMALLINT DEFAULT 1,
    customer_rank INT DEFAULT 0,
    supplier_rank INT DEFAULT 0,
    credit_limit  NUMERIC(18,2) DEFAULT 0,
    create_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS crm_team CASCADE;
CREATE TABLE crm_team (
    id   SERIAL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

-- ========== 销售模块 ==========

DROP TABLE IF EXISTS product_pricelist CASCADE;
CREATE TABLE product_pricelist (
    id   SERIAL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stock_warehouse CASCADE;
CREATE TABLE stock_warehouse (
    id         SERIAL,
    name       VARCHAR(100) NOT NULL,
    company_id INT,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS sale_order CASCADE;
CREATE TABLE sale_order (
    id               SERIAL,
    name             VARCHAR(50) NOT NULL,
    state            VARCHAR(20) DEFAULT 'draft',
    partner_id       INT,
    user_id          INT,
    company_id       INT,
    team_id          INT,
    pricelist_id     INT,
    warehouse_id     INT,
    date_order       TIMESTAMP,
    commitment_date  TIMESTAMP,
    invoice_status   VARCHAR(20) DEFAULT 'no',
    client_order_ref VARCHAR(200),
    origin           VARCHAR(200),
    note             TEXT,
    amount_untaxed   NUMERIC(18,2) DEFAULT 0,
    amount_tax       NUMERIC(18,2) DEFAULT 0,
    amount_total     NUMERIC(18,2) DEFAULT 0,
    currency_rate    NUMERIC(18,6) DEFAULT 1.0,
    create_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS product_template CASCADE;
CREATE TABLE product_template (
    id         SERIAL,
    name       VARCHAR(200) NOT NULL,
    type       VARCHAR(20) DEFAULT 'consu',
    categ_id   INT,
    list_price NUMERIC(18,2) DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS product_product CASCADE;
CREATE TABLE product_product (
    id              SERIAL,
    product_tmpl_id INT,
    default_code    VARCHAR(50),
    barcode         VARCHAR(50),
    active          SMALLINT DEFAULT 1,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS uom_uom CASCADE;
CREATE TABLE uom_uom (
    id   SERIAL,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS sale_order_line CASCADE;
CREATE TABLE sale_order_line (
    id                  SERIAL,
    order_id            INT NOT NULL,
    product_id          INT,
    product_template_id INT,
    product_uom         INT,
    salesman_id         INT,
    company_id          INT,
    name                VARCHAR(200),
    sequence            INT DEFAULT 10,
    invoice_status      VARCHAR(20) DEFAULT 'no',
    product_uom_qty     NUMERIC(18,4) DEFAULT 0,
    qty_delivered       NUMERIC(18,4) DEFAULT 0,
    qty_invoiced        NUMERIC(18,4) DEFAULT 0,
    qty_to_invoice      NUMERIC(18,4) DEFAULT 0,
    price_unit          NUMERIC(18,2) DEFAULT 0,
    discount            NUMERIC(18,2) DEFAULT 0,
    price_subtotal      NUMERIC(18,2) DEFAULT 0,
    price_tax           NUMERIC(18,2) DEFAULT 0,
    price_total         NUMERIC(18,2) DEFAULT 0,
    create_date         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ========== 采购模块 ==========

DROP TABLE IF EXISTS stock_picking_type CASCADE;
CREATE TABLE stock_picking_type (
    id            SERIAL,
    name          VARCHAR(100) NOT NULL,
    code          VARCHAR(20),
    sequence_code VARCHAR(20),
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS purchase_order CASCADE;
CREATE TABLE purchase_order (
    id              SERIAL,
    name            VARCHAR(50) NOT NULL,
    state           VARCHAR(20) DEFAULT 'draft',
    partner_id      INT,
    user_id         INT,
    company_id      INT,
    currency_id     INT,
    picking_type_id INT,
    date_order      TIMESTAMP,
    date_approve    TIMESTAMP,
    date_planned    TIMESTAMP,
    invoice_status  VARCHAR(20) DEFAULT 'no',
    origin          VARCHAR(200),
    notes           TEXT,
    amount_untaxed  NUMERIC(18,2) DEFAULT 0,
    amount_tax      NUMERIC(18,2) DEFAULT 0,
    amount_total    NUMERIC(18,2) DEFAULT 0,
    create_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ========== 会计模块 ==========

DROP TABLE IF EXISTS account_journal CASCADE;
CREATE TABLE account_journal (
    id         SERIAL,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(10),
    type       VARCHAR(20),
    company_id INT,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS account_move CASCADE;
CREATE TABLE account_move (
    id                     SERIAL,
    name                   VARCHAR(100) NOT NULL,
    move_type              VARCHAR(20) DEFAULT 'entry',
    state                  VARCHAR(20) DEFAULT 'draft',
    partner_id             INT,
    journal_id             INT,
    company_id             INT,
    currency_id            INT,
    invoice_user_id        INT,
    team_id                INT,
    date                   DATE,
    invoice_date           DATE,
    invoice_date_due       DATE,
    payment_state          VARCHAR(20) DEFAULT 'not_paid',
    ref                    VARCHAR(200),
    invoice_origin         VARCHAR(200),
    narration              TEXT,
    amount_untaxed         NUMERIC(18,2) DEFAULT 0,
    amount_tax             NUMERIC(18,2) DEFAULT 0,
    amount_total           NUMERIC(18,2) DEFAULT 0,
    amount_residual        NUMERIC(18,2) DEFAULT 0,
    amount_untaxed_signed  NUMERIC(18,2) DEFAULT 0,
    amount_total_signed    NUMERIC(18,2) DEFAULT 0,
    amount_residual_signed NUMERIC(18,2) DEFAULT 0,
    create_date            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ========== 库存模块 ==========

DROP TABLE IF EXISTS stock_location CASCADE;
CREATE TABLE stock_location (
    id            SERIAL,
    complete_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stock_picking CASCADE;
CREATE TABLE stock_picking (
    id               SERIAL,
    name             VARCHAR(50) NOT NULL,
    state            VARCHAR(20) DEFAULT 'draft',
    partner_id       INT,
    picking_type_id  INT,
    location_id      INT,
    location_dest_id INT,
    user_id          INT,
    company_id       INT,
    origin           VARCHAR(200),
    scheduled_date   TIMESTAMP,
    date_deadline    TIMESTAMP,
    date_done        TIMESTAMP,
    priority         VARCHAR(5) DEFAULT '0',
    note             TEXT,
    create_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ========== HR 模块 ==========

DROP TABLE IF EXISTS hr_department CASCADE;
CREATE TABLE hr_department (
    id            SERIAL,
    name          VARCHAR(100) NOT NULL,
    complete_name VARCHAR(200),
    parent_id     INT,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS hr_job CASCADE;
CREATE TABLE hr_job (
    id   SERIAL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS hr_work_location CASCADE;
CREATE TABLE hr_work_location (
    id   SERIAL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS hr_employee CASCADE;
CREATE TABLE hr_employee (
    id               SERIAL,
    name             VARCHAR(200) NOT NULL,
    job_title        VARCHAR(100),
    work_email       VARCHAR(200),
    work_phone       VARCHAR(50),
    mobile_phone     VARCHAR(50),
    department_id    INT,
    job_id           INT,
    company_id       INT,
    parent_id        INT,
    work_location_id INT,
    user_id          INT,
    active           SMALLINT DEFAULT 1,
    gender           VARCHAR(20),
    marital          VARCHAR(20),
    employee_type    VARCHAR(20) DEFAULT 'employee',
    create_date      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
