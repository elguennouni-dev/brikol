package ma.sanai3i.web;

import ma.sanai3i.dto.AdminDto.TempPassword;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.StaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration team, owner only (checked in StaffService). */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    public List<StaffService.StaffRow> list(@AuthenticationPrincipal AuthUser me) {
        return staff.list(me);
    }

    @PostMapping
    public ResponseEntity<StaffService.StaffRow> create(@AuthenticationPrincipal AuthUser me, @RequestBody StaffService.StaffInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staff.create(me, in));
    }

    @PutMapping("/{id}")
    public StaffService.StaffRow update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody StaffService.StaffInput in) {
        return staff.update(me, id, in);
    }

    @PostMapping("/{id}/block")
    public StaffService.StaffRow block(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return staff.setBlocked(me, id, true);
    }

    @PostMapping("/{id}/unblock")
    public StaffService.StaffRow unblock(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return staff.setBlocked(me, id, false);
    }

    @PostMapping("/{id}/reset-password")
    public TempPassword reset(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return new TempPassword(staff.resetPassword(me, id));
    }

    @PostMapping("/{id}/demote")
    public ResponseEntity<Void> demote(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        staff.demote(me, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        staff.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
