package lk.ac.sliit.tgms.order;

/** Controlled lifecycle values stored in orders.status. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    IN_PRODUCTION,
    READY_FOR_DELIVERY,
    COMPLETED,
    CANCELLED
}
