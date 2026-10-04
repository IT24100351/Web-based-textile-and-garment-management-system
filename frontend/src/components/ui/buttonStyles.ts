import { cn } from "./cn";

export type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "danger" | "success";
export type ButtonSize = "sm" | "default" | "lg" | "icon";

const variants: Record<ButtonVariant, string> = {
  primary: "border-primary bg-primary text-primary-foreground hover:bg-primary-hover",
  secondary: "border-border bg-surface-elevated text-foreground hover:border-border-strong hover:bg-surface-muted",
  outline: "border-border-strong bg-transparent text-foreground hover:border-primary hover:bg-primary-soft hover:text-primary",
  ghost: "border-transparent bg-transparent text-foreground-muted hover:bg-surface-muted hover:text-foreground",
  danger: "border-danger bg-danger text-primary-foreground hover:brightness-110",
  success: "border-success bg-success text-primary-foreground hover:brightness-110",
};

const sizes: Record<ButtonSize, string> = {
  sm: "min-h-9 rounded-lg px-3 text-sm",
  default: "min-h-11 rounded-xl px-4 text-sm",
  lg: "min-h-12 rounded-xl px-6 text-base",
  icon: "h-10 w-10 rounded-xl p-0",
};

export function buttonStyles({
  className,
  size = "default",
  variant = "primary",
}: {
  className?: string;
  size?: ButtonSize;
  variant?: ButtonVariant;
} = {}) {
  return cn(
    "inline-flex shrink-0 items-center justify-center gap-2 border font-semibold",
    size !== "icon" && "motion-button",
    "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-55",
    variants[variant],
    sizes[size],
    className,
  );
}
