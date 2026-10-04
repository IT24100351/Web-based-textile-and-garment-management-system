package lk.ac.sliit.tgms.report;

import java.util.List;

public record ReportSection(String key, String title, String basis, List<ReportMetric> metrics) {
    public ReportSection {
        metrics = List.copyOf(metrics);
    }
}
