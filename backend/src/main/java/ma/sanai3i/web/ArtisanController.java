package ma.sanai3i.web;

import jakarta.validation.Valid;
import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.ServiceRequest;
import ma.sanai3i.dto.Dto.UpdateArtisanProfile;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.ArtisanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artisan/me")
public class ArtisanController {
    private final ArtisanService service;

    public ArtisanController(ArtisanService service) {
        this.service = service;
    }

    @GetMapping
    public ArtisanDetail profile(@AuthenticationPrincipal AuthUser me) {
        return service.profile(me);
    }

    @PatchMapping
    public ArtisanDetail update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateArtisanProfile body) {
        return service.update(me, body);
    }

    @PostMapping("/services")
    public ArtisanDetail addService(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ServiceRequest body) {
        return service.addService(me, body);
    }

    @DeleteMapping("/services/{id}")
    public ArtisanDetail deleteService(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.deleteService(me, id);
    }
}
