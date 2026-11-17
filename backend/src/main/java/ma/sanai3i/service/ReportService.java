package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Report;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.MessageRepository;
import ma.sanai3i.repo.ReportRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Report" button on a person (artisan or client), a request, a review or a chat message; handled by admins. */
@Service
@Transactional
public class ReportService {
    public static final Set<String> REASONS = Set.of("FRAUD", "NO_SHOW", "RUDE", "FAKE_PROFILE", "BAD_WORK", "PRICE", "SPAM", "OTHER");

    public record ReportInput(String targetType, Long targetId, String reason, String details) {
    }

    public record ReportRow(Long id, String targetType, Long targetId, Long targetUserId, String targetName, String targetPhone,
                            String targetRole, long reportsAgainstTarget, Long artisanId, String reason, String details,
                            String reporterName, String reporterPhone, String status, String resolution, Instant createdAt,
                            Instant handledAt) {
    }

    private final ReportRepository reports;
    private final ma.sanai3i.repo.OfferRepository offers;
    private final UserRepository users;
    private final ArtisanProfileRepository artisans;
    private final JobRequestRepository requests;
    private final ReviewRepository reviews;
    private final MessageRepository messages;
    private final NotificationService notifications;
    private final AuditService audit;

    public ReportService(ReportRepository reports, UserRepository users, ArtisanProfileRepository artisans,
                         JobRequestRepository requests, ReviewRepository reviews, MessageRepository messages,
                         NotificationService notifications, AuditService audit, ma.sanai3i.repo.OfferRepository offers) {
        this.offers = offers;
        this.reports = reports;
        this.users = users;
        this.artisans = artisans;
        this.requests = requests;
        this.reviews = reviews;
        this.messages = messages;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** A client and an artisan know each other once an offer was made on one of the client's requests. */
    private boolean related(Long a, Long b) {
        return offers.existsByArtisanUserIdAndRequestClientId(a, b) || offers.existsByArtisanUserIdAndRequestClientId(b, a);
    }

    /** The client who posted it, an artisan who made an offer, or an approved artisan who is shown it. */
    private boolean canSee(Long meId, ma.sanai3i.domain.JobRequest r) {
        if (r.client.id.equals(meId) || offers.existsByRequestIdAndArtisanUserId(r.id, meId)) {
            return true;
        }
        return r.status == ma.sanai3i.domain.RequestStatus.OPEN && artisans.findByUserId(meId)
                .filter(a -> a.status == ma.sanai3i.domain.ArtisanStatus.APPROVED && a.category.id.equals(r.category.id))
                .map(a -> RequestService.matches(a, r)).orElse(false);
    }

    public void create(AuthUser me, ReportInput in) {
        String type = in.targetType() == null ? "" : in.targetType().trim().toUpperCase(Locale.ROOT);
        String reason = in.reason() == null ? "" : in.reason().trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(reason) || in.targetId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        Long targetId = in.targetId();
        Long targetUser;
        // Only what the reporter can actually see may be reported; anything else answers "not found" like an
        // unknown id (no way to probe which ids exist, no way to raise someone's report count from outside).
        Long meId = me.userId();
        switch (type) {
            case "ARTISAN" -> {           // the app knows artisans by their profile id
                targetUser = artisans.findById(targetId).map(a -> a.user.id).orElseThrow(this::notFound);
                type = "USER";
                targetId = targetUser;
                if (!related(meId, targetUser)) {
                    throw notFound();
                }
            }
            case "USER" -> {
                targetUser = users.findById(targetId).map(u -> u.id).orElseThrow(this::notFound);
                if (!related(meId, targetUser)) {
                    throw notFound();
                }
            }
            case "REQUEST" -> {
                ma.sanai3i.domain.JobRequest req = requests.findById(targetId).orElseThrow(this::notFound);
                if (!canSee(meId, req)) {
                    throw notFound();
                }
                targetUser = req.client.id;
            }
            case "REVIEW" -> {
                ma.sanai3i.domain.Review rv = reviews.findById(targetId).orElseThrow(this::notFound);
                if (!rv.artisan.user.id.equals(meId)) {           // the reviewed artisan
                    throw notFound();
                }
                targetUser = rv.client.id;
            }
            case "MESSAGE" -> {
                ma.sanai3i.domain.Message msg = messages.findById(targetId).orElseThrow(this::notFound);
                ma.sanai3i.domain.JobRequest req = requests.findById(msg.requestId).orElseThrow(this::notFound);
                boolean party = req.client.id.equals(meId) || offers.existsByRequestIdAndArtisanUserId(req.id, meId);
                if (!party) {
                    throw notFound();
                }
                targetUser = msg.senderId;
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        if (targetUser.equals(me.userId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_TARGET_SELF");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(me.userId(), type, targetId, "OPEN")) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED");
        }
        Report r = new Report();
        r.reporterId = me.userId();
        r.targetType = type;
        r.targetId = targetId;
        r.targetUserId = targetUser;
        r.reason = reason;
        r.details = in.details() == null || in.details().isBlank() ? null
                : in.details().trim().substring(0, Math.min(1000, in.details().trim().length()));
        reports.save(r);
        User reporter = users.findById(me.userId()).orElse(null);
        audit.logUser(reporter, "REPORT_CREATED", type, targetId, reason);
        for (User admin : users.findAll()) {
            if (admin.role == Role.ADMIN && !admin.blocked
                    && (admin.superAdmin || admin.permissionSet().contains(ma.sanai3i.domain.Permission.REPORTS))) {
                notifications.notify(admin.id, "REPORT_CREATED", null, null, null, reason);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ReportRow> list(String status) {
        String s = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        return reports.findByStatusOrderByCreatedAtDesc(s).stream().map(this::row).toList();
    }

    public ReportRow handle(AuthUser admin, Long id, String status, String resolution) {
        Report r = reports.findById(id).orElseThrow(this::notFound);
        String s = "DISMISSED".equalsIgnoreCase(status) ? "DISMISSED" : "RESOLVED";
        r.status = s;
        r.resolution = resolution == null || resolution.isBlank() ? null : resolution.trim();
        r.handledBy = admin.userId();
        r.handledAt = Instant.now();
        notifications.notify(r.reporterId, "REPORT_HANDLED", null, null, null, s);
        User who = users.findById(admin.userId()).orElse(null);
        audit.logUser(who, "REPORT_" + s, "REPORT", r.id, r.resolution);
        return row(r);
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return reports.countByStatus("OPEN");
    }

    private ReportRow row(Report r) {
        User target = r.targetUserId == null ? null : users.findById(r.targetUserId).orElse(null);
        User reporter = users.findById(r.reporterId).orElse(null);
        Long artisanId = target == null ? null : artisans.findByUserId(target.id).map(a -> a.id).orElse(null);
        return new ReportRow(r.id, r.targetType, r.targetId, r.targetUserId,
                target == null ? null : target.fullName, target == null ? null : target.phone,
                target == null ? null : target.role.name(),
                r.targetUserId == null ? 0 : reports.countByTargetUserId(r.targetUserId), artisanId, r.reason, r.details,
                reporter == null ? null : reporter.fullName, reporter == null ? null : reporter.phone, r.status,
                r.resolution, r.createdAt, r.handledAt);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
