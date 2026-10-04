import type { HTMLAttributes } from "react";

import { cn } from "./cn";

export type BadgeTone = "neutral" | "primary" | "success" | "warning" | "danger" | "info";

const tones: Record<BadgeTone, string> = {
  neutral: "border-border-strong bg-surface-muted text-foreground-muted",
  primary: "border-primary/30 bg-primary-soft text-primary",
  success: "border-success-border bg-success-soft text-success",
  warning: "border-warning-border bg-warning-soft text-warning",
  danger: "border-danger-border bg-danger-soft text-danger",
  info: "border-info-border bg-info-soft text-info",
};

export function Badge({
  className,
  tone = "neutral",
  ...props
}: HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }) {
  return (
    <span
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold transition-colors duration-[var(--motion-fast)] ease-[var(--ease-standard)]",
        tones[tone],
        className,
      )}
      {...props}
    />
  );
}
