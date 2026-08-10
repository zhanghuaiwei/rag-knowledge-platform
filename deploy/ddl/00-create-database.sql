-- =====================================================================
-- 通用企业知识库平台 · PostgreSQL 角色与数据库初始化（v0.2）
-- 目标：PostgreSQL 16+，仅适用于自管 PostgreSQL；托管数据库请用平台控制面。
--
-- 执行示例（密码通过 psql 变量传入，不写入仓库/命令历史）：
--   psql -v ragkb_app_password='***' \
--        -v ragkb_migrator_password='***' \
--        -U postgres -d postgres -f deploy/ddl/00-create-database.sql
--
-- 创建：
--   ragkb_owner     NOLOGIN，拥有 schema/table
--   ragkb_migrator  LOGIN，可 SET ROLE ragkb_owner 执行迁移
--   ragkb_app       LOGIN，仅运行时 DML，无 DDL/BYPASSRLS
-- =====================================================================

\set ON_ERROR_STOP on

\if :{?ragkb_app_password}
\else
  \echo 'ERROR: missing -v ragkb_app_password'
  \quit
\endif

\if :{?ragkb_migrator_password}
\else
  \echo 'ERROR: missing -v ragkb_migrator_password'
  \quit
\endif

SELECT 'CREATE ROLE ragkb_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ragkb_owner')\gexec

SELECT format(
    'CREATE ROLE ragkb_migrator LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT',
    :'ragkb_migrator_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ragkb_migrator')\gexec

SELECT format(
    'CREATE ROLE ragkb_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS INHERIT',
    :'ragkb_app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ragkb_app')\gexec

GRANT ragkb_owner TO ragkb_migrator;

SELECT 'CREATE DATABASE ragkb OWNER ragkb_owner ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ragkb')\gexec

ALTER ROLE ragkb_app SET timezone TO 'UTC';
ALTER ROLE ragkb_migrator SET timezone TO 'UTC';
GRANT CONNECT ON DATABASE ragkb TO ragkb_app, ragkb_migrator;

\connect ragkb

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE ragkb FROM PUBLIC;
GRANT CONNECT ON DATABASE ragkb TO ragkb_app, ragkb_migrator;
ALTER SCHEMA public OWNER TO ragkb_owner;
GRANT USAGE ON SCHEMA public TO ragkb_app;

SET ROLE ragkb_owner;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ragkb_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ragkb_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO ragkb_app;
RESET ROLE;

-- 校验（只读）：
--   SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls
--   FROM pg_roles WHERE rolname LIKE 'ragkb_%' ORDER BY rolname;
--
-- 后续：以 ragkb_migrator 连接 ragkb，依次执行 01-schema.sql、02-seed-data.sql；
-- 应用连接只使用 ragkb_app。密码轮换请通过 Secret Manager/数据库运维流程完成。

