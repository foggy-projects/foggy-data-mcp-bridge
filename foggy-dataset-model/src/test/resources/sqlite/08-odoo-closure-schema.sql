-- ============================================
-- Odoo 闭包表 (Closure Table) DDL
-- 文件: 08-odoo-closure-schema.sql
-- 说明: 为 Odoo 层级模型创建闭包表，供 Foggy 层级操作符使用
--       (selfAndDescendantsOf, descendantsOf, selfAndAncestorsOf, ancestorsOf 等)
-- ============================================

-- ========== res_company 闭包表 ==========

DROP TABLE IF EXISTS res_company_closure;
CREATE TABLE res_company_closure (
    parent_id   INTEGER NOT NULL,
    company_id  INTEGER NOT NULL,
    distance    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, company_id)
);
CREATE INDEX idx_company_closure_child ON res_company_closure (company_id);

-- ========== res_partner 闭包表 ==========

DROP TABLE IF EXISTS res_partner_closure;
CREATE TABLE res_partner_closure (
    parent_id   INTEGER NOT NULL,
    partner_id  INTEGER NOT NULL,
    distance    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, partner_id)
);
CREATE INDEX idx_partner_closure_child ON res_partner_closure (partner_id);

-- ========== hr_department 闭包表 ==========

DROP TABLE IF EXISTS hr_department_closure;
CREATE TABLE hr_department_closure (
    parent_id       INTEGER NOT NULL,
    department_id   INTEGER NOT NULL,
    distance        INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, department_id)
);
CREATE INDEX idx_department_closure_child ON hr_department_closure (department_id);

-- ========== hr_employee 闭包表 ==========

DROP TABLE IF EXISTS hr_employee_closure;
CREATE TABLE hr_employee_closure (
    parent_id    INTEGER NOT NULL,
    employee_id  INTEGER NOT NULL,
    distance     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, employee_id)
);
CREATE INDEX idx_employee_closure_child ON hr_employee_closure (employee_id);
