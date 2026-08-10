-- =====================================================================
-- 通用企业知识库平台 · 可选 PostgreSQL RLS 加固（v0.2）
--
-- 前置门禁：
--   1. 仅在新装 v0.2 Schema 上执行；
--   2. 应用已在每个业务事务内、鉴权成功后执行：
--        SET LOCAL app.tenant_id = '<verified-tenant-id>';
--   3. 连接池归还连接前有自动清理与跨租户回归测试；
--   4. ragkb_app 不拥有表且没有 BYPASSRLS。
--
-- RLS 是纵深防御，不替代应用层 subject/role/ACL/policy 校验。自定义 GUC
-- 只能由服务端从已验证身份写入，严禁接受客户端 SQL 或 tenant_id 原样透传。
-- =====================================================================

\set ON_ERROR_STOP on

SELECT (
    EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'tenant_member'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_user' AND column_name = 'tenant_id'
    )
) AS is_v02_schema \gset

\if :is_v02_schema

SET ROLE ragkb_owner;
BEGIN;

CREATE SCHEMA IF NOT EXISTS app_security AUTHORIZATION ragkb_owner;

CREATE OR REPLACE FUNCTION app_security.current_tenant_id()
RETURNS BIGINT
LANGUAGE sql
STABLE
PARALLEL SAFE
AS $$
    SELECT CASE
        WHEN current_setting('app.tenant_id', true) ~ '^[1-9][0-9]*$'
            THEN current_setting('app.tenant_id', true)::BIGINT
        ELSE NULL
    END
$$;

REVOKE ALL ON FUNCTION app_security.current_tenant_id() FROM PUBLIC;
GRANT USAGE ON SCHEMA app_security TO ragkb_app;
GRANT EXECUTE ON FUNCTION app_security.current_tenant_id() TO ragkb_app;

-- sys_tenant is tenant-scoped by primary key rather than a tenant_id column.
ALTER TABLE public.sys_tenant ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.sys_tenant;
CREATE POLICY tenant_isolation ON public.sys_tenant
    USING (id = app_security.current_tenant_id())
    WITH CHECK (id = app_security.current_tenant_id());

-- Apply a fail-closed policy to every public base table that declares tenant_id.
DO $$
DECLARE
    target_table RECORD;
BEGIN
    FOR target_table IN
        SELECT DISTINCT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
         AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = 'public'
          AND c.column_name = 'tenant_id'
          AND c.table_name <> 'sys_tenant'
        ORDER BY c.table_name
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target_table.table_name);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON public.%I', target_table.table_name);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I '
            'USING (tenant_id = app_security.current_tenant_id()) '
            'WITH CHECK (tenant_id = app_security.current_tenant_id())',
            target_table.table_name
        );
    END LOOP;
END;
$$;

COMMIT;
RESET ROLE;

\echo 'RLS enabled. Validate with ragkb_app using two real test tenants before production traffic.'
\echo 'Expected fail-closed behavior: RESET app.tenant_id; SELECT count(*) FROM kb; returns 0.'

\else

\echo 'Skipped: v0.2 new-install schema signature not found.'

\endif
