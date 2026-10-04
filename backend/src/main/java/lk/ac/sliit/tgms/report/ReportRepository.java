package lk.ac.sliit.tgms.report;

import java.time.Instant;

public interface ReportRepository {
    ReportSection orders(Instant fromInclusive, Instant toExclusive, Long customerId);

    ReportSection inventorySnapshot();

    ReportSection production(Instant fromInclusive, Instant toExclusive);

    ReportSection deliveries(Instant fromInclusive, Instant toExclusive);
}
