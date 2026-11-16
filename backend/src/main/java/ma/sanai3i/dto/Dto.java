package ma.sanai3i.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.sanai3i.domain.OfferStatus;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.WhenPref;

import java.time.Instant;
import java.util.List;

/** Request and response bodies of the REST API. */
public final class Dto {
    private Dto() {
    }

    // ---------- auth ----------
    public record RegisterRequest(
            @NotBlank String phone,
            @NotBlank @Size(min = 6, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 80) String city,
            Role role,
            String categoryCode,
            @Size(max = 80) String neighborhood) {
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    public record UserDto(Long id, String phone, String fullName, Role role, String city, Long artisanId) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    // ---------- catalog ----------
    public record CategoryDto(Long id, String code, String nameAr, String nameFr) {
    }

    public record ArtisanSummary(
            Long id,
            String nameAr, String nameFr,
            String tradeAr, String tradeFr,
            String neighborhoodAr, String neighborhoodFr,
            String city,
            String categoryCode,
            double rating, long reviewCount,
            int yearsExperience, int jobsDone,
            boolean verified, boolean availableToday,
            Integer fromPrice, String fromUnitAr, String fromUnitFr) {
    }

    public record ServiceDto(Long id, String labelAr, String labelFr, int price, String unitAr, String unitFr) {
    }

    public record ReviewDto(Long id, String clientName, int rating, String body, Instant createdAt) {
    }

    public record ArtisanDetail(
            ArtisanSummary summary,
            String bioAr, String bioFr,
            String phone, String whatsapp,
            List<ServiceDto> services,
            List<ReviewDto> reviews) {
    }

    // ---------- requests and offers ----------
    public record CreateRequest(
            @NotBlank String categoryCode,
            @NotBlank @Size(min = 5, max = 2000) String description,
            @Size(max = 80) String city,
            @Size(max = 80) String neighborhood,
            WhenPref whenPref,
            @Min(0) @Max(1000000) int budget,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Size(max = 200) String address) {
    }

    public record CreateOffer(
            @Min(1) @Max(1000000) int price,
            @Size(max = 60) String etaText,
            @Size(max = 500) String message) {
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String body) {
    }

    public record OfferDto(
            Long id, Long requestId,
            Long artisanId, String artisanNameAr, String artisanNameFr,
            double artisanRating, long artisanReviewCount,
            int price, String etaText, String message,
            OfferStatus status, Instant createdAt,
            /** Only filled for the owner of the request once the offer is accepted. */
            String artisanPhone) {
    }

    public record RequestDto(
            Long id,
            String categoryCode, String categoryNameAr, String categoryNameFr,
            String description, String city, String neighborhood,
            WhenPref whenPref, int budget, RequestStatus status, Instant createdAt,
            String clientName,
            /** Only filled for the accepted artisan. */
            String clientPhone,
            long offerCount, Long acceptedOfferId, boolean reviewed,
            List<OfferDto> offers,
            /** For artisans: their own offer on this request, if any. */
            OfferDto myOffer,
            Double lat, Double lng, String address) {
    }

    // ---------- artisan self-service ----------
    public record UpdateArtisanProfile(
            Boolean availableToday,
            @Size(max = 1000) String bioAr,
            @Size(max = 1000) String bioFr,
            @Size(max = 20) String whatsapp) {
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 120) String labelAr,
            @NotBlank @Size(max = 120) String labelFr,
            @Min(1) @Max(1000000) int price,
            @Size(max = 20) String unitAr,
            @Size(max = 20) String unitFr) {
    }
}
