"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";

import { Icon, type IconName } from "@/components/icons";

/* ---------- Tag ---------- */

export function Tag({ color, children }: { color?: string; children: ReactNode }) {
  return <span className={`tag${color ? ` tag-${color}` : ""}`}>{children}</span>;
}

/* ---------- Loading / Empty / Error ---------- */

export function Loading({ text = "加载中…" }: { text?: string }) {
  return (
    <div className="loading-block">
      <span className="spinner" />
      <span>{text}</span>
    </div>
  );
}

export function Empty({ icon = "📭", title, desc, action }: { icon?: string; title: string; desc?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <div className="empty-title">{title}</div>
      {desc ? <p style={{ marginBottom: action ? 16 : 0 }}>{desc}</p> : null}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <div className="empty-icon">⚠️</div>
      <div className="empty-title">加载失败</div>
      <p style={{ marginBottom: 16 }}>{message}</p>
      {onRetry ? (
        <button className="btn" onClick={onRetry}>
          <Icon name="refresh" size={14} /> 重试
        </button>
      ) : null}
    </div>
  );
}

export function SkeletonRows({ rows = 4, height = 44 }: { rows?: number; height?: number }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" style={{ height }} />
      ))}
    </div>
  );
}

/* ---------- Modal / Drawer / Confirm ---------- */

/** 弹层打开时锁定背景滚动（含 Esc 关闭的通用 effect）。 */
function useOverlayEffects(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);
}

export function Modal({
  title,
  open,
  onClose,
  footer,
  children,
  large,
}: {
  title: string;
  open: boolean;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
  large?: boolean;
}) {
  useOverlayEffects(open, onClose);

  if (!open) return null;
  return (
    <div className="modal-mask" onClick={onClose}>
      <div className={`modal${large ? " modal-lg" : ""}`} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-header">
          <span>{title}</span>
          <button className="icon-btn" onClick={onClose} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer ? <div className="modal-footer">{footer}</div> : null}
      </div>
    </div>
  );
}

export function Drawer({ title, open, onClose, children }: { title: string; open: boolean; onClose: () => void; children: ReactNode }) {
  useOverlayEffects(open, onClose);

  if (!open) return null;
  return (
    <>
      <div className="drawer-mask" onClick={onClose} />
      <div className="drawer" role="dialog" aria-modal="true" aria-label={title}>
        <div className="drawer-header">
          <span>{title}</span>
          <button className="icon-btn" onClick={onClose} aria-label="关闭">
            <Icon name="x" size={16} />
          </button>
        </div>
        <div className="drawer-body">{children}</div>
      </div>
    </>
  );
}

/** 危险操作确认：独立确认按钮 + 提交中防重复。 */
export function ConfirmModal({
  open,
  title,
  danger,
  loading,
  description,
  confirmText = "确认",
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  danger?: boolean;
  loading?: boolean;
  description: ReactNode;
  confirmText?: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal
      title={title}
      open={open}
      onClose={loading ? () => undefined : onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={loading}>
            取消
          </button>
          <button className={`btn ${danger ? "btn-danger" : "btn-primary"}`} onClick={onConfirm} disabled={loading}>
            {loading ? "处理中…" : confirmText}
          </button>
        </>
      }
    >
      <div style={{ display: "flex", gap: 12 }}>
        {danger ? (
          <span style={{ color: "var(--danger)", flexShrink: 0 }}>
            <Icon name="alert" size={22} />
          </span>
        ) : null}
        <div>{description}</div>
      </div>
    </Modal>
  );
}

/* ---------- Tabs ---------- */

export function Tabs<T extends string>({
  items,
  active,
  onChange,
}: {
  items: { key: T; label: ReactNode }[];
  active: T;
  onChange: (key: T) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {items.map((item) => (
        <button
          key={item.key}
          role="tab"
          aria-selected={active === item.key}
          className={`tab${active === item.key ? " active" : ""}`}
          onClick={() => onChange(item.key)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

/* ---------- Switch ---------- */

export function Switch({ checked, onChange, disabled }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      className={`switch${checked ? " on" : ""}`}
      disabled={disabled}
      onClick={() => onChange(!checked)}
    />
  );
}

/* ---------- Pagination ---------- */

export function Pagination({ page, size, total, onChange }: { page: number; size: number; total: number; onChange: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="pagination">
      <span>
        共 {total} 条 · 第 {page}/{pages} 页
      </span>
      <button className="btn btn-sm" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        上一页
      </button>
      <button className="btn btn-sm" disabled={page >= pages} onClick={() => onChange(page + 1)}>
        下一页
      </button>
    </div>
  );
}

/* ---------- Dropdown ---------- */

export function Dropdown({ trigger, children, align = "right" }: { trigger: ReactNode; children: ReactNode; align?: "left" | "right" }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="dropdown" ref={ref}>
      <button
        type="button"
        className="dropdown-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        {trigger}
      </button>
      {open ? (
        <div className="dropdown-menu" role="menu" style={align === "left" ? { left: 0, right: "auto" } : undefined} onClick={() => setOpen(false)}>
          {children}
        </div>
      ) : null}
    </div>
  );
}

/* ---------- StatCard ---------- */

export function StatCard({ icon, label, value, extra, trend }: { icon: IconName; label: string; value: ReactNode; extra?: ReactNode; trend?: "up" | "down" }) {
  return (
    <div className="card stat-card">
      <span className="stat-label">
        <Icon name={icon} size={15} /> {label}
      </span>
      <span className={`stat-value${trend ? ` stat-trend-${trend}` : ""}`}>{value}</span>
      {extra ? <span className="stat-extra">{extra}</span> : null}
    </div>
  );
}

/* ---------- Toast ---------- */

interface ToastItem {
  id: number;
  kind: "success" | "error" | "info";
  text: string;
}

const ToastContext = createContext<(kind: ToastItem["kind"], text: string) => void>(() => undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const idRef = useRef(0);
  const timersRef = useRef<number[]>([]);

  useEffect(
    () => () => {
      timersRef.current.forEach((timer) => clearTimeout(timer));
    },
    [],
  );

  const push = useCallback((kind: ToastItem["kind"], text: string) => {
    const id = ++idRef.current;
    setItems((prev) => [...prev.slice(-4), { id, kind, text }]);
    const timer = window.setTimeout(() => setItems((prev) => prev.filter((item) => item.id !== id)), 3200);
    timersRef.current.push(timer);
  }, []);

  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast-container" aria-live="polite">
        {items.map((item) => (
          <div key={item.id} className={`toast toast-${item.kind}`}>
            <Icon name={item.kind === "success" ? "check" : item.kind === "error" ? "alert" : "bell"} size={15} />
            <span>{item.text}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
