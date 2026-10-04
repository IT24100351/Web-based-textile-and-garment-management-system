import { cn } from "./ui/cn";

const markSource = "/brand/lankawear-mark.png";
const lockupSource = "/brand/lankawear-lockup.png";

export function BrandMark({ className }: { className?: string }) {
  return (
    <span className={cn("brand-logo-surface block shrink-0 overflow-hidden rounded-xl border border-border", className)}>
      <img alt="" className="h-full w-full object-cover" src={markSource} />
    </span>
  );
}

export function BrandIdentity({
  className,
  compact = false,
  responsive = false,
}: {
  className?: string;
  compact?: boolean;
  responsive?: boolean;
}) {
  return (
    <span className={cn("flex min-w-0 items-center gap-3", className)}>
      <BrandMark className="h-11 w-12" />
      {!compact ? (
        <span className={cn("min-w-0", responsive && "hidden sm:block")}>
          <strong className="block truncate text-sm font-bold tracking-tight text-foreground">LankaWear Apparel</strong>
          <span className="block truncate text-xs text-muted">Garment operations</span>
        </span>
      ) : null}
    </span>
  );
}

export function BrandLockup({ className }: { className?: string }) {
  return (
    <span className={cn("brand-logo-surface block aspect-[914/670] overflow-hidden rounded-2xl border border-border", className)}>
      <img alt="LankaWear Apparel" className="h-full w-full object-contain" src={lockupSource} />
    </span>
  );
}
