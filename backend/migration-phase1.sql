-- ============================================================================
-- Phase 1 migration: employees + admins  ->  companies + users
-- ============================================================================
--
-- RUN ORDER (this matters — read before running anything):
--
--   1. pg_dump a full backup. Non-negotiable. Example:
--        pg_dump -h $DB_HOST -U $DATABASE_USERNAME -d $DB_NAME -F c -f pre_phase1_backup.dump
--
--   2. Deploy the Phase 1 backend code and start it ONCE against this
--      database with ddl-auto=update still enabled. Hibernate will create
--      the new EMPTY "companies" and "users" tables from the entity
--      annotations. It will NOT touch or drop "employees"/"admins" — it
--      doesn't know they're related. Stop the app again after this.
--
--   3. Run SECTION A below (data migration) against the database.
--
--   4. Start the app again and smoke-test login (both /api/auth/login and
--      /api/admin/login) against a migrated account before doing anything
--      else.
--
--   5. Only once you've confirmed the app works end-to-end, OPTIONALLY run
--      SECTION B (drops the old tables). This is commented out on purpose —
--      don't run it same-day as the migration. Keep the old tables around
--      for at least a few days as a safety net.
--
-- Wrap Section A in a transaction so it's all-or-nothing.
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- SECTION A: migrate data
-- ---------------------------------------------------------------------------

-- A1. Create (or reuse) the single default company. Slug must be exactly
--     'default' — AdminController's bootstrap logic looks it up by this slug.
INSERT INTO companies (name, slug, created_at)
SELECT 'Default Company', 'default', now()
WHERE NOT EXISTS (SELECT 1 FROM companies WHERE slug = 'default');

-- A2. Sanity check: email must be globally unique across the merged table.
--     If this returns any rows, STOP — do not proceed past this point.
--     You have the same email in both employees and admins (or duplicated
--     within one table), which would violate the new uk_users_email
--     constraint. Resolve the collision manually before continuing.
--
--     SELECT email FROM (
--         SELECT email FROM employees
--         UNION ALL
--         SELECT email FROM admins
--     ) x GROUP BY email HAVING COUNT(*) > 1;

-- A3. Migrate employees -> users.
--     Old Employee.role was an unvalidated raw string set directly from
--     client input (see EmployeeServiceImpl.create() in the pre-Phase-1
--     code) — there was no enum, so anything could have ended up in there.
--     This maps anything containing "ADMIN" to OWNER and treats everything
--     else as EMPLOYEE. Re-run the SELECT below first and eyeball the
--     distinct values before trusting this mapping on your actual data:
--
--         SELECT DISTINCT role FROM employees;
--
INSERT INTO users (
    company_id, emp_id, name, email, password, role,
    department, salary, profile_image_url, frozen, primary_owner,
    created_at, updated_at
)
SELECT
    (SELECT id FROM companies WHERE slug = 'default'),
    e.emp_id,
    e.name,
    e.email,
    e.password,
    CASE WHEN UPPER(COALESCE(e.role, '')) LIKE '%ADMIN%' THEN 'OWNER' ELSE 'EMPLOYEE' END,
    e.department,
    e.salary::numeric(12,2),
    e.profile_image_url,
    false,
    false,
    now(),
    now()
FROM employees e;

-- A4. Migrate admins -> users.
--     Admins never had an emp_id concept, so it's left NULL (nullable on
--     User, matching how Owners created via /register also have no emp_id).
INSERT INTO users (
    company_id, emp_id, name, email, password, role,
    frozen, primary_owner, created_at, updated_at
)
SELECT
    (SELECT id FROM companies WHERE slug = 'default'),
    NULL,
    a.name,
    a.email,
    a.password,
    'OWNER',
    COALESCE(a.frozen, false),
    COALESCE(a.is_main, false),
    now(),
    now()
FROM admins a;

-- A5. Row-count sanity check — compare before committing.
--     Run these two queries and confirm: new_users_count = old_employees_count + old_admins_count
--
--     SELECT (SELECT COUNT(*) FROM employees) + (SELECT COUNT(*) FROM admins) AS old_total;
--     SELECT COUNT(*) AS new_total FROM users;

COMMIT;

-- ============================================================================
-- SECTION B (DO NOT RUN YET): drop old tables once you've verified the app
-- works end-to-end against the new schema. Uncomment only when ready.
-- ============================================================================

-- DROP TABLE employees;
-- DROP TABLE admins;
