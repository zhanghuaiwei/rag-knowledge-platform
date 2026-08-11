"""精排功能内部结果模型。"""

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class CandidateScore:
    """候选分块及其归一化相关性得分。"""

    chunk_id: str
    score: float
