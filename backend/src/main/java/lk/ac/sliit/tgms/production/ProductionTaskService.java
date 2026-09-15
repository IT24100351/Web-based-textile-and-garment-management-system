package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.ac.sliit.tgms.inventory.InventoryAvailabilityState;
import lk.ac.sliit.tgms.inventory.InventoryInsufficientStockException;
import lk.ac.sliit.tgms.inventory.InventoryMaterial;
import lk.ac.sliit.tgms.inventory.InventoryMaterialAvailability;
import lk.ac.sliit.tgms.inventory.InventoryMaterialService;
import lk.ac.sliit.tgms.inventory.InventoryMaterialUnavailableException;
import lk.ac.sliit.tgms.inventory.InventoryMaterialValidationException;
import lk.ac.sliit.tgms.notification.NotificationDispatchService;
import lk.ac.sliit.tgms.order.OrderHandoff;
import lk.ac.sliit.tgms.order.OrderStatus;
import lk.ac.sliit.tgms.order.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionTaskService {

    private static final int MAX_MATERIAL_REQUIREMENTS = 50;

    private final OrderService orderService;
    private final InventoryMaterialService inventoryMaterialService;
    private final ProductionTaskRepository productionTaskRepository;
    private final NotificationDispatchService notificationDispatchService;

    public ProductionTaskService(
            OrderService orderService,
            InventoryMaterialService inventoryMaterialService,
            ProductionTaskRepository productionTaskRepository,
            NotificationDispatchService notificationDispatchService) {
        this.orderService = orderService;
        this.inventoryMaterialService = inventoryMaterialService;
        this.productionTaskRepository = productionTaskRepository;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Transactional(readOnly = true)
    public List<OrderHandoff> getEligibleOrders() {
        return orderService.getProductionEligibleHandoffs();
    }

    /**
     * Inventory-owned material selector for Production. Production never reads inventory tables
     * directly and does not copy material master data into its own task relation.
     */
    @Transactional(readOnly = true)
    public List<ProductionInventoryMaterialOption> getMaterialOptions() {
        return inventoryMaterialService.getProductionSelectableMaterials().stream()
                .map(material -> new ProductionInventoryMaterialOption(
                        material.id(),
                        material.materialCode(),
                        material.materialName(),
                        material.materialType(),
                        material.unitOfMeasure(),
                        material.currentQuantity(),
                        material.status(),
                        material.stockState()))
                .toList();
    }

    @Transactional
    public ProductionTask createTask(long orderId) {
        if (orderId <= 0) {
            throw new ProductionTaskValidationException(
                    Map.of("orderId", "Select a valid positive order ID."));
        }

        OrderHandoff handoff = orderService.getHandoff(orderId);
        if (!handoff.readyForProduction()) {
            throw new ProductionOrderNotEligibleException();
        }

        return productionTaskRepository.createTask(orderId, generateTaskNumber());
    }

    @Transactional(readOnly = true)
    public List<ProductionTaskRecordSummary> getProductionRecords(String requestedView) {
        ProductionTaskRecordView view = ProductionTaskRecordView.parse(requestedView);
        return productionTaskRepository.findRecords(view).stream()
                .map(task -> {
                    OrderHandoff handoff = orderService.getHandoff(task.orderId());
                    return new ProductionTaskRecordSummary(
                            task,
                            handoff.orderNumber(),
                            handoff.customerId(),
                            handoff.currentStatus(),
                            handoff.items().size(),
                            handoff.readyForDelivery());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionTaskDetailView getTaskDetail(long taskId) {
        ProductionTask task = requireTask(taskId);
        return buildTaskDetail(task);
    }

    @Transactional(readOnly = true)
    public ProductionTaskMaterialAvailabilityReport checkMaterialAvailability(long taskId) {
        ProductionTask task = requireTask(taskId);
        return buildMaterialAvailabilityReport(task);
    }

    @Transactional(readOnly = true)
    public ProductionTaskMaterialUsageReport getMaterialUsage(long taskId) {
        ProductionTask task = requireTask(taskId);
        return buildMaterialUsageReport(task, productionTaskRepository.findMaterialUsage(task.id()));
    }

    /**
     * Deducts the complete TGMS-53 requirement set exactly once through Inventory Management's
     * atomic consumeStock contract. A task row lock serializes retries so duplicate submission
     * returns the existing history without double-deducting Inventory.
     */
    @Transactional
    public ProductionTaskMaterialUsageReport recordMaterialUsage(long taskId, long recordedByUserId) {
        if (recordedByUserId <= 0) {
            throw new ProductionTaskValidationException(
                    Map.of("recordedByUserId", "A valid authenticated staff user is required."));
        }
        ProductionTask task = requireTaskForUpdate(taskId);
        List<ProductionTaskMaterialRequirement> requirements =
                productionTaskRepository.findMaterialRequirements(task.id());
        List<ProductionTaskMaterialUsage> existing = productionTaskRepository.findMaterialUsage(task.id());
        if (!existing.isEmpty()) {
            if (usageMatchesRequirements(existing, requirements)) {
                return buildMaterialUsageReport(task, existing);
            }
            throw new ProductionTaskMaterialUsageException(
                    "Production material usage history is incomplete and cannot be safely replayed.",
                    Map.of("materials", "Review the saved usage history before attempting another deduction."));
        }
        if (task.status() != ProductionTaskStatus.IN_PROGRESS) {
            throw new ProductionTaskMaterialUsageException(
                    "Material usage can only be recorded for production that is in progress.",
                    Map.of("status", "Start the production task before recording material usage."));
        }
        if (requirements.isEmpty()) {
            throw new ProductionTaskMaterialUsageException(
                    "No approved material requirements are available to consume.",
                    Map.of("materials", "Assign material requirements before recording production usage."));
        }

        ProductionTaskMaterialAvailabilityReport availability = buildMaterialAvailabilityReport(task);
        if (!availability.allMaterialsAvailable()) {
            throw new ProductionMaterialShortageException(shortageFields(availability));
        }

        List<ProductionTaskMaterialUsage> recorded = new ArrayList<>();
        for (ProductionTaskMaterialRequirement requirement : requirements) {
            try {
                inventoryMaterialService.consumeStock(
                        requirement.inventoryMaterialId(), requirement.requiredQuantity());
            } catch (InventoryInsufficientStockException exception) {
                InventoryMaterial material =
                        inventoryMaterialService.requireMaterial(requirement.inventoryMaterialId());
                throw new ProductionMaterialShortageException(Map.of(
                        "materials", "Inventory changed before the Production deduction could finish. No Production usage was recorded.",
                        "inventoryMaterialId", material.materialCode() + " requires "
                                + exception.requestedQuantity().toPlainString() + " " + material.unitOfMeasure()
                                + " but Inventory currently has " + exception.currentQuantity().toPlainString()
                                + " " + material.unitOfMeasure() + "."));
            } catch (InventoryMaterialUnavailableException exception) {
                InventoryMaterial material =
                        inventoryMaterialService.requireMaterial(requirement.inventoryMaterialId());
                throw new ProductionMaterialShortageException(Map.of(
                        "materials", "Inventory changed before the Production deduction could finish. No Production usage was recorded.",
                        "inventoryMaterialId", material.materialCode()
                                + " is no longer active in Inventory."));
            }
            recorded.add(productionTaskRepository.createMaterialUsage(
                    task.id(),
                    requirement.inventoryMaterialId(),
                    requirement.requiredQuantity(),
                    recordedByUserId));
        }
        return buildMaterialUsageReport(task, recorded);
    }

    /**
     * Backward-compatible TGMS-54 start operation. TGMS-56 routes it through the same central
     * lifecycle implementation used by the generic status-update endpoint.
     */
    @Transactional
    public ProductionTaskDetailView startTask(long taskId, long changedByUserId) {
        ProductionTask task = requireTaskForUpdate(taskId);
        if (task.status() != ProductionTaskStatus.PENDING) {
            throw new ProductionTaskStartException(
                    "Only a pending production task can be started.",
                    Map.of("status", "This production task is already "
                            + task.status().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "."));
        }
        return transitionToInProgress(task, changedByUserId);
    }

    /**
     * TGMS-56 central Production lifecycle update. Only PENDING -> IN_PROGRESS and
     * IN_PROGRESS -> COMPLETED are permitted. Order progress is synchronized through the
     * Order Management service contract in the same transaction.
     */
    @Transactional
    public ProductionTaskDetailView updateStatus(
            long taskId,
            String requestedStatus,
            long changedByUserId) {
        if (changedByUserId <= 0) {
            throw new ProductionTaskStatusValidationException(
                    Map.of("changedByUserId", "A valid authenticated staff user is required."));
        }
        ProductionTaskStatus requested = parseStatus(requestedStatus);
        ProductionTask task = requireTaskForUpdate(taskId);
        if (!ProductionTaskStatusLifecycle.canTransition(task.status(), requested)) {
            throw new ProductionTaskStatusTransitionException(task.status(), requested);
        }
        return switch (requested) {
            case IN_PROGRESS -> transitionToInProgress(task, changedByUserId);
            case COMPLETED -> transitionToCompleted(task, changedByUserId);
            case PENDING -> throw new ProductionTaskStatusTransitionException(task.status(), requested);
        };
    }

    private ProductionTaskDetailView transitionToInProgress(
            ProductionTask task,
            long changedByUserId) {
        ProductionTaskMaterialAvailabilityReport availability = buildMaterialAvailabilityReport(task);
        if (!availability.hasMaterialRequirements()) {
            throw new ProductionTaskStartException(
                    "Assign required materials before starting production.",
                    Map.of("materials", "Assign at least one required inventory material before starting production."));
        }
        if (!availability.allMaterialsAvailable()) {
            throw new ProductionMaterialShortageException(shortageFields(availability));
        }

        OrderHandoff orderBeforeStart = orderService.getHandoff(task.orderId());
        int updated = productionTaskRepository.startPendingTask(task.id());
        if (updated != 1) {
            ProductionTask latest = requireTask(task.id());
            throw new ProductionTaskStartException(
                    "The production task could not be started because its status changed.",
                    Map.of("status", "Current production status is " + latest.status().name() + ". Reload and try again."));
        }

        OrderHandoff orderAfterStart =
                orderService.synchronizeProductionStarted(changedByUserId, task.orderId());
        if (orderBeforeStart.currentStatus() == OrderStatus.CONFIRMED
                && orderAfterStart.currentStatus() == OrderStatus.IN_PRODUCTION) {
            notificationDispatchService.productionStatus(
                    orderAfterStart.customerId(),
                    task.id(),
                    orderAfterStart.orderNumber(),
                    ProductionTaskStatus.IN_PROGRESS);
        }
        return buildTaskDetail(requireTask(task.id()));
    }

    private ProductionTaskDetailView transitionToCompleted(
            ProductionTask task,
            long changedByUserId) {
        List<ProductionTaskMaterialRequirement> requirements =
                productionTaskRepository.findMaterialRequirements(task.id());
        List<ProductionTaskMaterialUsage> usage = productionTaskRepository.findMaterialUsage(task.id());
        if (requirements.isEmpty() || !usageMatchesRequirements(usage, requirements)) {
            throw new ProductionTaskStatusTransitionException(
                    "Production cannot be completed until all approved material usage is recorded.",
                    Map.of("materialUsage",
                            "Record the complete approved material usage before marking production completed."));
        }
        if (task.qualityControlResult() != ProductionQualityControlResult.PASSED) {
            throw new ProductionTaskStatusTransitionException(
                    "Production cannot be completed until quality control has passed.",
                    Map.of("qualityControl", "Record a PASSED quality-control result before marking production completed."));
        }

        int updated = productionTaskRepository.completeInProgressTask(task.id());
        if (updated != 1) {
            ProductionTask latest = requireTask(task.id());
            throw new ProductionTaskStatusTransitionException(latest.status(), ProductionTaskStatus.COMPLETED);
        }

        if (productionTaskRepository.countIncompleteTasksForOrder(task.orderId()) == 0L) {
            OrderHandoff completedOrder =
                    orderService.synchronizeProductionCompleted(changedByUserId, task.orderId());
            notificationDispatchService.productionStatus(
                    completedOrder.customerId(),
                    task.id(),
                    completedOrder.orderNumber(),
                    ProductionTaskStatus.COMPLETED);
        }
        return buildTaskDetail(requireTask(task.id()));
    }

    @Transactional
    public ProductionTaskDetailView recordQualityControl(
            long taskId,
            String requestedResult,
            long checkedByUserId) {
        if (checkedByUserId <= 0) {
            throw new ProductionQualityControlException(
                    "A valid authenticated staff user is required.",
                    Map.of("qualityControl", "Sign in again before recording quality control."));
        }
        ProductionQualityControlResult result;
        try {
            result = ProductionQualityControlResult.valueOf(
                    requestedResult == null ? "" : requestedResult.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProductionQualityControlException(
                    "Quality-control result must be PASSED or FAILED.",
                    Map.of("qualityControl", "Choose PASSED or FAILED."));
        }
        if (result == ProductionQualityControlResult.PENDING) {
            throw new ProductionQualityControlException(
                    "Quality-control result must be PASSED or FAILED.",
                    Map.of("qualityControl", "Choose PASSED or FAILED."));
        }

        ProductionTask task = requireTaskForUpdate(taskId);
        if (task.status() != ProductionTaskStatus.IN_PROGRESS) {
            throw new ProductionQualityControlException(
                    "Quality control can only be recorded while production is in progress.",
                    Map.of("status", "Start production before recording quality control."));
        }
        List<ProductionTaskMaterialRequirement> requirements =
                productionTaskRepository.findMaterialRequirements(task.id());
        List<ProductionTaskMaterialUsage> usage = productionTaskRepository.findMaterialUsage(task.id());
        if (requirements.isEmpty() || !usageMatchesRequirements(usage, requirements)) {
            throw new ProductionQualityControlException(
                    "Quality control can only be recorded after approved material usage is complete.",
                    Map.of("materialUsage", "Record the complete approved material usage before quality control."));
        }

        if (productionTaskRepository.updateQualityControl(task.id(), result, checkedByUserId) != 1) {
            throw new ProductionQualityControlException(
                    "Quality control could not be recorded because the production task changed.",
                    Map.of("status", "Reload the production task and try again."));
        }
        return buildTaskDetail(requireTask(task.id()));
    }

    @Transactional
    public ProductionTaskDetailView saveTaskDetails(
            long taskId,
            String workDetails,
            String workAssignment,
            String workNotes) {
        ProductionTask task = requireTask(taskId);
        ValidatedWorkDetails validated = validateWorkDetails(workDetails, workAssignment, workNotes);

        productionTaskRepository.saveWorkDetails(
                task.id(),
                validated.workDetails(),
                validated.workAssignment(),
                validated.workNotes());

        return buildTaskDetail(task);
    }

    /**
     * Replaces the complete material requirement set after validating every line. This does not
     * consume Inventory stock; later Production consumption must use Inventory's atomic
     * consumeStock contract.
     */
    @Transactional
    public ProductionTaskDetailView saveMaterialRequirements(
            long taskId,
            List<ProductionTaskMaterialRequirementCommand> requirements) {
        ProductionTask task = requireTask(taskId);
        if (task.status() != ProductionTaskStatus.PENDING) {
            throw new ProductionTaskMaterialRequirementsLockedException();
        }
        List<ProductionTaskMaterialRequirementCommand> validated =
                validateMaterialRequirements(requirements);
        productionTaskRepository.replaceMaterialRequirements(task.id(), validated);
        return buildTaskDetail(task);
    }

    private ProductionTaskDetailView buildTaskDetail(ProductionTask task) {
        return new ProductionTaskDetailView(
                task,
                orderService.getHandoff(task.orderId()),
                productionTaskRepository.findWorkDetails(task.id()).orElse(null),
                resolveMaterialRequirements(task.id()));
    }

    private ProductionTaskMaterialAvailabilityReport buildMaterialAvailabilityReport(ProductionTask task) {
        List<ProductionTaskMaterialRequirementView> materials = resolveMaterialRequirements(task.id());
        boolean hasRequirements = !materials.isEmpty();
        boolean allAvailable = hasRequirements && materials.stream()
                .allMatch(material -> material.availabilityState() == InventoryAvailabilityState.AVAILABLE);
        return new ProductionTaskMaterialAvailabilityReport(
                task.id(),
                task.taskNumber(),
                task.status(),
                hasRequirements,
                allAvailable,
                task.status() == ProductionTaskStatus.PENDING && allAvailable,
                materials);
    }

    private Map<String, String> shortageFields(ProductionTaskMaterialAvailabilityReport availability) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("materials", "Resolve every material shortage before starting production.");
        for (int index = 0; index < availability.materials().size(); index++) {
            ProductionTaskMaterialRequirementView item = availability.materials().get(index);
            if (item.availabilityState() == InventoryAvailabilityState.AVAILABLE) {
                continue;
            }
            String message;
            if (item.availabilityState() == InventoryAvailabilityState.NOT_ACTIVE) {
                message = item.materialCode() + " is not active in Inventory and cannot support production start.";
            } else {
                message = item.materialCode() + " requires " + item.requiredQuantity().toPlainString()
                        + " " + item.unitOfMeasure() + " but Inventory currently has "
                        + item.currentQuantity().toPlainString() + " " + item.unitOfMeasure() + ".";
            }
            fields.put("materials[" + index + "]", message);
        }
        return fields;
    }

    private List<ProductionTaskMaterialRequirementView> resolveMaterialRequirements(long taskId) {
        return productionTaskRepository.findMaterialRequirements(taskId).stream()
                .map(requirement -> {
                    InventoryMaterial material =
                            inventoryMaterialService.requireMaterial(requirement.inventoryMaterialId());
                    InventoryMaterialAvailability availability = inventoryMaterialService.checkAvailability(
                            requirement.inventoryMaterialId(), requirement.requiredQuantity());
                    return new ProductionTaskMaterialRequirementView(
                            material.id(),
                            material.materialCode(),
                            material.materialName(),
                            material.materialType(),
                            material.unitOfMeasure(),
                            requirement.requiredQuantity(),
                            availability.currentQuantity(),
                            availability.status(),
                            availability.availabilityState(),
                            requirement.createdAt(),
                            requirement.updatedAt());
                })
                .toList();
    }

    private ProductionTaskMaterialUsageReport buildMaterialUsageReport(
            ProductionTask task,
            List<ProductionTaskMaterialUsage> usage) {
        List<ProductionTaskMaterialUsageView> materials = usage.stream()
                .map(entry -> {
                    InventoryMaterial material = inventoryMaterialService.requireMaterial(entry.inventoryMaterialId());
                    return new ProductionTaskMaterialUsageView(
                            entry.id(),
                            entry.inventoryMaterialId(),
                            material.materialCode(),
                            material.materialName(),
                            material.unitOfMeasure(),
                            entry.quantityUsed(),
                            material.currentQuantity(),
                            entry.recordedByUserId(),
                            entry.recordedAt());
                })
                .toList();
        return new ProductionTaskMaterialUsageReport(
                task.id(), task.taskNumber(), task.status(), !materials.isEmpty(), materials);
    }

    private boolean usageMatchesRequirements(
            List<ProductionTaskMaterialUsage> usage,
            List<ProductionTaskMaterialRequirement> requirements) {
        if (usage.size() != requirements.size()) {
            return false;
        }
        Map<Long, BigDecimal> expected = new LinkedHashMap<>();
        requirements.forEach(requirement -> expected.put(
                requirement.inventoryMaterialId(), requirement.requiredQuantity()));
        return usage.stream().allMatch(entry -> {
            BigDecimal required = expected.get(entry.inventoryMaterialId());
            return required != null && required.compareTo(entry.quantityUsed()) == 0;
        });
    }

    private ProductionTask requireTaskForUpdate(long taskId) {
        if (taskId <= 0) {
            throw new ProductionTaskValidationException(
                    Map.of("taskId", "Use a valid positive production task ID."));
        }
        return productionTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(ProductionTaskNotFoundException::new);
    }

    private List<ProductionTaskMaterialRequirementCommand> validateMaterialRequirements(
            List<ProductionTaskMaterialRequirementCommand> requirements) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (requirements == null || requirements.isEmpty()) {
            throw new ProductionTaskValidationException(
                    Map.of("materials", "Assign at least one required inventory material."));
        }
        if (requirements.size() > MAX_MATERIAL_REQUIREMENTS) {
            throw new ProductionTaskValidationException(Map.of(
                    "materials",
                    "A production task can contain at most " + MAX_MATERIAL_REQUIREMENTS
                            + " material requirements."));
        }

        Set<Long> seenMaterialIds = new HashSet<>();
        List<ProductionTaskMaterialRequirementCommand> validated = new ArrayList<>();
        for (int index = 0; index < requirements.size(); index++) {
            ProductionTaskMaterialRequirementCommand requirement = requirements.get(index);
            String prefix = "materials[" + index + "]";
            if (requirement == null) {
                fields.put(prefix, "Choose an inventory material and enter its required quantity.");
                continue;
            }

            Long materialId = requirement.inventoryMaterialId();
            BigDecimal requiredQuantity = requirement.requiredQuantity();
            boolean idValid = true;
            boolean quantityValid = true;

            if (materialId == null || materialId <= 0) {
                fields.put(prefix + ".inventoryMaterialId", "Select a valid inventory material.");
                idValid = false;
            } else if (!seenMaterialIds.add(materialId)) {
                fields.put(
                        prefix + ".inventoryMaterialId",
                        "This inventory material is already assigned to the production task.");
                idValid = false;
            }

            if (requiredQuantity == null) {
                fields.put(prefix + ".requiredQuantity", "Required quantity is required.");
                quantityValid = false;
            }

            if (idValid && quantityValid) {
                try {
                    InventoryMaterialAvailability availability = inventoryMaterialService.checkAvailability(
                            materialId, requiredQuantity);
                    if (availability.availabilityState() == InventoryAvailabilityState.NOT_ACTIVE) {
                        fields.put(
                                prefix + ".inventoryMaterialId",
                                "Select an active inventory material for new production requirements.");
                        idValid = false;
                    }
                } catch (InventoryMaterialValidationException exception) {
                    String materialError = exception.fields().get("materialId");
                    String quantityError = exception.fields().get("requiredQuantity");
                    if (materialError != null) {
                        fields.put(prefix + ".inventoryMaterialId", materialError);
                        idValid = false;
                    }
                    if (quantityError != null) {
                        fields.put(prefix + ".requiredQuantity", quantityError);
                        quantityValid = false;
                    }
                }
            }

            if (idValid && quantityValid) {
                validated.add(new ProductionTaskMaterialRequirementCommand(materialId, requiredQuantity));
            }
        }

        if (!fields.isEmpty()) {
            throw new ProductionTaskValidationException(fields);
        }
        return List.copyOf(validated);
    }

    private ProductionTask requireTask(long taskId) {
        if (taskId <= 0) {
            throw new ProductionTaskValidationException(
                    Map.of("taskId", "Use a valid positive production task ID."));
        }
        return productionTaskRepository.findById(taskId)
                .orElseThrow(ProductionTaskNotFoundException::new);
    }

    private ProductionTaskStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            throw new ProductionTaskStatusValidationException(
                    Map.of("status", "Choose a production status."));
        }
        try {
            return ProductionTaskStatus.valueOf(
                    requestedStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProductionTaskStatusValidationException(
                    Map.of("status", "Status must be PENDING, IN_PROGRESS, or COMPLETED."));
        }
    }

    private ValidatedWorkDetails validateWorkDetails(
            String workDetails,
            String workAssignment,
            String workNotes) {
        Map<String, String> fields = new LinkedHashMap<>();
        String normalizedDetails = normalizeRequired(workDetails);
        String normalizedAssignment = normalizeRequired(workAssignment);
        String normalizedNotes = normalizeOptional(workNotes);

        if (normalizedDetails == null) {
            fields.put("workDetails", "Enter the manufacturing work details for this task.");
        } else if (normalizedDetails.length() > 1000) {
            fields.put("workDetails", "Work details must be 1000 characters or fewer.");
        }

        if (normalizedAssignment == null) {
            fields.put("workAssignment", "Enter a work assignment, team, line, or responsible work unit.");
        } else if (normalizedAssignment.length() > 255) {
            fields.put("workAssignment", "Work assignment must be 255 characters or fewer.");
        }

        if (normalizedNotes != null && normalizedNotes.length() > 2000) {
            fields.put("workNotes", "Work notes must be 2000 characters or fewer.");
        }

        if (!fields.isEmpty()) {
            throw new ProductionTaskValidationException(fields);
        }

        return new ValidatedWorkDetails(normalizedDetails, normalizedAssignment, normalizedNotes);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptional(String value) {
        return normalizeRequired(value);
    }

    private String generateTaskNumber() {
        return "PRD-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }

    private record ValidatedWorkDetails(
            String workDetails,
            String workAssignment,
            String workNotes) {}
}
