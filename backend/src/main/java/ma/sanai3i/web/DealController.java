package ma.sanai3i.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.DealService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/requests/{id}")
public class DealController {
    public record MessageInput(@NotBlank @Size(max = 2000) String body) {
    }

    public record AppointmentInput(@NotNull Instant at) {
    }

    public record ClientReviewInput(@Min(1) @Max(5) int rating, @Size(max = 1000) String body) {
    }

    private final DealService deals;

    public DealController(DealService deals) {
        this.deals = deals;
    }

    @GetMapping("/messages")
    public DealService.Chat chat(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return deals.chat(me, id);
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public DealService.MessageDto send(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody MessageInput in) {
        return deals.send(me, id, in.body());
    }

    @PostMapping("/appointment")
    public DealService.Chat propose(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody AppointmentInput in) {
        return deals.proposeAppointment(me, id, in.at());
    }

    @PostMapping("/appointment/confirm")
    public DealService.Chat confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return deals.confirmAppointment(me, id);
    }

    @PostMapping("/client-review")
    public ResponseEntity<Void> reviewClient(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                             @Valid @RequestBody ClientReviewInput in) {
        deals.reviewClient(me, id, in.rating(), in.body());
        return ResponseEntity.noContent().build();
    }
}
