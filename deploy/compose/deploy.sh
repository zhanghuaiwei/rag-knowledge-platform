#!/usr/bin/env bash
# =====================================================================
# ragkb · 中间件一键部署（PostgreSQL + Redis + MinIO）
#
# 用法：cd deploy/compose && ./deploy.sh
#
# 流程：
#   1. 前置检查：docker / docker compose v2 / openssl / 必要文件
#   2. .env：不存在则从模板生成（随机密码，chmod 600）；
#      已存在则仅补全空字段，绝不覆盖已填写的真实密码
#   3. docker compose up -d（幂等，重复执行不会重跑数据库初始化）
#   4. 等待三个服务 healthy；超时即报错退出
#   5. 校验数据库初始化：ragkb 库表数 = 48、三个业务角色存在
#
# 安全边界：
#   * 脚本内不含任何密钥；密码写入 .env（已 gitignore，chmod 600）
#   * 不回显密码明文；需要查看时自行 cat .env
#   * 数据库重建：docker compose down -v 后重新运行本脚本
#
# 首次数据库初始化在 PostgreSQL 首次启动（空 pgdata 卷）时自动完成，
# 由 db-init/01-init-db.sh 执行 deploy/ddl/init.sql；详见 04-数据库设计.md §9。
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

REQUIRED_ENV_KEYS=(POSTGRES_PASSWORD MINIO_ROOT_PASSWORD RAGKB_APP_PASSWORD RAGKB_MIGRATOR_PASSWORD)
WANTED_TABLES=48
EXPECTED_ROLES=(ragkb_owner ragkb_migrator ragkb_app)

log()  { printf '\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[!] %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[1;31m[ERROR] %s\033[0m\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1（请先安装）"
}

check_prereqs() {
  log "前置检查"
  require_cmd docker
  require_cmd openssl
  docker compose version >/dev/null 2>&1 \
    || die "需要 docker compose v2（docker compose version 无输出）"
  docker info >/dev/null 2>&1 \
    || die "docker 守护进程不可用或当前用户无权限（试试 sudo 或加入 docker 组）"
  local missing=0
  for f in docker-compose.yml ../ddl/init.sql db-init/01-init-db.sh; do
    [ -f "$f" ] || { warn "缺少必需文件: $f"; missing=1; }
  done
  [ -x db-init/01-init-db.sh ] || { warn "db-init/01-init-db.sh 缺少执行权限（chmod +x）"; missing=1; }
  [ "$missing" -eq 0 ] || die "文件结构不完整，请按 deploy/ 目录结构完整上传后再运行"
}

gen_password() {
  # 24 位 base64（18 随机字节），不含 $ # 空格等 .env 插值危险字符
  openssl rand -base64 18 | tr -d '\n'
}

ensure_env_value() {
  # 仅在键缺失或值为空时写入；已填写的真实密码保持不动
  local key="$1" val="$2"
  if ! grep -qE "^${key}=.+" .env; then
    if grep -qE "^${key}=" .env; then
      sed -i.bak "s|^${key}=.*|${key}=${val}|" .env && rm -f .env.bak
    else
      printf '%s=%s\n' "$key" "$val" >> .env
    fi
    printf '  已生成 %s\n' "$key"
  fi
}

ensure_env() {
  if [ ! -f .env ]; then
    log "生成 .env（随机密码）"
    cp .env.example .env
    chmod 600 .env
    for k in "${REQUIRED_ENV_KEYS[@]}"; do ensure_env_value "$k" "$(gen_password)"; done
  else
    log "检测到已有 .env，仅补全空字段（不覆盖已填值）"
    chmod 600 .env
    for k in "${REQUIRED_ENV_KEYS[@]}"; do ensure_env_value "$k" "$(gen_password)"; done
  fi
  # 弱密码提醒（不改值）
  grep -qE "^MINIO_ROOT_USER=minioadmin" .env \
    && warn "MINIO_ROOT_USER 为默认值 minioadmin，公网暴露前请改为随机用户名"
  for k in "${REQUIRED_ENV_KEYS[@]}"; do
    v="$(grep -E "^${k}=" .env | head -1 | cut -d= -f2-)"
    [ "${#v}" -ge 16 ] || warn "${k} 长度不足 16 位（当前 ${#v}），建议改用随机长密码"
  done
}

wait_healthy() {
  local cname="$1" timeout="${2:-120}" status=""
  log "等待 $cname healthy（最长 ${timeout}s）"
  for _ in $(seq 1 "$timeout"); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cname" 2>/dev/null || true)"
    case "$status" in
      healthy) printf '  %s OK\n' "$cname"; return 0 ;;
      exited|dead) die "$cname 启动失败/退出（status=$status），查看 docker logs $cname" ;;
    esac
    sleep 1
  done
  die "$cname 未在 ${timeout}s 内变为 healthy（最后状态=$status），查看 docker logs $cname"
}

wait_table_count() {
  local want="$1" timeout="${2:-90}" got=""
  log "校验数据库初始化（ragkb 库表数应为 $want）"
  for _ in $(seq 1 "$timeout"); do
    got="$(docker compose exec -T postgres psql -U postgres -d ragkb -Atc \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';" 2>/dev/null || true)"
    case "$got" in
      "$want") printf '  表数 = %s ✓\n' "$got"; return 0 ;;
      [0-9]*) printf '  表数=%s（初始化进行中，重试）\r' "$got"; sleep 2 ;;
      *)       printf '  等待 ragkb 库就绪（当前=%s）\r' "$got"; sleep 2 ;;
    esac
  done
  die "数据库初始化未完成：${timeout}s 内表数未达 $want（最后=$got）。查看 docker logs ragkb-postgres"
}

verify_roles() {
  local role_count
  role_count="$(docker compose exec -T postgres psql -U postgres -d ragkb -Atc \
    "SELECT count(*) FROM pg_roles WHERE rolname IN ('ragkb_owner','ragkb_migrator','ragkb_app');" 2>/dev/null || true)"
  [ "$role_count" = "3" ] || die "业务角色校验失败（期望 3，实际 $role_count）"
  printf '  业务角色 ragkb_owner / ragkb_migrator / ragkb_app ✓\n'
}

summary() {
  printf '\n\033[1;32m部署完成。访问地址：\033[0m\n'
  printf '  PostgreSQL : 127.0.0.1:5432（库 ragkb，应用账号 ragkb_app）\n'
  printf '  Redis      : 127.0.0.1:6379\n'
  printf '  MinIO      : S3 127.0.0.1:9000 · Console http://127.0.0.1:9001\n'
  printf '\n后续：\n'
  printf '  * 应用连接 ragkb_app 的密码 = .env 的 RAGKB_APP_PASSWORD，需与 service 的 RAGKB_DB_PASSWORD 一致\n'
  printf '  * 查看密钥：cat .env（勿外传）\n'
  printf '  * 重建数据库：docker compose down -v 后重新 ./deploy.sh\n'
  printf '  * 查看初始化日志：docker logs ragkb-postgres | grep ragkb\n'
}

main() {
  check_prereqs
  ensure_env
  log "启动中间件（docker compose up -d）"
  docker compose up -d
  wait_healthy ragkb-postgres
  wait_healthy ragkb-redis
  wait_healthy ragkb-minio
  wait_table_count "$WANTED_TABLES"
  verify_roles
  summary
}

main "$@"
