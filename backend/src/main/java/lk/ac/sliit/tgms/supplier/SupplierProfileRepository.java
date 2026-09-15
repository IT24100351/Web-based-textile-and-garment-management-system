package lk.ac.sliit.tgms.supplier;

import java.util.Optional;

public interface SupplierProfileRepository {

    Optional<SupplierProfile> findByUserId(long userId);

    long create(long userId, String businessName, String contactPhone, String address);

    int update(long userId, String businessName, String contactPhone, String address);
}
