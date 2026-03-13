-- ============================================
-- Odoo 核心表结构 (MySQL)
-- 文件: 06-odoo-schema.sql
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========== 基础维度表 ==========

DROP TABLE IF EXISTS `res_company`;
CREATE TABLE `res_company` (
    `id`          INT AUTO_INCREMENT,
    `name`        VARCHAR(200) NOT NULL,
    `parent_id`   INT,
    `currency_id` INT,
    `country_id`  INT,
    `street`      VARCHAR(200),
    `city`        VARCHAR(100),
    `zip`         VARCHAR(20),
    `email`       VARCHAR(200),
    `phone`       VARCHAR(50),
    `website`     VARCHAR(200),
    `vat`         VARCHAR(50),
    `active`      TINYINT(1) DEFAULT 1,
    `create_date` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `res_currency`;
CREATE TABLE `res_currency` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(10) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `res_country`;
CREATE TABLE `res_country` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `code` VARCHAR(10),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `res_country_state`;
CREATE TABLE `res_country_state` (
    `id`         INT AUTO_INCREMENT,
    `name`       VARCHAR(100) NOT NULL,
    `code`       VARCHAR(10),
    `country_id` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `res_users`;
CREATE TABLE `res_users` (
    `id`         INT AUTO_INCREMENT,
    `login`      VARCHAR(100) NOT NULL,
    `company_id` INT,
    `partner_id` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `res_partner`;
CREATE TABLE `res_partner` (
    `id`            INT AUTO_INCREMENT,
    `name`          VARCHAR(200) NOT NULL,
    `display_name`  VARCHAR(200),
    `type`          VARCHAR(20) DEFAULT 'contact',
    `parent_id`     INT,
    `company_id`    INT,
    `user_id`       INT,
    `team_id`       INT,
    `country_id`    INT,
    `state_id`      INT,
    `email`         VARCHAR(200),
    `phone`         VARCHAR(50),
    `mobile`        VARCHAR(50),
    `street`        VARCHAR(200),
    `city`          VARCHAR(100),
    `zip`           VARCHAR(20),
    `website`       VARCHAR(200),
    `vat`           VARCHAR(50),
    `ref`           VARCHAR(50),
    `lang`          VARCHAR(10) DEFAULT 'en_US',
    `is_company`    TINYINT(1) DEFAULT 0,
    `active`        TINYINT(1) DEFAULT 1,
    `customer_rank` INT DEFAULT 0,
    `supplier_rank` INT DEFAULT 0,
    `credit_limit`  DECIMAL(18,2) DEFAULT 0,
    `create_date`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `crm_team`;
CREATE TABLE `crm_team` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 销售模块 ==========

DROP TABLE IF EXISTS `product_pricelist`;
CREATE TABLE `product_pricelist` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `stock_warehouse`;
CREATE TABLE `stock_warehouse` (
    `id`         INT AUTO_INCREMENT,
    `name`       VARCHAR(100) NOT NULL,
    `company_id` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `sale_order`;
CREATE TABLE `sale_order` (
    `id`               INT AUTO_INCREMENT,
    `name`             VARCHAR(50) NOT NULL,
    `state`            VARCHAR(20) DEFAULT 'draft',
    `partner_id`       INT,
    `user_id`          INT,
    `company_id`       INT,
    `team_id`          INT,
    `pricelist_id`     INT,
    `warehouse_id`     INT,
    `date_order`       DATETIME,
    `commitment_date`  DATETIME,
    `invoice_status`   VARCHAR(20) DEFAULT 'no',
    `client_order_ref` VARCHAR(200),
    `origin`           VARCHAR(200),
    `note`             TEXT,
    `amount_untaxed`   DECIMAL(18,2) DEFAULT 0,
    `amount_tax`       DECIMAL(18,2) DEFAULT 0,
    `amount_total`     DECIMAL(18,2) DEFAULT 0,
    `currency_rate`    DECIMAL(18,6) DEFAULT 1.0,
    `create_date`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `product_template`;
CREATE TABLE `product_template` (
    `id`         INT AUTO_INCREMENT,
    `name`       VARCHAR(200) NOT NULL,
    `type`       VARCHAR(20) DEFAULT 'consu',
    `categ_id`   INT,
    `list_price` DECIMAL(18,2) DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `product_product`;
CREATE TABLE `product_product` (
    `id`              INT AUTO_INCREMENT,
    `product_tmpl_id` INT,
    `default_code`    VARCHAR(50),
    `barcode`         VARCHAR(50),
    `active`          TINYINT(1) DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `uom_uom`;
CREATE TABLE `uom_uom` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `sale_order_line`;
CREATE TABLE `sale_order_line` (
    `id`                  INT AUTO_INCREMENT,
    `order_id`            INT NOT NULL,
    `product_id`          INT,
    `product_template_id` INT,
    `product_uom`         INT,
    `salesman_id`         INT,
    `company_id`          INT,
    `name`                VARCHAR(200),
    `sequence`            INT DEFAULT 10,
    `invoice_status`      VARCHAR(20) DEFAULT 'no',
    `product_uom_qty`     DECIMAL(18,4) DEFAULT 0,
    `qty_delivered`       DECIMAL(18,4) DEFAULT 0,
    `qty_invoiced`        DECIMAL(18,4) DEFAULT 0,
    `qty_to_invoice`      DECIMAL(18,4) DEFAULT 0,
    `price_unit`          DECIMAL(18,2) DEFAULT 0,
    `discount`            DECIMAL(18,2) DEFAULT 0,
    `price_subtotal`      DECIMAL(18,2) DEFAULT 0,
    `price_tax`           DECIMAL(18,2) DEFAULT 0,
    `price_total`         DECIMAL(18,2) DEFAULT 0,
    `create_date`         DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 采购模块 ==========

DROP TABLE IF EXISTS `stock_picking_type`;
CREATE TABLE `stock_picking_type` (
    `id`            INT AUTO_INCREMENT,
    `name`          VARCHAR(100) NOT NULL,
    `code`          VARCHAR(20),
    `sequence_code` VARCHAR(20),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order` (
    `id`              INT AUTO_INCREMENT,
    `name`            VARCHAR(50) NOT NULL,
    `state`           VARCHAR(20) DEFAULT 'draft',
    `partner_id`      INT,
    `user_id`         INT,
    `company_id`      INT,
    `currency_id`     INT,
    `picking_type_id` INT,
    `date_order`      DATETIME,
    `date_approve`    DATETIME,
    `date_planned`    DATETIME,
    `invoice_status`  VARCHAR(20) DEFAULT 'no',
    `origin`          VARCHAR(200),
    `notes`           TEXT,
    `amount_untaxed`  DECIMAL(18,2) DEFAULT 0,
    `amount_tax`      DECIMAL(18,2) DEFAULT 0,
    `amount_total`    DECIMAL(18,2) DEFAULT 0,
    `create_date`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 会计模块 ==========

DROP TABLE IF EXISTS `account_journal`;
CREATE TABLE `account_journal` (
    `id`         INT AUTO_INCREMENT,
    `name`       VARCHAR(100) NOT NULL,
    `code`       VARCHAR(10),
    `type`       VARCHAR(20),
    `company_id` INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `account_move`;
CREATE TABLE `account_move` (
    `id`                     INT AUTO_INCREMENT,
    `name`                   VARCHAR(100) NOT NULL,
    `move_type`              VARCHAR(20) DEFAULT 'entry',
    `state`                  VARCHAR(20) DEFAULT 'draft',
    `partner_id`             INT,
    `journal_id`             INT,
    `company_id`             INT,
    `currency_id`            INT,
    `invoice_user_id`        INT,
    `team_id`                INT,
    `date`                   DATE,
    `invoice_date`           DATE,
    `invoice_date_due`       DATE,
    `payment_state`          VARCHAR(20) DEFAULT 'not_paid',
    `ref`                    VARCHAR(200),
    `invoice_origin`         VARCHAR(200),
    `narration`              TEXT,
    `amount_untaxed`         DECIMAL(18,2) DEFAULT 0,
    `amount_tax`             DECIMAL(18,2) DEFAULT 0,
    `amount_total`           DECIMAL(18,2) DEFAULT 0,
    `amount_residual`        DECIMAL(18,2) DEFAULT 0,
    `amount_untaxed_signed`  DECIMAL(18,2) DEFAULT 0,
    `amount_total_signed`    DECIMAL(18,2) DEFAULT 0,
    `amount_residual_signed` DECIMAL(18,2) DEFAULT 0,
    `create_date`            DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 库存模块 ==========

DROP TABLE IF EXISTS `stock_location`;
CREATE TABLE `stock_location` (
    `id`            INT AUTO_INCREMENT,
    `complete_name` VARCHAR(200) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `stock_picking`;
CREATE TABLE `stock_picking` (
    `id`               INT AUTO_INCREMENT,
    `name`             VARCHAR(50) NOT NULL,
    `state`            VARCHAR(20) DEFAULT 'draft',
    `partner_id`       INT,
    `picking_type_id`  INT,
    `location_id`      INT,
    `location_dest_id` INT,
    `user_id`          INT,
    `company_id`       INT,
    `origin`           VARCHAR(200),
    `scheduled_date`   DATETIME,
    `date_deadline`    DATETIME,
    `date_done`        DATETIME,
    `priority`         VARCHAR(5) DEFAULT '0',
    `note`             TEXT,
    `create_date`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== HR 模块 ==========

DROP TABLE IF EXISTS `hr_department`;
CREATE TABLE `hr_department` (
    `id`            INT AUTO_INCREMENT,
    `name`          VARCHAR(100) NOT NULL,
    `complete_name` VARCHAR(200),
    `parent_id`     INT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `hr_job`;
CREATE TABLE `hr_job` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `hr_work_location`;
CREATE TABLE `hr_work_location` (
    `id`   INT AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `hr_employee`;
CREATE TABLE `hr_employee` (
    `id`               INT AUTO_INCREMENT,
    `name`             VARCHAR(200) NOT NULL,
    `job_title`        VARCHAR(100),
    `work_email`       VARCHAR(200),
    `work_phone`       VARCHAR(50),
    `mobile_phone`     VARCHAR(50),
    `department_id`    INT,
    `job_id`           INT,
    `company_id`       INT,
    `parent_id`        INT,
    `work_location_id` INT,
    `user_id`          INT,
    `active`           TINYINT(1) DEFAULT 1,
    `gender`           VARCHAR(20),
    `marital`          VARCHAR(20),
    `employee_type`    VARCHAR(20) DEFAULT 'employee',
    `create_date`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `write_date`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
