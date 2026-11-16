package ma.sanai3i.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.sanai3i.domain.Role;

import java.time.Instant;
import java.util.List;

/** Request and response bodies of the administration API (/api/admin). */
public final class AdminDto {
    private AdminDto() {
    }

    public record PageOf<T>(List<T> items, long total, int page, int size) {
    }

    // ---------- users
    public record UserRow(Long id, String phone, String fullName, Role role, String city, boolean blocked,
                          String blockedReason, Instant createdAt, Instant lastLoginAt, Long artisanId,
                          boolean verified, long requests, long offers) {
    }

    public record UserDetail(UserRow user, String tradeAr, String tradeFr, Double rating, long reviewCount,
                             int jobsDone, List<RequestRow> recentRequests, List<AuditRow> activity) {
    }

    public record BlockInput(@Size(max = 200) String reason) {
    }

    public record RoleInput(@NotNull Role role) {
    }

    public record VerifyInput(boolean verified) {
    }

    public record TempPassword(String password) {
    }

    // ---------- requests, reviews
    public record RequestRow(Long id, String categoryCode, String categoryNameAr, String categoryNameFr,
                             String clientName, String clientPhone, String city, String status, int budget,
                             long offers, Instant createdAt) {
    }

    public record OfferRow(Long id, String artisanName, int price, String status, String etaText, String message) {
    }

    public record RequestDetail(RequestRow request, String description, String neighborhood, String whenPref,
                                List<OfferRow> offers, Integer reviewRating, String reviewBody,
                                Double lat, Double lng, String address) {
    }

    public record CancelInput(@Size(max = 160) String reason) {
    }

    public record ReviewRow(Long id, Long requestId, String artisanName, String clientName, int rating, String body,
                            Instant createdAt) {
    }

    // ---------- categories
    public record CategoryRow(Long id, String code, String nameAr, String nameFr, int sortOrder, long artisans,
                              long requests) {
    }

    public record CategoryInput(@NotBlank @Size(max = 30) @Pattern(regexp = "[a-z0-9_-]+") String code,
                                @NotBlank @Size(max = 60) String nameAr,
                                @NotBlank @Size(max = 60) String nameFr,
                                int sortOrder) {
    }

    // ---------- trail, dashboard, system
    public record AuditRow(Long id, Long actorId, String actorName, String action, String targetType, Long targetId,
                           String detail, String ip, Instant createdAt) {
    }

    public record DayCount(String day, long count) {
    }

    public record CatCount(String code, String nameAr, String nameFr, long requests, long artisans) {
    }

    public record Stats(long users, long clients, long artisans, long admins, long blocked, long newUsers7,
                        long requests, long open, long accepted, long done, long cancelled, long newRequests7,
                        long offers, long reviews, double avgRating, long verifiedArtisans, long liveConnections,
                        List<DayCount> requestsPerDay, List<DayCount> usersPerDay, List<CatCount> categories) {
    }

    public record SystemInfo(String javaVersion, long uptimeSeconds, long heapUsedMb, long heapMaxMb, int processors,
                             boolean databaseOk, long liveConnections, Instant serverTime, String profiles) {
    }

    // ---------- broadcast and settings
    public record BroadcastInput(@NotBlank String audience, @NotBlank @Size(max = 160) String message) {
    }

    public record BroadcastResult(int sent) {
    }

    public record SettingsDto(boolean registrationsOpen, String bannerMessage) {
    }

    public record SettingsInput(boolean registrationsOpen, @Size(max = 300) String bannerMessage) {
    }
}
