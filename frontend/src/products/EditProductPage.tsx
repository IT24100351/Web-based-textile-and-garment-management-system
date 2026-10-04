import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import {
  deleteProduct,
  getProduct,
  getProductApiError,
  updateProduct,
  type CreateProductAvailability,
} from "../api/products";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import {
  getProductDetailPagePath,
  parseProductPageId,
  productCatalogPagePath,
  productManagementPagePath,
} from "../navigation/navigation";
import {
  normalizeProductForm,
  validateProductForm,
  type ProductField,
  type ProductFieldErrors,
  type ProductFormInput,
} from "./productForm";
import { ProductImage } from "./ProductImage";
import type { GarmentProductDetails, ProductVariant } from "./productTypes";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

function formFromProduct(
  product: GarmentProductDetails,
  variant: ProductVariant,
): ProductFormInput {
  return {
    name: product.name,
    category: product.category.name,
    description: product.description ?? "",
    imageUrl: product.imageUrl ?? "",
    size: variant.size,
    color: variant.color,
    price: variant.price,
    availability: variant.status as CreateProductAvailability,
    status: product.status,
  };
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span className="mt-2 block text-sm text-danger" id={id}>
      {message}
    </span>
  ) : null;
}

function EditProductNotFoundState() {
  return (
    <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <EmptyState
        action={(
          <Link
            className="inline-flex rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground transition hover:border-primary hover:text-foreground"
            to={productCatalogPagePath}
          >
            Back to garment catalog
          </Link>
        )}
        message="The requested product could not be found for maintenance."
        title="Product not found"
      />
    </div>
  );
}

function EditProductContent({ productId }: { productId: number }) {
  const navigate = useNavigate();
  const [product, setProduct] = useState<GarmentProductDetails | null>(null);
  const [selectedVariantId, setSelectedVariantId] = useState<number | null>(null);
  const [form, setForm] = useState<ProductFormInput | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ProductFieldErrors>({});
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isNotFound, setIsNotFound] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [navigateAfterDelete, setNavigateAfterDelete] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    void getProduct(productId, controller.signal)
      .then((storedProduct) => {
        if (controller.signal.aborted) {
          return;
        }
        const firstEditableVariant = storedProduct.variants[0];
        setProduct(storedProduct);
        if (firstEditableVariant) {
          setSelectedVariantId(firstEditableVariant.id);
          setForm(formFromProduct(storedProduct, firstEditableVariant));
        } else {
          setSelectedVariantId(null);
          setForm(null);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          const apiError = getProductApiError(
            error,
            "The product could not be loaded for editing. Please try again.",
          );
          if (apiError.status === 404 || apiError.code === "PRODUCT_NOT_FOUND") {
            setIsNotFound(true);
          } else {
            setLoadError(apiError.message);
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

  function retryLoad() {
    setIsLoading(true);
    setLoadError(null);
    setIsNotFound(false);
    setRequestVersion((current) => current + 1);
  }

  function updateField<Field extends ProductField>(
    field: Field,
    value: ProductFormInput[Field],
  ) {
    setForm((current) => current ? { ...current, [field]: value } : current);
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSuccessMessage(null);
  }

  function selectVariant(variantId: number) {
    if (!product) {
      return;
    }
    const variant = product.variants.find((candidate) => candidate.id === variantId);
    if (!variant) {
      return;
    }
    setSelectedVariantId(variant.id);
    setForm(formFromProduct(product, variant));
    setFieldErrors({});
    setSubmissionError(null);
    setSuccessMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);
    setSuccessMessage(null);
    if (!form || selectedVariantId === null) {
      return;
    }

    const errors = validateProductForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await updateProduct(productId, {
        variantId: selectedVariantId,
        ...normalizeProductForm(form),
      });
      const updatedVariant = result.product.variants.find(
        (variant) => variant.id === selectedVariantId,
      );
      setProduct(result.product);
      if (updatedVariant) {
        setForm(formFromProduct(result.product, updatedVariant));
      }
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getProductApiError(
        error,
        "The garment product could not be updated. Please try again.",
      );
      setSubmissionError(apiError.message);
      setFieldErrors(apiError.fields as ProductFieldErrors);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function confirmDelete() {
    if (isDeleting) return;
    setDeleteError(null);
    setIsDeleting(true);
    try {
      await deleteProduct(productId);
      setNavigateAfterDelete(true);
      setIsConfirmingDelete(false);
    } catch (error: unknown) {
      const apiError = getProductApiError(
        error,
        "The garment product could not be deleted. Please try again.",
      );
      setDeleteError(apiError.status === 404
        ? "Product no longer exists."
        : apiError.message);
    } finally {
      setIsDeleting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          message="Fetching the current product and variant values."
          title="Loading product editor"
        />
      </div>
    );
  }

  if (isNotFound) {
    return <EditProductNotFoundState />;
  }

  if (loadError || !product) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          action={(
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              onClick={retryLoad}
              type="button"
            >
              Retry
            </button>
          )}
          message={loadError ?? "The product could not be loaded for editing."}
          title="Product editor unavailable"
        />
      </div>
    );
  }

  const editableVariants = product.variants;
  if (!form || selectedVariantId === null || editableVariants.length === 0) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <EmptyState
          action={(
            <Link
              className="inline-flex rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground"
              to={productCatalogPagePath}
            >
              Back to garment catalog
            </Link>
          )}
          message="This product has no variants available for maintenance."
          title="No product variants"
        />
      </div>
    );
  }

  const isPubliclyVisible = product.status === "ACTIVE"
    && product.category.status === "ACTIVE"
    && product.variants.some((variant) => variant.status === "AVAILABLE");

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Garment Product Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Edit garment product
        </h1>
        <p className="mt-3 text-muted">
          Product ID <span className="font-semibold text-foreground">{product.id}</span> remains
          unchanged. Select a variant to maintain its current values.
        </p>

        <form
          className="mt-8 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8"
          noValidate
          onSubmit={handleSubmit}
        >
          {editableVariants.length > 1 ? (
            <label className="mb-6 block text-sm font-medium text-foreground" htmlFor="edit-variant">
              Variant to edit
              <select
                className={inputClassName}
                id="edit-variant"
                onChange={(event) => selectVariant(Number(event.target.value))}
                value={selectedVariantId}
              >
                {editableVariants.map((variant) => (
                  <option key={variant.id} value={variant.id}>
                    {variant.size} · {variant.color} · {variant.price}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-name">
              Product name
              <input
                aria-describedby={fieldErrors.name ? "edit-product-name-error" : undefined}
                aria-invalid={Boolean(fieldErrors.name)}
                autoComplete="off"
                className={inputClassName}
                id="edit-product-name"
                maxLength={160}
                onChange={(event) => updateField("name", event.target.value)}
                value={form.name}
              />
              <FieldError id="edit-product-name-error" message={fieldErrors.name} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-category">
              Category
              <input
                aria-describedby={fieldErrors.category ? "edit-product-category-error" : undefined}
                aria-invalid={Boolean(fieldErrors.category)}
                autoComplete="off"
                className={inputClassName}
                id="edit-product-category"
                maxLength={100}
                onChange={(event) => updateField("category", event.target.value)}
                value={form.category}
              />
              <FieldError id="edit-product-category-error" message={fieldErrors.category} />
            </label>

            <label className="block text-sm font-medium text-foreground sm:col-span-2" htmlFor="edit-product-description">
              Description <span className="font-normal text-muted">(optional)</span>
              <textarea
                aria-describedby={fieldErrors.description ? "edit-product-description-error" : undefined}
                aria-invalid={Boolean(fieldErrors.description)}
                className={`${inputClassName} min-h-28 resize-y`}
                id="edit-product-description"
                maxLength={2000}
                onChange={(event) => updateField("description", event.target.value)}
                value={form.description ?? ""}
              />
              <FieldError id="edit-product-description-error" message={fieldErrors.description} />
            </label>

            <label className="block text-sm font-medium text-foreground sm:col-span-2" htmlFor="edit-product-image-url">
              Product image URL or path <span className="font-normal text-muted">(optional)</span>
              <input
                aria-describedby={fieldErrors.imageUrl ? "edit-product-image-url-error" : "edit-product-image-url-help"}
                aria-invalid={Boolean(fieldErrors.imageUrl)}
                autoComplete="off"
                className={inputClassName}
                id="edit-product-image-url"
                inputMode="url"
                maxLength={500}
                onChange={(event) => updateField("imageUrl", event.target.value)}
                placeholder="https://… or /products/your-image.jpg"
                value={form.imageUrl ?? ""}
              />
              <span className="mt-2 block text-xs text-muted" id="edit-product-image-url-help">
                Leave empty to remove the current product picture.
              </span>
              <FieldError id="edit-product-image-url-error" message={fieldErrors.imageUrl} />
            </label>

            <ProductImage
              alt={`${product.name} image preview`}
              className="aspect-[16/9] w-full rounded-2xl border border-border sm:col-span-2"
              loading="eager"
              productName={product.name}
              src={form.imageUrl}
            />

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-size">
              Size
              <input
                aria-describedby={fieldErrors.size ? "edit-product-size-error" : undefined}
                aria-invalid={Boolean(fieldErrors.size)}
                autoComplete="off"
                className={inputClassName}
                id="edit-product-size"
                maxLength={32}
                onChange={(event) => updateField("size", event.target.value)}
                value={form.size}
              />
              <FieldError id="edit-product-size-error" message={fieldErrors.size} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-color">
              Color
              <input
                aria-describedby={fieldErrors.color ? "edit-product-color-error" : undefined}
                aria-invalid={Boolean(fieldErrors.color)}
                autoComplete="off"
                className={inputClassName}
                id="edit-product-color"
                maxLength={64}
                onChange={(event) => updateField("color", event.target.value)}
                value={form.color}
              />
              <FieldError id="edit-product-color-error" message={fieldErrors.color} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-price">
              Price
              <input
                aria-describedby={fieldErrors.price ? "edit-product-price-error" : undefined}
                aria-invalid={Boolean(fieldErrors.price)}
                className={inputClassName}
                id="edit-product-price"
                inputMode="decimal"
                onChange={(event) => updateField("price", event.target.value)}
                value={form.price}
              />
              <FieldError id="edit-product-price-error" message={fieldErrors.price} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-availability">
              Availability
              <select
                className={inputClassName}
                id="edit-product-availability"
                onChange={(event) => updateField(
                  "availability",
                  event.target.value as CreateProductAvailability,
                )}
                value={form.availability}
              >
                <option value="AVAILABLE">Available</option>
                <option value="UNAVAILABLE">Unavailable</option>
              </select>
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="edit-product-status">
              Product status
              <select
                className={inputClassName}
                id="edit-product-status"
                onChange={(event) => updateField(
                  "status",
                  event.target.value as ProductFormInput["status"],
                )}
                value={form.status}
              >
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
              <span className="mt-2 block text-xs text-muted">
                Use Inactive to stop selling without deleting historical product data.
              </span>
            </label>
          </div>

          {submissionError ? (
            <p
              className="mt-6 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger"
              role="alert"
            >
              {submissionError}
            </p>
          ) : null}
          {successMessage ? (
            <p
              aria-live="polite"
              className="mt-6 rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success"
            >
              {successMessage} Product ID {product.id} and variant ID {selectedVariantId} were preserved.
            </p>
          ) : null}

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <button
              className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? "Updating product…" : "Update garment product"}
            </button>
            <Link
              className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground transition hover:border-primary"
              to={productCatalogPagePath}
            >
              Back to catalog
            </Link>
            {isPubliclyVisible ? (
              <Link
                className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground transition hover:border-primary"
                to={getProductDetailPagePath(product.id)}
              >
                View updated details
              </Link>
            ) : null}
          </div>
        </form>

        <section className="mt-8 rounded-3xl border border-danger-border bg-danger-soft p-6 sm:p-8">
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-danger">
            Permanent action
          </p>
          <h2 className="mt-2 text-2xl font-bold text-foreground">Delete product</h2>
          <p className="mt-3 max-w-2xl leading-7 text-foreground-muted">
            Permanently delete this product only when it has never been used in an order or
            quotation. Use INACTIVE when a product should remain stored but stop being sold.
          </p>

          <button
            className="mt-6 rounded-xl border border-danger-border px-5 py-3 font-semibold text-danger transition hover:bg-danger-soft"
            onClick={() => {
              setDeleteError(null);
              setNavigateAfterDelete(false);
              setIsConfirmingDelete(true);
            }}
            type="button"
          >
            Delete product
          </button>
        </section>
        <ConfirmDialog
          cancelLabel="Cancel"
          confirmLabel="Delete"
          description="Are you sure you want to permanently delete this product? Products already used in orders or quotations cannot be deleted."
          error={deleteError}
          isBusy={isDeleting}
          isOpen={isConfirmingDelete}
          onAfterClose={() => {
            if (navigateAfterDelete) navigate(productManagementPagePath, { replace: true });
          }}
          onCancel={() => {
            setNavigateAfterDelete(false);
            setIsConfirmingDelete(false);
            setDeleteError(null);
          }}
          onConfirm={() => void confirmDelete()}
          title="Delete product?"
        />
      </div>
    </section>
  );
}

export function EditProductPage() {
  const { productId: productIdParameter } = useParams();
  const productId = parseProductPageId(productIdParameter);

  return productId === null
    ? <EditProductNotFoundState />
    : <EditProductContent key={productId} productId={productId} />;
}
