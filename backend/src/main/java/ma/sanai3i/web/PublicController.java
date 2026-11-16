package ma.sanai3i.web;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.dto.Dto.PriceRangeDto;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.service.PricingService;
import ma.sanai3i.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What every visitor needs before logging in: is sign-up open, banner, Google sign-in client. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    /** firebase: web push config (null when push is off). */
    public record PublicConfig(boolean registrationsOpen, String bannerMessage, String googleClientId,
                               boolean smsEnabled, Map<String, String> firebase, boolean monetization) {
    }

    private final SettingsService settings;
    private final String googleClientId;
    private final CategoryRepository categories;
    private final PricingService pricing;
    private final ma.sanai3i.service.OtpService otp;
    private final ma.sanai3i.push.PushService push;
    private final Map<String, String> firebase;

    public PublicController(SettingsService settings, @Value("${app.google.client-id:}") String googleClientId,
                            CategoryRepository categories, PricingService pricing,
                            ma.sanai3i.service.OtpService otp, ma.sanai3i.push.PushService push,
                            @Value("${app.push.web-api-key:}") String apiKey, @Value("${app.push.web-app-id:}") String appId,
                            @Value("${app.push.project-id:}") String projectId, @Value("${app.push.sender-id:}") String senderId,
                            @Value("${app.push.vapid-key:}") String vapidKey) {
        this.otp = otp;
        this.push = push;
        this.firebase = apiKey.isBlank() || appId.isBlank() || projectId.isBlank() || senderId.isBlank() || vapidKey.isBlank()
                ? null
                : Map.of("apiKey", apiKey, "appId", appId, "projectId", projectId, "messagingSenderId", senderId, "vapidKey", vapidKey);
        this.settings = settings;
        this.categories = categories;
        this.pricing = pricing;
        this.googleClientId = googleClientId;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        return new PublicConfig(settings.registrationsOpen(), settings.banner(),
                googleClientId == null || googleClientId.isBlank() ? null : googleClientId,
                otp.enabled(), push.enabled() ? firebase : null, settings.monetizationEnabled());
    }

    /** Allowed price range of a trade for a new request (see PricingService). */
    @GetMapping("/pricing/{code}")
    public PriceRangeDto pricing(@PathVariable String code) {
        var c = categories.findByCode(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_CATEGORY"));
        PricingService.Range r = pricing.range(c);
        return new PriceRangeDto(c.code, r.min(), r.max(), r.typicalLow(), r.typicalHigh(), r.samples());
    }
}
