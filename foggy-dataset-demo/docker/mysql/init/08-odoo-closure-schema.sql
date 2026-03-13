-- ============================================
-- Odoo 闭包表 (Closure Table) DDL - MySQL
-- 文件: 08-odoo-closure-schema.sql
-- ============================================

SET NAMES utf8mb4;

-- ========== res_company 闭包表 ==========

DROP TABLE IF EXISTS `res_company_closure`;
CREATE TABLE `res_company_closure` (
    `parent_id`  INT NOT NULL,
    `company_id` INT NOT NULL,
    `distance`   INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`parent_id`, `company_id`),
    INDEX `idx_company_closure_child` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== res_partner 闭包表 ==========

DROP TABLE IF EXISTS `res_partner_closure`;
CREATE TABLE `res_partner_closure` (
    `parent_id`  INT NOT NULL,
    `partner_id` INT NOT NULL,
    `distance`   INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`parent_id`, `partner_id`),
    INDEX `idx_partner_closure_child` (`partner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== hr_department 闭包表 ==========

DROP TABLE IF EXISTS `hr_department_closure`;
CREATE TABLE `hr_department_closure` (
    `parent_id`     INT NOT NULL,
    `department_id` INT NOT NULL,
    `distance`      INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`parent_id`, `department_id`),
    INDEX `idx_department_closure_child` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== hr_employee 闭包表 ==========

DROP TABLE IF EXISTS `hr_employee_closure`;
CREATE TABLE `hr_employee_closure` (
    `parent_id`   INT NOT NULL,
    `employee_id` INT NOT NULL,
    `distance`    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`parent_id`, `employee_id`),
    INDEX `idx_employee_closure_child` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
