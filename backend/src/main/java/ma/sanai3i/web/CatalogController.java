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

/** Public, read-only: anyone can browse artisans without an account. */
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return catalog.categories();
    }

    @GetMapping("/artisans")
    public List<ArtisanSummary> artisans(@RequestParam(required = false) String category,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(required = false) Boolean availableToday) {
        return catalog.search(category, q, city, availableToday);
    }

    @GetMapping("/artisans/{id}")
    public ArtisanDetail artisan(@PathVariable Long id) {
        return catalog.artisan(id);
    }
}
