import type {
  CreateMaterialSupplyInput,
  UpdateMaterialSupplyInput,
} from "../api/materialSupplies";

export type MaterialSupplyField = keyof CreateMaterialSupplyInput;
export type MaterialSupplyFieldErrors = Partial<Record<MaterialSupplyField, string>>;
export type MaterialSupplyDetailField = keyof UpdateMaterialSupplyInput;
export type MaterialSupplyDetailFieldErrors = Partial<
  Record<MaterialSupplyDetailField, string>
>;

const quantityPattern = /^\d{1,11}(\.\d{1,3})?$/;
const pricePattern = /^\d{1,10}(\.\d{1,2})?$/;
const nonNegativeIntegerPattern = /^\d+$/;

const requiredTextFields: Array<[MaterialSupplyField, string, number]> = [
  ["materialCode", "Material code", 64],
  ["materialName", "Material name", 160],
  ["unitOfMeasure", "Unit of measure", 32],
];

export function emptyMaterialSupplyForm(): CreateMaterialSupplyInput {
  return {
    materialCode: "",
    materialName: "",
    materialDescription: "",
    quantity: "",
    unitOfMeasure: "",
    unitPrice: "",
    deliveryLeadTimeDays: "",
    deliveryNotes: "",
  };
}

function normalizeText(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

export function normalizeMaterialSupplyForm(
  form: CreateMaterialSupplyInput,
): CreateMaterialSupplyInput {
  return {
    materialCode: normalizeText(form.materialCode),
    materialName: normalizeText(form.materialName),
    materialDescription: normalizeText(form.materialDescription),
    ...normalizeMaterialSupplyDetails(form),
    unitOfMeasure: normalizeText(form.unitOfMeasure),
  };
}

export function normalizeMaterialSupplyDetails(
  form: UpdateMaterialSupplyInput,
): UpdateMaterialSupplyInput {
  return {
    quantity: form.quantity.trim(),
    unitPrice: form.unitPrice.trim(),
    deliveryLeadTimeDays: form.deliveryLeadTimeDays.trim(),
    deliveryNotes: normalizeText(form.deliveryNotes),
  };
}

export function validateMaterialSupplyForm(
  form: CreateMaterialSupplyInput,
): MaterialSupplyFieldErrors {
  const normalized = normalizeMaterialSupplyForm(form);
  const errors: MaterialSupplyFieldErrors = {
    ...validateMaterialSupplyDetails(normalized),
  };

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
  return errors;
}

export function validateMaterialSupplyDetails(
  form: UpdateMaterialSupplyInput,
): MaterialSupplyDetailFieldErrors {
  const normalized = normalizeMaterialSupplyDetails(form);
  const errors: MaterialSupplyDetailFieldErrors = {};
  if (!quantityPattern.test(normalized.quantity) || Number(normalized.quantity) <= 0) {
    errors.quantity = "Quantity must be positive with at most 11 digits and 3 decimals.";
  }
  if (!pricePattern.test(normalized.unitPrice) || Number(normalized.unitPrice) <= 0) {
    errors.unitPrice = "Unit price must be positive with at most 10 digits and 2 decimals.";
  }
  if (!nonNegativeIntegerPattern.test(normalized.deliveryLeadTimeDays)
      || Number(normalized.deliveryLeadTimeDays) > 2_147_483_647) {
    errors.deliveryLeadTimeDays = "Delivery lead time must be a non-negative whole number.";
  }
  if (normalized.deliveryNotes.length > 500) {
    errors.deliveryNotes = "Delivery notes must not exceed 500 characters.";
  }
  return errors;
}
