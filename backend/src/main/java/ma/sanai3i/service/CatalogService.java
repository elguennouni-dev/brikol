package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.ArtisanSummary;
import ma.sanai3i.dto.Dto.CategoryDto;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CategoryRepository categories;
    private final ArtisanProfileRepository artisans;
    private final ReviewRepository reviews;
    private final Mapper mapper;

    public CatalogService(CategoryRepository categories, ArtisanProfileRepository artisans,
                          ReviewRepository reviews, Mapper mapper) {
        this.categories = categories;
        this.artisans = artisans;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    public List<CategoryDto> categories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.id, c.code, c.nameAr, c.nameFr))
                .toList();
    }

    /**
     * Filters in memory after loading (by category when given). Fine for the first few thousand artisans;
     * move the filters into a JPQL/Specification query when the catalog grows.
     */
    public List<ArtisanSummary> search(String category, String q, String city, Boolean availableToday) {
        List<ArtisanProfile> base = (category == null || category.isBlank())
                ? artisans.findAllByOrderByVerifiedDescIdAsc()
                : artisans.findByCategory_CodeOrderByVerifiedDescIdAsc(category.trim());

        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String wantedCity = city == null ? "" : city.trim();
        Map<Long, Mapper.Rating> ratings = mapper.allRatings();

        return base.stream()
                .filter(a -> a.status == ma.sanai3i.domain.ArtisanStatus.APPROVED)
                .filter(a -> wantedCity.isEmpty() || ma.sanai3i.common.Cities.same(a.city, wantedCity))
                .filter(a -> availableToday == null || a.availableToday == availableToday)
                .filter(a -> needle.isEmpty() || haystack(a).contains(needle))
                .map(a -> mapper.summary(a, ratings.getOrDefault(a.id, Mapper.NO_RATING)))
                .sorted(Comparator.comparing(ArtisanSummary::featured).reversed()
                        .thenComparing(Comparator.comparingDouble(ArtisanSummary::rating).reversed())
                        .thenComparing(Comparator.comparingLong(ArtisanSummary::reviewCount).reversed()))
                .toList();
    }

    public ArtisanDetail artisan(Long id) {
        ArtisanProfile a = artisans.findById(id)
                .filter(x -> x.status == ma.sanai3i.domain.ArtisanStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTISAN_NOT_FOUND"));
        return mapper.detail(a, mapper.ratingOf(a.id), reviews.findByArtisanIdOrderByCreatedAtDesc(a.id));
    }

    private static String haystack(ArtisanProfile a) {
        return Stream.of(a.nameAr, a.nameFr, a.tradeAr, a.tradeFr, a.neighborhoodAr, a.neighborhoodFr,
                        a.category == null ? null : a.category.nameAr, a.category == null ? null : a.category.nameFr, a.bioAr, a.bioFr)
                .filter(s -> s != null)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
