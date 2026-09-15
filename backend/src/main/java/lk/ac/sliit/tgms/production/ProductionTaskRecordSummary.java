package lk.ac.sliit.tgms.production;

import lk.ac.sliit.tgms.order.OrderStatus;

/** Production record summary enriched through the Order handoff instead of copied order data. */
public record ProductionTaskRecordSummary(
        ProductionTask task,
        String orderNumber,
        long customerId,
        OrderStatus orderStatus,
        int orderItemCount,
        boolean readyForDelivery) {}
