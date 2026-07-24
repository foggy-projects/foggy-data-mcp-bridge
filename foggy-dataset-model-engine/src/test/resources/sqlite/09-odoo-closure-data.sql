-- ============================================
-- Odoo 闭包表数据 (Closure Table Data)
-- 文件: 09-odoo-closure-data.sql
-- 说明: 根据 07-odoo-data.sql 中的 parent_id 关系生成闭包行
--       每行 (parent_id, child_id, distance): distance=0 自身, 1 直接子, 2 孙...
-- ============================================

-- ========== res_company_closure ==========
-- 层级结构:
--   1 "My Company (Shanghai)" (root)
--   ├── 2 "My Company (Beijing)"
--   └── 3 "Overseas Branch"

INSERT INTO res_company_closure (parent_id, company_id, distance) VALUES
-- 自身引用 (distance=0)
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
-- 1 → 2 (distance=1)
(1, 2, 1),
-- 1 → 3 (distance=1)
(1, 3, 1);

-- ========== hr_department_closure ==========
-- 层级结构:
--   4 "Management" (root)
--   ├── 1 "Sales"
--   ├── 2 "Purchasing"
--   └── 3 "Accounting"

INSERT INTO hr_department_closure (parent_id, department_id, distance) VALUES
-- 自身引用 (distance=0)
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
-- 4 → 1,2,3 (distance=1)
(4, 1, 1),
(4, 2, 1),
(4, 3, 1);

-- ========== hr_employee_closure ==========
-- 层级结构:
--   1 "Admin User" (root)
--   ├── 2 "Zhang Wei" (Sales Manager)
--   │   ├── 3 "Li Na" (Sales Rep)
--   │   └── 4 "Wang Jun" (Sales Rep)
--   ├── 5 "Chen Mei" (Purchaser)
--   └── 6 "Liu Fang" (Accountant)

INSERT INTO hr_employee_closure (parent_id, employee_id, distance) VALUES
-- 自身引用 (distance=0)
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
(5, 5, 0),
(6, 6, 0),
-- 1 → 2,5,6 (distance=1, 直接下属)
(1, 2, 1),
(1, 5, 1),
(1, 6, 1),
-- 2 → 3,4 (distance=1, 直接下属)
(2, 3, 1),
(2, 4, 1),
-- 1 → 3,4 (distance=2, 间接下属)
(1, 3, 2),
(1, 4, 2);

-- ========== res_partner_closure ==========
-- 当前测试数据中 partner 无层级关系（parent_id 均为 NULL）
-- 仅插入自身引用行，确保闭包表结构完整

INSERT INTO res_partner_closure (parent_id, partner_id, distance) VALUES
(1, 1, 0),
(2, 2, 0),
(3, 3, 0),
(4, 4, 0),
(5, 5, 0),
(6, 6, 0);
