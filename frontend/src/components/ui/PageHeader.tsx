import type { ReactNode } from "react";

import { cn } from "./cn";

export function PageHeader({
  actions,
  className,
  description,
  eyebrow,
  title,
}: {
  actions?: ReactNode;
  className?: string;
  description?: ReactNode;
  eyebrow?: string;
  title: ReactNode;
}) {
  return (
    <header className={cn("flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between", className)}>
      <div className="min-w-0">
        {eyebrow ? <p className="text-sm font-semibold tracking-wide text-primary">{eyebrow}</p> : null}
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">{title}</h1>
        {description ? (
          <div className="mt-3 max-w-3xl text-sm leading-6 text-foreground-muted sm:text-base">{description}</div>
        ) : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap gap-3">{actions}</div> : null}
    </header>
  );
}
