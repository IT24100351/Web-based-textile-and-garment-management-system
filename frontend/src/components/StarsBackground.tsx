import type { HTMLAttributes } from "react";

import { cn } from "./ui/cn";

export function StarsBackground({ children, className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn("stars-background", className)} {...props}>
      <div aria-hidden="true" className="stars-field">
        <span className="stars-layer stars-layer-near" />
        <span className="stars-layer stars-layer-middle" />
        <span className="stars-layer stars-layer-far" />
      </div>
      <div className="relative z-10">{children}</div>
    </div>
  );
}
