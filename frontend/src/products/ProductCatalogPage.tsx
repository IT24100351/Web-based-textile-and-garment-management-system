import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  getCatalogProducts,
  getProductApiError,
  type ProductCatalogFilters,
} from "../api/products";
import { useAuth } from "../auth/useAuth";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import {
  getCustomerPlaceOrderWithItemPath,
  getProductDetailPagePath,
} from "../navigation/navigation";
import { ProductImage } from "./ProductImage";
import type { GarmentProductDetails, VariantStatus } from "./productTypes";

interface CatalogFilterForm {
  search: string;
  category: string;
  size: string;
  color: string;
  availability: "" | VariantStatus;
}

const emptyFilterForm: CatalogFilterForm = {
  search: "",
  category: "",
  size: "",
  color: "",
  availability: "",
};

const filterInputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

function buildCatalogFilters(form: CatalogFilterForm): ProductCatalogFilters {
  const filters: ProductCatalogFilters = {};
  const search = form.search.trim();
  const category = form.category.trim();
  const size = form.size.trim();
  const color = form.color.trim();

  if (search) filters.search = search;
  if (category) filters.category = category;
  if (size) filters.size = size;
  if (color) filters.color = color;
  if (form.availability) filters.availability = form.availability;
  return filters;
}

function hasCatalogFilters(filters: ProductCatalogFilters) {
  return Object.keys(filters).length > 0;
}

function ProductCard({
  canAddToCart,
  product,
}: {
  canAddToCart: boolean;
  product: GarmentProductDetails;
}) {
  const availableVariants = product.variants.filter((variant) => variant.status === "AVAILABLE");
  const [selectedVariantId, setSelectedVariantId] = useState(
    availableVariants[0] ? String(availableVariants[0].id) : "",
  );
  const selectedVariant = availableVariants.find(
    (variant) => String(variant.id) === selectedVariantId,
  ) ?? availableVariants[0];
  const sizes = [...new Set(availableVariants.map((variant) => variant.size))];
  const colors = [...new Set(availableVariants.map((variant) => variant.color))];
  const sortedByPrice = [...availableVariants].sort(
    (first, second) => Number(first.price) - Number(second.price),
  );
  const minimumPrice = sortedByPrice[0]?.price;
  const maximumPrice = sortedByPrice.at(-1)?.price;

  return (
    <li className="motion-interactive-card group overflow-hidden rounded-3xl border border-border bg-surface app-shadow transition hover:-translate-y-1 hover:border-primary/40">
      <ProductImage
        alt={`${product.name} product photograph`}
        className="aspect-[4/3] w-full border-b border-border transition-transform duration-500 ease-[var(--ease-emphasized)] group-hover:scale-[1.015]"
        productName={product.name}
        src={product.imageUrl}
      />

      <div className="px-6 py-6">
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{product.category.name}</p>
        <h2 className="mt-2 text-2xl font-bold tracking-tight text-foreground">{product.name}</h2>
        {product.description ? <p className="mt-3 line-clamp-2 leading-6 text-foreground-muted">{product.description}</p> : null}
        {minimumPrice ? (
          <p className="mt-4 text-xl font-bold text-primary">
            LKR {minimumPrice === maximumPrice ? minimumPrice : `${minimumPrice} – ${maximumPrice}`}
          </p>
        ) : null}

        <div className="mt-5 grid gap-4 rounded-2xl border border-border bg-background/60 p-4 sm:grid-cols-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Sizes</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {sizes.map((size) => <span className="rounded-full border border-border bg-surface px-3 py-1 text-sm font-semibold text-foreground" key={size}>{size}</span>)}
            </div>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Colours</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {colors.map((color) => <span className="rounded-full border border-border bg-surface px-3 py-1 text-sm text-foreground" key={color}>{color}</span>)}
            </div>
          </div>
        </div>

        {selectedVariant ? (
          <div className="mt-5 rounded-2xl border border-primary/40 bg-primary-soft p-4">
            <label className="text-sm font-semibold text-foreground" htmlFor={`cart-variant-${product.id}`}>
              Choose size and colour
              <select
                className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2.5 text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                id={`cart-variant-${product.id}`}
                onChange={(event) => setSelectedVariantId(event.target.value)}
                value={String(selectedVariant.id)}
              >
                {availableVariants.map((variant) => (
                  <option key={variant.id} value={variant.id}>
                    {variant.size} · {variant.color} · LKR {variant.price}
                  </option>
                ))}
              </select>
            </label>
            <div className="mt-3 flex items-center justify-between gap-3 text-sm">
              <span className="font-medium text-foreground">{selectedVariant.size} · {selectedVariant.color}</span>
              <span className="font-bold text-primary">Price {selectedVariant.price}</span>
            </div>
            {canAddToCart ? (
              <Link
                aria-label={`Add ${product.name} to cart`}
                className="mt-3 flex w-full justify-center rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                to={getCustomerPlaceOrderWithItemPath(product.id, selectedVariant.id)}
              >
                Add to cart
              </Link>
            ) : null}
          </div>
        ) : null}
        <Link
          className="mt-5 inline-flex rounded-full border border-primary/50 px-4 py-2 text-sm font-semibold text-primary transition hover:border-primary hover:text-foreground"
          to={getProductDetailPagePath(product.id)}
        >
          View product details
        </Link>
      </div>
    </li>
  );
}

export function ProductCatalogPage() {
  const { user } = useAuth();
  const [filterForm, setFilterForm] = useState<CatalogFilterForm>(emptyFilterForm);
  const [appliedFilters, setAppliedFilters] = useState<ProductCatalogFilters>({});
  const [products, setProducts] = useState<GarmentProductDetails[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    void getCatalogProducts(appliedFilters, controller.signal)
      .then((catalogProducts) => {
        if (!controller.signal.aborted) {
          setProducts(catalogProducts);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setErrorMessage(getProductApiError(
            error,
            "The garment catalog could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [appliedFilters, requestVersion]);

  function updateFilter<Field extends keyof CatalogFilterForm>(
    field: Field,
    value: CatalogFilterForm[Field],
  ) {
    setFilterForm((current) => ({ ...current, [field]: value }));
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsLoading(true);
    setErrorMessage(null);
    setAppliedFilters(buildCatalogFilters(filterForm));
  }

  function clearFilters() {
    setFilterForm(emptyFilterForm);
    setIsLoading(true);
    setErrorMessage(null);
    setAppliedFilters({});
  }

  function retryCatalog() {
    setIsLoading(true);
    setErrorMessage(null);
    setRequestVersion((current) => current + 1);
  }

  const filtersAreActive = hasCatalogFilters(appliedFilters);

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Garment Product Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Garment catalog
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Browse active garments with variants that are currently available. Prices are
          shown exactly as recorded by the organization.
        </p>

        <form
          className="mt-8 rounded-3xl border border-border bg-surface/75 p-5 sm:p-6"
          onSubmit={applyFilters}
        >
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <label className="text-sm font-medium text-foreground" htmlFor="catalog-search">
              Product name
              <input
                className={filterInputClassName}
                id="catalog-search"
                maxLength={160}
                onChange={(event) => updateFilter("search", event.target.value)}
                placeholder="e.g. Classic Tee"
                type="search"
                value={filterForm.search}
              />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="catalog-category">
              Category
              <input
                className={filterInputClassName}
                id="catalog-category"
                maxLength={100}
                onChange={(event) => updateFilter("category", event.target.value)}
                placeholder="Exact category"
                value={filterForm.category}
              />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="catalog-size">
              Size
              <input
                className={filterInputClassName}
                id="catalog-size"
                maxLength={32}
                onChange={(event) => updateFilter("size", event.target.value)}
                placeholder="e.g. M"
                value={filterForm.size}
              />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="catalog-color">
              Color
              <input
                className={filterInputClassName}
                id="catalog-color"
                maxLength={64}
                onChange={(event) => updateFilter("color", event.target.value)}
                placeholder="e.g. Navy"
                value={filterForm.color}
              />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="catalog-availability">
              Availability
              <select
                className={filterInputClassName}
                id="catalog-availability"
                onChange={(event) => updateFilter(
                  "availability",
                  event.target.value as CatalogFilterForm["availability"],
                )}
                value={filterForm.availability}
              >
                <option value="">Any permitted status</option>
                <option value="AVAILABLE">Available</option>
                <option value="UNAVAILABLE">Unavailable</option>
                <option value="DISCONTINUED">Discontinued</option>
              </select>
            </label>
          </div>
          <p className="mt-4 text-xs leading-5 text-muted">
            Category, size, and color use exact case-insensitive matches. Public catalog
            permissions still hide unavailable and discontinued variants.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              type="submit"
            >
              Search catalog
            </button>
            <button
              className="rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground transition hover:border-border hover:text-foreground"
              onClick={clearFilters}
              type="button"
            >
              Clear filters
            </button>
          </div>
        </form>

        <div className="mt-8">
          {isLoading ? (
            <LoadingState
              message="Fetching the garments and variants that match the current filters."
              title="Loading garment catalog"
            />
          ) : errorMessage ? (
            <ErrorState
              action={(
                <button
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  onClick={retryCatalog}
                  type="button"
                >
                  Retry
                </button>
              )}
              message={errorMessage}
              title="Catalog unavailable"
            />
          ) : products.length === 0 ? (
            <EmptyState
              action={filtersAreActive ? (
                <button
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  onClick={clearFilters}
                  type="button"
                >
                  Clear filters
                </button>
              ) : undefined}
              message={filtersAreActive
                ? "No publicly available garments match all of the selected filters."
                : "There are no active garments with available size and color options right now."}
              title={filtersAreActive ? "No matching garments" : "No garments available"}
            />
          ) : (
            <>
              <p aria-live="polite" className="text-sm text-muted">
                {products.length} {products.length === 1 ? "product" : "products"} found
              </p>
              <ul className="motion-stagger mt-5 grid gap-6 md:grid-cols-2">
                {products.map((product) => (
                  <ProductCard
                    canAddToCart={user?.role === "CUSTOMER"}
                    key={product.id}
                    product={product}
                  />
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
