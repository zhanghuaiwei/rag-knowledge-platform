-- =====================================================================
-- v0.5 · 用户体系完善：凭据策略落库 + 租户成员生命周期
-- 目标：PostgreSQL 16+，在 v0.4 基线之上执行；逐语句幂等（重跑安全），执行前建议备份。
--
-- 背景（V0.5 用户体系，见 docs/api/server.openapi.yaml + 计划 ticklish-popping-blum.md）：
--   * createLocalUser / 管理员重置密码 置 must_change_password=true → 首登强制改密；
--   * 登录标识唯一性改为"仅未逻辑删除行"（部分唯一索引）→ 支持逻辑删除后用户名复用；
--   * 租户成员"移出"由应用层硬删 tenant_member（tenant_member_role / sys_user_org
--     经 FK ON DELETE CASCADE 级联），sys_user / user_credential 全局身份不动。
--
-- ⚠️ V0.4 重跑兼容（关键，勿忽略）：
--   V0.4:95 是 `ON CONFLICT ON CONSTRAINT uq_user_credential_username DO NOTHING`。
--   本迁移把该约束替换为同名【部分唯一索引】，因此 V0.4 在 V0.5 之后重跑会在第 95 行
--   报 "constraint does not exist"。
--   ⇒ 迁移必须按版本顺序执行：已应用 V0.5 的环境不要再重跑 V0.4。
--   （V0.4 的 MigrationSmokeTest 只读文件文本，不受影响。）
--
-- 说明：
--   * 本迁移不改 init.sql 基线，仅增量；回滚：不提供自动 down，由 DBA 评审执行。
-- =====================================================================

-- 执行方式：纯 SQL（不含 psql 元命令），兼容 psql / DBeaver / IDE / JDBC / 迁移工具。
--   psql -v ON_ERROR_STOP=1 -f deploy/ddl/migrations/V0.5__tenant_accounts.sql
-- 幂等性：对"已应用"状态逐语句幂等（重跑零报错，全为 no-op）：
--   * ADD COLUMN IF NOT EXISTS / DROP CONSTRAINT IF EXISTS / CREATE UNIQUE INDEX IF NOT EXISTS
--   * INSERT ... ON CONFLICT ... DO NOTHING（user_credential 用新部分索引推断语法，
--     见下注释；sys_user 按 id、tenant_member 按 (tenant_id,user_id)、
--     tenant_member_role 按 (tenant_id,user_id,role)）
--   * SELECT setval 固定到当前 max(id)，重跑无副作用
-- 注意：本迁移按"连接角色"直接执行，内部不再 SET ROLE 切换角色。
--   DDL 需要表属主（或其成员）或超级用户权限：
--   推荐以 ragkb_migrator 连接执行（ragkb_owner 成员，专门跑迁移）；postgres 超级用户亦可。
--   ragkb_app 是运行时 DML 角色，无 DDL 权限，用它执行会报 must be owner of table ...。
BEGIN;

-- ---------------------------------------------------------------------
-- 1. 凭据策略：强制首登改密标志（存量行回填 false）
-- ---------------------------------------------------------------------
ALTER TABLE user_credential
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN user_credential.must_change_password IS
'true 表示下次成功登录后必须先改密才能使用系统；createLocalUser / 管理员重置置 true，自助改密成功后置 false';

-- ---------------------------------------------------------------------
-- 2. 登录标识唯一性：约束 → 部分唯一索引（仅未逻辑删除行）
--    解决"逻辑删除（del_flag=1）后用户名不可复用"问题。
--
-- ⚠️ 关键变更：此后对 user_credential 的 upsert 必须用
--      `ON CONFLICT (lower(username)) WHERE del_flag = 0 DO NOTHING`；
--    `ON CONFLICT ON CONSTRAINT` 只对"约束"生效，部分唯一索引不可用该子句
--    （V0.4:95 的写法在本迁移之后会报错，见头注释的 V0.4 兼容说明）。
-- ---------------------------------------------------------------------
ALTER TABLE user_credential DROP CONSTRAINT IF EXISTS uq_user_credential_username;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_credential_username
    ON user_credential (lower(username)) WHERE del_flag = 0;

-- ---------------------------------------------------------------------
-- 3. 重发 bootstrap 管理员 seed（幂等 no-op；改用部分索引推断语法）
--    对齐 init.sql 默认租户 id=1 / code='default'。
-- ---------------------------------------------------------------------
INSERT INTO sys_user (id, primary_email, display_name, status, create_time, update_time)
VALUES (1, 'admin@ragkb.dev', 'Bootstrap Admin', 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- admin123 的 BCrypt hash（$2y$10$...，Spring BCryptPasswordEncoder 兼容）
-- bootstrap 管理员首登不强制改密（must_change_password=false）
INSERT INTO user_credential (user_id, username, password_hash, status, password_changed_at, must_change_password)
VALUES (1, 'admin', '$2y$10$mJmjunqPwc0cDJboJoL7cOHpum9CQdcL6/1bJhZpA8oZqFmg2LPd2', 'ACTIVE', now(), false)
ON CONFLICT (lower(username)) WHERE del_flag = 0 DO NOTHING;

INSERT INTO tenant_member (tenant_id, user_id, status, joined_at)
VALUES (1, 1, 'ACTIVE', now())
ON CONFLICT (tenant_id, user_id) DO NOTHING;

INSERT INTO tenant_member_role (tenant_id, user_id, role)
VALUES (1, 1, 'TENANT_ADMIN')
ON CONFLICT (tenant_id, user_id, role) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. 回填自增序列（幂等：固定到当前 max(id)）
-- ---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('sys_user', 'id'),
    GREATEST((SELECT max(id) FROM sys_user), 1), true);
SELECT setval(pg_get_serial_sequence('user_credential', 'id'),
    GREATEST((SELECT max(id) FROM user_credential), 1), true);
SELECT setval(pg_get_serial_sequence('tenant_member', 'id'),
    GREATEST((SELECT max(id) FROM tenant_member), 1), true);
SELECT setval(pg_get_serial_sequence('tenant_member_role', 'id'),
    GREATEST((SELECT max(id) FROM tenant_member_role), 1), true);

COMMIT;
