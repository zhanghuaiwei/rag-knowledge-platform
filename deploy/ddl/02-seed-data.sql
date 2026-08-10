-- =====================================================================
-- 通用企业知识库平台 · 最小安全种子（v0.2）
--
-- 仅用于本地/私有化新装后的结构自检：
--   * 不创建默认密码、管理员、API Key、模型密钥或外部连接
--   * 生产租户、身份源和管理员必须通过受审计的控制面创建
--   * 模型 revision 必须在投产前替换为部署侧锁定的真实版本/摘要
-- =====================================================================

\set ON_ERROR_STOP on
SET ROLE ragkb_owner;
BEGIN;

INSERT INTO sys_tenant (
    id, code, name, deployment_mode, data_region, status
) VALUES (
    1, 'default', 'Default Tenant', 'PRIVATE', 'default', 'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO index_profile (
    id,
    tenant_id,
    name,
    profile_version,
    embedding_provider,
    embedding_model,
    model_revision,
    embedding_dimension,
    normalization,
    distance_metric,
    chunker_config,
    analyzer_config,
    status,
    activated_at
) VALUES (
    1,
    1,
    'default-multilingual-1024',
    1,
    'embedding.default',
    'bge-m3',
    'deployment-pinned',
    1024,
    'L2',
    'COSINE',
    '{"strategy":"structure-aware","maxTokens":512,"overlapTokens":50}'::jsonb,
    '{"language":"multilingual"}'::jsonb,
    'DRAFT',
    NULL
) ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_tenant', 'id'),
    GREATEST((SELECT max(id) FROM sys_tenant), 1),
    true
);
SELECT setval(
    pg_get_serial_sequence('index_profile', 'id'),
    GREATEST((SELECT max(id) FROM index_profile), 1),
    true
);

COMMIT;
RESET ROLE;

\echo 'Minimal seed applied. Create identity providers and administrators through the audited control plane.'
