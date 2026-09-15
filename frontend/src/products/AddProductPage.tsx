import { useEffect, useState, type FormEvent } from "react";
import { ImagePlus, Plus, Trash2, X } from "lucide-react";

import {
  createProduct,
  getProductApiError,
  uploadProductImage,
  type CreateProductAvailability,
  type CreateProductInput,
  type ProductVariantInput,
} from "../api/products";
import { ProductImage } from "./ProductImage";
import type { GarmentProductDetails } from "./productTypes";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";
const pricePattern = /^\d{1,10}(\.\d{1,2})?$/;
const localProductImagePattern = /^\/products\/(?!.*\.\.)[A-Za-z0-9/_\-.]+$/;
const acceptedImageTypes = new Set(["image/jpeg", "image/png", "image/webp"]);
const maximumImageBytes = 5 * 1024 * 1024;

type VariantField = keyof ProductVariantInput;
type ProductDetailsField = "name" | "category" | "description" | "imageUrl";
type FormErrors = Record<string, string | undefined>;

// Create one blank variant row with the default status used for newly added products.
function emptyVariant(): ProductVariantInput {
  return { size: "", color: "", price: "", availability: "AVAILABLE" };
}

// Keep the initial form shape in a function so resets always get a fresh variant array.
function emptyForm(): CreateProductInput {
  return {
    name: "",
    category: "",
    description: "",
    imageUrl: "",
    variants: [emptyVariant()],
  };
}

// Trim outer whitespace and collapse repeated spaces before values are sent to the backend.
function normalizeText(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

function normalizeForm(form: CreateProductInput, imageUrl?: string): CreateProductInput {
  const description = form.description?.trim();
  const normalizedImageUrl = imageUrl?.trim() || form.imageUrl?.trim();
  return {
    name: normalizeText(form.name),
    category: normalizeText(form.category),
    ...(description ? { description: normalizeText(description) } : {}),
    ...(normalizedImageUrl ? { imageUrl: normalizedImageUrl } : {}),
    variants: form.variants.map((variant) => ({
      size: normalizeText(variant.size),
      color: normalizeText(variant.color),
      price: variant.price.trim(),
      availability: variant.availability,
    })),
  };
}

function validateForm(form: CreateProductInput): FormErrors {
  const errors: FormErrors = {};
  const name = form.name.trim();
  const category = form.category.trim();
  const description = form.description?.trim() ?? "";
  const imageUrl = form.imageUrl?.trim() ?? "";

  if (!name) errors.name = "Product name is required.";
  else if (name.length > 160) errors.name = "Product name must not exceed 160 characters.";
  if (!category) errors.category = "Category is required.";
  else if (category.length > 100) errors.category = "Category must not exceed 100 characters.";
  if (description.length > 2000) errors.description = "Description must not exceed 2000 characters.";
  if (imageUrl.length > 500) {
    errors.imageUrl = "Product image URL must not exceed 500 characters.";
  } else if (
    imageUrl
    && !localProductImagePattern.test(imageUrl)
    && !imageUrl.startsWith("/api/product-images/")
  ) {
    try {
      if (new URL(imageUrl).protocol !== "https:") throw new Error("Invalid protocol");
    } catch {
      errors.imageUrl = "Use an HTTPS image URL or upload a local photo.";
    }
  }

  if (form.variants.length === 0) errors.variants = "Add at least one size and colour option.";
  else if (form.variants.length > 100) errors.variants = "A product can contain at most 100 variants.";

  const combinations = new Set<string>();
  form.variants.forEach((variant, index) => {
    const size = normalizeText(variant.size);
    const color = normalizeText(variant.color);
    if (!size) errors[`variants.${index}.size`] = "Size is required.";
    else if (size.length > 32) errors[`variants.${index}.size`] = "Size must not exceed 32 characters.";
    if (!color) errors[`variants.${index}.color`] = "Color is required.";
    else if (color.length > 64) errors[`variants.${index}.color`] = "Color must not exceed 64 characters.";
    if (!pricePattern.test(variant.price.trim()) || Number(variant.price) <= 0) {
      errors[`variants.${index}.price`] = "Price must be positive with at most 10 digits and 2 decimals.";
    }
    const combination = `${size.toLowerCase()}\u0000${color.toLowerCase()}`;
    if (size && color && combinations.has(combination)) {
      errors[`variants.${index}.color`] = "This size and colour combination is duplicated.";
    }
    combinations.add(combination);
  });
  return errors;
}

function apiErrorsToFormErrors(fields: Record<string, string>): FormErrors {
  const errors: FormErrors = {};
  Object.entries(fields).forEach(([field, message]) => {
    const variantMatch = field.match(/^variants\[(\d+)]\.(size|color|price|availability)$/);
    errors[variantMatch ? `variants.${variantMatch[1]}.${variantMatch[2]}` : field] = message;
  });
  return errors;
}

// Renders field-level validation text only when that input currently has an error.
function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? <span className="mt-2 block text-sm text-danger" id={id}>{message}</span> : null;
}

export function AddProductPage() {
  const [form, setForm] = useState<CreateProductInput>(emptyForm);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [savedProduct, setSavedProduct] = useState<GarmentProductDetails | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submissionStage, setSubmissionStage] = useState<"uploading" | "saving" | null>(null);

  useEffect(() => () => {
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
  }, [imagePreviewUrl]);

  const displayedImage = imagePreviewUrl ?? form.imageUrl;

  function clearError(field: string) {
    // Clear the edited field only, keeping unrelated validation messages visible.
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
  }

  function updateDetailsField(field: ProductDetailsField, value: string) {
    // Update product-level fields such as name, category, description, and image URL.
    setForm((current) => ({ ...current, [field]: value }));
    clearError(field);
  }

  function updateVariant<Field extends VariantField>(
    index: number,
    field: Field,
    value: ProductVariantInput[Field],
  ) {
    // Replace a single variant row immutably so React can re-render the changed input safely.
    setForm((current) => ({
      ...current,
      variants: current.variants.map((variant, variantIndex) => (
        variantIndex === index ? { ...variant, [field]: value } : variant
      )),
    }));
    clearError(`variants.${index}.${field}`);
    clearError("variants");
  }

  function addVariant() {
    // Append another sellable size and colour option without modifying existing rows.
    setForm((current) => ({ ...current, variants: [...current.variants, emptyVariant()] }));
    clearError("variants");
  }

  function removeVariant(index: number) {
    setForm((current) => ({
      ...current,
      variants: current.variants.filter((_, variantIndex) => variantIndex !== index),
    }));
    setFieldErrors((current) => Object.fromEntries(
      Object.entries(current).filter(([field]) => !field.startsWith("variants.")),
    ));
  }

  function chooseImage(file: File | undefined) {
    clearError("imageFile");
    if (!file) return;
    if (!acceptedImageTypes.has(file.type)) {
      setFieldErrors((current) => ({ ...current, imageFile: "Use a JPEG, PNG, or WebP product photo." }));
      return;
    }
    if (file.size > maximumImageBytes) {
      setFieldErrors((current) => ({ ...current, imageFile: "Product photos must be 5 MB or smaller." }));
      return;
    }
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
    setImageFile(file);
    setImagePreviewUrl(URL.createObjectURL(file));
  }

  function removeImageFile() {
    // Remove only the selected upload; an existing URL can still be typed afterward.
    if (imagePreviewUrl) URL.revokeObjectURL(imagePreviewUrl);
    setImageFile(null);
    setImagePreviewUrl(null);
    clearError("imageFile");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);
    const errors = validateForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      let storedImageUrl: string | undefined;
      if (imageFile) {
        setSubmissionStage("uploading");
        storedImageUrl = (await uploadProductImage(imageFile)).imageUrl;
      }
      setSubmissionStage("saving");
      const result = await createProduct(normalizeForm(form, storedImageUrl));
      setSavedProduct(result.product);
    } catch (error: unknown) {
      const apiError = getProductApiError(error, "The garment product could not be saved. Please try again.");
      setSubmissionError(apiError.message);
      setFieldErrors((current) => ({ ...current, ...apiErrorsToFormErrors(apiError.fields) }));
    } finally {
      setIsSubmitting(false);
      setSubmissionStage(null);
    }
  }

  function resetForm() {
    // Return the page to its original add-product state after a successful save.
    removeImageFile();
    setForm(emptyForm());
    setFieldErrors({});
    setSubmissionError(null);
    setSavedProduct(null);
  }

  if (savedProduct) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-4xl rounded-3xl border border-success-border bg-success-soft p-7 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">Product saved</p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">{savedProduct.name}</h1>
          <p aria-live="polite" className="mt-3 text-success">
            The garment and {savedProduct.variants.length} {savedProduct.variants.length === 1 ? "variant" : "variants"} were saved successfully.
          </p>
          <div className="mt-7 grid gap-6 lg:grid-cols-[minmax(14rem,0.7fr)_minmax(0,1.3fr)]">
            <ProductImage alt={`${savedProduct.name} catalog presentation`} className="aspect-square w-full rounded-2xl border border-success-border" loading="eager" productName={savedProduct.name} src={savedProduct.imageUrl} />
            <div className="overflow-hidden rounded-2xl border border-success-border bg-background/60">
              <div className="grid grid-cols-[1fr_1fr_auto] gap-3 border-b border-success-border px-4 py-3 text-xs font-semibold uppercase tracking-wide text-muted"><span>Size</span><span>Colour</span><span>Price</span></div>
              {savedProduct.variants.map((variant) => (
                <div className="grid grid-cols-[1fr_1fr_auto] gap-3 border-b border-success-border/60 px-4 py-3 last:border-0" key={variant.id}>
                  <span className="font-medium text-foreground">{variant.size}</span><span className="text-foreground">{variant.color}</span><span className="font-semibold text-primary">LKR {variant.price}</span>
                </div>
              ))}
            </div>
          </div>
          <button className="mt-8 rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover" onClick={resetForm} type="button">Add another product</button>
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Garment Product Management</p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Add garment product</h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">Add the garment once, then create every size and colour combination with its own price and availability.</p>

        <form className="mt-8 grid gap-7" noValidate onSubmit={handleSubmit}>
          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">1 · Product details</p>
            <div className="mt-5 grid gap-6 sm:grid-cols-2">
              <label className="block text-sm font-medium text-foreground" htmlFor="product-name">Product name
                <input aria-invalid={Boolean(fieldErrors.name)} autoComplete="off" className={inputClassName} id="product-name" maxLength={160} onChange={(event) => updateDetailsField("name", event.target.value)} value={form.name} />
                <FieldError id="product-name-error" message={fieldErrors.name} />
              </label>
              <label className="block text-sm font-medium text-foreground" htmlFor="product-category">Category
                <input aria-label="Category" aria-invalid={Boolean(fieldErrors.category)} autoComplete="off" className={inputClassName} id="product-category" maxLength={100} onChange={(event) => updateDetailsField("category", event.target.value)} value={form.category} />
                <span className="mt-2 block text-xs text-muted">An existing category with the same name is reused.</span>
                <FieldError id="product-category-error" message={fieldErrors.category} />
              </label>
              <label className="block text-sm font-medium text-foreground sm:col-span-2" htmlFor="product-description">Description <span className="font-normal text-muted">(optional)</span>
                <textarea aria-invalid={Boolean(fieldErrors.description)} className={`${inputClassName} min-h-28 resize-y`} id="product-description" maxLength={2000} onChange={(event) => updateDetailsField("description", event.target.value)} placeholder="Fabric, fit, care instructions, or other catalog details" value={form.description ?? ""} />
                <FieldError id="product-description-error" message={fieldErrors.description} />
              </label>
            </div>
          </section>

          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">2 · Product photo</p>
            <div className="mt-5 grid gap-6 md:grid-cols-[minmax(14rem,0.7fr)_minmax(0,1.3fr)]">
              <ProductImage alt="Product photo preview" className="aspect-square w-full rounded-2xl border border-border" loading="eager" productName={form.name || "New garment"} src={displayedImage} />
              <div>
                <label className="flex cursor-pointer items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-primary/40 bg-primary-soft px-5 py-8 text-center font-semibold text-primary transition hover:border-primary" htmlFor="product-image-file"><ImagePlus aria-hidden="true" size={22} />Choose a local product photo</label>
                <input accept="image/jpeg,image/png,image/webp" className="sr-only" id="product-image-file" onChange={(event) => chooseImage(event.target.files?.[0])} type="file" />
                <p className="mt-3 text-xs leading-5 text-muted">JPEG, PNG, or WebP · maximum 5 MB. The photo is copied to local application storage.</p>
                {imageFile ? (
                  <div className="mt-4 flex items-center justify-between gap-3 rounded-xl border border-border bg-background px-4 py-3 text-sm"><span className="min-w-0 truncate text-foreground">{imageFile.name}</span><button aria-label="Remove selected photo" className="rounded-lg p-2 text-muted hover:bg-surface-muted hover:text-foreground" onClick={removeImageFile} type="button"><X aria-hidden="true" size={18} /></button></div>
                ) : null}
                <FieldError id="product-image-file-error" message={fieldErrors.imageFile} />
                <details className="mt-5 rounded-xl border border-border bg-background/60 p-4">
                  <summary className="cursor-pointer text-sm font-semibold text-foreground">Use an existing image URL instead</summary>
                  <label className="mt-4 block text-sm text-foreground" htmlFor="product-image-url">HTTPS URL or managed /products/ path
                    <input className={inputClassName} disabled={Boolean(imageFile)} id="product-image-url" maxLength={500} onChange={(event) => updateDetailsField("imageUrl", event.target.value)} placeholder="https://…" value={form.imageUrl ?? ""} />
                  </label>
                  <FieldError id="product-image-url-error" message={fieldErrors.imageUrl} />
                </details>
              </div>
            </div>
          </section>

          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">3 · Size, colour & price options</p><h2 className="mt-2 text-2xl font-bold text-foreground">Product variants</h2><p className="mt-2 text-sm leading-6 text-muted">Each row is one sellable combination, so prices can change by size and colour.</p></div>
              <button className="inline-flex items-center gap-2 rounded-xl border border-primary/50 px-4 py-2.5 text-sm font-semibold text-primary transition hover:border-primary" onClick={addVariant} type="button"><Plus aria-hidden="true" size={18} /> Add another option</button>
            </div>
            <datalist id="garment-size-options"><option value="XS" /><option value="S" /><option value="M" /><option value="L" /><option value="XL" /><option value="2XL" /><option value="3XL" /><option value="One size" /></datalist>
            <div className="mt-6 grid gap-4">
              {form.variants.map((variant, index) => (
                <fieldset className="rounded-2xl border border-border bg-background/60 p-4 sm:p-5" key={index}>
                  <legend className="px-2 text-sm font-semibold text-foreground">Option {index + 1}</legend>
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[0.75fr_1fr_1fr_1fr_auto] lg:items-start">
                    <label className="text-sm font-medium text-foreground">Size
                      <input aria-label={form.variants.length === 1 ? "Size" : `Size ${index + 1}`} className={inputClassName} list="garment-size-options" maxLength={32} onChange={(event) => updateVariant(index, "size", event.target.value)} placeholder="M" value={variant.size} />
                      <FieldError id={`variant-${index}-size-error`} message={fieldErrors[`variants.${index}.size`]} />
                    </label>
                    <label className="text-sm font-medium text-foreground">Colour
                      <input aria-label={form.variants.length === 1 ? "Color" : `Color ${index + 1}`} className={inputClassName} maxLength={64} onChange={(event) => updateVariant(index, "color", event.target.value)} placeholder="Navy blue" value={variant.color} />
                      <FieldError id={`variant-${index}-color-error`} message={fieldErrors[`variants.${index}.color`]} />
                    </label>
                    <label className="text-sm font-medium text-foreground">Price (LKR)
                      <input aria-label={form.variants.length === 1 ? "Price" : `Price ${index + 1}`} className={inputClassName} inputMode="decimal" onChange={(event) => updateVariant(index, "price", event.target.value)} placeholder="0.00" value={variant.price} />
                      <FieldError id={`variant-${index}-price-error`} message={fieldErrors[`variants.${index}.price`]} />
                    </label>
                    <label className="text-sm font-medium text-foreground">Availability
                      <select aria-label={form.variants.length === 1 ? "Availability" : `Availability ${index + 1}`} className={inputClassName} onChange={(event) => updateVariant(index, "availability", event.target.value as CreateProductAvailability)} value={variant.availability}><option value="AVAILABLE">Available</option><option value="UNAVAILABLE">Unavailable</option></select>
                    </label>
                    <button aria-label={`Remove option ${index + 1}`} className="mt-2 inline-flex h-12 w-12 items-center justify-center rounded-xl border border-danger-border text-danger transition hover:bg-danger-soft disabled:cursor-not-allowed disabled:opacity-35 lg:mt-8" disabled={form.variants.length === 1} onClick={() => removeVariant(index)} type="button"><Trash2 aria-hidden="true" size={19} /></button>
                  </div>
                </fieldset>
              ))}
            </div>
            <FieldError id="variants-error" message={fieldErrors.variants} />
          </section>

          {submissionError ? <p className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">{submissionError}</p> : null}
          <div className="flex flex-wrap items-center gap-4">
            <button className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60" disabled={isSubmitting} type="submit">{submissionStage === "uploading" ? "Uploading photo…" : submissionStage === "saving" ? "Saving product…" : "Save garment product"}</button>
            <p className="text-sm text-muted">Product name, category, and at least one complete option are required.</p>
          </div>
        </form>
      </div>
    </section>
  );
}
