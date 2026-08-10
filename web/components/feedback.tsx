"use client";

import { useCallback } from "react";
import { App } from "antd";

type ToastKind = "success" | "error" | "info";

/**
 * 全局轻提示：基于 antd message（App 上下文已由 AntdProvider 提供）。
 * 保持原 useToast 的 (kind, text) 调用语义；返回稳定引用，避免依赖
 * toast 的 effect 因每次渲染新函数而无限重跑。
 */
export function useToast(): (kind: ToastKind, text: string) => void {
  const { message } = App.useApp();
  return useCallback(
    (kind: ToastKind, text: string) => {
      if (kind === "success") message.success(text);
      else if (kind === "error") message.error(text);
      else message.info(text);
    },
    [message],
  );
}
