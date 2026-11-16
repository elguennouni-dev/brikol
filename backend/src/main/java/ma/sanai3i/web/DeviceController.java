package ma.sanai3i.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.sanai3i.domain.DeviceToken;
import ma.sanai3i.repo.DeviceTokenRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The app registers its push token after login (and removes it at logout). */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    public record DeviceInput(@NotBlank @Size(max = 400) String token, String platform, String lang) {
    }

    private final DeviceTokenRepository tokens;

    public DeviceController(DeviceTokenRepository tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> register(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DeviceInput in) {
        DeviceToken d = tokens.findByToken(in.token()).orElseGet(DeviceToken::new);
        d.userId = me.userId();                    // a shared phone moves to the user signed in now
        d.token = in.token();
        d.platform = "ANDROID".equalsIgnoreCase(in.platform()) ? "ANDROID" : "WEB";
        d.lang = "fr".equals(in.lang()) ? "fr" : "ar";
        d.lastSeenAt = Instant.now();
        tokens.save(d);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/remove")
    @Transactional
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody DeviceInput in) {
        tokens.findByToken(in.token()).filter(d -> d.userId.equals(me.userId())).ifPresent(tokens::delete);
        return ResponseEntity.noContent().build();
    }
}
