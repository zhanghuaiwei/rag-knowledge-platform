#!/bin/sh
# =====================================================================
# ragkb · PostgreSQL 首次初始化入口（挂载到 /docker-entrypoint-initdb.d/）
#
# 为什么需要这个包装脚本：
#   deploy/ddl/init.sql 通过 psql 变量（-v）接收两个业务角色密码；而官方
#   postgres 镜像对 initdb.d 下的 .sql 文件只会直接以 psql 执行、无法注入
#   -v。因此这里用 .sh 包装：从容器环境变量读取密码后，再以 psql -v 执行
#   init.sql，保持与 04-数据库设计.md §9.1 的「密码不写入仓库」一致。
#
# 触发时机：仅当 pgdata 卷为空（首次 docker compose up）时由镜像入口执行；
#   之后每次 up / 重启都跳过 —— 与 init.sql「一次性、非幂等」的性质一致。
#   需要重建时：docker compose down -v 清空卷后重新 up。
#
# 失败处理：psql 失败 → 脚本非零退出 → 镜像初始化中止、容器启动失败，
#   不会带病运行（与 init.sql 顶部的 \set ON_ERROR_STOP on 呼应）。
#
# 环境变量（来自 deploy/compose/.env，见 docker-compose.yml）：
#   POSTGRES_USER / POSTGRES_DB   镜像提供的 superuser 与默认库
#   RAGKB_APP_PASSWORD            对应 init.sql 的 ragkb_app_password
#   RAGKB_MIGRATOR_PASSWORD       对应 init.sql 的 ragkb_migrator_password
# =====================================================================
set -euo pipefail

echo "ragkb: initializing database schema (roles + ragkb DB + tables + seed)..."
psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v ragkb_app_password="$RAGKB_APP_PASSWORD" \
     -v ragkb_migrator_password="$RAGKB_MIGRATOR_PASSWORD" \
     -f /db-init/init.sql
echo "ragkb: database initialization completed."
