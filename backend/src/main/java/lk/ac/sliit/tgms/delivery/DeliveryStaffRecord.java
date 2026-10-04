package lk.ac.sliit.tgms.delivery;

import lk.ac.sliit.tgms.order.OrderDetail;

/**
 * Staff-facing Delivery read model. Delivery owns schedule/progress; Order Management remains the
 * source of truth for order/customer/item information and is resolved at read time.
 */
public record DeliveryStaffRecord(DeliveryRecord delivery, OrderDetail order) {}
