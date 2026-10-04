import type { ComponentProps } from "react";
import { Link } from "react-router-dom";

import { cn } from "./cn";

type HeroActionTone = "primary" | "secondary" | "quiet";

type HeroActionLinkProps = ComponentProps<typeof Link> & {
  tone?: HeroActionTone;
};

export function HeroActionLink({
  children,
  className,
  tone = "primary",
  ...props
}: HeroActionLinkProps) {
  return (
    <Link
      className={cn(
        "hero-action inline-flex min-h-12 shrink-0 items-center justify-center rounded-2xl border px-6 text-base font-semibold",
        `hero-action-${tone}`,
        className,
      )}
      {...props}
    >
      <span className="hero-action-label">{children}</span>
    </Link>
  );
}
