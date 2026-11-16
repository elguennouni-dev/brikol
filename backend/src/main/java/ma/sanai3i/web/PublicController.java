package ma.sanai3i.web;

import ma.sanai3i.service.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What every visitor needs before logging in: is sign-up open, banner, Google sign-in client. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    public record PublicConfig(boolean registrationsOpen, String bannerMessage, String googleClientId) {
    }

    private final SettingsService settings;
    private final String googleClientId;

    public PublicController(SettingsService settings, @Value("${app.google.client-id:}") String googleClientId) {
        this.settings = settings;
        this.googleClientId = googleClientId;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        return new PublicConfig(settings.registrationsOpen(), settings.banner(),
                googleClientId == null || googleClientId.isBlank() ? null : googleClientId);
    }
}
