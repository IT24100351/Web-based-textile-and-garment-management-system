import type { HTMLAttributes } from "react";

import { cn } from "../components/ui/cn";

export function MotionPage({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("motion-page", className)} {...props} />;
}
