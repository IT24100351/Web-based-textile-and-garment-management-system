import type { ReactNode } from "react";

export function FormField({
  children,
  description,
  error,
  htmlFor,
  label,
  required,
}: {
  children: ReactNode;
  description?: string;
  error?: string;
  htmlFor?: string;
  label: ReactNode;
  required?: boolean;
}) {
  return (
    <div>
      <label className="block text-sm font-semibold text-foreground" htmlFor={htmlFor}>
        {label}
        {required ? <span className="ml-1 text-danger" aria-hidden="true">*</span> : null}
      </label>
      {description ? <p className="mt-1 text-xs leading-5 text-muted">{description}</p> : null}
      {children}
      {error ? <p className="mt-2 text-sm text-danger" role="alert">{error}</p> : null}
    </div>
  );
}
