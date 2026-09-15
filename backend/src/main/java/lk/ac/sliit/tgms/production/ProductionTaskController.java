package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.authorization.RoleGuards.ProductionManagerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.ProductionTaskManageable;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.inventory.InventoryAvailabilityState;
import lk.ac.sliit.tgms.inventory.InventoryMaterialStatus;
import lk.ac.sliit.tgms.inventory.InventoryMaterialType;
import lk.ac.sliit.tgms.inventory.InventoryStockState;
import lk.ac.sliit.tgms.order.OrderHandoff;
import lk.ac.sliit.tgms.order.OrderHandoffItem;
import lk.ac.sliit.tgms.order.OrderStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/tasks")
public class ProductionTaskController {

    private final ProductionTaskService productionTaskService;

    public ProductionTaskController(ProductionTaskService productionTaskService) {
        this.productionTaskService = productionTaskService;
    }

    @GetMapping
    @ProductionTaskManageable
    public List<ProductionTaskRecordSummaryResponse> records(
            @RequestParam(required = false) String view) {
        return productionTaskService.getProductionRecords(view).stream()
                .map(ProductionTaskRecordSummaryResponse::from)
                .toList();
    }

    @GetMapping("/eligible-orders")
    @ProductionManagerOnly
    public List<EligibleOrderResponse> eligibleOrders() {
        return productionTaskService.getEligibleOrders().stream()
                .map(EligibleOrderResponse::from)
                .toList();
    }

    @GetMapping("/material-options")
    @ProductionTaskManageable
    public List<ProductionMaterialOptionResponse> materialOptions() {
        return productionTaskService.getMaterialOptions().stream()
                .map(ProductionMaterialOptionResponse::from)
                .toList();
    }

    @PostMapping
    @ProductionManagerOnly
    public CreateProductionTaskResponse create(@RequestBody CreateProductionTaskRequest request) {
        ProductionTask task = productionTaskService.createTask(request == null ? 0L : request.orderId());
        return new CreateProductionTaskResponse(
                "Production task created successfully.", ProductionTaskResponse.from(task));
    }

    @GetMapping("/{taskId}")
    @ProductionTaskManageable
    public ProductionTaskDetailResponse detail(@PathVariable long taskId) {
        return ProductionTaskDetailResponse.from(productionTaskService.getTaskDetail(taskId));
    }

    @GetMapping("/{taskId}/material-availability")
    @ProductionTaskManageable
    public ProductionTaskMaterialAvailabilityResponse materialAvailability(@PathVariable long taskId) {
        return ProductionTaskMaterialAvailabilityResponse.from(
                productionTaskService.checkMaterialAvailability(taskId));
    }

    @PostMapping("/{taskId}/start")
    @ProductionTaskManageable
    public StartProductionTaskResponse start(
            @PathVariable long taskId,
            @AuthenticationPrincipal Jwt jwt) {
        ProductionTaskDetailView detail = productionTaskService.startTask(taskId, currentUserId(jwt));
        return new StartProductionTaskResponse(
                "Production started successfully.",
                ProductionTaskDetailResponse.from(detail));
    }

    @PatchMapping("/{taskId}/status")
    @ProductionTaskManageable
    public UpdateProductionTaskStatusResponse updateStatus(
            @PathVariable long taskId,
            @RequestBody UpdateProductionTaskStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ProductionTaskDetailView detail = productionTaskService.updateStatus(
                taskId, request == null ? null : request.status(), currentUserId(jwt));
        return new UpdateProductionTaskStatusResponse(
                "Production status updated successfully.",
                ProductionTaskDetailResponse.from(detail));
    }

    @PatchMapping("/{taskId}/quality-control")
    @ProductionTaskManageable
    public UpdateProductionQualityControlResponse updateQualityControl(
            @PathVariable long taskId,
            @RequestBody UpdateProductionQualityControlRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ProductionTaskDetailView detail = productionTaskService.recordQualityControl(
                taskId,
                request == null ? null : request.result(),
                currentUserId(jwt));
        return new UpdateProductionQualityControlResponse(
                "Quality-control result recorded successfully.",
                ProductionTaskDetailResponse.from(detail));
    }

    @GetMapping("/{taskId}/material-usage")
    @ProductionTaskManageable
    public ProductionTaskMaterialUsageResponse materialUsage(@PathVariable long taskId) {
        return ProductionTaskMaterialUsageResponse.from(
                productionTaskService.getMaterialUsage(taskId));
    }

    @PostMapping("/{taskId}/material-usage")
    @ProductionTaskManageable
    public RecordProductionTaskMaterialUsageResponse recordMaterialUsage(
            @PathVariable long taskId,
            @AuthenticationPrincipal Jwt jwt) {
        ProductionTaskMaterialUsageReport usage = productionTaskService.recordMaterialUsage(
                taskId, currentUserId(jwt));
        return new RecordProductionTaskMaterialUsageResponse(
                usage.usageRecorded()
                        ? "Production material usage recorded successfully."
                        : "No production material usage was recorded.",
                ProductionTaskMaterialUsageResponse.from(usage));
    }

    @PutMapping("/{taskId}/details")
    @ProductionTaskManageable
    public UpdateProductionTaskDetailsResponse updateDetails(
            @PathVariable long taskId,
            @RequestBody UpdateProductionTaskDetailsRequest request) {
        ProductionTaskDetailView detail = productionTaskService.saveTaskDetails(
                taskId,
                request == null ? null : request.workDetails(),
                request == null ? null : request.workAssignment(),
                request == null ? null : request.workNotes());
        return new UpdateProductionTaskDetailsResponse(
                "Production task details saved successfully.",
                ProductionTaskDetailResponse.from(detail));
    }

    @PutMapping("/{taskId}/materials")
    @ProductionTaskManageable
    public UpdateProductionTaskMaterialsResponse updateMaterials(
            @PathVariable long taskId,
            @RequestBody UpdateProductionTaskMaterialsRequest request) {
        List<ProductionTaskMaterialRequirementCommand> commands = request == null
                || request.materials() == null
                ? null
                : request.materials().stream()
                        .map(item -> item == null
                                ? null
                                : new ProductionTaskMaterialRequirementCommand(
                                        item.inventoryMaterialId(), item.requiredQuantity()))
                        .toList();
        ProductionTaskDetailView detail =
                productionTaskService.saveMaterialRequirements(taskId, commands);
        return new UpdateProductionTaskMaterialsResponse(
                "Production material requirements saved successfully.",
                ProductionTaskDetailResponse.from(detail));
    }

    private long currentUserId(Jwt jwt) {
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

    public record CreateProductionTaskRequest(long orderId) {}

    public record CreateProductionTaskResponse(String message, ProductionTaskResponse task) {}

    public record UpdateProductionTaskDetailsRequest(
            String workDetails,
            String workAssignment,
            String workNotes) {}

    public record UpdateProductionTaskDetailsResponse(
            String message,
            ProductionTaskDetailResponse task) {}

    public record UpdateProductionTaskMaterialsRequest(List<ProductionTaskMaterialRequirementRequest> materials) {}

    public record ProductionTaskMaterialRequirementRequest(
            Long inventoryMaterialId,
            BigDecimal requiredQuantity) {}

    public record UpdateProductionTaskMaterialsResponse(
            String message,
            ProductionTaskDetailResponse task) {}

    public record StartProductionTaskResponse(
            String message,
            ProductionTaskDetailResponse task) {}

    public record UpdateProductionTaskStatusRequest(String status) {}

    public record UpdateProductionTaskStatusResponse(
            String message,
            ProductionTaskDetailResponse task) {}

    public record UpdateProductionQualityControlRequest(String result) {}

    public record UpdateProductionQualityControlResponse(
            String message,
            ProductionTaskDetailResponse task) {}

    public record ProductionTaskRecordSummaryResponse(
            ProductionTaskResponse task,
            String orderNumber,
            long customerId,
            OrderStatus orderStatus,
            int orderItemCount,
            boolean readyForDelivery) {
        static ProductionTaskRecordSummaryResponse from(ProductionTaskRecordSummary summary) {
            return new ProductionTaskRecordSummaryResponse(
                    ProductionTaskResponse.from(summary.task()),
                    summary.orderNumber(),
                    summary.customerId(),
                    summary.orderStatus(),
                    summary.orderItemCount(),
                    summary.readyForDelivery());
        }
    }

    public record RecordProductionTaskMaterialUsageResponse(
            String message,
            ProductionTaskMaterialUsageResponse usage) {}

    public record ProductionTaskMaterialUsageResponse(
            long taskId,
            String taskNumber,
            ProductionTaskStatus taskStatus,
            boolean usageRecorded,
            List<ProductionTaskMaterialUsageItemResponse> materials) {
        static ProductionTaskMaterialUsageResponse from(ProductionTaskMaterialUsageReport report) {
            return new ProductionTaskMaterialUsageResponse(
                    report.taskId(),
                    report.taskNumber(),
                    report.taskStatus(),
                    report.usageRecorded(),
                    report.materials().stream()
                            .map(ProductionTaskMaterialUsageItemResponse::from)
                            .toList());
        }
    }

    public record ProductionTaskMaterialUsageItemResponse(
            long id,
            long inventoryMaterialId,
            String materialCode,
            String materialName,
            String unitOfMeasure,
            BigDecimal quantityUsed,
            BigDecimal remainingQuantity,
            long recordedByUserId,
            Instant recordedAt) {
        static ProductionTaskMaterialUsageItemResponse from(ProductionTaskMaterialUsageView usage) {
            return new ProductionTaskMaterialUsageItemResponse(
                    usage.id(),
                    usage.inventoryMaterialId(),
                    usage.materialCode(),
                    usage.materialName(),
                    usage.unitOfMeasure(),
                    usage.quantityUsed(),
                    usage.remainingQuantity(),
                    usage.recordedByUserId(),
                    usage.recordedAt());
        }
    }

    public record ProductionTaskMaterialAvailabilityResponse(
            long taskId,
            String taskNumber,
            ProductionTaskStatus taskStatus,
            boolean hasMaterialRequirements,
            boolean allMaterialsAvailable,
            boolean canStart,
            List<ProductionTaskMaterialRequirementResponse> materials) {
        static ProductionTaskMaterialAvailabilityResponse from(
                ProductionTaskMaterialAvailabilityReport report) {
            return new ProductionTaskMaterialAvailabilityResponse(
                    report.taskId(),
                    report.taskNumber(),
                    report.taskStatus(),
                    report.hasMaterialRequirements(),
                    report.allMaterialsAvailable(),
                    report.canStart(),
                    report.materials().stream()
                            .map(ProductionTaskMaterialRequirementResponse::from)
                            .toList());
        }
    }

    public record ProductionTaskResponse(
            long id,
            String taskNumber,
            long orderId,
            ProductionTaskStatus status,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            ProductionQualityControlResult qualityControlResult,
            Long qualityCheckedByUserId,
            Instant qualityCheckedAt) {
        static ProductionTaskResponse from(ProductionTask task) {
            return new ProductionTaskResponse(
                    task.id(), task.taskNumber(), task.orderId(), task.status(), task.startedAt(),
                    task.completedAt(), task.createdAt(), task.updatedAt(),
                    task.qualityControlResult(), task.qualityCheckedByUserId(), task.qualityCheckedAt());
        }
    }

    public record ProductionTaskDetailResponse(
            ProductionTaskResponse task,
            EligibleOrderResponse order,
            ProductionTaskWorkDetailsResponse workDetails,
            List<ProductionTaskMaterialRequirementResponse> materialRequirements) {
        static ProductionTaskDetailResponse from(ProductionTaskDetailView detail) {
            return new ProductionTaskDetailResponse(
                    ProductionTaskResponse.from(detail.task()),
                    EligibleOrderResponse.from(detail.order()),
                    ProductionTaskWorkDetailsResponse.from(detail.workDetails()),
                    detail.materialRequirements().stream()
                            .map(ProductionTaskMaterialRequirementResponse::from)
                            .toList());
        }
    }

    public record ProductionTaskWorkDetailsResponse(
            String workDetails,
            String workAssignment,
            String workNotes,
            Instant createdAt,
            Instant updatedAt) {
        static ProductionTaskWorkDetailsResponse from(ProductionTaskWorkDetails details) {
            if (details == null) {
                return null;
            }
            return new ProductionTaskWorkDetailsResponse(
                    details.workDetails(), details.workAssignment(), details.workNotes(),
                    details.createdAt(), details.updatedAt());
        }
    }

    public record ProductionTaskMaterialRequirementResponse(
            long inventoryMaterialId,
            String materialCode,
            String materialName,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal requiredQuantity,
            BigDecimal currentQuantity,
            InventoryMaterialStatus inventoryStatus,
            InventoryAvailabilityState availabilityState,
            Instant createdAt,
            Instant updatedAt) {
        static ProductionTaskMaterialRequirementResponse from(
                ProductionTaskMaterialRequirementView requirement) {
            return new ProductionTaskMaterialRequirementResponse(
                    requirement.inventoryMaterialId(),
                    requirement.materialCode(),
                    requirement.materialName(),
                    requirement.materialType(),
                    requirement.unitOfMeasure(),
                    requirement.requiredQuantity(),
                    requirement.currentQuantity(),
                    requirement.inventoryStatus(),
                    requirement.availabilityState(),
                    requirement.createdAt(),
                    requirement.updatedAt());
        }
    }

    public record ProductionMaterialOptionResponse(
            long inventoryMaterialId,
            String materialCode,
            String materialName,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            InventoryMaterialStatus status,
            InventoryStockState stockState) {
        static ProductionMaterialOptionResponse from(ProductionInventoryMaterialOption option) {
            return new ProductionMaterialOptionResponse(
                    option.inventoryMaterialId(),
                    option.materialCode(),
                    option.materialName(),
                    option.materialType(),
                    option.unitOfMeasure(),
                    option.currentQuantity(),
                    option.status(),
                    option.stockState());
        }
    }

    public record EligibleOrderResponse(
            long orderId,
            String orderNumber,
            long customerId,
            OrderStatus currentStatus,
            boolean readyForProduction,
            List<EligibleOrderItemResponse> items) {
        static EligibleOrderResponse from(OrderHandoff handoff) {
            return new EligibleOrderResponse(
                    handoff.orderId(), handoff.orderNumber(), handoff.customerId(),
                    handoff.currentStatus(), handoff.readyForProduction(),
                    handoff.items().stream().map(EligibleOrderItemResponse::from).toList());
        }
    }

    public record EligibleOrderItemResponse(
            long orderItemId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor) {
        static EligibleOrderItemResponse from(OrderHandoffItem item) {
            return new EligibleOrderItemResponse(
                    item.orderItemId(), item.productId(), item.variantId(), item.quantity(),
                    item.selectedSize(), item.selectedColor());
        }
    }
}
