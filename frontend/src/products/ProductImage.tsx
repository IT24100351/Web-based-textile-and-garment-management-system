import { useState } from "react";

interface ProductImageProps {
  alt: string;
  className?: string;
  loading?: "eager" | "lazy";
  productName: string;
  src: string | null | undefined;
}

export function ProductImage({
  alt,
  className = "",
  loading = "lazy",
  productName,
  src,
}: ProductImageProps) {
  const [failedSource, setFailedSource] = useState<string | null>(null);
  const canDisplayImage = Boolean(src) && failedSource !== src;

  if (!canDisplayImage) {
    return (
      <div
        aria-label={`${productName} image unavailable`}
        className={`textile-grid grid place-items-center bg-gradient-to-br from-primary-soft via-surface-muted to-accent-soft font-black text-primary ${className}`}
        role="img"
      >
        <span className="grid h-24 w-24 place-items-center rounded-[1.75rem] border border-primary/20 bg-surface/80 text-3xl shadow-lg">
          {productName.slice(0, 2).toUpperCase()}
        </span>
      </div>
    );
  }

  return (
    <img
      alt={alt}
      className={`object-cover ${className}`}
      decoding="async"
      loading={loading}
      onError={() => setFailedSource(src ?? null)}
      src={src ?? undefined}
    />
  );
}
