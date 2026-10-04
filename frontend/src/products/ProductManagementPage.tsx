import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  deleteProduct,
  getManagedProducts,
  getProductApiError,
  type ProductManagementFilters,
} from "../api/products";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { MotionSwap } from "../motion/MotionSwap";
import { motionDurations } from "../motion/motionTokens";
import { usePresence } from "../motion/usePresence";
import {
  getEditProductPagePath,
  newProductPagePath,
} from "../navigation/navigation";
import { ProductImage } from "./ProductImage";
import type { GarmentProductDetails, ProductStatus } from "./productTypes";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

function ManagedProductCard({
  isRemoving,
  onDelete,
  onRemoved,
  product,
}: {
  isRemoving: boolean;
  onDelete: () => void;
  onRemoved: () => void;
  product: GarmentProductDetails;
}) {
  const presence = usePresence(!isRemoving, {
    exitDuration: motionDurations.smooth,
    onExitComplete: onRemoved,
  });

  if (!presence.isMounted) return null;

  return (
    <li
      className="motion-removable"
      data-motion-state={presence.motionState}
      inert={presence.motionState === "closed"}
      onTransitionEnd={presence.onTransitionEnd}
    >
      <div className="motion-removable-inner motion-record rounded-3xl border border-border bg-surface/70 p-5">
        <div className="flex gap-4">
          <ProductImage alt={`${product.name} product`} className="h-24 w-24 shrink-0 rounded-2xl border border-border object-cover" productName={product.name} src={product.imageUrl} />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-bold text-foreground">{product.name}</h2>
              <span className="motion-status-badge rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-foreground">{product.status}</span>
            </div>
            <p className="mt-1 text-sm text-muted">{product.category.name} · Product #{product.id}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {product.variants.map((variant) => (
                <span className="motion-status-badge rounded-full border border-border bg-background px-3 py-1 text-xs text-foreground" key={variant.id}>
                  {variant.size} · {variant.color} · {variant.status}
                </span>
              ))}
            </div>
          </div>
        </div>
        <div className="mt-5 flex flex-wrap gap-3">
          <Link className="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground" to={getEditProductPagePath(product.id)}>Edit</Link>
          <button className="rounded-xl border border-danger-border px-4 py-2 text-sm font-semibold text-danger" onClick={onDelete} type="button">Delete</button>
        </div>
      </div>
    </li>
  );
}

export function ProductManagementPage() {
  const [products, setProducts] = useState<GarmentProductDetails[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<"" | ProductStatus>("");
  const [filters, setFilters] = useState<ProductManagementFilters>({});
  const [isLoading, setIsLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<GarmentProductDetails | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [removingProductId, setRemovingProductId] = useState<number | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);
    void getManagedProducts(filters, controller.signal)
      .then(setProducts)
      .catch((requestError: unknown) => {
        if (!controller.signal.aborted) {
          setError(getProductApiError(
            requestError,
            "Product management records could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
          setHasLoaded(true);
        }
      });
    return () => controller.abort();
  }, [filters, reloadKey]);

  function submitFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: ProductManagementFilters = {};
    if (search.trim()) next.search = search.trim();
    if (status) next.status = status;
    setFilters(next);
  }

  function clearFilters() {
    setSearch("");
    setStatus("");
    setFilters({});
  }

  async function confirmDelete() {
    if (!deleteTarget || isDeleting) return;
    setDeleteError(null);
    setIsDeleting(true);
    try {
      await deleteProduct(deleteTarget.id);
      const deletedProductId = deleteTarget.id;
      setDeleteTarget(null);
      setRemovingProductId(deletedProductId);
    } catch (requestError: unknown) {
      const apiError = getProductApiError(
        requestError,
        "The garment product could not be deleted. Please try again.",
      );
      setDeleteError(apiError.status === 404 ? "Product no longer exists." : apiError.message);
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Sales Officer</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Product management</h1>
            <p className="mt-3 max-w-3xl text-muted">
              Maintain ACTIVE and INACTIVE products and all AVAILABLE or UNAVAILABLE variants without exposing hidden catalogue records to customers.
            </p>
          </div>
          <Link className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground" to={newProductPagePath}>
            Add product
          </Link>
        </div>

        <form className="mt-8 rounded-3xl border border-border bg-surface/70 p-5" onSubmit={submitFilters}>
          <div className="grid gap-4 md:grid-cols-[1fr_220px_auto] md:items-end">
            <label className="text-sm font-medium text-foreground" htmlFor="management-product-search">
              Search
              <input className={inputClassName} id="management-product-search" maxLength={160} onChange={(event) => setSearch(event.target.value)} placeholder="Product or category" value={search} />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="management-product-status">
              Product status
              <select className={inputClassName} id="management-product-status" onChange={(event) => setStatus(event.target.value as "" | ProductStatus)} value={status}>
                <option value="">All statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </label>
            <div className="flex gap-2">
              <button className="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground" type="submit">Search</button>
              <button className="rounded-xl border border-border px-5 py-2.5 text-sm font-semibold text-foreground" onClick={clearFilters} type="button">Clear</button>
            </div>
          </div>
        </form>

        <div aria-busy={isLoading} className="mt-8">
          <MotionSwap stateKey={!hasLoaded ? "loading" : error ? "error" : products.length === 0 ? "empty" : "content"}>
            {!hasLoaded ? (
              <LoadingState message="Loading staff product records." title="Loading product management" />
            ) : error ? (
              <ErrorState action={<button className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground" onClick={() => setReloadKey((value) => value + 1)} type="button">Retry</button>} message={error} title="Product management unavailable" />
            ) : products.length === 0 ? (
              <EmptyState message="No products match the current management filters." title="No products found" />
            ) : (
              <div>
                {isLoading ? <p className="mb-3 text-sm text-muted" role="status">Updating product results…</p> : null}
                <ul className="grid gap-5 lg:grid-cols-2">
                  {products.map((product) => (
                    <ManagedProductCard
                      isRemoving={removingProductId === product.id}
                      key={product.id}
                      onDelete={() => { setDeleteError(null); setDeleteTarget(product); }}
                      onRemoved={() => {
                        setProducts((current) => current.filter((item) => item.id !== product.id));
                        setRemovingProductId((current) => current === product.id ? null : current);
                      }}
                      product={product}
                    />
                  ))}
                </ul>
              </div>
            )}
          </MotionSwap>
        </div>

        <ConfirmDialog
          cancelLabel="Cancel"
          confirmLabel="Delete"
          description="Are you sure you want to permanently delete this product? Products already used in orders or quotations cannot be deleted."
          error={deleteError}
          isBusy={isDeleting}
          isOpen={deleteTarget !== null}
          onCancel={() => { setDeleteTarget(null); setDeleteError(null); }}
          onConfirm={() => void confirmDelete()}
          title="Delete product?"
        />
      </div>
    </section>
  );
}
