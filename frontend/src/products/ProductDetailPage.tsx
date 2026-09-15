import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { getCatalogProduct, getProductApiError } from "../api/products";
import { useAuth } from "../auth/useAuth";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  getCustomerPlaceOrderWithItemPath,
  getEditProductPagePath,
  parseProductPageId,
  productCatalogPagePath,
} from "../navigation/navigation";
import type { GarmentProductDetails } from "./productTypes";
import { ProductImage } from "./ProductImage";

function BackToCatalogLink() {
  return (
    <Link
      className="inline-flex rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground transition hover:border-primary hover:text-foreground"
      to={productCatalogPagePath}
    >
      Back to garment catalog
    </Link>
  );
}

function ProductNotFoundState() {
  return (
    <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <EmptyState
        action={<BackToCatalogLink />}
        message="This product does not exist or is not currently available in the public catalog."
        title="Product not found"
      />
    </div>
  );
}

function ProductDetailContent({ productId }: { productId: number }) {
  const { user } = useAuth();
  const [product, setProduct] = useState<GarmentProductDetails | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isNotFound, setIsNotFound] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestVersion, setRequestVersion] = useState(0);
  const [selectedVariantId, setSelectedVariantId] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getCatalogProduct(productId, controller.signal)
      .then((catalogProduct) => {
        if (!controller.signal.aborted) {
          setProduct(catalogProduct);
          setSelectedVariantId(String(
            catalogProduct.variants.find((variant) => variant.status === "AVAILABLE")?.id ?? "",
          ));
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          const apiError = getProductApiError(
            error,
            "The product details could not be loaded. Please try again.",
          );
          if (apiError.status === 404 || apiError.code === "PRODUCT_NOT_FOUND") {
            setIsNotFound(true);
          } else {
            setErrorMessage(apiError.message);
          }
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [productId, requestVersion]);

  function retryProduct() {
    setIsLoading(true);
    setErrorMessage(null);
    setIsNotFound(false);
    setRequestVersion((current) => current + 1);
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          message="Fetching the selected garment and its available variants."
          title="Loading product details"
        />
      </div>
    );
  }

  if (isNotFound || !product) {
    if (isNotFound) {
      return <ProductNotFoundState />;
    }
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          action={(
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              onClick={retryProduct}
              type="button"
            >
              Retry
            </button>
          )}
          message={errorMessage ?? "The product details could not be loaded."}
          title="Product details unavailable"
        />
      </div>
    );
  }

  const availableVariants = product.variants.filter((variant) => variant.status === "AVAILABLE");
  const selectedVariant = availableVariants.find(
    (variant) => String(variant.id) === selectedVariantId,
  );

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <BackToCatalogLink />

        <article className="mt-6 overflow-hidden rounded-3xl border border-border bg-surface/75 shadow-xl shadow-black/10">
          <header className="grid border-b border-border bg-gradient-to-br from-primary-soft to-surface lg:grid-cols-[minmax(18rem,0.8fr)_minmax(0,1.2fr)]">
            <ProductImage
              alt={`${product.name} product photograph`}
              className="aspect-square h-full min-h-72 w-full border-b border-border lg:border-b-0 lg:border-r"
              loading="eager"
              productName={product.name}
              src={product.imageUrl}
            />
            <div className="p-7 sm:p-10 lg:self-center">
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
                {product.category.name}
              </p>
              <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
                {product.name}
              </h1>
              <p className="mt-4 max-w-3xl leading-7 text-foreground-muted">
                {product.description ?? "No additional product description is available."}
              </p>
            </div>
          </header>

          <div className="p-7 sm:p-10">
            <dl className="grid gap-4 rounded-2xl border border-border bg-background/60 p-5 sm:grid-cols-3">
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Product ID</dt>
                <dd className="mt-1 font-semibold text-foreground">{product.id}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Category</dt>
                <dd className="mt-1 font-semibold text-foreground">{product.category.name}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-muted">Product status</dt>
                <dd className="mt-1"><StatusBadge label={product.status.toLowerCase()} status={product.status} /></dd>
              </div>
            </dl>

            {product.category.description ? (
              <p className="mt-5 text-sm leading-6 text-muted">
                {product.category.description}
              </p>
            ) : null}

            <h2 className="mt-9 text-2xl font-bold text-foreground">Available options</h2>
            <p className="mt-2 text-muted">
              Compare the recorded size, color, price, and availability for each option.
            </p>
            <ul className="mt-5 grid gap-4 sm:grid-cols-2">
              {product.variants.map((variant) => (
                <li
                  className="rounded-2xl border border-border bg-background/70 p-5"
                  key={variant.id}
                >
                  <h3 className="text-lg font-semibold text-foreground">
                    {variant.size} · {variant.color}
                  </h3>
                  <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                    <div>
                      <dt className="text-muted">Price</dt>
                      <dd className="mt-1 font-semibold text-primary">{variant.price}</dd>
                    </div>
                    <div>
                      <dt className="text-muted">Availability</dt>
                      <dd className="mt-1"><StatusBadge label={variant.status.toLowerCase()} status={variant.status} /></dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ul>

            {user?.role === "CUSTOMER" && selectedVariant ? (
              <section className="mt-7 rounded-2xl border border-primary/40 bg-primary-soft p-5" aria-labelledby="product-cart-heading">
                <h2 className="text-lg font-bold text-foreground" id="product-cart-heading">Add this garment to your order</h2>
                <label className="mt-4 block text-sm font-semibold text-foreground" htmlFor="detail-cart-variant">
                  Choose size and colour
                  <select
                    className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    id="detail-cart-variant"
                    onChange={(event) => setSelectedVariantId(event.target.value)}
                    value={selectedVariantId}
                  >
                    {availableVariants.map((variant) => (
                      <option key={variant.id} value={variant.id}>
                        {variant.size} · {variant.color} · LKR {variant.price}
                      </option>
                    ))}
                  </select>
                </label>
                <Link
                  className="mt-4 inline-flex w-full justify-center rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover sm:w-auto"
                  to={getCustomerPlaceOrderWithItemPath(product.id, selectedVariant.id)}
                >
                  Add to cart
                </Link>
              </section>
            ) : null}

            <div className="mt-9 flex flex-wrap gap-3">
              <BackToCatalogLink />
              {user?.role === "SALES_OFFICER" ? (
                <Link
                  className="inline-flex rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  to={getEditProductPagePath(product.id)}
                >
                  Edit product
                </Link>
              ) : null}
            </div>
          </div>
        </article>
      </div>
    </section>
  );
}

export function ProductDetailPage() {
  const { productId: productIdParameter } = useParams();
  const productId = parseProductPageId(productIdParameter);

  if (productId === null) {
    return <ProductNotFoundState />;
  }
  return <ProductDetailContent key={productId} productId={productId} />;
}
