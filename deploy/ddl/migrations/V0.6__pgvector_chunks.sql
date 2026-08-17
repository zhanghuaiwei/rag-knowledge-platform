-- =====================================================================
-- V0.6: 启用 pgvector，为 chunk_meta 增加向量列（最小 RAG 链路，2026-08-17）
--
-- 前置条件：
--   * PostgreSQL 已安装 pgvector 扩展（服务器 postgres 需换 pgvector/pgvector:pg16-alpine
--     镜像重建容器，或 apk add postgresql16-pgvector；见 deploy/compose/docker-compose.yml）。
--
-- 约束：
--   * 纯 SQL、逐语句幂等，可安全重跑（兼容 DBeaver 执行）；
--   * 不用 psql 元命令（\c / \gset 等）。
--
-- ⚠️ 维度约定：embedding vector(1024) 与 Embedding 模型 text-embedding-v3(dimensions=1024)
--   （rag-engine 侧 RAG_ENGINE_EMBEDDING_DIMENSION）一致。
--   更换 Embedding 模型/维度时必须：DROP INDEX idx_chunk_embedding_hnsw;
--   → ALTER TABLE chunk_meta ALTER COLUMN embedding TYPE vector(<新维度>); → 重建索引，
--   并重新摄取全部文档（旧向量与维度不匹配将无法查询）。
-- =====================================================================

-- 1) 启用扩展（不存在才创建，幂等）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2) chunk_meta 增加 embedding 列（已有则跳过）
ALTER TABLE chunk_meta ADD COLUMN IF NOT EXISTS embedding vector(1024);

-- 3) HNSW 余弦距离索引（top_k 余弦检索加速；对空表同样生效）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
    ON chunk_meta USING hnsw (embedding vector_cosine_ops);
