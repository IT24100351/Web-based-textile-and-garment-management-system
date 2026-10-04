import type { HTMLAttributes } from "react";

import { cn } from "./ui/cn";

/**
 * Shared theme-aware background for non-overview application pages.
 * The artwork is decorative and rendered through CSS so it stays outside the
 * accessibility tree and does not compete with page content.
 */
export function WorkspaceBackground({
  children,
  className,
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("workspace-themed-background", className)} {...props}>
      <div className="relative z-10">{children}</div>
    </div>
  );
}
