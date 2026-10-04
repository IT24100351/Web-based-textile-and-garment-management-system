package lk.ac.sliit.tgms.quotation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.product.OrderProductSelection;
import lk.ac.sliit.tgms.quotation.QuotationValidator.ValidatedQuotation;
import lk.ac.sliit.tgms.quotation.QuotationValidator.ValidatedQuotationItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotationService {

    private static final int MAX_CUSTOMER_SEARCH_LENGTH = 120;

    private final UserAccountRepository userAccountRepository;
    private final QuotationValidator quotationValidator;
    private final QuotationRepository quotationRepository;

    public QuotationService(
            UserAccountRepository userAccountRepository,
            QuotationValidator quotationValidator,
            QuotationRepository quotationRepository) {
        this.userAccountRepository = userAccountRepository;
        this.quotationValidator = quotationValidator;
        this.quotationRepository = quotationRepository;
    }

    @Transactional(readOnly = true)
    public List<QuotationCustomerOption> getSelectableCustomers(String search) {
        String normalized = normalizeOptional(search);
        if (normalized != null && normalized.length() > MAX_CUSTOMER_SEARCH_LENGTH) {
            throw new QuotationValidationException(
                    Map.of("search", "Customer search must not exceed 120 characters."));
        }
        return userAccountRepository.findActiveByRole(UserRole.CUSTOMER, normalized).stream()
                .map(this::toCustomerOption)
                .toList();
    }

    @Transactional
    public CreatedQuotation create(
            long issuedByUserId,
            Long customerId,
            List<CreateQuotationItemCommand> requestedItems) {
        ValidatedQuotation validated = quotationValidator.validate(customerId, requestedItems);
        Quotation quotation = quotationRepository.createQuotation(
                validated.customer().id(), issuedByUserId, generateQuotationNumber());

        List<QuotationItem> items = validated.items().stream()
                .map(item -> saveItem(quotation.id(), item))
                .toList();
        return new CreatedQuotation(
                quotation,
                toCustomerOption(validated.customer()),
                List.copyOf(items));
    }

    @Transactional(readOnly = true)
    public List<QuotationSummary> getAll() {
        return quotationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public QuotationDetail get(long quotationId) {
        if (quotationId <= 0) {
            throw new QuotationNotFoundException();
        }
        return quotationRepository.findById(quotationId)
                .orElseThrow(QuotationNotFoundException::new);
    }

    private QuotationItem saveItem(long quotationId, ValidatedQuotationItem validatedItem) {
        CreateQuotationItemCommand requested = validatedItem.requested();
        OrderProductSelection selection = validatedItem.selection();
        return quotationRepository.createItem(
                quotationId,
                selection.productId(),
                selection.variantId(),
                selection.productName(),
                requested.quantity(),
                selection.size(),
                selection.color(),
                selection.currentPrice());
    }

    private QuotationCustomerOption toCustomerOption(UserAccount account) {
        return new QuotationCustomerOption(account.id(), account.fullName(), account.email());
    }

    private String generateQuotationNumber() {
        return "QUO-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}
