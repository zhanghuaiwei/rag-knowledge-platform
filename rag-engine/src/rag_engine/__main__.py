"""``python -m rag_engine`` 启动入口。"""

import uvicorn

from rag_engine.config.settings import get_settings


def main() -> None:
    """按环境配置启动 Uvicorn。"""
    settings = get_settings()
    uvicorn.run(
        "rag_engine.main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level.casefold(),
        reload=settings.reload,
    )


if __name__ == "__main__":
    main()
