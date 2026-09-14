import type { CreateInventoryMaterialInput } from "../api/inventoryMaterials";

export type InventoryMaterialField = keyof CreateInventoryMaterialInput;
export type InventoryMaterialFieldErrors = Partial<
  Record<InventoryMaterialField, string>
>;

const positiveQuantityPattern = /^\d{1,11}(\.\d{1,3})?$/;
const optionalPositiveIntegerPattern = /^[1-9]\d*$/;

const requiredTextFields: Array<[InventoryMaterialField, string, number]> = [
  ["materialCode", "Material code", 64],
  ["materialName", "Material name", 160],
  ["unitOfMeasure", "Unit of measure", 32],
];

export function emptyInventoryMaterialForm(): CreateInventoryMaterialInput {
  return {
    sourceMaterialSupplyId: "",
    materialCode: "",
    materialName: "",
    materialDescription: "",
    materialType: "FABRIC",
    unitOfMeasure: "",
    currentQuantity: "",
    lowStockThreshold: "",
  };
}

function normalizeText(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

export function normalizeInventoryMaterialForm(
  form: CreateInventoryMaterialInput,
): CreateInventoryMaterialInput {
  return {
    sourceMaterialSupplyId: form.sourceMaterialSupplyId.trim(),
    materialCode: normalizeText(form.materialCode),
    materialName: normalizeText(form.materialName),
    materialDescription: normalizeText(form.materialDescription),
    materialType: form.materialType,
    unitOfMeasure: normalizeText(form.unitOfMeasure),
    currentQuantity: form.currentQuantity.trim(),
    lowStockThreshold: form.lowStockThreshold.trim(),
  };
}

export function validateInventoryMaterialForm(
  form: CreateInventoryMaterialInput,
): InventoryMaterialFieldErrors {
  const normalized = normalizeInventoryMaterialForm(form);
  const errors: InventoryMaterialFieldErrors = {};

  for (const [field, label, maximumLength] of requiredTextFields) {
    const value = normalized[field];
    if (!value) {
      errors[field] = `${label} is required.`;
    } else if (value.length > maximumLength) {
      errors[field] = `${label} must not exceed ${maximumLength} characters.`;
    }
  }

  if (normalized.materialDescription.length > 500) {
    errors.materialDescription = "Description must not exceed 500 characters.";
  }
  if (
    normalized.sourceMaterialSupplyId
    && (!optionalPositiveIntegerPattern.test(normalized.sourceMaterialSupplyId)
      || Number(normalized.sourceMaterialSupplyId) > Number.MAX_SAFE_INTEGER)
  ) {
    errors.sourceMaterialSupplyId =
      "Source material supply ID must be a positive whole number.";
  }
  if (!positiveQuantityPattern.test(normalized.currentQuantity)
      || Number(normalized.currentQuantity) <= 0) {
    errors.currentQuantity =
      "Opening quantity must be positive with at most 11 digits and 3 decimals.";
  }
  if (!positiveQuantityPattern.test(normalized.lowStockThreshold)
      || Number(normalized.lowStockThreshold) < 0) {
    errors.lowStockThreshold =
      "Low-stock threshold must be zero or greater with at most 11 digits and 3 decimals.";
  }

  return errors;
}
