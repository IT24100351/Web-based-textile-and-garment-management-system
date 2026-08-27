package lk.ac.sliit.tgms.order;

/** Manual payment record states. No external payment gateway is involved. */
public enum OrderPaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID
}
