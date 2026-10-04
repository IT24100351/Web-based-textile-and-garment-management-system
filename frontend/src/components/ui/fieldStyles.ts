import { cn } from "./cn";

export const fieldStyles = cn(
  "mt-2 min-h-11 w-full rounded-xl border border-border-strong bg-surface-elevated px-3.5 py-2.5",
  "text-foreground outline-none transition placeholder:text-muted focus:border-primary focus:ring-3 focus:ring-primary/15",
  "disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-muted aria-invalid:border-danger aria-invalid:ring-danger/15",
);
