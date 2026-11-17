package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.ArtisanStatus;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.ServiceItem;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.MediaDto;
import ma.sanai3i.dto.Dto.OnboardingDto;
import ma.sanai3i.dto.Dto.OnboardingInput;
import ma.sanai3i.dto.Dto.ServiceRequest;
import ma.sanai3i.dto.Dto.UpdateArtisanProfile;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.MediaRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

/** What an artisan edits on his own profile, and his activation file (reviewed by an admin). */
@Service
@Transactional
public class ArtisanService {
    /** Moroccan CIN: 1 or 2 letters then digits, e.g. BE123456. */
    private static final Pattern CIN = Pattern.compile("^[A-Z]{1,2}[0-9]{3,8}$");
    public static final int MIN_PORTFOLIO = 2;

    private final ArtisanProfileRepository artisans;
    private final ReviewRepository reviews;
    private final Mapper mapper;
    private final CategoryRepository categories;
    private final MediaRepository mediaRepo;
    private final MediaService media;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public ArtisanService(ArtisanProfileRepository artisans, ReviewRepository reviews, Mapper mapper,
                          CategoryRepository categories, MediaRepository mediaRepo, MediaService media,
                          UserRepository users, NotificationService notifications, AuditService audit) {
        this.artisans = artisans;
        this.reviews = reviews;
        this.mapper = mapper;
        this.categories = categories;
        this.mediaRepo = mediaRepo;
        this.media = media;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ArtisanDetail profile(AuthUser me) {
        return detail(profileOf(me));
    }

    public ArtisanDetail update(AuthUser me, UpdateArtisanProfile in) {
        ArtisanProfile a = profileOf(me);
        if (in.availableToday() != null) {
            a.availableToday = in.availableToday();
        }
        Map<String, Object> changes = new LinkedHashMap<>();
        if (in.bioFr() != null || in.bioAr() != null) {
            changes.put("bio", (in.bioFr() != null ? in.bioFr() : in.bioAr()).trim());
        }
        if (in.whatsapp() != null && !in.whatsapp().isBlank()) {
            changes.put("whatsapp", whatsapp(in.whatsapp()));
        }
        changes.entrySet().removeIf(e -> unchanged(a, e.getKey(), e.getValue()));
        if (a.status == ArtisanStatus.APPROVED || a.status == ArtisanStatus.SUSPENDED) {
            propose(a, changes);
        } else {
            apply(a, changes);
        }
        return detail(a);
    }

    public ArtisanDetail addService(AuthUser me, ServiceRequest in) {
        ArtisanProfile a = profileOf(me);
        ServiceItem s = new ServiceItem();
        s.artisan = a;
        s.labelAr = in.labelAr().trim();
        s.labelFr = in.labelFr().trim();
        s.price = in.price();
        s.unitAr = in.unitAr() == null ? "" : in.unitAr().trim();
        s.unitFr = in.unitFr() == null ? "" : in.unitFr().trim();
        a.services.add(s);
        artisans.save(a);
        return detail(a);
    }

    public ArtisanDetail deleteService(AuthUser me, Long serviceId) {
        ArtisanProfile a = profileOf(me);
        boolean removed = a.services.removeIf(s -> s.id.equals(serviceId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND");
        }
        return detail(a);
    }

    // ------------------------------------------------------------------ activation file

    @Transactional(readOnly = true)
    public OnboardingDto onboarding(AuthUser me) {
        return onboardingOf(profileOf(me));
    }

    /**
     * Saves the file. DRAFT / REJECTED: everything can change. APPROVED: only the description, place, radius,
     * WhatsApp, photo and portfolio (identity and trade are locked). PENDING: nothing until the admin answers.
     */
    public OnboardingDto saveOnboarding(AuthUser me, OnboardingInput in) {
        ArtisanProfile a = profileOf(me);
        if (a.status == ArtisanStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "UNDER_REVIEW");
        }
        boolean identityOpen = a.status != ArtisanStatus.APPROVED;
        Long owner = a.user.id;
        if (identityOpen) {
            if (notBlank(in.fullName())) {
                String name = in.fullName().trim();
                a.user.fullName = name;
                a.nameFr = name;
                a.nameAr = name;
            }
            if (in.cin() != null) {
                String cin = in.cin().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
                if (!cin.isEmpty() && !CIN.matcher(cin).matches()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CIN_INVALID");
                }
                a.cin = cin.isEmpty() ? null : cin;
            }
            if (in.yearsExperience() != null) {
                a.yearsExperience = in.yearsExperience();
            }
            if (notBlank(in.categoryCode()) && !in.categoryCode().equals(a.category.code)) {
                Category c = categories.findByCode(in.categoryCode())
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY"));
                a.category = c;
                a.tradeAr = c.nameAr;
                a.tradeFr = c.nameFr;
            }
            if (in.idFrontId() != null) {
                link(owner, a.id, in.idFrontId(), Media.ID_FRONT, true);
            }
            if (in.idBackId() != null) {
                link(owner, a.id, in.idBackId(), Media.ID_BACK, true);
            }
        }
        if (in.radiusKm() != null) {
            a.radiusKm = in.radiusKm();           // only changes which requests he sees (from a ~1 km grid)
        }
        // what clients see: applied at once while the file is not approved yet, otherwise kept for the team
        Map<String, Object> changes = new LinkedHashMap<>();
        if (in.bio() != null) {
            changes.put("bio", in.bio().trim());
        }
        if (notBlank(in.city())) {
            changes.put("city", in.city().trim());
        }
        if (in.address() != null) {
            changes.put("address", in.address().isBlank() ? "" : in.address().trim());
        }
        if (in.lat() != null && in.lng() != null) {
            if (Math.abs(in.lat()) > 90 || Math.abs(in.lng()) > 180) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
            }
            changes.put("lat", in.lat());
            changes.put("lng", in.lng());
        }
        if (notBlank(in.whatsapp())) {
            changes.put("whatsapp", whatsapp(in.whatsapp()));
        }
        if (in.avatarId() != null) {
            media.owned(owner, in.avatarId(), Media.AVATAR);
            changes.put("avatarId", in.avatarId());
        }
        if (in.portfolioIds() != null) {
            for (String id : in.portfolioIds()) {
                media.owned(owner, id, Media.PORTFOLIO);
            }
            changes.put("portfolioIds", new ArrayList<>(in.portfolioIds()));
        }
        changes.entrySet().removeIf(e -> unchanged(a, e.getKey(), e.getValue()));
        if (a.status == ArtisanStatus.APPROVED || a.status == ArtisanStatus.SUSPENDED) {
            propose(a, changes);
        } else {
            apply(a, changes);
        }
        if (a.status == ArtisanStatus.REJECTED) {
            a.status = ArtisanStatus.DRAFT;       // fixing a refused file: it goes back to draft until sent again
        }
        return onboardingOf(a);
    }

    /** A Moroccan mobile number, stored as +2126XXXXXXXX (no free text: it becomes a wa.me link). */
    static String whatsapp(String raw) {
        try {
            String p = ma.sanai3i.common.Phones.normalize(raw);
            if (!p.startsWith("+2126") && !p.startsWith("+2127")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WHATSAPP_INVALID");
            }
            return p;
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WHATSAPP_INVALID");
        }
    }

    private static boolean unchanged(ArtisanProfile a, String key, Object v) {
        return switch (key) {
            case "bio" -> v.equals(a.bioFr);
            case "city" -> v.equals(a.city);
            case "address" -> v.equals(a.address == null ? "" : a.address);
            case "lat" -> v.equals(a.lat);
            case "lng" -> false;                          // lat and lng go together
            case "whatsapp" -> v.equals(a.whatsapp);
            case "avatarId" -> v.equals(a.avatarId);
            default -> false;
        };
    }

    /** Approved artisan: the change waits for the team (merged with earlier pending changes). */
    private void propose(ArtisanProfile a, Map<String, Object> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Map<String, Object> pending = pendingOf(a);
        boolean first = pending.isEmpty();
        pending.putAll(changes);
        a.pendingChanges = json(pending);
        if (first) {
            a.pendingSince = Instant.now();
            for (User admin : users.findAll()) {
                if (admin.role == Role.ADMIN && !admin.blocked
                        && (admin.superAdmin || admin.permissionSet().contains(ma.sanai3i.domain.Permission.FILES))) {
                    notifications.notify(admin.id, "ARTISAN_CHANGES", null, a.nameAr, a.nameFr, a.category.code);
                }
            }
        }
        audit.logUser(a.user, "ARTISAN_CHANGES_PROPOSED", "ARTISAN", a.id, String.join(",", changes.keySet()));
    }

    /** Called by the team (or directly while the file is not approved yet). */
    @SuppressWarnings("unchecked")
    public void apply(ArtisanProfile a, Map<String, Object> changes) {
        if (changes.containsKey("bio")) {
            String bio = (String) changes.get("bio");
            a.bioFr = bio;
            a.bioAr = bio;
        }
        if (changes.containsKey("city")) {
            a.city = (String) changes.get("city");
            a.user.city = a.city;
        }
        if (changes.containsKey("address")) {
            String ad = (String) changes.get("address");
            a.address = ad == null || ad.isBlank() ? null : ad;
        }
        if (changes.get("lat") instanceof Number lat && changes.get("lng") instanceof Number lng) {
            a.lat = lat.doubleValue();
            a.lng = lng.doubleValue();
        }
        if (changes.containsKey("whatsapp")) {
            a.whatsapp = (String) changes.get("whatsapp");
        }
        if (changes.containsKey("avatarId")) {
            a.avatarId = (String) changes.get("avatarId");
        }
        if (changes.get("portfolioIds") instanceof List<?> ids) {
            // the list given is the new portfolio (order kept); files left out are unlinked
            HashSet<Object> keep = new HashSet<>(ids);
            for (Media m : mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO)) {
                if (!keep.contains(m.id)) {
                    m.artisanId = null;
                }
            }
            for (Object id : ids) {
                Media m = media.owned(a.user.id, String.valueOf(id), Media.PORTFOLIO);
                m.artisanId = a.id;
            }
        }
    }

    public Map<String, Object> pendingOf(ArtisanProfile a) {
        if (a.pendingChanges == null || a.pendingChanges.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(JSON.readValue(a.pendingChanges,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    }));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String json(Map<String, Object> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR");
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Sends the file to the admins. Every required field must be filled. */
    public OnboardingDto submit(AuthUser me) {
        ArtisanProfile a = profileOf(me);
        if (a.status == ArtisanStatus.PENDING || a.status == ArtisanStatus.APPROVED || a.status == ArtisanStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "UNDER_REVIEW");
        }
        if (!missing(a).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ONBOARDING_INCOMPLETE");
        }
        // one identity, one artisan account (no second free trial, no review farming with a twin account)
        if (artisans.existsByCinAndIdNot(a.cin, a.id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CIN_TAKEN");
        }
        a.status = ArtisanStatus.PENDING;
        a.submittedAt = Instant.now();
        a.rejectionReason = null;
        audit.logUser(a.user, "ARTISAN_SUBMITTED", "ARTISAN", a.id, a.category.code);
        for (User admin : users.findAll()) {
            if (admin.role == Role.ADMIN && !admin.blocked
                    && (admin.superAdmin || admin.permissionSet().contains(ma.sanai3i.domain.Permission.FILES))) {
                notifications.notify(admin.id, "ARTISAN_SUBMITTED", null, a.nameAr, a.nameFr, a.category.code);
            }
        }
        return onboardingOf(a);
    }

    public OnboardingDto onboardingOf(ArtisanProfile a) {
        List<Media> ids = new ArrayList<>(mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.ID_FRONT));
        List<Media> backs = mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.ID_BACK);
        MediaDto avatar = a.avatarId == null ? null : mediaRepo.findById(a.avatarId).map(MediaService::dto).orElse(null);
        Map<String, Object> pending = pendingOf(a);
        return new OnboardingDto(a.status.name(), a.status == ArtisanStatus.SUSPENDED ? a.suspendedReason : a.rejectionReason,
                a.user.fullName, a.cin, a.yearsExperience,
                a.bioFr, a.category.code, a.city, a.address, a.lat, a.lng, a.radiusKm, a.whatsapp, avatar,
                ids.isEmpty() ? null : MediaService.dto(ids.get(ids.size() - 1)),
                backs.isEmpty() ? null : MediaService.dto(backs.get(backs.size() - 1)),
                mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO).stream().map(MediaService::dto).toList(),
                missing(a), pending.isEmpty() ? null : pending, a.pendingSince);
    }

    private List<String> missing(ArtisanProfile a) {
        List<String> out = new ArrayList<>();
        if (a.user.fullName == null || a.user.fullName.trim().split("\\s+").length < 2) {
            out.add("fullName");
        }
        if (a.cin == null) {
            out.add("cin");
        }
        if (mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.ID_FRONT).isEmpty()) {
            out.add("idFront");
        }
        if (mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.ID_BACK).isEmpty()) {
            out.add("idBack");
        }
        if (a.avatarId == null) {
            out.add("avatar");
        }
        if (a.bioFr == null || a.bioFr.trim().length() < 30) {
            out.add("bio");
        }
        if (a.lat == null || a.lng == null) {
            out.add("place");
        }
        if (mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO).size() < MIN_PORTFOLIO) {
            out.add("portfolio");
        }
        return out;
    }

    /** ID card images: a new upload replaces the previous one (only the latest is kept linked). */
    private void link(Long owner, Long artisanId, String mediaId, String purpose, boolean replace) {
        Media m = media.owned(owner, mediaId, purpose);
        if (replace) {
            for (Media old : mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(artisanId, purpose)) {
                if (!old.id.equals(m.id)) {
                    old.artisanId = null;
                }
            }
        }
        m.artisanId = artisanId;
    }

    private ArtisanDetail detail(ArtisanProfile a) {
        return mapper.detail(a, mapper.ratingOf(a.id), reviews.findByArtisanIdOrderByCreatedAtDesc(a.id));
    }

    private ArtisanProfile profileOf(AuthUser me) {
        if (me.role() != Role.ARTISAN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        return artisans.findByUserId(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
