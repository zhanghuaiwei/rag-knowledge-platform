"""ContentSafetyProvider 的 Noop 实现（永远放行）。

作为占位实现存在，让 ``safety/`` 不再是空壳：
- 摄取链路未来要在解析前调用 ``ContentSafetyProvider.scan`` 做内容安全判定；
- 真实 provider（病毒扫描 / DLP / 提示词注入检测）接入前，先用 Noop 让链路可运行；
- 接真 provider 时只需在 ``container.py`` 替换注入，无需改 ingestion/generation 调用点。

⚠️ Noop **仅用于开发/演示**，生产环境必须替换为真实安全扫描 provider，
否则任何恶意文件/提示词都会被放行。
"""

from rag_engine.safety.models import SafetyDecision, SafetyScanResult


class NoopSafetyProvider:
    """``ContentSafetyProvider`` 的 Noop 实现：永远返回 ALLOWED。"""

    def scan(self, object_key: str) -> SafetyScanResult:
        """直接放行；不做任何检查。

        Args:
            object_key: 对象存储 key（当前不使用，保留以匹配端口签名）。

        Returns:
            永远是 ``decision=ALLOWED``、空 reason_codes 的结果。
        """
        del object_key  # 显式标记未使用，避免 lint 警告
        return SafetyScanResult(decision=SafetyDecision.ALLOWED, reason_codes=[])
