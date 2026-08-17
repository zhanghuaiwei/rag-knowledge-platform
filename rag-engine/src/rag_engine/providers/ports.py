"""[已迁移] 适配器层端口定义已移至 :mod:`rag_engine.providers._provider_ports`。

本文件保留为 thin re-export 兼容层，避免一次性破坏既有 import；
新代码请直接从 ``rag_engine.providers._provider_ports`` 导入。
"""

import warnings

from rag_engine.providers._provider_ports import ObjectStore, SecretResolver

__all__ = ["ObjectStore", "SecretResolver"]


def _deprecated_re_export() -> None:
    warnings.warn(
        "rag_engine.providers.ports 已迁移到 rag_engine.providers._provider_ports，"
        "请更新 import 路径。",
        DeprecationWarning,
        stacklevel=2,
    )


_deprecated_re_export()
