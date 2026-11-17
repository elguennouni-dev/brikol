package ma.sanai3i.web;

import jakarta.validation.constraints.Size;
import ma.sanai3i.domain.Permission;
import ma.sanai3i.domain.User;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.security.StaffAccess;
import ma.sanai3i.service.CaseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Problems between clients and artisans, warranty claims and strikes. Needs the REPORTS permission. */
@RestController
public class CaseController {
    public record HandleInput(String status, @Size(max = 500) String resolution, String strikeClient, String strikeArtisan,
                              boolean closeJob) {
    }

    public record StrikeInput(String reason, Long requestId) {
    }

    private final CaseService cases;
    private final StaffAccess access;

    public CaseController(CaseService cases, StaffAccess access) {
        this.cases = cases;
        this.access = access;
    }

    @GetMapping("/api/admin/cases")
    public List<CaseService.CaseRow> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String status) {
        access.require(me, Permission.REPORTS);
        return cases.list(status);
    }

    @GetMapping("/api/admin/cases/{id}")
    public CaseService.CaseDetail detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        access.require(me, Permission.REPORTS);
        return cases.detail(id);
    }

    @PostMapping("/api/admin/cases/{id}")
    public CaseService.CaseDetail handle(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody HandleInput in) {
        User staff = access.require(me, Permission.REPORTS);
        return cases.handle(staff, id, in.status(), in.resolution(), in.strikeClient(), in.strikeArtisan(), in.closeJob());
    }

    @GetMapping("/api/admin/users/{userId}/strikes")
    public CaseService.Party strikes(@AuthenticationPrincipal AuthUser me, @PathVariable Long userId) {
        access.require(me, Permission.REPORTS);
        return cases.party(userId);
    }

    @PostMapping("/api/admin/users/{userId}/strikes")
    public CaseService.Party strike(@AuthenticationPrincipal AuthUser me, @PathVariable Long userId, @RequestBody StrikeInput in) {
        User staff = access.require(me, Permission.REPORTS);
        return cases.strike(staff, userId, in.requestId(), in.reason());
    }

    @DeleteMapping("/api/admin/strikes/{id}")
    public CaseService.Party removeStrike(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        User staff = access.require(me, Permission.REPORTS);
        return cases.removeStrike(staff, id);
    }
}
