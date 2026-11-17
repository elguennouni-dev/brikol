package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ClientReview;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Message;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.User;
import ma.sanai3i.push.PushService;
import ma.sanai3i.repo.ClientReviewRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.MessageRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** What happens between a client and the chosen artisan once an offer is accepted: chat, appointment, mutual reviews. */
@Service
@Transactional
public class DealService {
    public record MessageDto(Long id, Long requestId, boolean mine, String senderName, String body, Instant createdAt,
                             boolean read) {
    }

    public record Chat(Long requestId, String categoryCode, String categoryNameAr, String categoryNameFr, String status,
                       String otherName, String otherPhone, String myRole, Instant appointmentAt, String appointmentBy,
                       boolean appointmentConfirmed, List<MessageDto> messages) {
    }

    /** The two sides of a deal, seen from the current user. */
    private record Side(JobRequest r, Offer offer, User me, User other, String myRole) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ma.sanai3i.security.RateLimiter limiter;
    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final MessageRepository messages;
    private final ClientReviewRepository clientReviews;
    private final NotificationService notifications;
    private final NotificationHub hub;
    private final PushService push;
    private final AuditService audit;

    public DealService(JobRequestRepository requests, OfferRepository offers, MessageRepository messages,
                       ClientReviewRepository clientReviews, NotificationService notifications, NotificationHub hub,
                       PushService push, AuditService audit) {
        this.requests = requests;
        this.offers = offers;
        this.messages = messages;
        this.clientReviews = clientReviews;
        this.notifications = notifications;
        this.hub = hub;
        this.push = push;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ chat

    public Chat chat(AuthUser me, Long requestId) {
        Side s = side(me, requestId);
        for (Message m : messages.findByRequestIdAndSenderIdNotAndReadAtIsNull(requestId, s.me().id)) {
            m.readAt = Instant.now();
        }
        List<MessageDto> list = messages.findByRequestIdOrderByIdAsc(requestId).stream()
                .filter(m -> m.senderId.equals(s.me().id) || m.senderId.equals(s.other().id))   // not an earlier artisan's chat
                .map(m -> dto(m, s.me().id, m.senderId.equals(s.me().id) ? s.me() : s.other())).toList();
        JobRequest r = s.r();
        return new Chat(r.id, r.category.code, r.category.nameAr, r.category.nameFr, r.status.name(),
                Mapper.shortName(s.other().fullName), s.other().phone, s.myRole(), r.appointmentAt, r.appointmentBy,
                r.appointmentConfirmed, list);
    }

    public MessageDto send(AuthUser me, Long requestId, String body) {
        Side s = side(me, requestId);
        if (s.r().status != RequestStatus.ACCEPTED && s.r().status != RequestStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_CLOSED");
        }
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        if (!limiter.allow("msg:" + s.me().id, 20, 60_000L) || !limiter.allow("msgh:" + s.me().id, 300, 3_600_000L)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }
        Message m = new Message();
        m.requestId = requestId;
        m.senderId = s.me().id;
        m.body = text.length() > 2000 ? text.substring(0, 2000) : text;
        messages.save(m);
        MessageDto forOther = dto(m, s.other().id, s.me());
        Long otherId = s.other().id;
        String preview = m.body.length() > 80 ? m.body.substring(0, 80) + "…" : m.body;
        String name = Mapper.shortName(s.me().fullName);
        NotificationService.afterCommit(() -> {
            hub.publishEvent(otherId, "message", forOther);
            push.send(otherId, "NEW_MESSAGE", name, preview, "/app/#/chat/" + requestId);
        });
        return dto(m, s.me().id, s.me());
    }

    // ------------------------------------------------------------------ appointment

    public Chat proposeAppointment(AuthUser me, Long requestId, Instant at) {
        Side s = side(me, requestId);
        if (s.r().status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        Instant now = Instant.now();
        if (at == null || at.isBefore(now.plus(30, ChronoUnit.MINUTES)) || at.isAfter(now.plus(90, ChronoUnit.DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPOINTMENT_INVALID");
        }
        JobRequest r = s.r();
        r.appointmentAt = at;
        r.appointmentBy = s.myRole();
        r.appointmentConfirmed = false;
        r.reminderSent = false;
        notifications.notify(s.other().id, "APPOINTMENT_PROPOSED", r.id, s.me().fullName, s.me().fullName, at.toString());
        audit.logUser(s.me(), "APPOINTMENT_PROPOSED", "REQUEST", r.id, at.toString());
        return chat(me, requestId);
    }

    public Chat confirmAppointment(AuthUser me, Long requestId) {
        Side s = side(me, requestId);
        JobRequest r = s.r();
        if (r.appointmentAt == null || r.appointmentConfirmed) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_INVALID");
        }
        if (s.myRole().equals(r.appointmentBy)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_OTHER");
        }
        r.appointmentConfirmed = true;
        notifications.notify(s.other().id, "APPOINTMENT_CONFIRMED", r.id, s.me().fullName, s.me().fullName, r.appointmentAt.toString());
        return chat(me, requestId);
    }

    /** One hour before a confirmed appointment both sides get a reminder. */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reminders() {
        Instant now = Instant.now();
        for (JobRequest r : requests.findByStatusAndAppointmentConfirmedTrueAndReminderSentFalseAndAppointmentAtBetween(
                RequestStatus.ACCEPTED, now, now.plus(65, ChronoUnit.MINUTES))) {
            r.reminderSent = true;
            Offer o = r.acceptedOfferId == null ? null : offers.findById(r.acceptedOfferId).orElse(null);
            notifications.notify(r.client.id, "APPOINTMENT_REMINDER", r.id, null, null, r.appointmentAt.toString());
            if (o != null) {
                notifications.notify(o.artisan.user.id, "APPOINTMENT_REMINDER", r.id, null, null, r.appointmentAt.toString());
            }
        }
    }

    // ------------------------------------------------------------------ the artisan rates the client

    public void reviewClient(AuthUser me, Long requestId, int rating, String body) {
        Side s = side(me, requestId);
        if (!"ARTISAN".equals(s.myRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        if (s.r().status != RequestStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_DONE");
        }
        if (clientReviews.existsByRequestId(requestId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVIEWED");
        }
        ClientReview cr = new ClientReview();
        cr.requestId = requestId;
        cr.artisanId = s.offer().artisan.id;
        cr.clientId = s.other().id;
        cr.rating = rating;
        cr.body = body == null || body.isBlank() ? null : body.trim();
        clientReviews.save(cr);
        notifications.notify(s.other().id, "CLIENT_REVIEWED", requestId, s.offer().artisan.nameAr, s.offer().artisan.nameFr,
                String.valueOf(rating));
        audit.logUser(s.me(), "CLIENT_REVIEWED", "REQUEST", requestId, String.valueOf(rating));
    }

    // ------------------------------------------------------------------ helpers

    private Side side(AuthUser me, Long requestId) {
        JobRequest r = requests.findById(requestId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        if (r.acceptedOfferId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
        }
        Offer o = offers.findById(r.acceptedOfferId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND"));
        User client = r.client;
        User artisan = o.artisan.user;
        if (client.id.equals(me.userId())) {
            return new Side(r, o, client, artisan, "CLIENT");
        }
        if (artisan.id.equals(me.userId())) {
            return new Side(r, o, artisan, client, "ARTISAN");
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    private static MessageDto dto(Message m, Long viewerId, User sender) {
        return new MessageDto(m.id, m.requestId, m.senderId.equals(viewerId), Mapper.shortName(sender.fullName), m.body,
                m.createdAt, m.readAt != null);
    }
}
