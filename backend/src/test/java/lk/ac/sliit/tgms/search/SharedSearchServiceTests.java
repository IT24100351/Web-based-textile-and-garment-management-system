package lk.ac.sliit.tgms.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.delivery.DeliveryService;
import lk.ac.sliit.tgms.inventory.InventoryMaterialService;
import lk.ac.sliit.tgms.order.OrderService;
import lk.ac.sliit.tgms.order.OrderStatus;
import lk.ac.sliit.tgms.order.OrderSummary;
import lk.ac.sliit.tgms.product.ProductService;
import lk.ac.sliit.tgms.supplier.MaterialSupplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SharedSearchServiceTests {
    private AuthService authService;
    private ProductService productService;
    private MaterialSupplyService supplyService;
    private InventoryMaterialService inventoryService;
    private OrderService orderService;
    private DeliveryService deliveryService;
    private SharedSearchService service;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        productService = mock(ProductService.class);
        supplyService = mock(MaterialSupplyService.class);
        inventoryService = mock(InventoryMaterialService.class);
        orderService = mock(OrderService.class);
        deliveryService = mock(DeliveryService.class);
        service = new SharedSearchService(
                authService, productService, supplyService, inventoryService, orderService, deliveryService);
        when(productService.getPublicCatalog("ORD", null, null, null, null)).thenReturn(List.of());
    }

    @Test
    void customerSearchUsesOwnershipScopedOrderServiceAndNeverStaffDeliverySearch() {
        when(authService.requireActiveUser(51L)).thenReturn(account(51L, UserRole.CUSTOMER));
        when(orderService.getCustomerOrders(51L, "ORD")).thenReturn(List.of(new OrderSummary(
                91L, "ORD-91", 51L, "Customer", "customer@example.com", OrderStatus.CONFIRMED,
                Instant.parse("2026-08-26T10:00:00Z"), Instant.parse("2026-08-26T10:00:00Z"), 2,
                "Classic Oxford Shirt", "/products/classic-oxford-shirt.jpg",
                new java.math.BigDecimal("1500.00"))));

        SharedSearchPage page = service.search(51L, "  ORD  ", 0, 10);

        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).path()).isEqualTo("/orders/history/91");
        verify(orderService).getCustomerOrders(51L, "ORD");
        verify(orderService, never()).getStaffOrders("ORD", null);
        verify(deliveryService, never()).getStaffRecords("ORD", null);
        verify(supplyService, never()).getPermittedSupplies(51L, "ORD", null);
    }

    @Test
    void salesOfficerReusesStaffOrderAndDeliverySearchButNotInventoryOrSupplierSearch() {
        when(authService.requireActiveUser(61L)).thenReturn(account(61L, UserRole.SALES_OFFICER));
        when(orderService.getStaffOrders("ORD", null)).thenReturn(List.of());
        when(deliveryService.getStaffRecords("ORD", null)).thenReturn(List.of());

        service.search(61L, "ORD", 0, 10);

        verify(orderService).getStaffOrders("ORD", null);
        verify(deliveryService).getStaffRecords("ORD", null);
        verify(inventoryService, never()).getMaterials("ORD", null, null);
        verify(supplyService, never()).getPermittedSupplies(61L, "ORD", null);
    }

    @Test
    void invalidPaginationAndShortQueryAreRejectedBeforeModuleSearch() {
        when(authService.requireActiveUser(71L)).thenReturn(account(71L, UserRole.ADMINISTRATOR));

        assertThatThrownBy(() -> service.search(71L, "x", -1, 100))
                .isInstanceOf(SharedSearchValidationException.class)
                .satisfies(exception -> assertThat(((SharedSearchValidationException) exception).fields())
                        .containsKeys("search", "page", "size"));

        verify(productService, never()).getPublicCatalog("x", null, null, null, null);
    }

    private UserAccount account(long id, UserRole role) {
        return new UserAccount(id, "user@example.com", "hash", "User", role, true);
    }
}
