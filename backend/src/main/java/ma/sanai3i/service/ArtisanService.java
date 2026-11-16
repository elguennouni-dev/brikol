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
        if (in.bioAr() != null) {
            a.bioAr = in.bioAr().trim();
        }
        if (in.bioFr() != null) {
            a.bioFr = in.bioFr().trim();
        }
        if (in.whatsapp() != null && !in.whatsapp().isBlank()) {
            a.whatsapp = in.whatsapp().trim();
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
        if (in.bio() != null) {
            String bio = in.bio().trim();
            a.bioFr = bio;
            a.bioAr = bio;
        }
        if (notBlank(in.city())) {
            a.city = in.city().trim();
            a.user.city = a.city;
        }
        if (in.address() != null) {
            a.address = in.address().isBlank() ? null : in.address().trim();
        }
        if (in.lat() != null && in.lng() != null) {
            a.lat = in.lat();
            a.lng = in.lng();
        }
        if (in.radiusKm() != null) {
            a.radiusKm = in.radiusKm();
        }
        if (notBlank(in.whatsapp())) {
            a.whatsapp = in.whatsapp().trim();
        }
        if (in.avatarId() != null) {
            media.owned(owner, in.avatarId(), Media.AVATAR);
            a.avatarId = in.avatarId();
        }
        if (in.portfolioIds() != null) {
            // the list given is the new portfolio (order kept); files left out are unlinked
            List<Media> current = mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO);
            HashSet<String> keep = new HashSet<>(in.portfolioIds());
            for (Media m : current) {
                if (!keep.contains(m.id)) {
                    m.artisanId = null;
                }
            }
            for (String id : in.portfolioIds()) {
                Media m = media.owned(owner, id, Media.PORTFOLIO);
                m.artisanId = a.id;
            }
        }
        if (a.status == ArtisanStatus.REJECTED) {
            a.status = ArtisanStatus.DRAFT;       // fixing a refused file: it goes back to draft until sent again
        }
        return onboardingOf(a);
    }

    /** Sends the file to the admins. Every required field must be filled. */
    public OnboardingDto submit(AuthUser me) {
        ArtisanProfile a = profileOf(me);
        if (a.status == ArtisanStatus.PENDING || a.status == ArtisanStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "UNDER_REVIEW");
        }
        if (!missing(a).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ONBOARDING_INCOMPLETE");
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
        return new OnboardingDto(a.status.name(), a.rejectionReason, a.user.fullName, a.cin, a.yearsExperience,
                a.bioFr, a.category.code, a.city, a.address, a.lat, a.lng, a.radiusKm, a.whatsapp, avatar,
                ids.isEmpty() ? null : MediaService.dto(ids.get(ids.size() - 1)),
                backs.isEmpty() ? null : MediaService.dto(backs.get(backs.size() - 1)),
                mediaRepo.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO).stream().map(MediaService::dto).toList(),
                missing(a));
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
