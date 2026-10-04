package lk.ac.sliit.tgms.report;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardReportService {
    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final long MAX_RANGE_DAYS = 365;
    private static final String REPORT_TIME_ZONE = "UTC";

    private final AuthService authService;
    private final ReportRepository reportRepository;
    private final Clock clock = Clock.systemUTC();

    public DashboardReportService(AuthService authService, ReportRepository reportRepository) {
        this.authService = authService;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public DashboardReport getDashboard(long userId, String rawFrom, String rawTo) {
        UserAccount account = authService.requireActiveUser(userId);
        DateRange range = resolveRange(rawFrom, rawTo);
        Instant fromInclusive = range.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = range.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<ReportSection> sections = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        switch (account.role()) {
            case ADMINISTRATOR -> {
                sections.add(reportRepository.orders(fromInclusive, toExclusive, null));
                sections.add(reportRepository.inventorySnapshot());
                sections.add(reportRepository.production(fromInclusive, toExclusive));
                sections.add(reportRepository.deliveries(fromInclusive, toExclusive));
            }
            case INVENTORY_MANAGER -> sections.add(reportRepository.inventorySnapshot());
            case PRODUCTION_MANAGER -> sections.add(reportRepository.production(fromInclusive, toExclusive));
            case SALES_OFFICER -> {
                sections.add(reportRepository.orders(fromInclusive, toExclusive, null));
                sections.add(reportRepository.deliveries(fromInclusive, toExclusive));
            }
            case CUSTOMER -> sections.add(reportRepository.orders(fromInclusive, toExclusive, userId));
            case SUPPLIER -> notes.add(
                    "Supplier Management has no Order, Inventory, Production or Delivery report scope in this ticket, so no cross-module operational metrics are exposed.");
        }

        notes.add("Date-filtered metrics use records created from " + range.from() + " through "
                + range.to() + " inclusive in UTC.");
        if (sections.stream().anyMatch(section -> section.key().equals("inventory"))) {
            notes.add("Inventory metrics are a current snapshot and are intentionally not constrained by the date range.");
        }
        return new DashboardReport(
                account.role(),
                range.from(),
                range.to(),
                REPORT_TIME_ZONE,
                clock.instant(),
                sections,
                notes);
    }

    private DateRange resolveRange(String rawFrom, String rawTo) {
        Map<String, String> fields = new LinkedHashMap<>();
        boolean fromProvided = rawFrom != null && !rawFrom.isBlank();
        boolean toProvided = rawTo != null && !rawTo.isBlank();
        if (fromProvided != toProvided) {
            if (!fromProvided) {
                fields.put("from", "Provide both from and to dates, or omit both for the default period.");
            }
            if (!toProvided) {
                fields.put("to", "Provide both from and to dates, or omit both for the default period.");
            }
            throw new ReportValidationException(fields);
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!fromProvided) {
            return new DateRange(today.minusDays(DEFAULT_PERIOD_DAYS - 1L), today);
        }

        LocalDate from = parseDate("from", rawFrom, fields);
        LocalDate to = parseDate("to", rawTo, fields);
        if (!fields.isEmpty()) {
            throw new ReportValidationException(fields);
        }
        if (from.isAfter(to)) {
            fields.put("from", "From date must be on or before the to date.");
        } else if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            fields.put("to", "Report range cannot exceed 366 calendar days.");
        }
        if (!fields.isEmpty()) {
            throw new ReportValidationException(fields);
        }
        return new DateRange(from, to);
    }

    private LocalDate parseDate(String field, String value, Map<String, String> fields) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            fields.put(field, "Use ISO date format YYYY-MM-DD.");
            return LocalDate.ofEpochDay(0);
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}
