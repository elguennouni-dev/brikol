package ma.sanai3i.web;

import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.ArtisanSummary;
import ma.sanai3i.dto.Dto.CategoryDto;
import ma.sanai3i.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Categories are public. The artisan list is for staff; a profile only for those who got an offer from that artisan. */
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    private final ma.sanai3i.security.StaffAccess access;

    public CatalogController(CatalogService catalog, ma.sanai3i.security.StaffAccess access) {
        this.catalog = catalog;
        this.access = access;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return catalog.categories();
    }

    @GetMapping("/artisans")
    public List<ArtisanSummary> artisans(@org.springframework.security.core.annotation.AuthenticationPrincipal ma.sanai3i.security.AuthUser me,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(required = false) Boolean availableToday) {
        access.staff(me);
        return catalog.search(category, q, city, availableToday);
    }

    @GetMapping("/artisans/{id}")
    public ArtisanDetail artisan(@org.springframework.security.core.annotation.AuthenticationPrincipal ma.sanai3i.security.AuthUser me,
                                 @PathVariable Long id) {
        return catalog.artisan(me, me.role() == ma.sanai3i.domain.Role.ADMIN && access.has(me, ma.sanai3i.domain.Permission.USERS), id);
    }
}
