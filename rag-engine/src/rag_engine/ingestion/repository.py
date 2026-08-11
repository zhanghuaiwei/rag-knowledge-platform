"""开发态有界内存任务仓库。"""

from collections import OrderedDict
from threading import RLock

from rag_engine.ingestion.models import IngestTaskSnapshot


class InMemoryIngestTaskRepository:
    """线程安全、有界但不持久化的任务仓库。

    该仓库仅用于最小契约联调。生产任务必须由持久化 task/outbox 和 worker 承担。
    """

    def __init__(self, *, max_tasks: int) -> None:
        if max_tasks < 1:
            raise ValueError("max_tasks must be positive")
        self._max_tasks = max_tasks
        self._tasks: OrderedDict[str, IngestTaskSnapshot] = OrderedDict()
        self._lock = RLock()

    def save(self, snapshot: IngestTaskSnapshot) -> None:
        """保存快照，并从最旧记录开始淘汰超出容量的任务。"""
        with self._lock:
            self._tasks[snapshot.task_id] = snapshot
            self._tasks.move_to_end(snapshot.task_id)
            while len(self._tasks) > self._max_tasks:
                self._tasks.popitem(last=False)

    def get(self, task_id: str) -> IngestTaskSnapshot | None:
        """按 taskId 返回不可变快照；不存在或已淘汰时返回 ``None``。"""
        with self._lock:
            return self._tasks.get(task_id)
