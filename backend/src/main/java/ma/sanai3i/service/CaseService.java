package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.JobCase;
import ma.sanai3i.domain.JobItem;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.Strike;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.MediaDto;
import ma.sanai3i.repo.JobCaseRepository;
import ma.sanai3i.repo.JobItemRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.MessageRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.repo.StrikeRepository;
import ma.sanai3i.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** The team's side of problems and warranty claims: everything needed to decide, in one place. */
@Service
@Transactional
public class CaseService {
    public record CaseRow(Long id, Long requestId, String kind, String reason, String status, String openedRole, String openedByName,
                          String categoryNameAr, String categoryNameFr, String clientName, String artisanName,
                          String details, Instant createdAt, Instant updatedAt) {
    }

    public record Party(Long userId, String name, String phone, boolean blocked, Instant suspendedUntil, long recentStrikes,
                        List<StrikeDto> strikes) {
    }

    public record StrikeDto(Long id, String reason, Long requestId, boolean removed, boolean automatic, Instant createdAt) {
    }

    public record ChatLine(String from, String body, Instant at) {
    }

    public record CaseDetail(CaseRow row, String mediaUrl, String resolution, Instant handledAt,
                             String requestStatus, String description, String city, String address, int agreedPrice,
                             List<JobService.ItemDto> items, Instant acceptedAppointment, boolean appointmentConfirmed,
                             Instant arrivedAt, Instant finishedAt, Instant doneAt, String doneBy, Instant warrantyUntil,
                             List<MediaDto> requestMedia, List<MediaDto> before, List<MediaDto> after,
                             List<JobService.CaseDto> otherCases, List<ChatLine> chat, Party client, Party artisan) {
    }

    static final Set<String> OPEN = Set.of("OPEN", "ESCALATED");

    private final JobCaseRepository cases;
    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final JobItemRepository items;
    private final MessageRepository messages;
    private final StrikeRepository strikeRepo;
    private final UserRepository users;
    private final MediaService media;
    private final StrikeService strikes;
    private final NotificationService notifications;
    private final AuditService audit;

    public CaseService(JobCaseRepository cases, JobRequestRepository requests, OfferRepository offers, JobItemRepository items,
                       MessageRepository messages, StrikeRepository strikeRepo, UserRepository users, MediaService media,
                       StrikeService strikes, NotificationService notifications, AuditService audit) {
        this.cases = cases;
        this.requests = requests;
        this.offers = offers;
        this.items = items;
        this.messages = messages;
        this.strikeRepo = strikeRepo;
        this.users = users;
        this.media = media;
        this.strikes = strikes;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** status: "open" (default: problems and escalated warranty claims waiting for the team) or "all". */
    @Transactional(readOnly = true)
    public List<CaseRow> list(String status) {
        List<JobCase> list = "all".equalsIgnoreCase(status) ? cases.findTop200ByOrderByCreatedAtDesc()
                : cases.findByStatusInOrderByCreatedAtDesc(OPEN).stream()
                .filter(c -> JobCase.PROBLEM.equals(c.kind) || "ESCALATED".equals(c.status)).toList();
        return list.stream().map(this::row).toList();
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return list("open").size();
    }

    @Transactional(readOnly = true)
    public CaseDetail detail(Long id) {
        JobCase c = find(id);
        JobRequest r = request(c);
        Offer o = r.acceptedOfferId == null ? null : offers.findById(r.acceptedOfferId).orElse(null);
        User artisan = o == null ? null : o.artisan.user;
        List<JobService.ItemDto> itemDtos = items.findByRequestIdOrderByIdAsc(r.id).stream()
                .map(i -> new JobService.ItemDto(i.id, i.kind, i.label, i.amount, i.status, i.createdBy, false, false,
                        i.mediaId == null ? null : "/media/" + i.mediaId, i.createdAt)).toList();
        List<ChatLine> chat = messages.findByRequestIdOrderByIdAsc(r.id).stream()
                .map(m -> new ChatLine(m.senderId.equals(r.client.id) ? "CLIENT" : "ARTISAN", m.body, m.createdAt)).toList();
        List<JobService.CaseDto> others = cases.findByRequestIdOrderByIdAsc(r.id).stream().filter(x -> !x.id.equals(c.id))
                .map(x -> JobService.caseDto(x, -1L)).toList();
        return new CaseDetail(row(c), c.mediaId == null ? null : "/media/" + c.mediaId, c.resolution, c.handledAt,
                r.status.name(), r.description, r.city, r.address, o == null ? r.budget : o.price, itemDtos,
                r.appointmentAt, r.appointmentConfirmed, r.arrivedAt, r.finishedAt, r.doneAt, r.doneBy, r.warrantyUntil,
                media.ofRequest(r.id, Media.REQUEST), media.ofRequest(r.id, Media.JOB_BEFORE), media.ofRequest(r.id, Media.JOB_AFTER),
                others, chat, party(r.client), artisan == null ? null : party(artisan));
    }

    /**
     * status RESOLVED (closed) or OPEN (still working on it). Optional strikes for either side, and the job can be
     * closed as done when the team checked it was done and paid.
     */
    public CaseDetail handle(User staff, Long id, String status, String resolution, String strikeClient, String strikeArtisan,
                             boolean closeJob) {
        JobCase c = find(id);
        JobRequest r = request(c);
        Offer o = r.acceptedOfferId == null ? null : offers.findById(r.acceptedOfferId).orElse(null);
        String st = status == null ? "RESOLVED" : status.trim().toUpperCase();
        if (!st.equals("RESOLVED") && !st.equals("OPEN")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        Instant now = Instant.now();
        c.resolution = resolution == null || resolution.isBlank() ? c.resolution : resolution.trim().substring(0, Math.min(500, resolution.trim().length()));
        c.handledBy = staff.id;
        c.updatedAt = now;
        if (st.equals("RESOLVED")) {
            c.status = "RESOLVED";
            c.handledAt = now;
            notifications.notify(c.openedBy, "CASE_RESOLVED", r.id, null, null, c.resolution);
        } else if (JobCase.WARRANTY.equals(c.kind) && "ESCALATED".equals(c.status)) {
            c.status = "ESCALATED";
        }
        if (strikeClient != null && !strikeClient.isBlank()) {
            strikes.give(r.client.id, r.id, c.id, strikeClient.trim().toUpperCase(), staff);
        }
        if (strikeArtisan != null && !strikeArtisan.isBlank() && o != null) {
            strikes.give(o.artisan.user.id, r.id, c.id, strikeArtisan.trim().toUpperCase(), staff);
        }
        if (closeJob && r.status == RequestStatus.ACCEPTED && o != null) {
            r.status = RequestStatus.DONE;
            r.doneAt = now;
            r.doneBy = "ADMIN";
            r.warrantyUntil = now.plus(JobService.WARRANTY_DAYS, java.time.temporal.ChronoUnit.DAYS);
            if (r.finishedAt == null) {
                r.finishedAt = now;
            }
            o.artisan.jobsDone = o.artisan.jobsDone + 1;
            for (JobItem i : items.findByRequestIdOrderByIdAsc(r.id)) {
                if ("PENDING".equals(i.status)) {
                    i.status = "REFUSED";
                    i.answeredAt = now;
                }
            }
            notifications.notify(r.client.id, "JOB_CLOSED_ADMIN", r.id, null, null, null);
            notifications.notify(o.artisan.user.id, "JOB_CLOSED_ADMIN", r.id, null, null, null);
        }
        audit.logUser(staff, "CASE_HANDLED", "REQUEST", r.id, c.kind + " " + c.reason + " → " + c.status);
        return detail(id);
    }

    public Party strike(User staff, Long userId, Long requestId, String reason) {
        strikes.give(userId, requestId, null, reason == null ? "" : reason.trim().toUpperCase(), staff);
        return party(users.findById(userId).orElseThrow());
    }

    public Party removeStrike(User staff, Long strikeId) {
        Strike s = strikeRepo.findById(strikeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
        strikes.remove(strikeId, staff);
        return party(users.findById(s.userId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Party party(Long userId) {
        return party(users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND")));
    }

    private Party party(User u) {
        List<StrikeDto> list = strikeRepo.findByUserIdOrderByCreatedAtDesc(u.id).stream()
                .map(s -> new StrikeDto(s.id, s.reason, s.requestId, s.removed, s.createdBy == null, s.createdAt)).toList();
        return new Party(u.id, u.fullName, u.phone, u.blocked, u.suspended() ? u.suspendedUntil : null, strikes.recent(u.id), list);
    }

    private CaseRow row(JobCase c) {
        JobRequest r = request(c);
        Offer o = r.acceptedOfferId == null ? null : offers.findById(r.acceptedOfferId).orElse(null);
        String opener = users.findById(c.openedBy).map(u -> u.fullName).orElse("?");
        return new CaseRow(c.id, r.id, c.kind, c.reason, c.status, c.openedRole, opener, r.category.nameAr, r.category.nameFr,
                r.client.fullName, o == null ? null : o.artisan.nameFr, c.details, c.createdAt, c.updatedAt);
    }

    private JobCase find(Long id) {
        return cases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private JobRequest request(JobCase c) {
        return requests.findById(c.requestId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
    }
}
