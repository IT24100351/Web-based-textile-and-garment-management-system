package lk.ac.sliit.tgms.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.ac.sliit.tgms.auth.UserRole;

public record DashboardReport(
        UserRole role,
        LocalDate from,
        LocalDate to,
        String timeZone,
        Instant generatedAt,
        List<ReportSection> sections,
        List<String> notes) {
    public DashboardReport {
        sections = List.copyOf(sections);
        notes = List.copyOf(notes);
    }
}
