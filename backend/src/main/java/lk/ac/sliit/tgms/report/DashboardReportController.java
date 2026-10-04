package lk.ac.sliit.tgms.report;

import lk.ac.sliit.tgms.auth.InvalidSessionException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class DashboardReportController {
    private final DashboardReportService dashboardReportService;

    public DashboardReportController(DashboardReportService dashboardReportService) {
        this.dashboardReportService = dashboardReportService;
    }

    @GetMapping("/dashboard")
    public DashboardReport dashboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return dashboardReportService.getDashboard(currentUserId(jwt), from, to);
    }

    private long currentUserId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new InvalidSessionException();
        }
    }
}
