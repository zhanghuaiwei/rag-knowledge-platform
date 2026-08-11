"use client";

import { useEffect, useRef, type Dispatch, type SetStateAction } from "react";

import type { ChatStreamResult } from "@/api-client";
import type { DisplayMessage } from "@/components/chat/types";

/**
 * mock SSE 逐 token 流：模拟生成中/完成/停止三态。
 * 接入真实后端后由 api-client 的 SSE 封装驱动（契约待冻结），本 Hook 仅演示用。
 */
export function useMockStream(
  setMessages: Dispatch<SetStateAction<DisplayMessage[]>>,
  onSettle: () => void,
) {
  const streamTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (streamTimer.current) clearInterval(streamTimer.current);
    },
    [],
  );

  const stopStreaming = () => {
    if (streamTimer.current) {
      clearInterval(streamTimer.current);
      streamTimer.current = null;
    }
    setMessages((prev) => prev.map((m) => (m.streaming ? { ...m, streaming: false, content: `${m.content}\n\n（已停止生成）` } : m)));
    onSettle();
  };

  const streamAnswer = (result: ChatStreamResult) => {
    const full = result.content;
    let cursor = 0;
    streamTimer.current = setInterval(() => {
      cursor += 2 + Math.floor(Math.random() * 3);
      const done = cursor >= full.length;
      setMessages((prev) =>
        prev.map((m) =>
          m.id === result.messageId
            ? {
                ...m,
                content: full.slice(0, cursor),
                streaming: !done,
                ...(done ? { sources: result.sources, suggestions: result.suggestions } : {}),
              }
            : m,
        ),
      );
      if (done && streamTimer.current) {
        clearInterval(streamTimer.current);
        streamTimer.current = null;
        onSettle();
      }
    }, 30);
  };

  return { streamAnswer, stopStreaming };
}
