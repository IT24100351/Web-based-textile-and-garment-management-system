package lk.ac.sliit.tgms.supplier;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierProfileService {

    private final SupplierProfileRepository supplierProfileRepository;
    private final AuthService authService;

    public SupplierProfileService(
            SupplierProfileRepository supplierProfileRepository, AuthService authService) {
        this.supplierProfileRepository = supplierProfileRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public SupplierProfile getOwnProfile(long userId) {
        requireSupplierAccount(userId);
        return supplierProfileRepository.findByUserId(userId)
                .orElseThrow(SupplierProfileNotFoundException::new);
    }

    @Transactional
    public SupplierProfileSaveResult saveOwnProfile(
            long userId, String businessName, String contactPhone, String address) {
        requireSupplierAccount(userId);
        String normalizedBusinessName = normalizeText(businessName);
        String normalizedContactPhone = normalizeText(contactPhone);
        String normalizedAddress = normalizeText(address);
        validate(normalizedBusinessName, normalizedContactPhone, normalizedAddress);

        boolean created = supplierProfileRepository.findByUserId(userId).isEmpty();
        if (created) {
            try {
                supplierProfileRepository.create(
                        userId,
                        normalizedBusinessName,
                        normalizedContactPhone,
                        normalizedAddress);
            } catch (DuplicateKeyException exception) {
                created = false;
                updateExisting(
                        userId,
                        normalizedBusinessName,
                        normalizedContactPhone,
                        normalizedAddress);
            }
        } else {
            updateExisting(
                    userId,
                    normalizedBusinessName,
                    normalizedContactPhone,
                    normalizedAddress);
        }

        SupplierProfile profile = supplierProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Saved supplier profile could not be read back."));
        return new SupplierProfileSaveResult(profile, created);
    }

    private void requireSupplierAccount(long userId) {
        if (userId <= 0) {
            throw new SupplierProfileValidationException(
                    Map.of("userId", "Authenticated user ID must be a positive number."));
        }
        UserAccount account = authService.requireActiveUser(userId);
        if (account.role() != UserRole.SUPPLIER) {
            throw new AccessDeniedException("Only supplier accounts can own supplier profiles.");
        }
    }

    private void updateExisting(
            long userId, String businessName, String contactPhone, String address) {
        int updated = supplierProfileRepository.update(
                userId, businessName, contactPhone, address);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Supplier profile update affected an unexpected row count.");
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }

    private void validate(String businessName, String contactPhone, String address) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateRequiredText(fields, "businessName", businessName, 160, "Business name");
        validateRequiredText(fields, "contactPhone", contactPhone, 32, "Contact phone");
        validateRequiredText(fields, "address", address, 500, "Address");
        if (!fields.isEmpty()) {
            throw new SupplierProfileValidationException(fields);
        }
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
}
