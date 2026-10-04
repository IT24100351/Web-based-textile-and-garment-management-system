package lk.ac.sliit.tgms.api;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.ac.sliit.tgms.admin.AdminSelfAccessException;
import lk.ac.sliit.tgms.admin.AdminUserNotFoundException;
import lk.ac.sliit.tgms.admin.AdminUserValidationException;
import lk.ac.sliit.tgms.admin.LastAdministratorException;
import lk.ac.sliit.tgms.auth.EmailAlreadyRegisteredException;
import lk.ac.sliit.tgms.auth.EmailNotVerifiedException;
import lk.ac.sliit.tgms.auth.InvalidEmailVerificationException;
import lk.ac.sliit.tgms.auth.InvalidCredentialsException;
import lk.ac.sliit.tgms.auth.InvalidPasswordResetException;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.auth.PasswordPolicyException;
import lk.ac.sliit.tgms.delivery.DeliveryOrderAlreadyAssignedException;
import lk.ac.sliit.tgms.delivery.DeliveryNotFoundException;
import lk.ac.sliit.tgms.delivery.DeliveryHistoryRequiredException;
import lk.ac.sliit.tgms.delivery.DeliveryOrderNotEligibleException;
import lk.ac.sliit.tgms.delivery.DeliveryScheduleConflictException;
import lk.ac.sliit.tgms.delivery.DeliveryStatusTransitionException;
import lk.ac.sliit.tgms.delivery.DeliveryValidationException;
import lk.ac.sliit.tgms.inventory.InventoryInsufficientStockException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialInUseException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialHasStockException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialNotFoundException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialUnavailableException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialCodeExistsException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialValidationException;
import lk.ac.sliit.tgms.notification.NotificationNotFoundException;
import lk.ac.sliit.tgms.notification.NotificationValidationException;
import lk.ac.sliit.tgms.order.OrderBillingStateException;
import lk.ac.sliit.tgms.order.OrderBillingValidationException;
import lk.ac.sliit.tgms.order.OrderCancellationException;
import lk.ac.sliit.tgms.order.OrderCustomerNotSelectableException;
import lk.ac.sliit.tgms.order.OrderNotFoundException;
import lk.ac.sliit.tgms.order.OrderInUseException;
import lk.ac.sliit.tgms.order.OrderReadValidationException;
import lk.ac.sliit.tgms.order.OrderStatusTransitionException;
import lk.ac.sliit.tgms.order.OrderStatusValidationException;
import lk.ac.sliit.tgms.order.OrderProductNotSelectableException;
import lk.ac.sliit.tgms.order.OrderValidationException;
import lk.ac.sliit.tgms.quotation.QuotationCustomerNotSelectableException;
import lk.ac.sliit.tgms.quotation.QuotationNotFoundException;
import lk.ac.sliit.tgms.quotation.QuotationProductNotSelectableException;
import lk.ac.sliit.tgms.quotation.QuotationValidationException;
import lk.ac.sliit.tgms.product.InactiveProductCategoryException;
import lk.ac.sliit.tgms.product.ProductInUseException;
import lk.ac.sliit.tgms.product.ProductNotFoundException;
import lk.ac.sliit.tgms.product.ProductNotSelectableException;
import lk.ac.sliit.tgms.product.ProductValidationException;
import lk.ac.sliit.tgms.profile.ProfilePasswordValidationException;
import lk.ac.sliit.tgms.production.ProductionOrderNotEligibleException;
import lk.ac.sliit.tgms.production.ProductionQualityControlException;
import lk.ac.sliit.tgms.production.ProductionTaskStartException;
import lk.ac.sliit.tgms.production.ProductionTaskStatusTransitionException;
import lk.ac.sliit.tgms.production.ProductionTaskCompletedException;
import lk.ac.sliit.tgms.production.ProductionTaskStatusValidationException;
import lk.ac.sliit.tgms.production.ProductionMaterialShortageException;
import lk.ac.sliit.tgms.production.ProductionTaskValidationException;
import lk.ac.sliit.tgms.production.ProductionTaskNotFoundException;
import lk.ac.sliit.tgms.production.ProductionTaskInUseException;
import lk.ac.sliit.tgms.production.ProductionTaskMaterialRequirementsLockedException;
import lk.ac.sliit.tgms.production.ProductionTaskMaterialUsageException;
import lk.ac.sliit.tgms.search.SharedSearchValidationException;
import lk.ac.sliit.tgms.report.ReportValidationException;
import lk.ac.sliit.tgms.supplier.MaterialSupplyCodeExistsException;
import lk.ac.sliit.tgms.supplier.MaterialSupplyInUseException;
import lk.ac.sliit.tgms.supplier.MaterialSupplyNotFoundException;
import lk.ac.sliit.tgms.supplier.MaterialSupplyValidationException;
import lk.ac.sliit.tgms.supplier.SupplierProfileNotFoundException;
import lk.ac.sliit.tgms.supplier.SupplierProfileValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validationFailed(
            MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", "Please correct the highlighted fields.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST", "Request body must be valid JSON."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> productImageTooLarge() {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR",
                        "Please correct the highlighted product fields.",
                        Map.of("imageFile", "Product photos must be 5 MB or smaller.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> requestValueTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        String field = safeRequestField(exception.getName());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR",
                        "Please correct the highlighted fields.",
                        Map.of(field, "Value must use the expected format.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingRequestParameter(
            MissingServletRequestParameterException exception) {
        String field = safeRequestField(exception.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR",
                        "Please correct the highlighted fields.",
                        Map.of(field, "This parameter is required.")));
    }

    private String safeRequestField(String value) {
        if (value == null || value.isBlank()) {
            return "request";
        }
        return value.matches("[A-Za-z][A-Za-z0-9_.\\[\\]-]{0,63}") ? value : "request";
    }

    @ExceptionHandler(PasswordPolicyException.class)
    public ResponseEntity<ApiErrorResponse> passwordPolicy(PasswordPolicyException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", "Please correct the highlighted fields.",
                        Map.of("password", exception.getMessage())));
    }

    @ExceptionHandler(ProfilePasswordValidationException.class)
    public ResponseEntity<ApiErrorResponse> profilePasswordValidation(
            ProfilePasswordValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(InvalidPasswordResetException.class)
    public ResponseEntity<ApiErrorResponse> invalidPasswordReset(
            InvalidPasswordResetException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_PASSWORD_RESET", exception.getMessage()));
    }

    @ExceptionHandler(InvalidEmailVerificationException.class)
    public ResponseEntity<ApiErrorResponse> invalidEmailVerification(
            InvalidEmailVerificationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_EMAIL_VERIFICATION", exception.getMessage()));
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiErrorResponse> emailNotVerified(
            EmailNotVerifiedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of("EMAIL_NOT_VERIFIED", exception.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> emailConflict(
            EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("EMAIL_ALREADY_REGISTERED", exception.getMessage()));
    }

    @ExceptionHandler(AdminUserValidationException.class)
    public ResponseEntity<ApiErrorResponse> adminUserValidation(
            AdminUserValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(AdminUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> adminUserNotFound(
            AdminUserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("USER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(AdminSelfAccessException.class)
    public ResponseEntity<ApiErrorResponse> adminSelfAccess(
            AdminSelfAccessException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("ADMIN_SELF_ACCESS_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(LastAdministratorException.class)
    public ResponseEntity<ApiErrorResponse> lastAdministrator(
            LastAdministratorException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("LAST_ADMINISTRATOR_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(ProductValidationException.class)
    public ResponseEntity<ApiErrorResponse> productValidation(
            ProductValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(InactiveProductCategoryException.class)
    public ResponseEntity<ApiErrorResponse> inactiveProductCategory(
            InactiveProductCategoryException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CATEGORY_INACTIVE", exception.getMessage()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> productNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("PRODUCT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ProductNotSelectableException.class)
    public ResponseEntity<ApiErrorResponse> productNotSelectable(
            ProductNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("PRODUCT_NOT_SELECTABLE", exception.getMessage()));
    }

    @ExceptionHandler(ProductInUseException.class)
    public ResponseEntity<ApiErrorResponse> productInUse(ProductInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("PRODUCT_IN_USE", exception.getMessage()));
    }

    @ExceptionHandler(SupplierProfileValidationException.class)
    public ResponseEntity<ApiErrorResponse> supplierProfileValidation(
            SupplierProfileValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(SupplierProfileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> supplierProfileNotFound(
            SupplierProfileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("SUPPLIER_PROFILE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(MaterialSupplyValidationException.class)
    public ResponseEntity<ApiErrorResponse> materialSupplyValidation(
            MaterialSupplyValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(MaterialSupplyCodeExistsException.class)
    public ResponseEntity<ApiErrorResponse> materialSupplyCodeExists(
            MaterialSupplyCodeExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "MATERIAL_SUPPLY_CODE_EXISTS",
                        exception.getMessage(),
                        Map.of("materialCode", exception.getMessage())));
    }

    @ExceptionHandler(MaterialSupplyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> materialSupplyNotFound(
            MaterialSupplyNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("MATERIAL_SUPPLY_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(MaterialSupplyInUseException.class)
    public ResponseEntity<ApiErrorResponse> materialSupplyInUse(
            MaterialSupplyInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("SUPPLY_IN_USE", exception.getMessage()));
    }

    @ExceptionHandler(InventoryMaterialValidationException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialValidation(
            InventoryMaterialValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(InventoryMaterialCodeExistsException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialCodeExists(
            InventoryMaterialCodeExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "INVENTORY_MATERIAL_CODE_EXISTS",
                        exception.getMessage(),
                        Map.of("materialCode", exception.getMessage())));
    }

    @ExceptionHandler(InventoryInsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> inventoryInsufficientStock(
            InventoryInsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "INSUFFICIENT_STOCK",
                        exception.getMessage(),
                        Map.of("quantity", exception.getMessage())));
    }

    @ExceptionHandler(InventoryMaterialInUseException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialInUse(
            InventoryMaterialInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("MATERIAL_IN_USE", exception.getMessage()));
    }

    @ExceptionHandler(InventoryMaterialHasStockException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialHasStock(InventoryMaterialHasStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("MATERIAL_HAS_STOCK", exception.getMessage()));
    }

    @ExceptionHandler(InventoryMaterialNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialNotFound(
            InventoryMaterialNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("INVENTORY_MATERIAL_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InventoryMaterialUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> inventoryMaterialUnavailable(
            InventoryMaterialUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("INVENTORY_MATERIAL_NOT_ACTIVE", exception.getMessage()));
    }



    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<ApiErrorResponse> reportValidation(
            ReportValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(SharedSearchValidationException.class)
    public ResponseEntity<ApiErrorResponse> sharedSearchValidation(
            SharedSearchValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(NotificationValidationException.class)
    public ResponseEntity<ApiErrorResponse> notificationValidation(
            NotificationValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notificationNotFound(
            NotificationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOTIFICATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(OrderReadValidationException.class)
    public ResponseEntity<ApiErrorResponse> orderReadValidation(
            OrderReadValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(OrderBillingValidationException.class)
    public ResponseEntity<ApiErrorResponse> orderBillingValidation(
            OrderBillingValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(OrderBillingStateException.class)
    public ResponseEntity<ApiErrorResponse> orderBillingState(
            OrderBillingStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(OrderCancellationException.class)
    public ResponseEntity<ApiErrorResponse> orderCancellation(OrderCancellationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "ORDER_CANCELLATION_BLOCKED",
                        exception.getMessage(),
                        Map.of("status", exception.getMessage())));
    }

    @ExceptionHandler(OrderStatusValidationException.class)
    public ResponseEntity<ApiErrorResponse> orderStatusValidation(
            OrderStatusValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(OrderStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> orderStatusTransition(
            OrderStatusTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "ORDER_STATUS_TRANSITION_NOT_ALLOWED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> orderNotFound(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("ORDER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(OrderInUseException.class)
    public ResponseEntity<ApiErrorResponse> orderInUse(OrderInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("ORDER_IN_USE", exception.getMessage()));
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ApiErrorResponse> orderValidation(OrderValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(OrderCustomerNotSelectableException.class)
    public ResponseEntity<ApiErrorResponse> orderCustomerNotSelectable(
            OrderCustomerNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "CUSTOMER_NOT_SELECTABLE",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(OrderProductNotSelectableException.class)
    public ResponseEntity<ApiErrorResponse> orderProductNotSelectable(
            OrderProductNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCT_NOT_SELECTABLE",
                        exception.getMessage(),
                        exception.fields()));
    }


    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> deliveryNotFound(DeliveryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("DELIVERY_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DeliveryHistoryRequiredException.class)
    public ResponseEntity<ApiErrorResponse> deliveryHistoryRequired(DeliveryHistoryRequiredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("DELIVERY_HISTORY_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(DeliveryStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> deliveryStatusTransition(
            DeliveryStatusTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "DELIVERY_STATUS_TRANSITION_NOT_ALLOWED", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(DeliveryValidationException.class)
    public ResponseEntity<ApiErrorResponse> deliveryValidation(DeliveryValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(DeliveryOrderNotEligibleException.class)
    public ResponseEntity<ApiErrorResponse> deliveryOrderNotEligible(
            DeliveryOrderNotEligibleException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "ORDER_NOT_READY_FOR_DELIVERY", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(DeliveryOrderAlreadyAssignedException.class)
    public ResponseEntity<ApiErrorResponse> deliveryOrderAlreadyAssigned(
            DeliveryOrderAlreadyAssignedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "DELIVERY_ALREADY_EXISTS", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(DeliveryScheduleConflictException.class)
    public ResponseEntity<ApiErrorResponse> deliveryScheduleConflict(
            DeliveryScheduleConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "DELIVERY_SCHEDULE_CONFLICT", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(ProductionTaskValidationException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskValidation(
            ProductionTaskValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(ProductionOrderNotEligibleException.class)
    public ResponseEntity<ApiErrorResponse> productionOrderNotEligible(
            ProductionOrderNotEligibleException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "ORDER_NOT_READY_FOR_PRODUCTION",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskNotFound(
            ProductionTaskNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("PRODUCTION_TASK_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ProductionTaskInUseException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskInUse(ProductionTaskInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("PRODUCTION_TASK_IN_USE", exception.getMessage()));
    }

    @ExceptionHandler(ProductionMaterialShortageException.class)
    public ResponseEntity<ApiErrorResponse> productionMaterialShortage(
            ProductionMaterialShortageException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_MATERIAL_SHORTAGE",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskStartException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskStartBlocked(
            ProductionTaskStartException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_TASK_START_NOT_ALLOWED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskMaterialUsageException.class)
    public ResponseEntity<ApiErrorResponse> productionMaterialUsageBlocked(
            ProductionTaskMaterialUsageException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_MATERIAL_USAGE_NOT_ALLOWED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionQualityControlException.class)
    public ResponseEntity<ApiErrorResponse> productionQualityControl(
            ProductionQualityControlException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_QUALITY_CONTROL_NOT_ALLOWED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskStatusValidationException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskStatusValidation(
            ProductionTaskStatusValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(ProductionTaskStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskStatusTransition(
            ProductionTaskStatusTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_STATUS_TRANSITION_NOT_ALLOWED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskMaterialRequirementsLockedException.class)
    public ResponseEntity<ApiErrorResponse> productionMaterialRequirementsLocked(
            ProductionTaskMaterialRequirementsLockedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCTION_MATERIAL_REQUIREMENTS_LOCKED",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(ProductionTaskCompletedException.class)
    public ResponseEntity<ApiErrorResponse> productionTaskCompleted(
            ProductionTaskCompletedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("PRODUCTION_TASK_COMPLETED", exception.getMessage()));
    }

    @ExceptionHandler(QuotationValidationException.class)
    public ResponseEntity<ApiErrorResponse> quotationValidation(
            QuotationValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFields(
                        "VALIDATION_ERROR", exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(QuotationCustomerNotSelectableException.class)
    public ResponseEntity<ApiErrorResponse> quotationCustomerNotSelectable(
            QuotationCustomerNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "CUSTOMER_NOT_SELECTABLE",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(QuotationProductNotSelectableException.class)
    public ResponseEntity<ApiErrorResponse> quotationProductNotSelectable(
            QuotationProductNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.withFields(
                        "PRODUCT_NOT_SELECTABLE",
                        exception.getMessage(),
                        exception.fields()));
    }

    @ExceptionHandler(QuotationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> quotationNotFound(QuotationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("QUOTATION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidSessionException.class})
    public ResponseEntity<ApiErrorResponse> unauthorized(RuntimeException exception) {
        String code = exception instanceof InvalidCredentialsException
                ? "INVALID_CREDENTIALS"
                : "INVALID_SESSION";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(code, exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> accessDenied() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean hasAuthenticatedUser = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (!hasAuthenticatedUser) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiErrorResponse.of(
                            "UNAUTHORIZED", "Authentication is required."));
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(
                        "FORBIDDEN", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpectedFailure(Exception exception) {
        LOGGER.error("Unhandled API failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_ERROR", "The request could not be completed."));
    }
}
