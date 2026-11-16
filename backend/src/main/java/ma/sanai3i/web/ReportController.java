package ma.sanai3i.web;

import jakarta.validation.constraints.Size;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReportController {
    public record HandleInput(String status, @Size(max = 500) String resolution) {
    }

    private final ReportService reports;
    private final ma.sanai3i.security.StaffAccess access;

    public ReportController(ReportService reports, ma.sanai3i.security.StaffAccess access) {
        this.reports = reports;
        this.access = access;
    }

    @PostMapping("/api/reports")
    public ResponseEntity<Void> create(@AuthenticationPrincipal AuthUser me, @RequestBody ReportService.ReportInput in) {
        reports.create(me, in);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/admin/reports")
    public List<ReportService.ReportRow> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String status) {
        access.require(me, ma.sanai3i.domain.Permission.REPORTS);
        return reports.list(status);
    }

    @PostMapping("/api/admin/reports/{id}")
    public ReportService.ReportRow handle(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                          @RequestBody HandleInput in) {
        access.require(me, ma.sanai3i.domain.Permission.REPORTS);
        return reports.handle(me, id, in.status(), in.resolution());
    }
}
