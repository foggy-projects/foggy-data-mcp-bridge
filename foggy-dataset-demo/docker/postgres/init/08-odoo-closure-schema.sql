-- ============================================
-- Odoo 闭包表 (Closure Table) DDL - PostgreSQL
-- 文件: 08-odoo-closure-schema.sql
-- ============================================

-- ========== res_company 闭包表 ==========

DROP TABLE IF EXISTS res_company_closure CASCADE;
CREATE TABLE res_company_closure (
    parent_id   INT NOT NULL,
    company_id  INT NOT NULL,
    distance    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, company_id)
);
CREATE INDEX idx_company_closure_child ON res_company_closure (company_id);

-- ========== res_partner 闭包表 ==========

DROP TABLE IF EXISTS res_partner_closure CASCADE;
CREATE TABLE res_partner_closure (
    parent_id   INT NOT NULL,
    partner_id  INT NOT NULL,
    distance    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, partner_id)
);
CREATE INDEX idx_partner_closure_child ON res_partner_closure (partner_id);

-- ========== hr_department 闭包表 ==========

DROP TABLE IF EXISTS hr_department_closure CASCADE;
CREATE TABLE hr_department_closure (
    parent_id       INT NOT NULL,
    department_id   INT NOT NULL,
    distance        INT NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, department_id)
);
CREATE INDEX idx_department_closure_child ON hr_department_closure (department_id);

-- ========== hr_employee 闭包表 ==========

DROP TABLE IF EXISTS hr_employee_closure CASCADE;
CREATE TABLE hr_employee_closure (
    parent_id    INT NOT NULL,
    employee_id  INT NOT NULL,
    distance     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, employee_id)
);
CREATE INDEX idx_employee_closure_child ON hr_employee_closure (employee_id);
