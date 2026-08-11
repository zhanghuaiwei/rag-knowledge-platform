"""进入解析器前的内容安全判定模型。"""

from enum import StrEnum

from pydantic import BaseModel, Field


class SafetyDecision(StrEnum):
    """内容安全流水线的最终判定。"""

    ALLOWED = "ALLOWED"
    BLOCKED = "BLOCKED"


class SafetyScanResult(BaseModel):
    """安全扫描结果；原因码不包含原文或秘密信息。"""

    decision: SafetyDecision
    reason_codes: list[str] = Field(default_factory=list)
