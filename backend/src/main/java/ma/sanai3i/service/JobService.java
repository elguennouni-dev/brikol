package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.JobCase;
import ma.sanai3i.domain.JobItem;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.OfferStatus;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.MediaDto;
import ma.sanai3i.repo.JobCaseRepository;
import ma.sanai3i.repo.JobItemRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * The job itself, once an offer is accepted. Brikol never holds the money (it is paid cash), so it protects both
 * sides another way: everything agreed is written down and confirmed by the other side (price, extras, advances),
 * the job is closed with a code only the client has, the client gets a 7-day warranty, and either side can ask
 * the team for help. Repeat offenders get strikes (see StrikeService).
 */
@Service
@Transactional
public class JobService {
    public static final int CODE_TRIES = 5;
    public static final int WARRANTY_DAYS = 7;
    /** A warranty claim the artisan has not handled after this many hours goes to the team. */
    public static final int WARRANTY_HOURS = 72;
    public static final int MAX_AMOUNT = 100_000;

    static final Set<String> CLIENT_REASONS = Set.of("NO_SHOW", "PRICE", "QUALITY", "BEHAVIOR", "OTHER");
    static final Set<String> ARTISAN_REASONS = Set.of("UNPAID", "CLIENT_ABSENT", "PRICE", "BEHAVIOR", "OTHER");
    static final Set<String> ACTIVE = Set.of("OPEN", "FIXED", "ESCALATED");

    public record ItemDto(Long id, String kind, String label, int amount, String status, String createdBy, boolean mine,
                          boolean canAnswer, String mediaUrl, Instant createdAt) {
    }

    public record CaseDto(Long id, String kind, String reason, String status, boolean mine, String openedRole, String details,
                          String mediaUrl, String resolution, Instant createdAt, Instant updatedAt) {
    }

    public record JobView(Long requestId, String status, String myRole, String categoryCode, String categoryNameAr,
                          String categoryNameFr, String description, String otherName, String otherPhone, Long artisanId,
                          int agreedPrice, int extras, int advances, int total, int remaining,
                          Instant appointmentAt, String appointmentBy, boolean appointmentConfirmed,
                          Instant arrivedAt, Instant finishedAt, Instant doneAt, String doneBy, Instant warrantyUntil,
                          /** Only for the client, while the job is open. */
                          String code, boolean codeLocked, int codeTriesLeft,
                          List<ItemDto> items, List<MediaDto> before, List<MediaDto> after, List<CaseDto> cases,
                          long otherStrikes, boolean reviewed) {
    }

    private record Side(JobRequest r, Offer offer, User me, User other, String role) {
        boolean client() {
            return "CLIENT".equals(role);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final UserRepository users;
    private final JobItemRepository items;
    private final JobCaseRepository cases;
    private final MediaService media;
    private final StrikeService strikes;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ma.sanai3i.repo.ReviewRepository reviews;

    public JobService(JobRequestRepository requests, OfferRepository offers, UserRepository users, JobItemRepository items,
                      JobCaseRepository cases, MediaService media, StrikeService strikes,
                      NotificationService notifications, AuditService audit, ma.sanai3i.repo.ReviewRepository reviews) {
        this.requests = requests;
        this.offers = offers;
        this.users = users;
        this.items = items;
        this.cases = cases;
        this.media = media;
        this.strikes = strikes;
        this.notifications = notifications;
        this.audit = audit;
        this.reviews = reviews;
    }

    public static String newCode() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    // ------------------------------------------------------------------ view

    public JobView view(AuthUser me, Long requestId) {
        return view(side(me, requestId));
    }

    private JobView view(Side s) {
        JobRequest r = s.r();
        if (r.doneCode == null && r.status == RequestStatus.ACCEPTED) {
            r.doneCode = newCode();                 // jobs accepted before this feature
        }
        List<JobItem> list = items.findByRequestIdOrderByIdAsc(r.id);
        int extras = sum(list, JobItem.EXTRA);
        int advances = sum(list, JobItem.ADVANCE);
        int agreed = s.offer().price;
        List<ItemDto> itemDtos = list.stream().map(i -> new ItemDto(i.id, i.kind, i.label, i.amount, i.status, i.createdBy,
                i.createdBy.equals(s.role()), "PENDING".equals(i.status) && !i.createdBy.equals(s.role()) && r.status == RequestStatus.ACCEPTED,
                i.mediaId == null ? null : "/media/" + i.mediaId, i.createdAt)).toList();
        List<CaseDto> caseDtos = cases.findByRequestIdOrderByIdAsc(r.id).stream()
                .filter(c -> JobCase.WARRANTY.equals(c.kind) || c.openedBy.equals(s.me().id))   // problems are between one side and the team
                .map(c -> caseDto(c, s.me().id)).toList();
        boolean showCode = s.client() && r.status == RequestStatus.ACCEPTED;
        return new JobView(r.id, r.status.name(), s.role(), r.category.code, r.category.nameAr, r.category.nameFr, r.description,
                Mapper.shortName(s.other().fullName), s.other().phone, s.offer().artisan.id,
                agreed, extras, advances, agreed + extras, Math.max(0, agreed + extras - advances),
                r.appointmentAt, r.appointmentBy, r.appointmentConfirmed, r.arrivedAt, r.finishedAt, r.doneAt, r.doneBy, r.warrantyUntil,
                showCode ? r.doneCode : null, r.codeAttempts >= CODE_TRIES, Math.max(0, CODE_TRIES - r.codeAttempts),
                itemDtos, media.ofRequest(r.id, Media.JOB_BEFORE), media.ofRequest(r.id, Media.JOB_AFTER), caseDtos,
                strikes.recent(s.other().id), s.client() && r.status == RequestStatus.DONE && reviews.existsByRequestId(r.id));
    }

    static CaseDto caseDto(JobCase c, Long viewerId) {
        return new CaseDto(c.id, c.kind, c.reason, c.status, c.openedBy.equals(viewerId), c.openedRole, c.details,
                c.mediaId == null ? null : "/media/" + c.mediaId, c.resolution, c.createdAt, c.updatedAt);
    }

    private static int sum(List<JobItem> list, String kind) {
        return list.stream().filter(i -> kind.equals(i.kind) && "ACCEPTED".equals(i.status)).mapToInt(i -> i.amount).sum();
    }

    // ------------------------------------------------------------------ artisan: here / finished / code

    public JobView arrived(AuthUser me, Long requestId) {
        Side s = open(me, requestId, "ARTISAN");
        if (s.r().arrivedAt == null) {
            s.r().arrivedAt = Instant.now();
            notifications.notify(s.other().id, "ARTISAN_ARRIVED", s.r().id, s.offer().artisan.nameAr, s.offer().artisan.nameFr, null);
            audit.logUser(s.me(), "ARTISAN_ARRIVED", "REQUEST", s.r().id, null);
        }
        return view(s);
    }

    public JobView finished(AuthUser me, Long requestId) {
        Side s = open(me, requestId, "ARTISAN");
        JobRequest r = s.r();
        if (r.arrivedAt == null) {
            r.arrivedAt = Instant.now();
        }
        r.finishedAt = Instant.now();
        if (r.doneCode == null) {
            r.doneCode = newCode();
        }
        // the client gets the code again with the amount to pay, so it reaches them even outside the app
        JobView v = view(s);
        notifications.notify(s.other().id, "JOB_FINISHED", r.id, s.offer().artisan.nameAr, s.offer().artisan.nameFr,
                v.remaining() + "|" + r.doneCode);
        audit.logUser(s.me(), "JOB_FINISHED", "REQUEST", r.id, null);
        return v;
    }

    /** The failed tries must be saved even though the call answers with an error. */
    @Transactional(noRollbackFor = ApiException.class)
    public JobView enterCode(AuthUser me, Long requestId, String code) {
        Side s = open(me, requestId, "ARTISAN");
        JobRequest r = s.r();
        if (r.codeAttempts >= CODE_TRIES) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_LOCKED");
        }
        String typed = code == null ? "" : code.replaceAll("\\D", "");
        if (r.doneCode == null || !r.doneCode.equals(typed)) {
            r.codeAttempts = r.codeAttempts + 1;
            requests.save(r);
            throw new ApiException(HttpStatus.BAD_REQUEST, r.codeAttempts >= CODE_TRIES ? "CODE_LOCKED" : "CODE_WRONG");
        }
        close(s, "CODE");
        return view(s);
    }

    /** The client closes the job alone (the artisan could not type the code). */
    public JobView confirmByClient(AuthUser me, Long requestId) {
        Side s = open(me, requestId, "CLIENT");
        close(s, "CLIENT");
        return view(s);
    }

    private void close(Side s, String by) {
        JobRequest r = s.r();
        Instant now = Instant.now();
        r.status = RequestStatus.DONE;
        r.doneAt = now;
        r.doneBy = by;
        r.warrantyUntil = now.plus(WARRANTY_DAYS, ChronoUnit.DAYS);
        if (r.finishedAt == null) {
            r.finishedAt = now;
        }
        s.offer().artisan.jobsDone = s.offer().artisan.jobsDone + 1;
        // pending extras / advances that nobody answered do not count
        for (JobItem i : items.findByRequestIdOrderByIdAsc(r.id)) {
            if ("PENDING".equals(i.status)) {
                i.status = "REFUSED";
                i.answeredAt = now;
            }
        }
        User client = r.client;
        User artisan = s.offer().artisan.user;
        if ("CODE".equals(by)) {
            notifications.notify(client.id, "JOB_DONE_CODE", r.id, s.offer().artisan.nameAr, s.offer().artisan.nameFr, null);
            notifications.notify(artisan.id, "JOB_CLOSED", r.id, client.fullName, client.fullName, null);
        } else {
            notifications.notify(artisan.id, "JOB_DONE", r.id, client.fullName, client.fullName, null);
        }
        audit.logUser(s.me(), "JOB_DONE", "REQUEST", r.id, by);
    }

    /** The artisan cannot do the job after all: the request is open again and the client can pick another offer. */
    public JobView withdraw(AuthUser me, Long requestId) {
        Side s = open(me, requestId, "ARTISAN");
        JobRequest r = s.r();
        List<JobItem> list = items.findByRequestIdOrderByIdAsc(r.id);
        if (list.stream().anyMatch(i -> JobItem.ADVANCE.equals(i.kind) && "ACCEPTED".equals(i.status))) {
            throw new ApiException(HttpStatus.CONFLICT, "ADVANCE_GIVEN");     // money changed hands: the team must handle it
        }
        Instant now = Instant.now();
        boolean late = r.arrivedAt != null
                || (r.appointmentConfirmed && r.appointmentAt != null && r.appointmentAt.isBefore(now.plus(24, ChronoUnit.HOURS)));
        items.deleteAll(list);
        for (Offer o : offers.findByRequestIdOrderByPriceAsc(r.id)) {
            if (o.id.equals(s.offer().id)) {
                o.status = OfferStatus.REJECTED;
            } else if (o.status == OfferStatus.REJECTED) {
                o.status = OfferStatus.PENDING;          // the other artisans' offers are back for the client
            }
        }
        r.status = RequestStatus.OPEN;
        r.acceptedOfferId = null;
        r.appointmentAt = null;
        r.appointmentBy = null;
        r.appointmentConfirmed = false;
        r.reminderSent = false;
        r.arrivedAt = null;
        r.finishedAt = null;
        r.doneCode = null;
        r.codeAttempts = 0;
        notifications.notify(r.client.id, "ARTISAN_WITHDREW", r.id, s.offer().artisan.nameAr, s.offer().artisan.nameFr, null);
        audit.logUser(s.me(), "ARTISAN_WITHDREW", "REQUEST", r.id, late ? "late" : null);
        if (late) {
            strikes.give(s.me().id, r.id, null, "LATE_CANCEL", null);
        }
        return null;
    }

    // ------------------------------------------------------------------ extras and advances

    public JobView addItem(AuthUser me, Long requestId, String kind, String label, int amount, String mediaId) {
        Side s = open(me, requestId, null);
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (!JobItem.EXTRA.equals(k) && !JobItem.ADVANCE.equals(k)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        if (JobItem.EXTRA.equals(k) && s.client()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_INVALID");
        }
        if (items.findByRequestIdOrderByIdAsc(s.r().id).stream().filter(i -> "PENDING".equals(i.status)).count() >= 5) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_PENDING");
        }
        JobItem i = new JobItem();
        i.requestId = s.r().id;
        i.kind = k;
        i.label = label == null || label.isBlank() ? null : label.trim().substring(0, Math.min(120, label.trim().length()));
        i.amount = amount;
        i.createdBy = s.role();
        if (mediaId != null && !mediaId.isBlank()) {
            i.mediaId = media.attach(s.me().id, s.r().id, mediaId, Media.RECEIPT).id;
        }
        items.save(i);
        String name = s.client() ? s.me().fullName : null;
        notifications.notify(s.other().id, JobItem.EXTRA.equals(k) ? "EXTRA_PROPOSED" : "ADVANCE_DECLARED", s.r().id,
                s.client() ? name : s.offer().artisan.nameAr, s.client() ? name : s.offer().artisan.nameFr, String.valueOf(amount));
        audit.logUser(s.me(), k + "_ADDED", "REQUEST", s.r().id, amount + " MAD");
        return view(s);
    }

    public JobView answerItem(AuthUser me, Long requestId, Long itemId, boolean accept) {
        Side s = open(me, requestId, null);
        JobItem i = items.findById(itemId).filter(x -> x.requestId.equals(s.r().id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!"PENDING".equals(i.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ANSWERED");
        }
        if (i.createdBy.equals(s.role())) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_OTHER");
        }
        i.status = accept ? "ACCEPTED" : "REFUSED";
        i.answeredAt = Instant.now();
        String n = s.client() ? s.me().fullName : null;
        notifications.notify(s.other().id, accept ? "ITEM_ACCEPTED" : "ITEM_REFUSED", s.r().id,
                s.client() ? n : s.offer().artisan.nameAr, s.client() ? n : s.offer().artisan.nameFr, String.valueOf(i.amount));
        audit.logUser(s.me(), i.kind + (accept ? "_ACCEPTED" : "_REFUSED"), "REQUEST", s.r().id, i.amount + " MAD");
        return view(s);
    }

    // ------------------------------------------------------------------ photos before / after

    public JobView addPhoto(AuthUser me, Long requestId, String mediaId, String when) {
        Side s = side(me, requestId);
        if (s.client()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        if (s.r().status != RequestStatus.ACCEPTED && s.r().status != RequestStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        String purpose = "BEFORE".equalsIgnoreCase(when) ? Media.JOB_BEFORE : Media.JOB_AFTER;
        if (media.ofRequest(s.r().id, purpose).size() >= 6) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_FILES");
        }
        media.attach(s.me().id, s.r().id, mediaId, purpose);
        return view(s);
    }

    // ------------------------------------------------------------------ problems and warranty

    public JobView openCase(AuthUser me, Long requestId, String kind, String reason, String details, String mediaId) {
        Side s = side(me, requestId);
        JobRequest r = s.r();
        String k = kind == null ? JobCase.PROBLEM : kind.trim().toUpperCase();
        String why = reason == null ? "OTHER" : reason.trim().toUpperCase();
        Instant now = Instant.now();
        if (JobCase.WARRANTY.equals(k)) {
            if (!s.client()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_ONLY");
            }
            if (r.status != RequestStatus.DONE || r.warrantyUntil == null || r.warrantyUntil.isBefore(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "WARRANTY_OVER");
            }
            if (cases.existsByRequestIdAndKindAndStatusIn(r.id, JobCase.WARRANTY, ACTIVE)) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED");
            }
            why = "CAME_BACK";
        } else if (JobCase.PROBLEM.equals(k)) {
            if (!(s.client() ? CLIENT_REASONS : ARTISAN_REASONS).contains(why)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
            }
            if (r.status != RequestStatus.ACCEPTED && r.status != RequestStatus.DONE) {
                throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
            }
            if (r.status == RequestStatus.DONE && r.doneAt != null && r.doneAt.isBefore(now.minus(30, ChronoUnit.DAYS))) {
                throw new ApiException(HttpStatus.CONFLICT, "TOO_LATE");
            }
            if ("UNPAID".equals(why) && r.finishedAt == null) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_FINISHED");
            }
            if ("NO_SHOW".equals(why) && (r.status != RequestStatus.ACCEPTED || r.arrivedAt != null)) {
                throw new ApiException(HttpStatus.CONFLICT, "ARTISAN_ARRIVED");
            }
            if (cases.existsByRequestIdAndOpenedByAndReasonAndStatusIn(r.id, s.me().id, why, ACTIVE)) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        JobCase c = new JobCase();
        c.requestId = r.id;
        c.kind = k;
        c.reason = why;
        c.openedBy = s.me().id;
        c.openedRole = s.role();
        c.details = details == null || details.isBlank() ? null : details.trim().substring(0, Math.min(1000, details.trim().length()));
        if (mediaId != null && !mediaId.isBlank()) {
            c.mediaId = media.attach(s.me().id, r.id, mediaId, Media.CASE).id;
        }
        cases.save(c);
        if (JobCase.WARRANTY.equals(k)) {
            notifications.notify(s.other().id, "WARRANTY_OPENED", r.id, s.me().fullName, s.me().fullName, null);
        } else {
            notifyStaff("CASE_NEW", r.id, why);
        }
        audit.logUser(s.me(), "CASE_OPENED", "REQUEST", r.id, k + " " + why);
        return view(s);
    }

    /** Warranty: the artisan came back and fixed it; the client must confirm. */
    public JobView warrantyFixed(AuthUser me, Long requestId, Long caseId) {
        Side s = side(me, requestId);
        if (s.client()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        JobCase c = warrantyCase(s, caseId);
        if (!"OPEN".equals(c.status) && !"ESCALATED".equals(c.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ANSWERED");
        }
        c.status = "FIXED";
        c.updatedAt = Instant.now();
        notifications.notify(s.other().id, "WARRANTY_FIXED", s.r().id, s.offer().artisan.nameAr, s.offer().artisan.nameFr, null);
        audit.logUser(s.me(), "WARRANTY_FIXED", "REQUEST", s.r().id, null);
        return view(s);
    }

    public JobView warrantyAnswer(AuthUser me, Long requestId, Long caseId, boolean fixed) {
        Side s = side(me, requestId);
        if (!s.client()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_ONLY");
        }
        JobCase c = warrantyCase(s, caseId);
        if (!"FIXED".equals(c.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_OTHER");
        }
        Instant now = Instant.now();
        c.updatedAt = now;
        if (fixed) {
            c.status = "RESOLVED";
            c.handledAt = now;
            c.resolution = "Confirmé par le client";
        } else {
            c.status = "OPEN";                       // back to the artisan, the 72 h start again
            notifications.notify(s.other().id, "WARRANTY_NOT_FIXED", s.r().id, s.me().fullName, s.me().fullName, null);
        }
        audit.logUser(s.me(), fixed ? "WARRANTY_RESOLVED" : "WARRANTY_NOT_FIXED", "REQUEST", s.r().id, null);
        return view(s);
    }

    private JobCase warrantyCase(Side s, Long caseId) {
        return cases.findById(caseId).filter(c -> c.requestId.equals(s.r().id) && JobCase.WARRANTY.equals(c.kind))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** Warranty claims the artisan ignored go to the team. */
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 2 * 60 * 1000L)
    public void escalate() {
        Instant limit = Instant.now().minus(WARRANTY_HOURS, ChronoUnit.HOURS);
        for (JobCase c : cases.findByKindAndStatusAndUpdatedAtBefore(JobCase.WARRANTY, "OPEN", limit)) {
            c.status = "ESCALATED";
            c.updatedAt = Instant.now();
            notifyStaff("CASE_NEW", c.requestId, "WARRANTY");
        }
    }

    void notifyStaff(String kind, Long requestId, String detail) {
        for (User admin : users.findAll()) {
            if (StrikeService.handlesCases(admin)) {
                notifications.notify(admin.id, kind, requestId, null, null, detail);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The job must be in progress (ACCEPTED); role = CLIENT / ARTISAN when only that side may do it. */
    private Side open(AuthUser me, Long requestId, String role) {
        Side s = side(me, requestId);
        if (s.r().status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        if (role != null && !role.equals(s.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT".equals(role) ? "CLIENT_ONLY" : "ARTISAN_ONLY");
        }
        return s;
    }

    private Side side(AuthUser me, Long requestId) {
        JobRequest r = requests.findById(requestId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        if (r.acceptedOfferId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
        }
        Offer o = offers.findById(r.acceptedOfferId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        User artisan = o.artisan.user;
        if (r.client.id.equals(me.userId())) {
            return new Side(r, o, r.client, artisan, "CLIENT");
        }
        if (artisan.id.equals(me.userId())) {
            return new Side(r, o, artisan, r.client, "ARTISAN");
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }
}
