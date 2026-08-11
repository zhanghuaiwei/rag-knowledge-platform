-- =====================================================================
-- v0.3 · 全表统一审计列迁移（standalone，独立于 deploy/ddl/init.sql 基线）
-- 目标：PostgreSQL 16+，在 v0.2 基线之上执行；一次性、非幂等，执行前备份。
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

-- 执行方式：本文件为纯 SQL（不含 psql 元命令），兼容 psql / IDE / DBeaver / JDBC / 迁移工具。
--   psql -v ON_ERROR_STOP=1 -f deploy/ddl/migrations/V0.3__unified_audit_columns.sql
-- 需以 ragkb_migrator（或具备 ragkb_owner 成员资格的超级用户）执行；内部 SET ROLE ragkb_owner。
SET ROLE ragkb_app;
BEGIN;

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
ALTER TABLE sys_tenant RENAME COLUMN created_at TO create_time;
ALTER TABLE sys_tenant RENAME COLUMN updated_at TO update_time;
ALTER TABLE sys_tenant ADD COLUMN create_by BIGINT;
ALTER TABLE sys_tenant ADD COLUMN update_by BIGINT;
ALTER TABLE sys_tenant ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_user
ALTER TABLE sys_user RENAME COLUMN created_at TO create_time;
ALTER TABLE sys_user RENAME COLUMN updated_at TO update_time;
ALTER TABLE sys_user ADD COLUMN create_by BIGINT;
ALTER TABLE sys_user ADD COLUMN update_by BIGINT;
ALTER TABLE sys_user ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- identity_account
ALTER TABLE identity_account RENAME COLUMN created_at TO create_time;
ALTER TABLE identity_account RENAME COLUMN updated_at TO update_time;
ALTER TABLE identity_account ADD COLUMN create_by BIGINT;
ALTER TABLE identity_account ADD COLUMN update_by BIGINT;
ALTER TABLE identity_account ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- identity_provider
ALTER TABLE identity_provider RENAME COLUMN created_at TO create_time;
ALTER TABLE identity_provider RENAME COLUMN updated_at TO update_time;
ALTER TABLE identity_provider ADD COLUMN create_by BIGINT;
ALTER TABLE identity_provider ADD COLUMN update_by BIGINT;
ALTER TABLE identity_provider ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- tenant_member
ALTER TABLE tenant_member RENAME COLUMN created_at TO create_time;
ALTER TABLE tenant_member RENAME COLUMN updated_at TO update_time;
ALTER TABLE tenant_member ADD COLUMN create_by BIGINT;
ALTER TABLE tenant_member ADD COLUMN update_by BIGINT;
ALTER TABLE tenant_member ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- tenant_member_role
ALTER TABLE tenant_member_role RENAME COLUMN created_at TO create_time;
ALTER TABLE tenant_member_role ADD COLUMN create_by BIGINT;
ALTER TABLE tenant_member_role ADD COLUMN update_by BIGINT;
ALTER TABLE tenant_member_role ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE tenant_member_role ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_org
ALTER TABLE sys_org RENAME COLUMN created_at TO create_time;
ALTER TABLE sys_org RENAME COLUMN updated_at TO update_time;
ALTER TABLE sys_org ADD COLUMN create_by BIGINT;
ALTER TABLE sys_org ADD COLUMN update_by BIGINT;
ALTER TABLE sys_org ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- sys_user_org
ALTER TABLE sys_user_org RENAME COLUMN created_at TO create_time;
ALTER TABLE sys_user_org ADD COLUMN create_by BIGINT;
ALTER TABLE sys_user_org ADD COLUMN update_by BIGINT;
ALTER TABLE sys_user_org ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sys_user_org ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 2. Knowledge configuration
-- ---------------------------------------------------------------------
-- retention_policy
ALTER TABLE retention_policy RENAME COLUMN created_at TO create_time;
ALTER TABLE retention_policy RENAME COLUMN updated_at TO update_time;
ALTER TABLE retention_policy ADD COLUMN create_by BIGINT;
ALTER TABLE retention_policy ADD COLUMN update_by BIGINT;
ALTER TABLE retention_policy ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- index_profile
ALTER TABLE index_profile RENAME COLUMN created_at TO create_time;
ALTER TABLE index_profile ADD COLUMN create_by BIGINT;
ALTER TABLE index_profile ADD COLUMN update_by BIGINT;
ALTER TABLE index_profile ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_profile ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- kb
ALTER TABLE kb RENAME COLUMN created_by TO create_by;
ALTER TABLE kb RENAME COLUMN updated_by TO update_by;
ALTER TABLE kb RENAME COLUMN created_at TO create_time;
ALTER TABLE kb RENAME COLUMN updated_at TO update_time;
ALTER TABLE kb ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;
UPDATE kb SET del_flag = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE kb DROP COLUMN deleted_at;
ALTER TABLE kb ADD CONSTRAINT ck_kb_del_flag CHECK (
    (status IN ('DELETING', 'DELETED') AND del_flag = 1)
        OR (status NOT IN ('DELETING', 'DELETED') AND del_flag = 0)
);

-- kb_member
ALTER TABLE kb_member RENAME COLUMN created_by TO create_by;
ALTER TABLE kb_member RENAME COLUMN created_at TO create_time;
ALTER TABLE kb_member RENAME COLUMN updated_at TO update_time;
ALTER TABLE kb_member ADD COLUMN update_by BIGINT;
ALTER TABLE kb_member ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- metadata_schema
ALTER TABLE metadata_schema RENAME COLUMN created_by TO create_by;
ALTER TABLE metadata_schema RENAME COLUMN created_at TO create_time;
ALTER TABLE metadata_schema ADD COLUMN update_by BIGINT;
ALTER TABLE metadata_schema ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE metadata_schema ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 3. Content source / sync / document
-- ---------------------------------------------------------------------
-- source_connection
ALTER TABLE source_connection RENAME COLUMN created_by TO create_by;
ALTER TABLE source_connection RENAME COLUMN created_at TO create_time;
ALTER TABLE source_connection RENAME COLUMN updated_at TO update_time;
ALTER TABLE source_connection ADD COLUMN update_by BIGINT;
ALTER TABLE source_connection ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- sync_job
ALTER TABLE sync_job ADD COLUMN create_by BIGINT;
ALTER TABLE sync_job ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sync_job ADD COLUMN update_by BIGINT;
ALTER TABLE sync_job ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE sync_job ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- source_object
ALTER TABLE source_object RENAME COLUMN created_at TO create_time;
ALTER TABLE source_object RENAME COLUMN updated_at TO update_time;
ALTER TABLE source_object ADD COLUMN create_by BIGINT;
ALTER TABLE source_object ADD COLUMN update_by BIGINT;
ALTER TABLE source_object ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- document
ALTER TABLE document RENAME COLUMN created_by TO create_by;
ALTER TABLE document RENAME COLUMN updated_by TO update_by;
ALTER TABLE document RENAME COLUMN created_at TO create_time;
ALTER TABLE document RENAME COLUMN updated_at TO update_time;
ALTER TABLE document ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;
UPDATE document SET del_flag = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE document DROP COLUMN deleted_at;
ALTER TABLE document ADD CONSTRAINT ck_document_del_flag CHECK (
    (lifecycle_status IN ('DELETING', 'DELETED') AND del_flag = 1)
        OR (lifecycle_status NOT IN ('DELETING', 'DELETED') AND del_flag = 0)
);

-- document_version
ALTER TABLE document_version RENAME COLUMN created_by TO create_by;
ALTER TABLE document_version RENAME COLUMN created_at TO create_time;
ALTER TABLE document_version ADD COLUMN update_by BIGINT;
ALTER TABLE document_version ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_version ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- parse_task
ALTER TABLE parse_task ADD COLUMN create_by BIGINT;
ALTER TABLE parse_task ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE parse_task ADD COLUMN update_by BIGINT;
ALTER TABLE parse_task ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE parse_task ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_metadata
ALTER TABLE document_metadata RENAME COLUMN created_at TO create_time;
ALTER TABLE document_metadata RENAME COLUMN updated_at TO update_time;
ALTER TABLE document_metadata ADD COLUMN create_by BIGINT;
ALTER TABLE document_metadata ADD COLUMN update_by BIGINT;
ALTER TABLE document_metadata ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_acl
ALTER TABLE document_acl RENAME COLUMN created_by TO create_by;
ALTER TABLE document_acl RENAME COLUMN created_at TO create_time;
ALTER TABLE document_acl ADD COLUMN update_by BIGINT;
ALTER TABLE document_acl ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_acl ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_review
ALTER TABLE document_review RENAME COLUMN created_at TO create_time;
ALTER TABLE document_review ADD COLUMN create_by BIGINT;
ALTER TABLE document_review ADD COLUMN update_by BIGINT;
ALTER TABLE document_review ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_review ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- legal_hold
ALTER TABLE legal_hold RENAME COLUMN created_by TO create_by;
ALTER TABLE legal_hold RENAME COLUMN created_at TO create_time;
ALTER TABLE legal_hold ADD COLUMN update_by BIGINT;
ALTER TABLE legal_hold ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE legal_hold ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- legal_hold_document
ALTER TABLE legal_hold_document RENAME COLUMN created_at TO create_time;
ALTER TABLE legal_hold_document ADD COLUMN create_by BIGINT;
ALTER TABLE legal_hold_document ADD COLUMN update_by BIGINT;
ALTER TABLE legal_hold_document ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE legal_hold_document ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 4. Chunk / index / policy
-- ---------------------------------------------------------------------
-- chunk_meta
ALTER TABLE chunk_meta RENAME COLUMN created_at TO create_time;
ALTER TABLE chunk_meta ADD COLUMN create_by BIGINT;
ALTER TABLE chunk_meta ADD COLUMN update_by BIGINT;
ALTER TABLE chunk_meta ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chunk_meta ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- index_build
ALTER TABLE index_build ADD COLUMN create_by BIGINT;
ALTER TABLE index_build ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_build ADD COLUMN update_by BIGINT;
ALTER TABLE index_build ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE index_build ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- policy_snapshot
ALTER TABLE policy_snapshot RENAME COLUMN created_at TO create_time;
ALTER TABLE policy_snapshot ADD COLUMN create_by BIGINT;
ALTER TABLE policy_snapshot ADD COLUMN update_by BIGINT;
ALTER TABLE policy_snapshot ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE policy_snapshot ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 5. Conversation / analytics
-- ---------------------------------------------------------------------
-- chat_session
ALTER TABLE chat_session RENAME COLUMN created_at TO create_time;
ALTER TABLE chat_session RENAME COLUMN updated_at TO update_time;
ALTER TABLE chat_session ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;
UPDATE chat_session SET del_flag = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE chat_session DROP COLUMN deleted_at;
ALTER TABLE chat_session ADD CONSTRAINT ck_chat_session_del_flag CHECK (
    (status = 'DELETED' AND del_flag = 1)
        OR (status <> 'DELETED' AND del_flag = 0)
);
ALTER TABLE chat_session ADD COLUMN create_by BIGINT;
ALTER TABLE chat_session ADD COLUMN update_by BIGINT;

-- chat_session_kb
ALTER TABLE chat_session_kb RENAME COLUMN created_at TO create_time;
ALTER TABLE chat_session_kb ADD COLUMN create_by BIGINT;
ALTER TABLE chat_session_kb ADD COLUMN update_by BIGINT;
ALTER TABLE chat_session_kb ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_session_kb ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- chat_message
ALTER TABLE chat_message RENAME COLUMN created_at TO create_time;
ALTER TABLE chat_message ADD COLUMN create_by BIGINT;
ALTER TABLE chat_message ADD COLUMN update_by BIGINT;
ALTER TABLE chat_message ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_message ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- chat_message_source
ALTER TABLE chat_message_source RENAME COLUMN created_at TO create_time;
ALTER TABLE chat_message_source ADD COLUMN create_by BIGINT;
ALTER TABLE chat_message_source ADD COLUMN update_by BIGINT;
ALTER TABLE chat_message_source ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE chat_message_source ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- usage_daily
ALTER TABLE usage_daily RENAME COLUMN created_at TO create_time;
ALTER TABLE usage_daily RENAME COLUMN updated_at TO update_time;
ALTER TABLE usage_daily ADD COLUMN create_by BIGINT;
ALTER TABLE usage_daily ADD COLUMN update_by BIGINT;
ALTER TABLE usage_daily ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- cost_record
ALTER TABLE cost_record RENAME COLUMN created_at TO create_time;
ALTER TABLE cost_record ADD COLUMN create_by BIGINT;
ALTER TABLE cost_record ADD COLUMN update_by BIGINT;
ALTER TABLE cost_record ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cost_record ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- model_route_config
ALTER TABLE model_route_config RENAME COLUMN created_at TO create_time;
ALTER TABLE model_route_config RENAME COLUMN updated_at TO update_time;
ALTER TABLE model_route_config ADD COLUMN create_by BIGINT;
ALTER TABLE model_route_config ADD COLUMN update_by BIGINT;
ALTER TABLE model_route_config ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 6. Integration / audit / event
-- ---------------------------------------------------------------------
-- api_key
ALTER TABLE api_key RENAME COLUMN created_by TO create_by;
ALTER TABLE api_key RENAME COLUMN created_at TO create_time;
ALTER TABLE api_key ADD COLUMN update_by BIGINT;
ALTER TABLE api_key ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE api_key ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- api_key_kb
ALTER TABLE api_key_kb RENAME COLUMN created_at TO create_time;
ALTER TABLE api_key_kb ADD COLUMN create_by BIGINT;
ALTER TABLE api_key_kb ADD COLUMN update_by BIGINT;
ALTER TABLE api_key_kb ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE api_key_kb ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- idempotency_record
ALTER TABLE idempotency_record RENAME COLUMN created_at TO create_time;
ALTER TABLE idempotency_record RENAME COLUMN updated_at TO update_time;
ALTER TABLE idempotency_record ADD COLUMN create_by BIGINT;
ALTER TABLE idempotency_record ADD COLUMN update_by BIGINT;
ALTER TABLE idempotency_record ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- audit_log
ALTER TABLE audit_log ADD COLUMN create_by BIGINT;
ALTER TABLE audit_log ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE audit_log ADD COLUMN update_by BIGINT;
ALTER TABLE audit_log ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE audit_log ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- outbox_event
ALTER TABLE outbox_event ADD COLUMN create_by BIGINT;
ALTER TABLE outbox_event ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_event ADD COLUMN update_by BIGINT;
ALTER TABLE outbox_event ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE outbox_event ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 7. Deletion evidence
-- ---------------------------------------------------------------------
-- deletion_task
ALTER TABLE deletion_task RENAME COLUMN created_at TO create_time;
ALTER TABLE deletion_task ADD COLUMN create_by BIGINT;
ALTER TABLE deletion_task ADD COLUMN update_by BIGINT;
ALTER TABLE deletion_task ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_task ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- deletion_target
ALTER TABLE deletion_target RENAME COLUMN updated_at TO update_time;
ALTER TABLE deletion_target ADD COLUMN create_by BIGINT;
ALTER TABLE deletion_target ADD COLUMN create_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_target ADD COLUMN update_by BIGINT;
ALTER TABLE deletion_target ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- deletion_receipt
ALTER TABLE deletion_receipt RENAME COLUMN created_at TO create_time;
ALTER TABLE deletion_receipt ADD COLUMN create_by BIGINT;
ALTER TABLE deletion_receipt ADD COLUMN update_by BIGINT;
ALTER TABLE deletion_receipt ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE deletion_receipt ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- 8. Auxiliary knowledge / webhook
-- ---------------------------------------------------------------------
-- tag
ALTER TABLE tag RENAME COLUMN created_at TO create_time;
ALTER TABLE tag ADD COLUMN create_by BIGINT;
ALTER TABLE tag ADD COLUMN update_by BIGINT;
ALTER TABLE tag ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE tag ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- document_tag
ALTER TABLE document_tag RENAME COLUMN created_at TO create_time;
ALTER TABLE document_tag ADD COLUMN create_by BIGINT;
ALTER TABLE document_tag ADD COLUMN update_by BIGINT;
ALTER TABLE document_tag ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE document_tag ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- user_favorite
ALTER TABLE user_favorite RENAME COLUMN created_at TO create_time;
ALTER TABLE user_favorite ADD COLUMN create_by BIGINT;
ALTER TABLE user_favorite ADD COLUMN update_by BIGINT;
ALTER TABLE user_favorite ADD COLUMN update_time TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE user_favorite ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- notification
ALTER TABLE notification RENAME COLUMN created_at TO create_time;
ALTER TABLE notification RENAME COLUMN updated_at TO update_time;
ALTER TABLE notification ADD COLUMN create_by BIGINT;
ALTER TABLE notification ADD COLUMN update_by BIGINT;
ALTER TABLE notification ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- webhook_subscription
ALTER TABLE webhook_subscription RENAME COLUMN created_by TO create_by;
ALTER TABLE webhook_subscription RENAME COLUMN created_at TO create_time;
ALTER TABLE webhook_subscription RENAME COLUMN updated_at TO update_time;
ALTER TABLE webhook_subscription ADD COLUMN update_by BIGINT;
ALTER TABLE webhook_subscription ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- webhook_delivery
ALTER TABLE webhook_delivery RENAME COLUMN created_at TO create_time;
ALTER TABLE webhook_delivery RENAME COLUMN updated_at TO update_time;
ALTER TABLE webhook_delivery ADD COLUMN create_by BIGINT;
ALTER TABLE webhook_delivery ADD COLUMN update_by BIGINT;
ALTER TABLE webhook_delivery ADD COLUMN del_flag SMALLINT NOT NULL DEFAULT 0;

-- 重建引用新列名的过期 CHECK
ALTER TABLE policy_snapshot ADD CONSTRAINT ck_policy_snapshot_expiry
    CHECK (expires_at > create_time);
ALTER TABLE idempotency_record ADD CONSTRAINT ck_idempotency_expiry
    CHECK (expires_at > create_time);

-- 触发器函数：set_updated_at()（维护 updated_at）已被列改名废弃，替换为 set_update_time()
DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
CREATE FUNCTION set_update_time()
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
            'CREATE TRIGGER trg_%I_update_time BEFORE UPDATE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION set_update_time()',
            table_name, table_name
        );
    END LOOP;
END;
$$;

COMMIT;
RESET ROLE;

