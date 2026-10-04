package lk.ac.sliit.tgms.notification;

import java.util.concurrent.Executor;
import lk.ac.sliit.tgms.delivery.DeliveryStatus;
import lk.ac.sliit.tgms.inventory.InventoryMaterial;
import lk.ac.sliit.tgms.inventory.InventoryStockState;
import lk.ac.sliit.tgms.order.OrderStatus;
import lk.ac.sliit.tgms.production.ProductionTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationWriterService writerService;
    private final Executor notificationExecutor;
    private final boolean enabled;

    public NotificationDispatchService(
            NotificationWriterService writerService,
            @Qualifier("notificationExecutor") Executor notificationExecutor,
            @Value("${tgms.notifications.enabled:true}") boolean enabled) {
        this.writerService = writerService;
        this.notificationExecutor = notificationExecutor;
        this.enabled = enabled;
    }

    public void lowStock(InventoryMaterial material) {
        if (material == null || material.stockState() != InventoryStockState.LOW_STOCK) {
            return;
        }
        scheduleAfterCommit(
                "INVENTORY", material.id(), () -> writerService.writeLowStock(material));
    }

    public void orderStatus(
            long customerId, long orderId, String orderNumber, OrderStatus status) {
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.CANCELLED) {
            return;
        }
        scheduleAfterCommit(
                "ORDER", orderId,
                () -> writerService.writeOrderStatus(customerId, orderId, orderNumber, status));
    }

    public void productionStatus(
            long customerId,
            long productionTaskId,
            String orderNumber,
            ProductionTaskStatus status) {
        if (status != ProductionTaskStatus.IN_PROGRESS && status != ProductionTaskStatus.COMPLETED) {
            return;
        }
        scheduleAfterCommit(
                "PRODUCTION",
                productionTaskId,
                () -> writerService.writeProductionStatus(
                        customerId, productionTaskId, orderNumber, status));
    }

    public void deliveryStatus(
            long customerId,
            long deliveryId,
            String deliveryNumber,
            String orderNumber,
            DeliveryStatus status) {
        if (status != DeliveryStatus.OUT_FOR_DELIVERY
                && status != DeliveryStatus.DELIVERED
                && status != DeliveryStatus.CANCELLED) {
            return;
        }
        scheduleAfterCommit(
                "DELIVERY",
                deliveryId,
                () -> writerService.writeDeliveryStatus(
                        customerId, deliveryId, deliveryNumber, orderNumber, status));
    }

    private void scheduleAfterCommit(String source, long sourceRecordId, Runnable writer) {
        if (!enabled) {
            return;
        }
        Runnable enqueue = () -> {
            try {
                notificationExecutor.execute(() -> runSafely(source, sourceRecordId, writer));
            } catch (RuntimeException exception) {
                logFailure("scheduling", source, sourceRecordId, exception);
            }
        };

        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        enqueue.run();
                    }
                });
            } else {
                enqueue.run();
            }
        } catch (RuntimeException exception) {
            // Notification infrastructure is explicitly best-effort. It must never fail the
            // inventory/order/production/delivery transaction that produced the real event.
            logFailure("registration", source, sourceRecordId, exception);
        }
    }

    private void runSafely(String source, long sourceRecordId, Runnable writer) {
        try {
            writer.run();
        } catch (RuntimeException exception) {
            logFailure("persistence", source, sourceRecordId, exception);
        }
    }

    private void logFailure(
            String phase, String source, long sourceRecordId, RuntimeException exception) {
        LOGGER.error(
                "Operational notification {} failed for {} record {} ({}).",
                phase,
                source,
                sourceRecordId,
                exception.getClass().getSimpleName());
    }
}
