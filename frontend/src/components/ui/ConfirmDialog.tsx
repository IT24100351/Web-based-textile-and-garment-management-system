import { AlertTriangle, X } from "lucide-react";
import { useCallback, useEffect, useRef } from "react";
import { createPortal } from "react-dom";

import { usePresence } from "../../motion/usePresence";
import { Button } from "./Button";

export function ConfirmDialog({
  cancelLabel = "Keep current state",
  confirmLabel,
  description,
  error,
  isBusy = false,
  isOpen,
  onAfterClose,
  onCancel,
  onConfirm,
  title,
}: {
  cancelLabel?: string;
  confirmLabel: string;
  description: string;
  error?: string | null;
  isBusy?: boolean;
  isOpen: boolean;
  onAfterClose?: () => void;
  onCancel: () => void;
  onConfirm: () => void;
  title: string;
}) {
  const cancelButton = useRef<HTMLButtonElement>(null);
  const previouslyFocusedElement = useRef<HTMLElement | null>(null);
  const wasOpen = useRef(false);
  const handleExitComplete = useCallback(() => {
    const previousFocus = previouslyFocusedElement.current;
    if (previousFocus?.isConnected) previousFocus.focus();
    onAfterClose?.();
  }, [onAfterClose]);
  const presence = usePresence(isOpen, { onExitComplete: handleExitComplete });

  useEffect(() => {
    if (isOpen && !wasOpen.current) {
      previouslyFocusedElement.current = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    }
    wasOpen.current = isOpen;
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !presence.isMounted) return undefined;
    cancelButton.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isBusy) onCancel();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [isBusy, isOpen, onCancel, presence.isMounted]);

  if (!presence.isMounted) return null;

  return createPortal(
    <div
      className="motion-dialog-overlay fixed inset-0 z-[100] grid place-items-center bg-black/55 p-4"
      data-motion-state={presence.motionState}
      inert={presence.motionState === "closed"}
      onTransitionEnd={presence.onTransitionEnd}
      role="presentation"
    >
      <section
        aria-describedby="confirm-dialog-description"
        aria-labelledby="confirm-dialog-title"
        aria-modal="true"
        className="motion-dialog-content app-shadow w-full max-w-md rounded-2xl border border-danger-border bg-surface-elevated p-6"
        role="alertdialog"
      >
        <div className="flex items-start justify-between gap-4">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-danger-soft text-danger">
            <AlertTriangle aria-hidden="true" className="h-5 w-5" />
          </span>
          <button
            aria-label="Close confirmation"
            className="grid h-9 w-9 place-items-center rounded-lg text-muted transition hover:bg-surface-muted hover:text-foreground active:scale-95"
            disabled={isBusy}
            onClick={onCancel}
            type="button"
          >
            <X aria-hidden="true" className="h-4 w-4" />
          </button>
        </div>
        <h2 className="mt-5 text-xl font-bold text-foreground" id="confirm-dialog-title">{title}</h2>
        <p className="mt-2 text-sm leading-6 text-foreground-muted" id="confirm-dialog-description">{description}</p>
        {error ? (
          <p className="mt-4 rounded-xl border border-danger-border bg-danger-soft p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button disabled={isBusy} onClick={onCancel} ref={cancelButton} variant="secondary">{cancelLabel}</Button>
          <Button isLoading={isBusy} onClick={onConfirm} variant="danger">{confirmLabel}</Button>
        </div>
      </section>
    </div>,
    document.body,
  );
}
