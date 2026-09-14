package lk.ac.sliit.tgms.supplier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MaterialSupplyRepository {

    long create(
            long supplierId,
            String materialCode,
            String materialName,
            String materialDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes);

    Optional<MaterialSupply> findById(long supplyId);

    int updateDetails(
            long supplyId,
            long supplierId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes);

    int archive(long supplyId, long supplierId);

    List<MaterialSupplyListItem> findAll(MaterialSupplyQuery query);
}
