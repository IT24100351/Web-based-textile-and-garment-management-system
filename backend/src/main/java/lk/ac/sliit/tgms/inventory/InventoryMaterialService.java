package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.notification.NotificationDispatchService;
import lk.ac.sliit.tgms.supplier.MaterialSupplyNotFoundException;
import lk.ac.sliit.tgms.supplier.MaterialSupplyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryMaterialService {

    private final InventoryMaterialRepository inventoryMaterialRepository;
    private final MaterialSupplyService materialSupplyService;
    private final NotificationDispatchService notificationDispatchService;

    public InventoryMaterialService(
            InventoryMaterialRepository inventoryMaterialRepository,
            MaterialSupplyService materialSupplyService,
            NotificationDispatchService notificationDispatchService) {
        this.inventoryMaterialRepository = inventoryMaterialRepository;
        this.materialSupplyService = materialSupplyService;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Transactional
    public InventoryMaterial createMaterial(
            long userId,
            Long sourceMaterialSupplyId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold) {
        validateSourceMaterialSupplyId(userId, sourceMaterialSupplyId);
        String normalizedCode = normalizeRequiredText(materialCode);
        String normalizedName = normalizeRequiredText(materialName);
        String normalizedDescription = normalizeOptionalText(materialDescription);
        String normalizedUnit = normalizeRequiredText(unitOfMeasure);
        validateCreate(
                normalizedCode,
                normalizedName,
                normalizedDescription,
                materialType,
                normalizedUnit,
                currentQuantity,
                lowStockThreshold);

        long materialId;
        try {
            materialId = inventoryMaterialRepository.create(
                    sourceMaterialSupplyId,
                    normalizedCode,
                    normalizedName,
                    normalizedDescription,
                    materialType,
                    normalizedUnit,
                    currentQuantity,
                    lowStockThreshold);
        } catch (DuplicateKeyException exception) {
            throw new InventoryMaterialCodeExistsException();
        }
        InventoryMaterial created = inventoryMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created inventory material could not be read back."));
        if (created.status() == InventoryMaterialStatus.ACTIVE
                && created.stockState() == InventoryStockState.LOW_STOCK) {
            notificationDispatchService.lowStock(created);
        }
        return created;
    }

    @Transactional
    public InventoryMaterial updateMaterialMetadata(
            long materialId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal lowStockThreshold,
            InventoryMaterialStatus status) {
        InventoryMaterial current = requireMaterial(materialId);
        if (current.status() == InventoryMaterialStatus.DISCONTINUED) {
            throw new InventoryMaterialArchivedException();
        }

        String normalizedCode = normalizeRequiredText(materialCode);
        String normalizedName = normalizeRequiredText(materialName);
        String normalizedDescription = normalizeOptionalText(materialDescription);
        String normalizedUnit = normalizeRequiredText(unitOfMeasure);
        validateMetadata(
                normalizedCode,
                normalizedName,
                normalizedDescription,
                materialType,
                normalizedUnit,
                lowStockThreshold,
                status);

        try {
            int updatedRows = inventoryMaterialRepository.updateMetadata(
                    materialId,
                    normalizedCode,
                    normalizedName,
                    normalizedDescription,
                    materialType,
                    normalizedUnit,
                    lowStockThreshold,
                    status);
            if (updatedRows != 1) {
                InventoryMaterial latest = requireMaterial(materialId);
                if (latest.status() == InventoryMaterialStatus.DISCONTINUED) {
                    throw new InventoryMaterialArchivedException();
                }
                throw new IllegalStateException("Inventory material metadata was not updated.");
            }
        } catch (DuplicateKeyException exception) {
            throw new InventoryMaterialCodeExistsException();
        }

        InventoryMaterial updated = inventoryMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalStateException(
                        "Updated inventory material could not be read back."));
        if (updated.status() == InventoryMaterialStatus.ACTIVE
                && (current.status() != InventoryMaterialStatus.ACTIVE
                        || current.stockState() != InventoryStockState.LOW_STOCK)
                && updated.stockState() == InventoryStockState.LOW_STOCK) {
            notificationDispatchService.lowStock(updated);
        }
        return updated;
    }

    @Transactional
    public InventoryMaterial archiveMaterial(long materialId) {
        InventoryMaterial current = requireMaterial(materialId);
        if (current.status() == InventoryMaterialStatus.DISCONTINUED) {
            return current;
        }
        int archivedRows = inventoryMaterialRepository.archive(materialId);
        if (archivedRows != 1) {
            throw new IllegalStateException("Inventory material could not be archived.");
        }
        return inventoryMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalStateException(
                        "Archived inventory material could not be read back."));
    }

    @Transactional(readOnly = true)
    public List<InventoryMaterial> getMaterials() {
        return getMaterials(null, null, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryMaterial> getMaterials(
            String search, String status, String materialType) {
        String normalizedSearch = normalizeOptionalText(search);
        Map<String, String> fields = new LinkedHashMap<>();
        validateOptionalText(fields, "search", normalizedSearch, 160, "Search");
        InventoryMaterialStatus normalizedStatus = parseStatus(status, fields);
        InventoryMaterialType normalizedType = parseMaterialType(materialType, fields);
        if (!fields.isEmpty()) {
            throw new InventoryMaterialValidationException(fields);
        }
        return inventoryMaterialRepository.findAll(
                new InventoryMaterialQuery(normalizedSearch, normalizedStatus, normalizedType));
    }

    /**
     * Stable read contract for Production task material assignment. Only ACTIVE Inventory-owned
     * materials are selectable; Production stores only the stable material ID and required quantity.
     */
    @Transactional(readOnly = true)
    public List<InventoryMaterial> getProductionSelectableMaterials() {
        return inventoryMaterialRepository.findAll(
                        new InventoryMaterialQuery(null, InventoryMaterialStatus.ACTIVE, null))
                .stream()
                .sorted(Comparator.comparing(InventoryMaterial::materialCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryMaterial> getLowStockMaterials() {
        return inventoryMaterialRepository.findAll().stream()
                .filter(material -> material.status() == InventoryMaterialStatus.ACTIVE)
                .filter(material -> material.stockState() == InventoryStockState.LOW_STOCK)
                .toList();
    }

    /**
     * Returns a point-in-time availability check for Production Management.
     * A positive result is not a reservation. Production must use {@link #consumeStock(long,
     * BigDecimal)} for the final atomic deduction rather than changing current_quantity directly.
     */
    @Transactional(readOnly = true)
    public InventoryMaterialAvailability checkAvailability(
            long materialId, BigDecimal requiredQuantity) {
        validateMaterialId(materialId);
        validateRequiredQuantity(requiredQuantity);
        InventoryMaterial material = requireMaterial(materialId);

        if (material.status() != InventoryMaterialStatus.ACTIVE) {
            return new InventoryMaterialAvailability(
                    material.id(),
                    material.currentQuantity(),
                    requiredQuantity,
                    material.status(),
                    false,
                    InventoryAvailabilityState.NOT_ACTIVE);
        }
        boolean available = material.currentQuantity().compareTo(requiredQuantity) >= 0;
        return new InventoryMaterialAvailability(
                material.id(),
                material.currentQuantity(),
                requiredQuantity,
                material.status(),
                available,
                available
                        ? InventoryAvailabilityState.AVAILABLE
                        : InventoryAvailabilityState.INSUFFICIENT_STOCK);
    }

    /**
     * Inventory-owned atomic stock deduction contract. This method is safe for a future Production
     * service integration because the repository decrements only when the material is ACTIVE and
     * sufficient stock exists in the same SQL statement.
     */
    @Transactional
    public InventoryMaterial consumeStock(long materialId, BigDecimal quantity) {
        validateMaterialId(materialId);
        validateUsageQuantity(quantity);
        InventoryMaterial before = requireMaterial(materialId);

        int updatedRows = inventoryMaterialRepository.consumeStockIfAvailable(
                materialId, quantity);
        if (updatedRows == 0) {
            InventoryMaterial material = inventoryMaterialRepository.findById(materialId)
                    .orElseThrow(() -> materialNotFound());
            if (material.status() != InventoryMaterialStatus.ACTIVE) {
                throw new InventoryMaterialUnavailableException(material.status());
            }
            throw new InventoryInsufficientStockException(quantity, material.currentQuantity());
        }

        InventoryMaterial updated = inventoryMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalStateException(
                        "Updated inventory material could not be read back."));
        if (before.stockState() != InventoryStockState.LOW_STOCK
                && updated.stockState() == InventoryStockState.LOW_STOCK) {
            notificationDispatchService.lowStock(updated);
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public InventoryMaterial requireMaterial(long materialId) {
        validateMaterialId(materialId);
        return inventoryMaterialRepository.findById(materialId)
                .orElseThrow(() -> materialNotFound());
    }

    private InventoryMaterialStatus parseStatus(String status, Map<String, String> fields) {
        String normalizedStatus = normalizeOptionalText(status);
        if (normalizedStatus == null) {
            return null;
        }
        try {
            return InventoryMaterialStatus.valueOf(normalizedStatus.toUpperCase());
        } catch (IllegalArgumentException exception) {
            fields.put("status", "Status must be ACTIVE, INACTIVE, or DISCONTINUED.");
            return null;
        }
    }

    private InventoryMaterialType parseMaterialType(
            String materialType, Map<String, String> fields) {
        String normalizedType = normalizeOptionalText(materialType);
        if (normalizedType == null) {
            return null;
        }
        try {
            return InventoryMaterialType.valueOf(normalizedType.toUpperCase());
        } catch (IllegalArgumentException exception) {
            fields.put("materialType", "Material type must be FABRIC or RAW_MATERIAL.");
            return null;
        }
    }

    private void validateMaterialId(long materialId) {
        if (materialId <= 0) {
            throw new InventoryMaterialValidationException(
                    Map.of("materialId", "Material ID must be a positive number."));
        }
    }

    private InventoryMaterialValidationException materialNotFound() {
        return new InventoryMaterialValidationException(
                Map.of("materialId", "Inventory material was not found."));
    }

    private void validateUsageQuantity(BigDecimal quantity) {
        Map<String, String> fields = new LinkedHashMap<>();
        validatePositiveDecimal(
                fields,
                "quantity",
                quantity,
                11,
                3,
                "Usage quantity",
                "Usage quantity is required.");
        if (!fields.isEmpty()) {
            throw new InventoryMaterialValidationException(fields);
        }
    }

    private void validateRequiredQuantity(BigDecimal requiredQuantity) {
        Map<String, String> fields = new LinkedHashMap<>();
        validatePositiveDecimal(
                fields,
                "requiredQuantity",
                requiredQuantity,
                11,
                3,
                "Required quantity",
                "Required quantity is required.");
        if (!fields.isEmpty()) {
            throw new InventoryMaterialValidationException(fields);
        }
    }

    private void validateSourceMaterialSupplyId(long userId, Long sourceMaterialSupplyId) {
        if (sourceMaterialSupplyId == null) {
            return;
        }
        if (sourceMaterialSupplyId <= 0) {
            throw new InventoryMaterialValidationException(Map.of(
                    "sourceMaterialSupplyId",
                    "Source material supply ID must be a positive number when provided."));
        }
        try {
            materialSupplyService.getPermittedSupply(userId, sourceMaterialSupplyId);
        } catch (MaterialSupplyNotFoundException exception) {
            throw new InventoryMaterialValidationException(Map.of(
                    "sourceMaterialSupplyId",
                    "Source material supply was not found."));
        }
    }

    private void validateCreate(
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateMetadataFields(
                fields,
                materialCode,
                materialName,
                materialDescription,
                materialType,
                unitOfMeasure,
                lowStockThreshold);
        validateNonNegativeDecimal(
                fields, "currentQuantity", currentQuantity, 11, 3, "Current quantity");
        if (!fields.isEmpty()) {
            throw new InventoryMaterialValidationException(fields);
        }
    }

    private void validateMetadata(
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal lowStockThreshold,
            InventoryMaterialStatus status) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateMetadataFields(
                fields,
                materialCode,
                materialName,
                materialDescription,
                materialType,
                unitOfMeasure,
                lowStockThreshold);
        if (status == null) {
            fields.put("status", "Status is required.");
        } else if (status == InventoryMaterialStatus.DISCONTINUED) {
            fields.put("status", "Use the archive operation to discontinue an inventory material.");
        }
        if (!fields.isEmpty()) {
            throw new InventoryMaterialValidationException(fields);
        }
    }

    private void validateMetadataFields(
            Map<String, String> fields,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal lowStockThreshold) {
        validateRequiredText(fields, "materialCode", materialCode, 64, "Material code");
        validateRequiredText(fields, "materialName", materialName, 160, "Material name");
        validateOptionalText(
                fields, "materialDescription", materialDescription, 500, "Description");
        if (materialType == null) {
            fields.put("materialType", "Material type is required.");
        }
        validateRequiredText(fields, "unitOfMeasure", unitOfMeasure, 32, "Unit of measure");
        validateNonNegativeDecimal(
                fields,
                "lowStockThreshold",
                lowStockThreshold,
                11,
                3,
                "Low-stock threshold");
    }

    private String normalizeRequiredText(String value) {
        return value == null ? "" : normalizeText(value);
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeText(value);
    }

    private String normalizeText(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }

    private void validateRequiredText(
            Map<String, String> fields,
            String field,
            String value,
            int maxLength,
            String label) {
        if (value.isBlank()) {
            fields.put(field, label + " is required.");
        } else if (value.length() > maxLength) {
            fields.put(field, label + " must be " + maxLength + " characters or fewer.");
        }
    }

    private void validateOptionalText(
            Map<String, String> fields,
            String field,
            String value,
            int maxLength,
            String label) {
        if (value != null && value.length() > maxLength) {
            fields.put(field, label + " must be " + maxLength + " characters or fewer.");
        }
    }

    private void validateNonNegativeDecimal(
            Map<String, String> fields,
            String field,
            BigDecimal value,
            int integerDigits,
            int scale,
            String label) {
        if (value == null) {
            fields.put(field, label + " is required.");
            return;
        }
        if (value.signum() < 0) {
            fields.put(field, label + " cannot be negative.");
            return;
        }
        if (value.scale() > scale || value.precision() - value.scale() > integerDigits) {
            fields.put(field, label + " must fit " + integerDigits
                    + " digits and " + scale + " decimal places.");
        }
    }

    private void validatePositiveDecimal(
            Map<String, String> fields,
            String field,
            BigDecimal value,
            int integerDigits,
            int scale,
            String label,
            String requiredMessage) {
        if (value == null) {
            fields.put(field, requiredMessage);
        } else if (value.signum() <= 0) {
            fields.put(field, label + " must be greater than zero.");
        } else if (value.scale() > scale || value.precision() - value.scale() > integerDigits) {
            fields.put(field, label + " must fit " + integerDigits
                    + " digits and " + scale + " decimal places.");
        }
    }
}
