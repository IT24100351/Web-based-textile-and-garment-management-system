package lk.ac.sliit.tgms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.product.OrderProductSelection;
import lk.ac.sliit.tgms.product.ProductNotSelectableException;
import lk.ac.sliit.tgms.product.ProductService;
import lk.ac.sliit.tgms.product.VariantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class OrderValidatorTests {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ProductService productService;

    private OrderValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new OrderValidator(userAccountRepository, productService);
    }

    @Test
    void rejectsMissingSelectionsAndQuantityBeforeCallingProductManagement() {
        List<CreateOrderItemCommand> items = List.of(new CreateOrderItemCommand(null, null, 0));

        assertThatThrownBy(() -> validator.validate(51L, items))
                .isInstanceOfSatisfying(OrderValidationException.class, exception -> {
                    assertThat(exception.fields())
                            .containsKeys(
                                    "items[0].productId",
                                    "items[0].variantId",
                                    "items[0].quantity");
                });

        verify(productService, never()).requireOrderSelectableVariant(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void mapsUnavailableVariantToTheExactOrderLineField() {
        UserAccount customer = customer();
        when(userAccountRepository.findById(51L)).thenReturn(Optional.of(customer));
        when(productService.requireOrderSelectableVariant(61L, 81L))
                .thenThrow(new ProductNotSelectableException());

        assertThatThrownBy(() -> validator.validate(
                        51L, List.of(new CreateOrderItemCommand(61L, 81L, 2))))
                .isInstanceOfSatisfying(OrderProductNotSelectableException.class, exception ->
                        assertThat(exception.fields())
                                .containsEntry(
                                        "items[0].variantId",
                                        "This product/size/color selection is no longer available. Choose an available garment variant."));
    }

    @Test
    void acceptsActiveCustomerAndAvailableVariant() {
        UserAccount customer = customer();
        OrderProductSelection selection = new OrderProductSelection(
                61L,
                81L,
                "Classic Oxford Shirt",
                71L,
                "Formal Wear",
                "M",
                "White",
                new BigDecimal("2490.00"),
                VariantStatus.AVAILABLE);
        when(userAccountRepository.findById(51L)).thenReturn(Optional.of(customer));
        when(productService.requireOrderSelectableVariant(61L, 81L)).thenReturn(selection);

        OrderValidator.ValidatedOrder result = validator.validate(
                51L, List.of(new CreateOrderItemCommand(61L, 81L, 2)));

        assertThat(result.customer()).isEqualTo(customer);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).selection()).isEqualTo(selection);
    }

    private UserAccount customer() {
        return new UserAccount(
                51L,
                "customer@example.com",
                "hash",
                "Asha Perera",
                UserRole.CUSTOMER,
                true);
    }
}
