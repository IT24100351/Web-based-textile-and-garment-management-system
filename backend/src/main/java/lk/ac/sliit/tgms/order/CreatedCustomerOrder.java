package lk.ac.sliit.tgms.order;

import java.util.List;

public record CreatedCustomerOrder(
        CustomerOrder order,
        OrderCustomerOption customer,
        List<CreatedOrderItem> items) {

    public CreatedCustomerOrder {
        items = List.copyOf(items);
    }
}
