package lk.ac.sliit.tgms.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

public final class RoleGuards {

    private RoleGuards() {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public @interface AdministratorOnly {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('SUPPLIER')")
    public @interface SupplierOnly {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('INVENTORY_MANAGER')")
    public @interface InventoryManagerOnly {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('SUPPLIER', 'INVENTORY_MANAGER', 'ADMINISTRATOR')")
    public @interface SupplyRecordsReadable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('INVENTORY_MANAGER', 'PRODUCTION_MANAGER')")
    public @interface InventoryAvailabilityReadable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('PRODUCTION_MANAGER')")
    public @interface ProductionManagerOnly {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER', 'ADMINISTRATOR')")
    public @interface ProductionTaskManageable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('SALES_OFFICER', 'ADMINISTRATOR')")
    public @interface OrderStaffReadable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('SALES_OFFICER', 'ADMINISTRATOR')")
    public @interface OrderStatusWritable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('SALES_OFFICER', 'ADMINISTRATOR')")
    public @interface OrderBillingWritable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAnyRole('PRODUCTION_MANAGER', 'SALES_OFFICER', 'ADMINISTRATOR')")
    public @interface OrderHandoffReadable {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('SALES_OFFICER')")
    public @interface SalesOfficerOnly {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('CUSTOMER')")
    public @interface CustomerOnly {}
}
