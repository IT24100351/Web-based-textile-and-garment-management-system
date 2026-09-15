package lk.ac.sliit.tgms.supplier;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialSupplyService {

    private final MaterialSupplyRepository materialSupplyRepository;
    private final SupplierProfileRepository supplierProfileRepository;
    private final AuthService authService;

    public MaterialSupplyService(
            MaterialSupplyRepository materialSupplyRepository,
            SupplierProfileRepository supplierProfileRepository,
            AuthService authService) {
        this.materialSupplyRepository = materialSupplyRepository;
        this.supplierProfileRepository = supplierProfileRepository;
        this.authService = authService;
    }

    @Transactional
    public MaterialSupply createOwnSupply(
            long userId,
            String materialCode,
            String materialName,
            String materialDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            Integer deliveryLeadTimeDays,
            String deliveryNotes) {
        SupplierProfile supplier = requireSupplierProfile(userId);
        String normalizedCode = normalizeRequiredText(materialCode);
        String normalizedName = normalizeRequiredText(materialName);
        String normalizedDescription = normalizeOptionalText(materialDescription);
        String normalizedUnit = normalizeRequiredText(unitOfMeasure);
        String normalizedDeliveryNotes = normalizeOptionalText(deliveryNotes);
        validate(
                normalizedCode,
                normalizedName,
                normalizedDescription,
                quantity,
                normalizedUnit,
                unitPrice,
                deliveryLeadTimeDays,
                normalizedDeliveryNotes);

        long supplyId;
        try {
            supplyId = materialSupplyRepository.create(
                    supplier.id(),
                    normalizedCode,
                    normalizedName,
                    normalizedDescription,
                    quantity,
                    normalizedUnit,
                    unitPrice,
                    deliveryLeadTimeDays,
                    normalizedDeliveryNotes);
        } catch (DuplicateKeyException exception) {
            throw new MaterialSupplyCodeExistsException();
        }

        return materialSupplyRepository.findById(supplyId)
                .filter(supply -> supply.supplierId() == supplier.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Created material supply could not be read back."));
    }

    @Transactional(readOnly = true)
    public List<MaterialSupplyListItem> getPermittedSupplies(
            long userId, String search, String status) {
        UserAccount account = authService.requireActiveUser(userId);
        Long supplierId = switch (account.role()) {
            case SUPPLIER -> supplierProfileRepository.findByUserId(userId)
                    .map(SupplierProfile::id)
                    .orElseThrow(SupplierProfileNotFoundException::new);
            case INVENTORY_MANAGER, ADMINISTRATOR -> null;
            default -> throw new AccessDeniedException(
                    "This account cannot read material supply records.");
        };

        String normalizedSearch = normalizeOptionalText(search);
        Map<String, String> fields = new LinkedHashMap<>();
        validateOptionalText(fields, "search", normalizedSearch, 160, "Search");
        MaterialSupplyStatus normalizedStatus = parseStatus(status, fields);
        if (!fields.isEmpty()) {
            throw new MaterialSupplyValidationException(fields);
        }
        return materialSupplyRepository.findAll(
                new MaterialSupplyQuery(supplierId, normalizedSearch, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public MaterialSupply getPermittedSupply(long userId, long supplyId) {
        UserAccount account = authService.requireActiveUser(userId);
        return switch (account.role()) {
            case SUPPLIER -> {
                SupplierProfile supplier = supplierProfileRepository.findByUserId(userId)
                        .orElseThrow(SupplierProfileNotFoundException::new);
                yield requireOwnedSupply(supplyId, supplier.id());
            }
            case INVENTORY_MANAGER, ADMINISTRATOR -> requireSupply(supplyId);
            default -> throw new AccessDeniedException(
                    "This account cannot read material supply records.");
        };
    }

    @Transactional
    public MaterialSupply updateOwnSupplyDetails(
            long userId,
            long supplyId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Integer deliveryLeadTimeDays,
            String deliveryNotes) {
        SupplierProfile supplier = requireSupplierProfile(userId);
        MaterialSupply currentSupply = requireOwnedSupply(supplyId, supplier.id());
        if (currentSupply.status() == MaterialSupplyStatus.DISCONTINUED) {
            throw new MaterialSupplyArchivedException();
        }
        String normalizedDeliveryNotes = normalizeOptionalText(deliveryNotes);
        Map<String, String> fields = new LinkedHashMap<>();
        validateDetails(
                fields,
                quantity,
                unitPrice,
                deliveryLeadTimeDays,
                normalizedDeliveryNotes);
        if (!fields.isEmpty()) {
            throw new MaterialSupplyValidationException(fields);
        }

        int updatedRows = materialSupplyRepository.updateDetails(
                supplyId,
                supplier.id(),
                quantity,
                unitPrice,
                deliveryLeadTimeDays,
                normalizedDeliveryNotes);
        if (updatedRows != 1) {
            throw new MaterialSupplyNotFoundException();
        }
        return requireOwnedSupply(supplyId, supplier.id());
    }

    @Transactional
    public MaterialSupply archiveOwnSupply(long userId, long supplyId) {
        SupplierProfile supplier = requireSupplierProfile(userId);
        MaterialSupply supply = requireOwnedSupply(supplyId, supplier.id());
        if (supply.status() == MaterialSupplyStatus.DISCONTINUED) {
            return supply;
        }
        int archivedRows = materialSupplyRepository.archive(supplyId, supplier.id());
        if (archivedRows != 1) {
            throw new MaterialSupplyNotFoundException();
        }
        return requireOwnedSupply(supplyId, supplier.id());
    }

    private MaterialSupply requireOwnedSupply(long supplyId, long supplierId) {
        MaterialSupply supply = requireSupply(supplyId);
        if (supply.supplierId() != supplierId) {
            throw new MaterialSupplyNotFoundException();
        }
        return supply;
    }

    private MaterialSupply requireSupply(long supplyId) {
        if (supplyId <= 0) {
            throw new MaterialSupplyNotFoundException();
        }
        return materialSupplyRepository.findById(supplyId)
                .orElseThrow(MaterialSupplyNotFoundException::new);
    }

    private MaterialSupplyStatus parseStatus(String status, Map<String, String> fields) {
        String normalizedStatus = normalizeOptionalText(status);
        if (normalizedStatus == null) {
            return null;
        }
        try {
            return MaterialSupplyStatus.valueOf(normalizedStatus.toUpperCase());
        } catch (IllegalArgumentException exception) {
            fields.put("status", "Status must be ACTIVE, INACTIVE, or DISCONTINUED.");
            return null;
        }
    }

    private SupplierProfile requireSupplierProfile(long userId) {
        if (userId <= 0) {
            throw new MaterialSupplyValidationException(
                    Map.of("userId", "Authenticated user ID must be a positive number."));
        }
        UserAccount account = authService.requireActiveUser(userId);
        if (account.role() != UserRole.SUPPLIER) {
            throw new AccessDeniedException("Only supplier accounts can create material supplies.");
        }
        return supplierProfileRepository.findByUserId(userId)
                .orElseThrow(SupplierProfileNotFoundException::new);
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

    private void validate(
            String materialCode,
            String materialName,
            String materialDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            Integer deliveryLeadTimeDays,
            String deliveryNotes) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateRequiredText(fields, "materialCode", materialCode, 64, "Material code");
        validateRequiredText(fields, "materialName", materialName, 160, "Material name");
        validateOptionalText(
                fields, "materialDescription", materialDescription, 500, "Description");
        validateRequiredText(fields, "unitOfMeasure", unitOfMeasure, 32, "Unit of measure");
        validateDetails(fields, quantity, unitPrice, deliveryLeadTimeDays, deliveryNotes);
        if (!fields.isEmpty()) {
            throw new MaterialSupplyValidationException(fields);
        }
    }

    private void validateDetails(
            Map<String, String> fields,
            BigDecimal quantity,
            BigDecimal unitPrice,
            Integer deliveryLeadTimeDays,
            String deliveryNotes) {
        validatePositiveDecimal(fields, "quantity", quantity, 11, 3, "Quantity");
        validatePositiveDecimal(fields, "unitPrice", unitPrice, 10, 2, "Unit price");
        if (deliveryLeadTimeDays == null) {
            fields.put("deliveryLeadTimeDays", "Delivery lead time is required.");
        } else if (deliveryLeadTimeDays < 0) {
            fields.put(
                    "deliveryLeadTimeDays", "Delivery lead time must not be negative.");
        }
        validateOptionalText(fields, "deliveryNotes", deliveryNotes, 500, "Delivery notes");
    }

    private void validateRequiredText(
            Map<String, String> fields,
            String field,
            String value,
            int maximumLength,
            String label) {
        if (value.isBlank()) {
            fields.put(field, label + " is required.");
        } else if (value.length() > maximumLength) {
            fields.put(field, label + " must not exceed " + maximumLength + " characters.");
        }
    }

    private void validateOptionalText(
            Map<String, String> fields,
            String field,
            String value,
            int maximumLength,
            String label) {
        if (value != null && value.length() > maximumLength) {
            fields.put(field, label + " must not exceed " + maximumLength + " characters.");
        }
    }

    private void validatePositiveDecimal(
            Map<String, String> fields,
            String field,
            BigDecimal value,
            int maximumIntegerDigits,
            int maximumFractionDigits,
            String label) {
        if (value == null) {
            fields.put(field, label + " is required.");
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            fields.put(field, label + " must be greater than zero.");
            return;
        }
        int fractionDigits = Math.max(value.scale(), 0);
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (integerDigits > maximumIntegerDigits || fractionDigits > maximumFractionDigits) {
            fields.put(
                    field,
                    label + " must have at most " + maximumIntegerDigits
                            + " digits and " + maximumFractionDigits + " decimals.");
        }
    }
}
