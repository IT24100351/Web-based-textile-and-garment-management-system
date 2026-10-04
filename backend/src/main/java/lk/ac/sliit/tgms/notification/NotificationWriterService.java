package lk.ac.sliit.tgms.notification;

import java.util.LinkedHashSet;
import java.util.Set;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.inventory.InventoryMaterial;
import lk.ac.sliit.tgms.order.OrderStatus;
import lk.ac.sliit.tgms.production.ProductionTaskStatus;
import lk.ac.sliit.tgms.delivery.DeliveryStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationWriterService {

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    public NotificationWriterService(
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeLowStock(InventoryMaterial material) {
        Set<Long> recipients = new LinkedHashSet<>();
        activeUserIds(UserRole.INVENTORY_MANAGER, recipients);
        activeUserIds(UserRole.ADMINISTRATOR, recipients);
        String title = "Low stock: " + material.materialCode();
        String message = String.format(
                "%s has %s %s remaining; the configured low-stock threshold is %s.",
                material.materialName(),
                material.currentQuantity().stripTrailingZeros().toPlainString(),
                material.unitOfMeasure(),
                material.lowStockThreshold().stripTrailingZeros().toPlainString());
        for (long recipientId : recipients) {
            notificationRepository.create(
                    recipientId,
                    NotificationKind.LOW_STOCK,
                    title,
                    message,
                    NotificationSourceModule.INVENTORY,
                    material.id());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeOrderStatus(
            long customerId, long orderId, String orderNumber, OrderStatus status) {
        String statusText = humanize(status.name());
        notificationRepository.create(
                customerId,
                NotificationKind.ORDER_STATUS,
                "Order " + statusText,
                "Order " + orderNumber + " is now " + statusText.toLowerCase() + ".",
                NotificationSourceModule.ORDER,
                orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeProductionStatus(
            long customerId,
            long productionTaskId,
            String orderNumber,
            ProductionTaskStatus status) {
        String title = status == ProductionTaskStatus.IN_PROGRESS
                ? "Production started"
                : "Production completed";
        String message = status == ProductionTaskStatus.IN_PROGRESS
                ? "Production has started for order " + orderNumber + "."
                : "Production is complete for order " + orderNumber + " and it is ready for delivery preparation.";
        notificationRepository.create(
                customerId,
                NotificationKind.PRODUCTION_STATUS,
                title,
                message,
                NotificationSourceModule.PRODUCTION,
                productionTaskId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeDeliveryStatus(
            long customerId,
            long deliveryId,
            String deliveryNumber,
            String orderNumber,
            DeliveryStatus status) {
        String statusText = humanize(status.name());
        notificationRepository.create(
                customerId,
                NotificationKind.DELIVERY_STATUS,
                "Delivery " + statusText,
                "Delivery " + deliveryNumber + " for order " + orderNumber + " is now "
                        + statusText.toLowerCase() + ".",
                NotificationSourceModule.DELIVERY,
                deliveryId);
    }

    private void activeUserIds(UserRole role, Set<Long> target) {
        userAccountRepository.findActiveByRole(role, null).stream()
                .map(UserAccount::id)
                .forEach(target::add);
    }

    private String humanize(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
