# 统一验证入口（对应全局 CLAUDE.md 的 Inner Loop：lint / test / security）
# 注意：backend 目标需要 JDK 21（本机仅 JDK 8 时不可用，见 .ai/project.json toolchainNote）。

.PHONY: lint typecheck test frontend-test rag-test backend-test security

WEB = cd web && pnpm
RAG = cd rag-engine && uv run
SERVICE = cd service && mvn -B -q

lint:
	$(WEB) lint
	$(RAG) ruff check .

typecheck:
	$(WEB) typecheck

test: frontend-test rag-test backend-test

frontend-test:
	$(WEB) test

rag-test:
	$(RAG) pytest -q

backend-test:
	$(SERVICE) test

# 轻量密钥扫描：只扫常见硬编码模式；.env 已被 .gitignore 排除，依赖目录忽略
security:
	@grep -rInE --exclude-dir=node_modules --exclude-dir=.venv --exclude-dir=dist --exclude-dir=.next \
	  "(api[_-]?key|password|passwd|secret|client[_-]?secret|access[_-]?key|token)\s*[:=]\s*['\"][^'\"]{8,}['\"]" \
	  --include="*.java" --include="*.py" --include="*.ts" --include="*.tsx" --include="*.yml" --include="*.yaml" \
	  service rag-engine web 2>/dev/null \
	  && { echo "secret-scan: 发现疑似硬编码密钥，请检查"; exit 1; } \
	  || echo "secret-scan: 未发现硬编码密钥模式"
