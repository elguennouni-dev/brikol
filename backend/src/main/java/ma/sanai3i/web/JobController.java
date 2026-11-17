package ma.sanai3i.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.JobService;
import ma.sanai3i.service.JobService.JobView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The job after an offer is accepted: arrival, extras, advances, the closing code, photos, problems and warranty. */
@RestController
@RequestMapping("/api/requests/{id}/job")
public class JobController {
    public record CodeInput(String code) {
    }

    public record ItemInput(String kind, @Size(max = 120) String label, int amount, String mediaId) {
    }

    public record AnswerInput(boolean accept) {
    }

    public record PhotoInput(String mediaId, String when) {
    }

    public record CaseInput(String kind, String reason, @Size(max = 1000) String details, String mediaId) {
    }

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    public JobView view(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return jobs.view(me, id);
    }

    @PostMapping("/arrived")
    public JobView arrived(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return jobs.arrived(me, id);
    }

    @PostMapping("/finished")
    public JobView finished(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return jobs.finished(me, id);
    }

    @PostMapping("/code")
    public JobView code(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody CodeInput in) {
        return jobs.enterCode(me, id, in.code());
    }

    @PostMapping("/confirm")
    public JobView confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return jobs.confirmByClient(me, id);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        jobs.withdraw(me, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    public JobView addItem(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ItemInput in) {
        return jobs.addItem(me, id, in.kind(), in.label(), in.amount(), in.mediaId());
    }

    @PostMapping("/items/{itemId}/answer")
    public JobView answer(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long itemId,
                          @RequestBody AnswerInput in) {
        return jobs.answerItem(me, id, itemId, in.accept());
    }

    @PostMapping("/photos")
    public JobView photo(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody PhotoInput in) {
        return jobs.addPhoto(me, id, in.mediaId(), in.when());
    }

    @PostMapping("/cases")
    public JobView openCase(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody CaseInput in) {
        return jobs.openCase(me, id, in.kind(), in.reason(), in.details(), in.mediaId());
    }

    @PostMapping("/cases/{caseId}/fixed")
    public JobView fixed(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long caseId) {
        return jobs.warrantyFixed(me, id, caseId);
    }

    @PostMapping("/cases/{caseId}/answer")
    public JobView warrantyAnswer(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long caseId,
                                  @RequestBody AnswerInput in) {
        return jobs.warrantyAnswer(me, id, caseId, in.accept());
    }
}
