package lk.ac.sliit.tgms.quotation;

import static lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;

import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotations")
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @GetMapping("/customers")
    @SalesOfficerOnly
    public List<CustomerResponse> customers(@RequestParam(required = false) String search) {
        return quotationService.getSelectableCustomers(search).stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @GetMapping
    @SalesOfficerOnly
    public List<QuotationSummaryResponse> quotations() {
        return quotationService.getAll().stream()
                .map(QuotationSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{quotationId}")
    @SalesOfficerOnly
    public QuotationDetailResponse quotation(@PathVariable long quotationId) {
        return QuotationDetailResponse.from(quotationService.get(quotationId));
    }

    @PostMapping
    @SalesOfficerOnly
    public ResponseEntity<CreateQuotationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateQuotationRequest request) {
        CreatedQuotation created = quotationService.create(
                authenticatedUserId(jwt),
                request == null ? null : request.customerId(),
                request == null ? null : toCommands(request.items()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateQuotationResponse.from(created));
    }

    private List<CreateQuotationItemCommand> toCommands(List<CreateQuotationItemRequest> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> item == null
                        ? null
                        : new CreateQuotationItemCommand(
                                item.productId(), item.variantId(), item.quantity()))
                .toList();
    }

    private long authenticatedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidSessionException();
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new InvalidSessionException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new InvalidSessionException();
        }
    }

    public record CreateQuotationRequest(
            Long customerId,
            List<CreateQuotationItemRequest> items) {}

    public record CreateQuotationItemRequest(Long productId, Long variantId, Integer quantity) {}

    public record CustomerResponse(long id, String fullName, String email) {
        static CustomerResponse from(QuotationCustomerOption customer) {
            return new CustomerResponse(customer.id(), customer.fullName(), customer.email());
        }
    }

    public record CreateQuotationResponse(
            String message,
            long id,
            String quotationNumber,
            CustomerResponse customer,
            Instant issuedAt,
            List<QuotationItemResponse> items,
            String totalAmount) {

        static CreateQuotationResponse from(CreatedQuotation created) {
            return new CreateQuotationResponse(
                    "Customer quotation issued successfully.",
                    created.quotation().id(),
                    created.quotation().quotationNumber(),
                    CustomerResponse.from(created.customer()),
                    created.quotation().issuedAt(),
                    created.items().stream().map(QuotationItemResponse::from).toList(),
                    created.totalAmount().setScale(2).toPlainString());
        }
    }

    public record QuotationSummaryResponse(
            long id,
            String quotationNumber,
            long customerId,
            String customerName,
            String customerEmail,
            Instant issuedAt,
            int itemCount,
            String totalAmount) {

        static QuotationSummaryResponse from(QuotationSummary quotation) {
            return new QuotationSummaryResponse(
                    quotation.id(),
                    quotation.quotationNumber(),
                    quotation.customerId(),
                    quotation.customerName(),
                    quotation.customerEmail(),
                    quotation.issuedAt(),
                    quotation.itemCount(),
                    quotation.totalAmount().setScale(2).toPlainString());
        }
    }

    public record QuotationDetailResponse(
            long id,
            String quotationNumber,
            long customerId,
            String customerName,
            String customerEmail,
            Instant issuedAt,
            List<QuotationItemResponse> items,
            String totalAmount) {

        static QuotationDetailResponse from(QuotationDetail quotation) {
            return new QuotationDetailResponse(
                    quotation.id(),
                    quotation.quotationNumber(),
                    quotation.customerId(),
                    quotation.customerName(),
                    quotation.customerEmail(),
                    quotation.issuedAt(),
                    quotation.items().stream().map(QuotationItemResponse::from).toList(),
                    quotation.totalAmount().setScale(2).toPlainString());
        }
    }

    public record QuotationItemResponse(
            long id,
            long productId,
            long variantId,
            String productName,
            int quantity,
            String selectedSize,
            String selectedColor,
            String unitPriceSnapshot,
            String lineTotal) {

        static QuotationItemResponse from(QuotationItem item) {
            return new QuotationItemResponse(
                    item.id(),
                    item.productId(),
                    item.variantId(),
                    item.productNameSnapshot(),
                    item.quantity(),
                    item.selectedSize(),
                    item.selectedColor(),
                    item.unitPriceSnapshot().setScale(2).toPlainString(),
                    item.lineTotal().setScale(2).toPlainString());
        }
    }
}
