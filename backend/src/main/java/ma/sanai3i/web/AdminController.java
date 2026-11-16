package ma.sanai3i.web;

import jakarta.validation.Valid;
import ma.sanai3i.domain.Role;
import ma.sanai3i.dto.AdminDto.AuditRow;
import ma.sanai3i.dto.AdminDto.BlockInput;
import ma.sanai3i.dto.AdminDto.BroadcastInput;
import ma.sanai3i.dto.AdminDto.BroadcastResult;
import ma.sanai3i.dto.AdminDto.CancelInput;
import ma.sanai3i.dto.AdminDto.CategoryInput;
import ma.sanai3i.dto.AdminDto.CategoryRow;
import ma.sanai3i.dto.AdminDto.PageOf;
import ma.sanai3i.dto.AdminDto.RequestDetail;
import ma.sanai3i.dto.AdminDto.RequestRow;
import ma.sanai3i.dto.AdminDto.ReviewRow;
import ma.sanai3i.dto.AdminDto.RoleInput;
import ma.sanai3i.dto.AdminDto.SettingsDto;
import ma.sanai3i.dto.AdminDto.SettingsInput;
import ma.sanai3i.dto.AdminDto.Stats;
import ma.sanai3i.dto.AdminDto.SystemInfo;
import ma.sanai3i.dto.AdminDto.TempPassword;
import ma.sanai3i.dto.AdminDto.UserDetail;
import ma.sanai3i.dto.AdminDto.UserRow;
import ma.sanai3i.dto.AdminDto.VerifyInput;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.AdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reserved to ROLE_ADMIN by SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/stats")
    public Stats stats() {
        return admin.stats();
    }

    @GetMapping("/system")
    public SystemInfo system() {
        return admin.system();
    }

    // ---- users
    @GetMapping("/users")
    public PageOf<UserRow> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.users(q, role, status, page, size);
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable Long id) {
        return admin.user(id);
    }

    @PostMapping("/users/{id}/block")
    public UserRow block(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody BlockInput body) {
        return admin.block(me, id, body.reason());
    }

    @PostMapping("/users/{id}/unblock")
    public UserRow unblock(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return admin.unblock(me, id);
    }

    @PostMapping("/users/{id}/reset-password")
    public TempPassword resetPassword(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return new TempPassword(admin.resetPassword(me, id));
    }

    @PostMapping("/users/{id}/role")
    public UserRow role(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RoleInput body) {
        Role role = body.role();
        return admin.setRole(me, id, role);
    }

    @PostMapping("/artisans/{id}/verified")
    public UserRow verify(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody VerifyInput body) {
        return admin.verify(me, id, body.verified());
    }

    // ---- artisan activation
    @GetMapping("/artisan-files")
    public List<ma.sanai3i.dto.AdminDto.ArtisanReviewRow> artisanFiles(@RequestParam(required = false) String status) {
        return admin.artisanFiles(status);
    }

    @GetMapping("/artisan-files/{id}")
    public ma.sanai3i.dto.AdminDto.ArtisanReview artisanFile(@PathVariable Long id) {
        return admin.artisanFile(id);
    }

    @PostMapping("/artisan-files/{id}/approve")
    public ma.sanai3i.dto.AdminDto.ArtisanReview approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return admin.approveArtisan(me, id);
    }

    @PostMapping("/artisan-files/{id}/reject")
    public ma.sanai3i.dto.AdminDto.ArtisanReview reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                        @Valid @RequestBody ma.sanai3i.dto.AdminDto.ReviewDecision body) {
        return admin.rejectArtisan(me, id, body.reason());
    }

    // ---- requests and reviews
    @GetMapping("/requests")
    public PageOf<RequestRow> requests(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.requests(q, status, category, page, size);
    }

    @GetMapping("/requests/{id}")
    public RequestDetail request(@PathVariable Long id) {
        return admin.request(id);
    }

    @PostMapping("/requests/{id}/cancel")
    public RequestRow cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody CancelInput body) {
        return admin.cancelRequest(me, id, body.reason());
    }

    @GetMapping("/reviews")
    public PageOf<ReviewRow> reviews(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.reviews(page, size);
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        admin.deleteReview(me, id);
        return ResponseEntity.noContent().build();
    }

    // ---- categories
    @GetMapping("/categories")
    public List<CategoryRow> categories() {
        return admin.categories();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryRow> createCategory(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CategoryInput body) {
        return ResponseEntity.status(201).body(admin.createCategory(me, body));
    }

    @PutMapping("/categories/{id}")
    public CategoryRow updateCategory(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody CategoryInput body) {
        return admin.updateCategory(me, id, body);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        admin.deleteCategory(me, id);
        return ResponseEntity.noContent().build();
    }

    // ---- trail, broadcast, settings
    @GetMapping("/audit")
    public PageOf<AuditRow> audit(@RequestParam(required = false) String action, @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return admin.audit(action, q, page, size);
    }

    @PostMapping("/broadcast")
    public BroadcastResult broadcast(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BroadcastInput body) {
        return new BroadcastResult(admin.broadcast(me, body.audience(), body.message()));
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return admin.settings();
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SettingsInput body) {
        return admin.updateSettings(me, body.registrationsOpen(), body.bannerMessage());
    }

    // ---- exports
    @GetMapping("/export/users.csv")
    public ResponseEntity<byte[]> exportUsers() {
        return csv("users.csv", admin.exportUsers());
    }

    @GetMapping("/export/requests.csv")
    public ResponseEntity<byte[]> exportRequests() {
        return csv("requests.csv", admin.exportRequests());
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
