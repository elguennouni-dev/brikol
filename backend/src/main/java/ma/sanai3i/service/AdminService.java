package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.AuditLog;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.OfferStatus;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.Review;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.AdminDto.AuditRow;
import ma.sanai3i.dto.AdminDto.CatCount;
import ma.sanai3i.dto.AdminDto.CategoryInput;
import ma.sanai3i.dto.AdminDto.CategoryRow;
import ma.sanai3i.dto.AdminDto.DayCount;
import ma.sanai3i.dto.AdminDto.OfferRow;
import ma.sanai3i.dto.AdminDto.PageOf;
import ma.sanai3i.dto.AdminDto.RequestDetail;
import ma.sanai3i.dto.AdminDto.RequestRow;
import ma.sanai3i.dto.AdminDto.ReviewRow;
import ma.sanai3i.dto.AdminDto.SettingsDto;
import ma.sanai3i.dto.AdminDto.Stats;
import ma.sanai3i.dto.AdminDto.SystemInfo;
import ma.sanai3i.dto.AdminDto.UserDetail;
import ma.sanai3i.dto.AdminDto.UserRow;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.AuditLogRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.security.BlockList;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Everything the administrator can see and do. Every change is written to the audit trail. */
@Service
@Transactional
public class AdminService {
    private static final String PASSWORD_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository users;
    private final ArtisanProfileRepository artisans;
    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final ReviewRepository reviews;
    private final CategoryRepository categories;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final NotificationService notifications;
    private final NotificationHub hub;
    private final BlockList blockList;
    private final SettingsService settings;
    private final ArtisanService artisanService;
    private final ma.sanai3i.repo.ReportRepository reportRepo;
    private final MediaService mediaService;
    private final PasswordEncoder encoder;
    private final Environment env;
    private final SecureRandom random = new SecureRandom();

    public AdminService(UserRepository users, ArtisanProfileRepository artisans, JobRequestRepository requests,
                        OfferRepository offers, ReviewRepository reviews, CategoryRepository categories,
                        AuditLogRepository auditLog, AuditService audit, NotificationService notifications,
                        NotificationHub hub, BlockList blockList, SettingsService settings,
                        PasswordEncoder encoder, Environment env, ArtisanService artisanService,
                        MediaService mediaService, ma.sanai3i.repo.ReportRepository reportRepo) {
        this.reportRepo = reportRepo;
        this.artisanService = artisanService;
        this.mediaService = mediaService;
        this.users = users;
        this.artisans = artisans;
        this.requests = requests;
        this.offers = offers;
        this.reviews = reviews;
        this.categories = categories;
        this.auditLog = auditLog;
        this.audit = audit;
        this.notifications = notifications;
        this.hub = hub;
        this.blockList = blockList;
        this.settings = settings;
        this.encoder = encoder;
        this.env = env;
    }

    // ------------------------------------------------------------------ dashboard and system

    @Transactional(readOnly = true)
    public Stats stats() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Review> allReviews = reviews.findAll();
        double avg = allReviews.stream().mapToInt(r -> r.rating).average().orElse(0.0);
        List<CatCount> perCategory = new ArrayList<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            perCategory.add(new CatCount(c.code, c.nameAr, c.nameFr, requests.countByCategoryId(c.id),
                    artisans.countByCategoryId(c.id)));
        }
        List<Instant> requestTimes = requests.findAll().stream().map(r -> r.createdAt).toList();
        List<Instant> userTimes = users.findAll().stream().map(u -> u.createdAt).toList();
        return new Stats(
                users.count(), users.countByRole(Role.CLIENT), users.countByRole(Role.ARTISAN),
                users.countByRole(Role.ADMIN), users.countByBlockedTrue(), users.countByCreatedAtAfter(weekAgo),
                requests.count(), requests.countByStatus(RequestStatus.OPEN), requests.countByStatus(RequestStatus.ACCEPTED),
                requests.countByStatus(RequestStatus.DONE), requests.countByStatus(RequestStatus.CANCELLED),
                requests.countByCreatedAtAfter(weekAgo),
                offers.count(), allReviews.size(), Math.round(avg * 10.0) / 10.0, artisans.countByVerifiedTrue(),
                hub.connections(), perDay(requestTimes, 14), perDay(userTimes, 14), perCategory,
                artisans.countByStatus(ma.sanai3i.domain.ArtisanStatus.PENDING), reportRepo.countByStatus("OPEN"));
    }

    private static List<DayCount> perDay(List<Instant> times, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<LocalDate, Long> counts = times.stream()
                .collect(Collectors.groupingBy(i -> i.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<DayCount> out = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            out.add(new DayCount(d.toString(), counts.getOrDefault(d, 0L)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SystemInfo system() {
        Runtime rt = Runtime.getRuntime();
        boolean dbOk;
        try {
            users.count();
            dbOk = true;
        } catch (RuntimeException e) {
            dbOk = false;
        }
        String profiles = env.getActiveProfiles().length == 0 ? "default" : String.join(",", env.getActiveProfiles());
        return new SystemInfo(System.getProperty("java.version"),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024), rt.maxMemory() / (1024 * 1024),
                rt.availableProcessors(), dbOk, hub.connections(), Instant.now(), profiles);
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public PageOf<UserRow> users(String q, String role, String status, int page, int size) {
        String needle = norm(q);
        List<User> filtered = users.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(u -> needle.isEmpty() || u.fullName.toLowerCase(Locale.ROOT).contains(needle)
                        || u.phone.contains(needle.replace(" ", "")) || String.valueOf(u.id).equals(needle))
                .filter(u -> isBlank(role) || u.role.name().equalsIgnoreCase(role))
                .filter(u -> isBlank(status) || ("blocked".equalsIgnoreCase(status) ? u.blocked : !u.blocked))
                .toList();
        return slice(filtered, page, size, this::userRow);
    }

    @Transactional(readOnly = true)
    public UserDetail user(Long id) {
        User u = findUser(id);
        ArtisanProfile a = artisans.findByUserId(u.id).orElse(null);
        List<RequestRow> recent = new ArrayList<>();
        if (u.role == Role.CLIENT) {
            requests.findByClientIdOrderByCreatedAtDesc(u.id).stream().limit(10).forEach(r -> recent.add(requestRow(r)));
        } else if (a != null) {
            offers.findByArtisanIdOrderByCreatedAtDesc(a.id).stream().limit(10).forEach(o -> recent.add(requestRow(o.request)));
        }
        List<AuditRow> activity = new ArrayList<>();
        auditLog.findTop15ByActorIdOrderByIdDesc(u.id).forEach(l -> activity.add(auditRow(l)));
        return new UserDetail(userRow(u), a == null ? null : a.tradeAr, a == null ? null : a.tradeFr,
                a == null ? null : reviews.averageRating(a.id), a == null ? 0 : reviews.countByArtisanId(a.id),
                a == null ? 0 : a.jobsDone, recent, activity);
    }

    public UserRow block(AuthUser admin, Long id, String reason) {
        User u = findUser(id);
        guardTarget(admin, u);
        u.blocked = true;
        u.blockedReason = isBlank(reason) ? null : reason.trim();
        blockList.block(u.id);
        hub.disconnect(u.id);
        log(admin, "USER_BLOCKED", "USER", u.id, u.fullName + (u.blockedReason == null ? "" : " · " + u.blockedReason));
        return userRow(u);
    }

    public UserRow unblock(AuthUser admin, Long id) {
        User u = findUser(id);
        guardTarget(admin, u);
        u.blocked = false;
        u.blockedReason = null;
        blockList.unblock(u.id);
        log(admin, "USER_UNBLOCKED", "USER", u.id, u.fullName);
        return userRow(u);
    }

    /** Sets a random password and returns it once: the admin hands it to the user, who should change it. */
    public String resetPassword(AuthUser admin, Long id) {
        User u = findUser(id);
        guardTarget(admin, u);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        u.passwordHash = encoder.encode(sb.toString());
        log(admin, "PASSWORD_RESET", "USER", u.id, u.fullName);
        return sb.toString();
    }

    public UserRow verify(AuthUser admin, Long artisanId, boolean verified) {
        ArtisanProfile a = artisans.findById(artisanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTISAN_NOT_FOUND"));
        a.verified = verified;
        log(admin, verified ? "ARTISAN_VERIFIED" : "ARTISAN_UNVERIFIED", "USER", a.user.id, a.nameFr);
        return userRow(a.user);
    }

    // ------------------------------------------------------------------ requests and reviews

    @Transactional(readOnly = true)
    public PageOf<RequestRow> requests(String q, String status, String category, int page, int size) {
        String needle = norm(q);
        List<JobRequest> filtered = requests.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(r -> isBlank(status) || r.status.name().equalsIgnoreCase(status))
                .filter(r -> isBlank(category) || r.category.code.equalsIgnoreCase(category))
                .filter(r -> needle.isEmpty() || r.description.toLowerCase(Locale.ROOT).contains(needle)
                        || r.client.fullName.toLowerCase(Locale.ROOT).contains(needle)
                        || r.client.phone.contains(needle.replace(" ", "")) || String.valueOf(r.id).equals(needle))
                .toList();
        return slice(filtered, page, size, this::requestRow);
    }

    @Transactional(readOnly = true)
    public RequestDetail request(Long id) {
        JobRequest r = findRequest(id);
        List<OfferRow> rows = offers.findByRequestIdOrderByPriceAsc(r.id).stream()
                .map(o -> new OfferRow(o.id, o.artisan.nameFr, o.price, o.status.name(), o.etaText, o.message)).toList();
        Review review = reviews.findAll().stream().filter(x -> r.id.equals(x.requestId)).findFirst().orElse(null);
        return new RequestDetail(requestRow(r), r.description, r.neighborhood, r.whenPref.name(), rows,
                review == null ? null : review.rating, review == null ? null : review.body, r.lat, r.lng, r.address,
                mediaService.ofRequest(r.id),
                r.priceMin != null ? r.priceMin : r.category.minPrice, r.priceMax != null ? r.priceMax : r.category.maxPrice);
    }

    public RequestRow cancelRequest(AuthUser admin, Long id, String reason) {
        JobRequest r = findRequest(id);
        if (r.status != RequestStatus.OPEN && r.status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED");
        }
        r.status = RequestStatus.CANCELLED;
        String why = isBlank(reason) ? null : reason.trim();
        notifications.notify(r.client.id, "ADMIN_CANCELLED", r.id, null, null, why);
        for (Offer o : offers.findByRequestIdOrderByPriceAsc(r.id)) {
            if (o.status == OfferStatus.PENDING) {
                o.status = OfferStatus.REJECTED;
            }
            notifications.notify(o.artisan.user.id, "ADMIN_CANCELLED", r.id, null, null, why);
        }
        log(admin, "REQUEST_CANCELLED_BY_ADMIN", "REQUEST", r.id, why);
        return requestRow(r);
    }

    @Transactional(readOnly = true)
    public PageOf<ReviewRow> reviews(int page, int size) {
        List<Review> all = reviews.findAll(Sort.by(Sort.Direction.DESC, "id"));
        return slice(all, page, size, r -> new ReviewRow(r.id, r.requestId, r.artisan.nameFr, r.client.fullName,
                r.rating, r.body, r.createdAt));
    }

    public void deleteReview(AuthUser admin, Long id) {
        Review r = reviews.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND"));
        String detail = r.artisan.nameFr + " · " + r.rating + "★ · " + (r.body == null ? "" : r.body);
        reviews.delete(r);          // ratings are computed from the reviews, so the average updates by itself
        log(admin, "REVIEW_DELETED", "REVIEW", id, detail);
    }

    // ------------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public List<CategoryRow> categories() {
        return categories.findAllByOrderBySortOrderAsc().stream().map(this::categoryRow).toList();
    }

    public CategoryRow createCategory(AuthUser admin, CategoryInput in) {
        if (categories.findByCode(in.code()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_EXISTS");
        }
        Category c = new Category();
        c.code = in.code();
        c.nameAr = in.nameAr().trim();
        c.nameFr = in.nameFr().trim();
        c.sortOrder = in.sortOrder();
        applyPrices(c, in);
        categories.save(c);
        log(admin, "CATEGORY_CREATED", "CATEGORY", c.id, c.code);
        return categoryRow(c);
    }

    public CategoryRow updateCategory(AuthUser admin, Long id, CategoryInput in) {
        Category c = categories.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND"));
        // the code is referenced by the web app (icons, colours): it is fixed once created
        c.nameAr = in.nameAr().trim();
        c.nameFr = in.nameFr().trim();
        c.sortOrder = in.sortOrder();
        applyPrices(c, in);
        log(admin, "CATEGORY_UPDATED", "CATEGORY", c.id, c.code);
        return categoryRow(c);
    }

    public void deleteCategory(AuthUser admin, Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND"));
        if (artisans.countByCategoryId(c.id) > 0 || requests.countByCategoryId(c.id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_IN_USE");
        }
        categories.delete(c);
        log(admin, "CATEGORY_DELETED", "CATEGORY", id, c.code);
    }

    private static void applyPrices(Category c, CategoryInput in) {
        int min = in.minPrice() == null ? c.minPrice : in.minPrice();
        int max = in.maxPrice() == null ? c.maxPrice : in.maxPrice();
        if (max <= min) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_BOUNDS");
        }
        c.minPrice = min;
        c.maxPrice = max;
    }

    // ------------------------------------------------------------------ artisan activation

    @Transactional(readOnly = true)
    public List<ma.sanai3i.dto.AdminDto.ArtisanReviewRow> artisanFiles(String status) {
        ma.sanai3i.domain.ArtisanStatus wanted = isBlank(status) ? ma.sanai3i.domain.ArtisanStatus.PENDING
                : ma.sanai3i.domain.ArtisanStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return artisans.findByStatusOrderBySubmittedAtAsc(wanted).stream()
                .map(a -> new ma.sanai3i.dto.AdminDto.ArtisanReviewRow(a.id, a.user.id, a.user.fullName, a.user.phone,
                        a.category.code, a.category.nameAr, a.category.nameFr, a.city, a.status.name(), a.createdAt, a.submittedAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public ma.sanai3i.dto.AdminDto.ArtisanReview artisanFile(Long artisanId) {
        ArtisanProfile a = findArtisan(artisanId);
        return new ma.sanai3i.dto.AdminDto.ArtisanReview(a.id, a.user.id, a.user.phone, a.user.email, a.createdAt,
                a.submittedAt, a.reviewedAt, artisanService.onboardingOf(a));
    }

    public ma.sanai3i.dto.AdminDto.ArtisanReview approveArtisan(AuthUser admin, Long artisanId) {
        ArtisanProfile a = findArtisan(artisanId);
        if (a.status != ma.sanai3i.domain.ArtisanStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING");
        }
        a.status = ma.sanai3i.domain.ArtisanStatus.APPROVED;
        a.verified = true;                 // identity checked by an admin
        a.rejectionReason = null;
        a.reviewedAt = Instant.now();
        notifications.notify(a.user.id, "ARTISAN_APPROVED", null, null, null, null);
        log(admin, "ARTISAN_APPROVED", "USER", a.user.id, a.user.fullName);
        return artisanFile(artisanId);
    }

    public ma.sanai3i.dto.AdminDto.ArtisanReview rejectArtisan(AuthUser admin, Long artisanId, String reason) {
        ArtisanProfile a = findArtisan(artisanId);
        if (a.status != ma.sanai3i.domain.ArtisanStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING");
        }
        if (isBlank(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        a.status = ma.sanai3i.domain.ArtisanStatus.REJECTED;
        a.rejectionReason = reason.trim();
        a.reviewedAt = Instant.now();
        String note = a.rejectionReason.length() > 150 ? a.rejectionReason.substring(0, 150) + "…" : a.rejectionReason;
        notifications.notify(a.user.id, "ARTISAN_REJECTED", null, null, null, note);
        log(admin, "ARTISAN_REJECTED", "USER", a.user.id, a.user.fullName + " · " + note);
        return artisanFile(artisanId);
    }

    private ArtisanProfile findArtisan(Long id) {
        return artisans.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTISAN_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ trail, broadcast, settings

    @Transactional(readOnly = true)
    public PageOf<AuditRow> audit(String action, String q, int page, int size) {
        int s = clampSize(size);
        org.springframework.data.domain.Page<AuditLog> p = auditLog.search(
                action == null ? "" : action.trim(), q == null ? "" : q.trim(), PageRequest.of(Math.max(0, page), s));
        return new PageOf<>(p.getContent().stream().map(this::auditRow).toList(), p.getTotalElements(), Math.max(0, page), s);
    }

    /** audience: ALL, CLIENTS or ARTISANS. Administrators and blocked users do not receive it. */
    public int broadcast(AuthUser admin, String audience, String message) {
        String a = audience.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("ALL", "CLIENTS", "ARTISANS").contains(a)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        int sent = 0;
        for (User u : users.findAll()) {
            boolean target = !u.blocked && u.role != Role.ADMIN
                    && ("ALL".equals(a) || ("CLIENTS".equals(a) && u.role == Role.CLIENT) || ("ARTISANS".equals(a) && u.role == Role.ARTISAN));
            if (target) {
                notifications.notify(u.id, "ANNOUNCEMENT", null, null, null, message.trim());
                sent++;
            }
        }
        log(admin, "BROADCAST", "SYSTEM", null, a + " · " + sent + " · " + message.trim());
        return sent;
    }

    @Transactional(readOnly = true)
    public SettingsDto settings() {
        return new SettingsDto(settings.registrationsOpen(), settings.banner());
    }

    public SettingsDto updateSettings(AuthUser admin, boolean registrationsOpen, String banner) {
        settings.update(registrationsOpen, banner);
        log(admin, "SETTINGS_CHANGED", "SYSTEM", null, "registrationsOpen=" + registrationsOpen
                + (isBlank(banner) ? "" : " · banner: " + banner.trim()));
        return new SettingsDto(settings.registrationsOpen(), settings.banner());
    }

    // ------------------------------------------------------------------ csv export

    @Transactional(readOnly = true)
    public String exportUsers() {
        StringBuilder sb = new StringBuilder("﻿id;phone;name;role;city;blocked;created;last_login\n");
        for (User u : users.findAll(Sort.by("id"))) {
            sb.append(u.id).append(';').append(csv(u.phone)).append(';').append(csv(u.fullName)).append(';')
                    .append(u.role).append(';').append(csv(u.city)).append(';').append(u.blocked).append(';')
                    .append(u.createdAt).append(';').append(u.lastLoginAt == null ? "" : u.lastLoginAt).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportRequests() {
        StringBuilder sb = new StringBuilder("﻿id;category;client;client_phone;city;status;budget;offers;created\n");
        for (JobRequest r : requests.findAll(Sort.by("id"))) {
            sb.append(r.id).append(';').append(csv(r.category.code)).append(';').append(csv(r.client.fullName)).append(';')
                    .append(csv(r.client.phone)).append(';').append(csv(r.city)).append(';').append(r.status).append(';')
                    .append(r.budget).append(';').append(offers.countByRequestId(r.id)).append(';').append(r.createdAt).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private UserRow userRow(User u) {
        ArtisanProfile a = artisans.findByUserId(u.id).orElse(null);
        return new UserRow(u.id, u.phone, u.fullName, u.role, u.city, u.blocked, u.blockedReason, u.createdAt,
                u.lastLoginAt, a == null ? null : a.id, a != null && a.verified, requests.countByClientId(u.id),
                a == null ? 0 : offers.countByArtisanId(a.id));
    }

    private RequestRow requestRow(JobRequest r) {
        return new RequestRow(r.id, r.category.code, r.category.nameAr, r.category.nameFr, r.client.fullName,
                r.client.phone, r.city, r.status.name(), r.budget, offers.countByRequestId(r.id), r.createdAt);
    }

    private CategoryRow categoryRow(Category c) {
        return new CategoryRow(c.id, c.code, c.nameAr, c.nameFr, c.sortOrder, artisans.countByCategoryId(c.id),
                requests.countByCategoryId(c.id), c.minPrice, c.maxPrice);
    }

    private AuditRow auditRow(AuditLog l) {
        return new AuditRow(l.id, l.actorId, l.actorName, l.action, l.targetType, l.targetId, l.detail, l.ip, l.createdAt);
    }

    private void log(AuthUser admin, String action, String targetType, Long targetId, String detail) {
        User who = users.findById(admin.userId()).orElse(null);
        audit.log(admin.userId(), who == null ? null : who.fullName, action, targetType, targetId, detail);
    }

    private void guardTarget(AuthUser admin, User target) {
        if (target.id.equals(admin.userId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_TARGET_SELF");
        }
        if (target.role == Role.ADMIN) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_TARGET_ADMIN");
        }
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }

    private JobRequest findRequest(Long id) {
        return requests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
    }

    private static <T, R> PageOf<R> slice(List<T> all, int page, int size, java.util.function.Function<T, R> map) {
        int s = clampSize(size);
        int p = Math.max(0, page);
        int from = Math.min(all.size(), p * s);
        int to = Math.min(all.size(), from + s);
        return new PageOf<>(all.subList(from, to).stream().map(map).toList(), all.size(), p, s);
    }

    private static int clampSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    private static String norm(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Quotes a CSV field and neutralises spreadsheet formulas (=, +, -, @ at the start). */
    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
