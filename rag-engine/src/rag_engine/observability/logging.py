"""标准库日志初始化，避免在业务功能包中散落日志格式。"""

import logging


def configure_logging(level: str) -> None:
    """配置进程日志级别和稳定格式，不输出请求正文或凭证。"""
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    logging.getLogger("rag_engine").setLevel(level)
