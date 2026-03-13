-- ============================================
-- Odoo 闭包表数据 (PostgreSQL)
-- 文件: 09-odoo-closure-data.sql
-- ============================================

-- ========== res_company_closure ==========

INSERT INTO res_company_closure (parent_id, company_id, distance) VALUES
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(1, 2, 1),
(1, 3, 1);

-- ========== hr_department_closure ==========

INSERT INTO hr_department_closure (parent_id, department_id, distance) VALUES
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
(4, 1, 1),
(4, 2, 1),
(4, 3, 1);

-- ========== hr_employee_closure ==========

INSERT INTO hr_employee_closure (parent_id, employee_id, distance) VALUES
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
(5, 5, 0),
(6, 6, 0),
(1, 2, 1),
(1, 5, 1),
(1, 6, 1),
(2, 3, 1),
(2, 4, 1),
(1, 3, 2),
(1, 4, 2);

-- ========== res_partner_closure ==========

INSERT INTO res_partner_closure (parent_id, partner_id, distance) VALUES
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
(5, 5, 0),
(6, 6, 0);
