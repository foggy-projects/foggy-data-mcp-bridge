-- ═══════════════════════════════════════════════════════════════════
-- Foggy Closure Table Refresh Functions (PostgreSQL)
--
-- Generates (parent_id, child_id, distance) closure rows from
-- Odoo's parent_id fields using recursive CTEs.
--
-- Usage:
--   SELECT refresh_company_closure();
--   SELECT refresh_department_closure();
--   SELECT refresh_employee_closure();
--   SELECT refresh_partner_closure();
--   SELECT refresh_all_closures();
--
-- Schedule via pg_cron or call after hierarchy changes.
-- ═══════════════════════════════════════════════════════════════════

-- ─── res_company_closure ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS res_company_closure (
    parent_id  INTEGER NOT NULL,
    company_id INTEGER NOT NULL,
    distance   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, company_id)
);
CREATE INDEX IF NOT EXISTS idx_company_closure_child ON res_company_closure (company_id);

CREATE OR REPLACE FUNCTION refresh_company_closure() RETURNS void AS $$
BEGIN
    TRUNCATE res_company_closure;
    INSERT INTO res_company_closure (parent_id, company_id, distance)
    WITH RECURSIVE tree AS (
        -- distance=0: self
        SELECT id AS parent_id, id AS company_id, 0 AS distance
        FROM res_company
        UNION ALL
        -- distance+1: parent → child
        SELECT t.parent_id, c.id, t.distance + 1
        FROM tree t
        JOIN res_company c ON c.parent_id = t.company_id
        WHERE c.parent_id IS NOT NULL
    )
    SELECT parent_id, company_id, distance FROM tree;
END;
$$ LANGUAGE plpgsql;


-- ─── hr_department_closure ───────────────────────────────────────

CREATE TABLE IF NOT EXISTS hr_department_closure (
    parent_id     INTEGER NOT NULL,
    department_id INTEGER NOT NULL,
    distance      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, department_id)
);
CREATE INDEX IF NOT EXISTS idx_department_closure_child ON hr_department_closure (department_id);

CREATE OR REPLACE FUNCTION refresh_department_closure() RETURNS void AS $$
BEGIN
    TRUNCATE hr_department_closure;
    INSERT INTO hr_department_closure (parent_id, department_id, distance)
    WITH RECURSIVE tree AS (
        SELECT id AS parent_id, id AS department_id, 0 AS distance
        FROM hr_department
        WHERE active = true
        UNION ALL
        SELECT t.parent_id, d.id, t.distance + 1
        FROM tree t
        JOIN hr_department d ON d.parent_id = t.department_id
        WHERE d.active = true AND d.parent_id IS NOT NULL
    )
    SELECT parent_id, department_id, distance FROM tree;
END;
$$ LANGUAGE plpgsql;


-- ─── hr_employee_closure ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS hr_employee_closure (
    parent_id   INTEGER NOT NULL,
    employee_id INTEGER NOT NULL,
    distance    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, employee_id)
);
CREATE INDEX IF NOT EXISTS idx_employee_closure_child ON hr_employee_closure (employee_id);

CREATE OR REPLACE FUNCTION refresh_employee_closure() RETURNS void AS $$
BEGIN
    TRUNCATE hr_employee_closure;
    INSERT INTO hr_employee_closure (parent_id, employee_id, distance)
    WITH RECURSIVE tree AS (
        SELECT id AS parent_id, id AS employee_id, 0 AS distance
        FROM hr_employee
        WHERE active = true
        UNION ALL
        SELECT t.parent_id, e.id, t.distance + 1
        FROM tree t
        JOIN hr_employee e ON e.parent_id = t.employee_id
        WHERE e.active = true AND e.parent_id IS NOT NULL
    )
    SELECT parent_id, employee_id, distance FROM tree;
END;
$$ LANGUAGE plpgsql;


-- ─── res_partner_closure ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS res_partner_closure (
    parent_id  INTEGER NOT NULL,
    partner_id INTEGER NOT NULL,
    distance   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (parent_id, partner_id)
);
CREATE INDEX IF NOT EXISTS idx_partner_closure_child ON res_partner_closure (partner_id);

CREATE OR REPLACE FUNCTION refresh_partner_closure() RETURNS void AS $$
BEGIN
    TRUNCATE res_partner_closure;
    INSERT INTO res_partner_closure (parent_id, partner_id, distance)
    WITH RECURSIVE tree AS (
        SELECT id AS parent_id, id AS partner_id, 0 AS distance
        FROM res_partner
        WHERE active = true
        UNION ALL
        SELECT t.parent_id, p.id, t.distance + 1
        FROM tree t
        JOIN res_partner p ON p.parent_id = t.partner_id
        WHERE p.active = true AND p.parent_id IS NOT NULL
    )
    SELECT parent_id, partner_id, distance FROM tree;
END;
$$ LANGUAGE plpgsql;


-- ─── Refresh all ─────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION refresh_all_closures() RETURNS void AS $$
BEGIN
    PERFORM refresh_company_closure();
    PERFORM refresh_department_closure();
    PERFORM refresh_employee_closure();
    PERFORM refresh_partner_closure();
END;
$$ LANGUAGE plpgsql;
