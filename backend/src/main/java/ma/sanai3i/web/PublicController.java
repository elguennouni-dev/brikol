package ma.sanai3i.web;

import ma.sanai3i.dto.AdminDto.SettingsDto;
import ma.sanai3i.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What every visitor needs before logging in: is sign-up open, is there a banner to show. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final SettingsService settings;

    public PublicController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/config")
    public SettingsDto config() {
        return new SettingsDto(settings.registrationsOpen(), settings.banner());
    }
}
