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
            @Size(max = 80) String neighborhood,
            /** SMS code, required when SMS verification is on */
            @Size(max = 10) String otp) {
    }

    public record OtpRequest(@NotBlank String phone, @NotBlank String purpose, String lang) {
    }

    public record ResetPasswordRequest(@NotBlank String phone, @NotBlank @Size(max = 10) String otp,
                                       @NotBlank @Size(min = 6, max = 72) String password) {
    }

    /** credential = ID token from Google. The other fields are only needed the first time (account creation). */
    public record GoogleAuthRequest(
            @NotBlank @Size(max = 4096) String credential,
            String phone,
            Role role,
            String categoryCode,
            @Size(max = 80) String neighborhood,
            @Size(max = 10) String otp) {
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    /** artisanStatus: DRAFT, PENDING, APPROVED or REJECTED for artisans, null otherwise. */
    public record UserDto(Long id, String phone, String fullName, Role role, String city, Long artisanId,
                          String artisanStatus,
                          /** administration: owner of the platform, and staff permissions */
                          boolean superAdmin, List<String> permissions,
                          /** set while the account is suspended after too many strikes */
                          java.time.Instant suspendedUntil) {
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
            Integer fromPrice, String fromUnitAr, String fromUnitFr,
            String avatarUrl,
            /** paid "featured" placement */
            boolean featured) {
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
            List<ReviewDto> reviews,
            List<MediaDto> portfolio) {
    }

    // ---------- artisan activation file ----------
    public record OnboardingDto(
            String status, String rejectionReason,
            String fullName, String cin, int yearsExperience, String bio,
            String categoryCode, String city, String address, Double lat, Double lng, int radiusKm, String whatsapp,
            MediaDto avatar, MediaDto idFront, MediaDto idBack, List<MediaDto> portfolio,
            /** Fields still missing before the file can be sent for review. */
            List<String> missing) {
    }

    public record OnboardingInput(
            @Size(max = 120) String fullName,
            @Size(max = 20) String cin,
            @Min(0) @Max(70) Integer yearsExperience,
            @Size(max = 1000) String bio,
            String categoryCode,
            @Size(max = 80) String city,
            @Size(max = 200) String address,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Min(1) @Max(100) Integer radiusKm,
            @Size(max = 20) String whatsapp,
            String avatarId, String idFrontId, String idBackId,
            @Size(max = 12) List<String> portfolioIds) {
    }

    // ---------- requests and offers ----------
    public record CreateRequest(
            @NotBlank String categoryCode,
            @NotBlank @Size(min = 5, max = 2000) String description,
            @Size(max = 80) String city,
            @Size(max = 80) String neighborhood,
            WhenPref whenPref,
            @Min(0) @Max(1000000) int budget,
            @Size(max = 6) List<String> mediaIds,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Size(max = 200) String address) {
    }

    public record CounterOffer(@Min(1) @Max(1000000) int price) {
    }

    public record MediaDto(String id, String kind, String mime, String url) {
    }

    public record PriceRangeDto(String categoryCode, int min, int max, Integer typicalLow, Integer typicalHigh, int samples) {
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
            String artisanPhone,
            /** Negotiation: who made the current price (ARTISAN or CLIENT), counter-offers so far, first price. */
            String lastBy, int rounds, int maxRounds, Integer firstPrice) {
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
            Double lat, Double lng, String address,
            List<MediaDto> media,
            /** Allowed price range for budget, offers and counter-offers. */
            int priceMin, int priceMax,
            /** For artisans: distance from their base, in km (null when unknown). */
            Double distanceKm,
            /** How artisans rated this client (shown to artisans), and whether this artisan already did. */
            Double clientRating, long clientReviewCount, boolean clientReviewedByMe,
            /** Chat messages not read yet by the viewer. */
            long unreadMessages,
            /** Appointment: when, proposed by CLIENT or ARTISAN, confirmed by the other side. */
            Instant appointmentAt, String appointmentBy, boolean appointmentConfirmed) {
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
