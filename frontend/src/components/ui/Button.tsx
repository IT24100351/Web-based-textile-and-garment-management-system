import { LoaderCircle } from "lucide-react";
import { forwardRef, type ButtonHTMLAttributes } from "react";

import { buttonStyles, type ButtonSize, type ButtonVariant } from "./buttonStyles";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  isLoading?: boolean;
  size?: ButtonSize;
  variant?: ButtonVariant;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({
  children,
  className,
  disabled,
  isLoading = false,
  size = "default",
  type = "button",
  variant = "primary",
  ...props
}, ref) {
  return (
    <button
      aria-busy={isLoading || undefined}
      className={buttonStyles({ className, size, variant })}
      data-loading={isLoading || undefined}
      disabled={disabled || isLoading}
      ref={ref}
      type={type}
      {...props}
    >
      {isLoading ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin motion-reduce:animate-none" /> : null}
      <span className="transition-opacity duration-[var(--motion-fast)]">{children}</span>
    </button>
  );
});
