import type { HTMLAttributes } from "react";

import { cn } from "./cn";

export function Card({
  className,
  interactive = false,
  ...props
}: HTMLAttributes<HTMLElement> & { interactive?: boolean }) {
  return <section className={cn("app-shadow rounded-2xl border border-border bg-surface", interactive && "motion-interactive-card", className)} {...props} />;
}

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("border-b border-border px-5 py-4 sm:px-6", className)} {...props} />;
}

export function CardContent({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("p-5 sm:p-6", className)} {...props} />;
}
