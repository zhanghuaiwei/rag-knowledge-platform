-- =====================================================================
-- v0.3 · 全表统一审计列迁移（standalone，独立于 deploy/ddl/init.sql 基线）
-- 目标：PostgreSQL 16+，在 v0.2 基线之上执行；逐语句幂等（重跑安全），执行前建议备份。
--
-- 变更（对齐 MyBatis-Plus BaseAuditEntity 与 docs/04）：
--   created_at -> create_time   updated_at -> update_time
--   created_by -> create_by     updated_by -> update_by
--   deleted_at -> del_flag（kb/document/chat_session 保留与生命周期状态的耦合 CHECK）
--   sync_job/index_build/parse_task/audit_log/outbox_event 等补全 5 列
--   触发器函数 set_updated_at() -> set_update_time()（维护 update_time 兜底）
--
-- 说明：
--   * RENAME COLUMN 会自动更新引用该列的普通列索引，故索引无需重建。
--   * 追加写证据表（audit_log/document_review/chat_message_source/deletion_receipt）同样带
--     del_flag 列（schema 统一），但运行时角色 REVOKE UPDATE/DELETE 保留，逻辑删除的 UPDATE
--     会被拒绝，证据行保持追加写不可变；业务删除统一走 deletion_task。
--   * 回滚：不提供自动 down；如需回退，按 docs/04 §9.3 由 DBA 评审执行。
-- =====================================================================

-- 执行方式：纯 SQL（不含 psql 元命令），兼容 psql / DBeaver / IDE / JDBC / 迁移工具。
--   psql -v ON_ERROR_STOP=1 -f deploy/ddl/migrations/V0.3__unified_audit_columns.sql
-- 幂等性：本迁移对"已应用"状态逐语句幂等（重跑零报错，全为 no-op）：
--   * RENAME COLUMN / UPDATE 回填经辅助函数守卫（源列不存在或目标列已存在则跳过）
--   * ADD COLUMN IF NOT EXISTS / DROP COLUMN IF EXISTS / DROP CONSTRAINT IF EXISTS 前置重建
--   * 函数/触发器用 CREATE OR REPLACE
--   首次执行与重跑行为一致，无需手动判断是否已应用。
-- 注意：本迁移按"连接角色"直接执行，内部不再 SET ROLE 切换角色。
--   DDL 需要表属主（或其成员）或超级用户权限：
--   推荐以 ragkb_migrator 连接执行（ragkb_owner 成员，专门跑迁移）；postgres 超级用户亦可。
--   ragkb_app 是运行时 DML 角色，无 DDL 权限，用它执行会报 must be owner of table ...。
BEGIN;

-- 幂等辅助函数（仅本迁移内部使用，事务结束前删除）
CREATE OR REPLACE FUNCTION _v03_rename_col(t TEXT, src TEXT, dst TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = current_schema() AND c.relname = t AND a.attname = src
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = current_schema() AND c.relname = t AND a.attname = dst
    ) THEN
        EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I', t, src, dst);
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION _v03_backfill_del_flag(t TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = current_schema() AND c.relname = t AND a.attname = 'deleted_at'
    ) THEN
        EXECUTE format('UPDATE %I SET del_flag = 1 WHERE deleted_at IS NOT NULL', t);
    END IF;
END
$$;



-- 先移除引用将被改名/删除列的 CHECK，避免依赖 RENAME 自动改写
ALTER TABLE policy_snapshot DROP CONSTRAINT IF EXISTS ck_policy_snapshot_expiry;
ALTER TABLE idempotency_record DROP CONSTRAINT IF EXISTS ck_idempotency_expiry;
ALTER TABLE kb DROP CONSTRAINT IF EXISTS ck_kb_deleted_at;
ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_deleted_at;
ALTER TABLE chat_session DROP CONSTRAINT IF EXISTS ck_chat_session_deleted_at;

-- ---------------------------------------------------------------------
-- 1. Tenant / Identity
-- ---------------------------------------------------------------------
-- sys_tenant
SELECT _v03_rename_col('sys_tenant', 'created_at', 'create_time');
SELECT _v03_rename_col('sys_tenant', 'updated_at', 'update_time');
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE sys_tenant ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_user
SELECT _v03_rename_col('sys_user', 'created_at', 'create_time');
SELECT _v03_rename_col('sys_user', 'updated_at', 'update_time');
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- identity_account
SELECT _v03_rename_col('identity_account', 'created_at', 'create_time');
SELECT _v03_rename_col('identity_account', 'updated_at', 'update_time');
ALTER TABLE identity_account ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE identity_account ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE identity_account ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- identity_provider
SELECT _v03_rename_col('identity_provider', 'created_at', 'create_time');
SELECT _v03_rename_col('identity_provider', 'updated_at', 'update_time');
ALTER TABLE identity_provider ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE identity_provider ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE identity_provider ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- tenant_member
SELECT _v03_rename_col('tenant_member', 'created_at', 'create_time');
SELECT _v03_rename_col('tenant_member', 'updated_at', 'update_time');
ALTER TABLE tenant_member ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE tenant_member ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE tenant_member ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- tenant_member_role
SELECT _v03_rename_col('tenant_member_role', 'created_at', 'create_time');
ALTER TABLE tenant_member_role ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE tenant_member_role ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE tenant_member_role ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE tenant_member_role ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_org
SELECT _v03_rename_col('sys_org', 'created_at', 'create_time');
SELECT _v03_rename_col('sys_org', 'updated_at', 'update_time');
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_user_org
SELECT _v03_rename_col('sys_user_org', 'created_at', 'create_time');
ALTER TABLE sys_user_org ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE sys_user_org ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE sys_user_org ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sys_user_org ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 2. Knowledge configuration
-- ---------------------------------------------------------------------
-- retention_policy
SELECT _v03_rename_col('retention_policy', 'created_at', 'create_time');
SELECT _v03_rename_col('retention_policy', 'updated_at', 'update_time');
ALTER TABLE retention_policy ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE retention_policy ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE retention_policy ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- index_profile
SELECT _v03_rename_col('index_profile', 'created_at', 'create_time');
ALTER TABLE index_profile ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE index_profile ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE index_profile ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_profile ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- kb
SELECT _v03_rename_col('kb', 'created_by', 'create_by');
SELECT _v03_rename_col('kb', 'updated_by', 'update_by');
SELECT _v03_rename_col('kb', 'created_at', 'create_time');
SELECT _v03_rename_col('kb', 'updated_at', 'update_time');
ALTER TABLE kb ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;
SELECT _v03_backfill_del_flag('kb');
ALTER TABLE kb DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE kb DROP CONSTRAINT IF EXISTS ck_kb_del_flag;
ALTER TABLE kb ADD CONSTRAINT ck_kb_del_flag CHECK (
    (status IN ('DELETING', 'DELETED') AND del_flag = 1)
        OR (status NOT IN ('DELETING', 'DELETED') AND del_flag = 0)
);

-- kb_member
SELECT _v03_rename_col('kb_member', 'created_by', 'create_by');
SELECT _v03_rename_col('kb_member', 'created_at', 'create_time');
SELECT _v03_rename_col('kb_member', 'updated_at', 'update_time');
ALTER TABLE kb_member ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE kb_member ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- metadata_schema
SELECT _v03_rename_col('metadata_schema', 'created_by', 'create_by');
SELECT _v03_rename_col('metadata_schema', 'created_at', 'create_time');
ALTER TABLE metadata_schema ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE metadata_schema ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE metadata_schema ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 3. Content source / sync / document
-- ---------------------------------------------------------------------
-- source_connection
SELECT _v03_rename_col('source_connection', 'created_by', 'create_by');
SELECT _v03_rename_col('source_connection', 'created_at', 'create_time');
SELECT _v03_rename_col('source_connection', 'updated_at', 'update_time');
ALTER TABLE source_connection ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE source_connection ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- sync_job
ALTER TABLE sync_job ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE sync_job ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sync_job ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE sync_job ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sync_job ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- source_object
SELECT _v03_rename_col('source_object', 'created_at', 'create_time');
SELECT _v03_rename_col('source_object', 'updated_at', 'update_time');
ALTER TABLE source_object ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE source_object ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE source_object ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- document
SELECT _v03_rename_col('document', 'created_by', 'create_by');
SELECT _v03_rename_col('document', 'updated_by', 'update_by');
SELECT _v03_rename_col('document', 'created_at', 'create_time');
SELECT _v03_rename_col('document', 'updated_at', 'update_time');
ALTER TABLE document ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;
SELECT _v03_backfill_del_flag('document');
ALTER TABLE document DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_del_flag;
ALTER TABLE document ADD CONSTRAINT ck_document_del_flag CHECK (
    (lifecycle_status IN ('DELETING', 'DELETED') AND del_flag = 1)
        OR (lifecycle_status NOT IN ('DELETING', 'DELETED') AND del_flag = 0)
);

-- document_version
SELECT _v03_rename_col('document_version', 'created_by', 'create_by');
SELECT _v03_rename_col('document_version', 'created_at', 'create_time');
ALTER TABLE document_version ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE document_version ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_version ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- parse_task
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_metadata
SELECT _v03_rename_col('document_metadata', 'created_at', 'create_time');
SELECT _v03_rename_col('document_metadata', 'updated_at', 'update_time');
ALTER TABLE document_metadata ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE document_metadata ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE document_metadata ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_acl
SELECT _v03_rename_col('document_acl', 'created_by', 'create_by');
SELECT _v03_rename_col('document_acl', 'created_at', 'create_time');
ALTER TABLE document_acl ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE document_acl ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_acl ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_review
SELECT _v03_rename_col('document_review', 'created_at', 'create_time');
ALTER TABLE document_review ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE document_review ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE document_review ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_review ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- legal_hold
SELECT _v03_rename_col('legal_hold', 'created_by', 'create_by');
SELECT _v03_rename_col('legal_hold', 'created_at', 'create_time');
ALTER TABLE legal_hold ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE legal_hold ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE legal_hold ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- legal_hold_document
SELECT _v03_rename_col('legal_hold_document', 'created_at', 'create_time');
ALTER TABLE legal_hold_document ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE legal_hold_document ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE legal_hold_document ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE legal_hold_document ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 4. Chunk / index / policy
-- ---------------------------------------------------------------------
-- chunk_meta
SELECT _v03_rename_col('chunk_meta', 'created_at', 'create_time');
ALTER TABLE chunk_meta ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE chunk_meta ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE chunk_meta ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chunk_meta ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- index_build
ALTER TABLE index_build ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE index_build ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_build ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE index_build ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_build ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- policy_snapshot
SELECT _v03_rename_col('policy_snapshot', 'created_at', 'create_time');
ALTER TABLE policy_snapshot ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE policy_snapshot ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE policy_snapshot ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE policy_snapshot ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 5. Conversation / analytics
-- ---------------------------------------------------------------------
-- chat_session
SELECT _v03_rename_col('chat_session', 'created_at', 'create_time');
SELECT _v03_rename_col('chat_session', 'updated_at', 'update_time');
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;
SELECT _v03_backfill_del_flag('chat_session');
ALTER TABLE chat_session DROP COLUMN IF EXISTS deleted_at;
ALTER TABLE chat_session DROP CONSTRAINT IF EXISTS ck_chat_session_del_flag;
ALTER TABLE chat_session ADD CONSTRAINT ck_chat_session_del_flag CHECK (
    (status = 'DELETED' AND del_flag = 1)
        OR (status <> 'DELETED' AND del_flag = 0)
);
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS update_by BIGINT;

-- chat_session_kb
SELECT _v03_rename_col('chat_session_kb', 'created_at', 'create_time');
ALTER TABLE chat_session_kb ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE chat_session_kb ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE chat_session_kb ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_session_kb ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- chat_message
SELECT _v03_rename_col('chat_message', 'created_at', 'create_time');
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- chat_message_source
SELECT _v03_rename_col('chat_message_source', 'created_at', 'create_time');
ALTER TABLE chat_message_source ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE chat_message_source ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE chat_message_source ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_message_source ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- usage_daily
SELECT _v03_rename_col('usage_daily', 'created_at', 'create_time');
SELECT _v03_rename_col('usage_daily', 'updated_at', 'update_time');
ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE usage_daily ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- cost_record
SELECT _v03_rename_col('cost_record', 'created_at', 'create_time');
ALTER TABLE cost_record ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE cost_record ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE cost_record ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cost_record ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- model_route_config
SELECT _v03_rename_col('model_route_config', 'created_at', 'create_time');
SELECT _v03_rename_col('model_route_config', 'updated_at', 'update_time');
ALTER TABLE model_route_config ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE model_route_config ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE model_route_config ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 6. Integration / audit / event
-- ---------------------------------------------------------------------
-- api_key
SELECT _v03_rename_col('api_key', 'created_by', 'create_by');
SELECT _v03_rename_col('api_key', 'created_at', 'create_time');
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- api_key_kb
SELECT _v03_rename_col('api_key_kb', 'created_at', 'create_time');
ALTER TABLE api_key_kb ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE api_key_kb ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE api_key_kb ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE api_key_kb ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- idempotency_record
SELECT _v03_rename_col('idempotency_record', 'created_at', 'create_time');
SELECT _v03_rename_col('idempotency_record', 'updated_at', 'update_time');
ALTER TABLE idempotency_record ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE idempotency_record ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE idempotency_record ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- audit_log
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- outbox_event
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_event ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 7. Deletion evidence
-- ---------------------------------------------------------------------
-- deletion_task
SELECT _v03_rename_col('deletion_task', 'created_at', 'create_time');
ALTER TABLE deletion_task ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE deletion_task ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE deletion_task ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_task ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- deletion_target
SELECT _v03_rename_col('deletion_target', 'updated_at', 'update_time');
ALTER TABLE deletion_target ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE deletion_target ADD COLUMN IF NOT EXISTS create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_target ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE deletion_target ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- deletion_receipt
SELECT _v03_rename_col('deletion_receipt', 'created_at', 'create_time');
ALTER TABLE deletion_receipt ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE deletion_receipt ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE deletion_receipt ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_receipt ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 8. Auxiliary knowledge / webhook
-- ---------------------------------------------------------------------
-- tag
SELECT _v03_rename_col('tag', 'created_at', 'create_time');
ALTER TABLE tag ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE tag ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE tag ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE tag ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_tag
SELECT _v03_rename_col('document_tag', 'created_at', 'create_time');
ALTER TABLE document_tag ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE document_tag ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE document_tag ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_tag ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- user_favorite
SELECT _v03_rename_col('user_favorite', 'created_at', 'create_time');
ALTER TABLE user_favorite ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE user_favorite ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE user_favorite ADD COLUMN IF NOT EXISTS update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE user_favorite ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- notification
SELECT _v03_rename_col('notification', 'created_at', 'create_time');
SELECT _v03_rename_col('notification', 'updated_at', 'update_time');
ALTER TABLE notification ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- webhook_subscription
SELECT _v03_rename_col('webhook_subscription', 'created_by', 'create_by');
SELECT _v03_rename_col('webhook_subscription', 'created_at', 'create_time');
SELECT _v03_rename_col('webhook_subscription', 'updated_at', 'update_time');
ALTER TABLE webhook_subscription ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE webhook_subscription ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- webhook_delivery
SELECT _v03_rename_col('webhook_delivery', 'created_at', 'create_time');
SELECT _v03_rename_col('webhook_delivery', 'updated_at', 'update_time');
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS create_by BIGINT;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS update_by BIGINT;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS del_flag SMALLINT NOT NULL DEFAULT 0;

-- 重建引用新列名的过期 CHECK
ALTER TABLE policy_snapshot ADD CONSTRAINT ck_policy_snapshot_expiry
    CHECK (expires_at > create_time);
ALTER TABLE idempotency_record ADD CONSTRAINT ck_idempotency_expiry
    CHECK (expires_at > create_time);

-- 触发器函数：set_updated_at()（维护 updated_at）已被列改名废弃，替换为 set_update_time()
DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.update_time = now();
    RETURN NEW;
END;
$$;
GRANT EXECUTE ON FUNCTION set_update_time() TO ragkb_app;

-- 重建 update_time 触发器（与 v0.2 基线同一批业务表）
DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'sys_tenant', 'sys_user', 'identity_account', 'identity_provider',
        'tenant_member', 'sys_org', 'retention_policy', 'kb',
        'kb_member', 'source_connection', 'source_object', 'document',
        'document_metadata', 'chat_session', 'usage_daily', 'model_route_config',
        'idempotency_record', 'deletion_target', 'webhook_subscription', 'webhook_delivery',
        'notification'
    ]
    LOOP
        EXECUTE format(
            'CREATE OR REPLACE TRIGGER trg_%I_update_time BEFORE UPDATE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION set_update_time()',
            table_name, table_name
        );
    END LOOP;
END;
$$;

DROP FUNCTION IF EXISTS _v03_rename_col(TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS _v03_backfill_del_flag(TEXT);
COMMIT;
RESET ROLE;

