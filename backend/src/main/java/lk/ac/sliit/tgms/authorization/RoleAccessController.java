package lk.ac.sliit.tgms.authorization;

import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.authorization.RoleGuards.AdministratorOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.InventoryManagerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.ProductionManagerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.SupplierOnly;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role-access")
public class RoleAccessController {

    @AdministratorOnly
    @GetMapping("/administrator")
    public RoleAccessResponse administratorAccess() {
        return allowed(UserRole.ADMINISTRATOR);
    }

    @SupplierOnly
    @GetMapping("/supplier")
    public RoleAccessResponse supplierAccess() {
        return allowed(UserRole.SUPPLIER);
    }

    @InventoryManagerOnly
    @GetMapping("/inventory-manager")
    public RoleAccessResponse inventoryManagerAccess() {
        return allowed(UserRole.INVENTORY_MANAGER);
    }

    @ProductionManagerOnly
    @GetMapping("/production-manager")
    public RoleAccessResponse productionManagerAccess() {
        return allowed(UserRole.PRODUCTION_MANAGER);
    }

    @SalesOfficerOnly
    @GetMapping("/sales-officer")
    public RoleAccessResponse salesOfficerAccess() {
        return allowed(UserRole.SALES_OFFICER);
    }

    private RoleAccessResponse allowed(UserRole role) {
        return new RoleAccessResponse(role, "Role access confirmed.");
    }

    public record RoleAccessResponse(UserRole role, String message) {}
}
