package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Cities;
import ma.sanai3i.common.Geo;
import ma.sanai3i.domain.ArtisanStatus;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.OfferStatus;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.Review;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.domain.WhenPref;
import ma.sanai3i.dto.Dto.CounterOffer;
import ma.sanai3i.dto.Dto.CreateOffer;
import ma.sanai3i.dto.Dto.CreateRequest;
import ma.sanai3i.dto.Dto.OfferDto;
import ma.sanai3i.dto.Dto.RequestDto;
import ma.sanai3i.dto.Dto.ReviewRequest;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Request lifecycle: OPEN (artisans send offers) -> ACCEPTED (client picks one offer) -> DONE (client confirms).
 * A request can be CANCELLED while OPEN or ACCEPTED. Money never goes through the app (cash on site).
 */
@Service
@Transactional
public class RequestService {
    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ArtisanProfileRepository artisans;
    private final ReviewRepository reviews;
    private final Mapper mapper;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PricingService pricing;
    private final MediaService media;
    private final BillingService billing;
    private final JobService jobs;

    public RequestService(JobRequestRepository requests, OfferRepository offers, UserRepository users,
                          CategoryRepository categories, ArtisanProfileRepository artisans,
                          ReviewRepository reviews, Mapper mapper, NotificationService notifications,
                          AuditService audit, PricingService pricing, MediaService media, BillingService billing, JobService jobs) {
        this.billing = billing;
        this.pricing = pricing;
        this.media = media;
        this.requests = requests;
        this.offers = offers;
        this.users = users;
        this.categories = categories;
        this.artisans = artisans;
        this.reviews = reviews;
        this.mapper = mapper;
        this.notifications = notifications;
        this.audit = audit;
        this.jobs = jobs;
    }

    // ------------------------------------------------------------------ client side

    @org.springframework.beans.factory.annotation.Value("${app.limits.max-open-requests:5}")
    int maxOpenRequests = 5;
    @org.springframework.beans.factory.annotation.Value("${app.limits.requests-per-day:10}")
    int requestsPerDay = 10;

    public RequestDto create(AuthUser me, CreateRequest in) {
        if (me.role() != Role.CLIENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_ONLY");
        }
        User client = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
        StrikeService.requireActive(client);
        // each request pings every matching artisan: a few at a time, a few a day
        if (requests.countByClientIdAndStatusIn(client.id, List.of(RequestStatus.OPEN, RequestStatus.ACCEPTED)) >= maxOpenRequests) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_OPEN_REQUESTS");
        }
        if (requests.countByClientIdAndCreatedAtAfter(client.id, java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)) >= requestsPerDay) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS_TODAY");
        }
        if ((in.lat() == null) != (in.lng() == null)
                || (in.lat() != null && (Math.abs(in.lat()) > 90 || Math.abs(in.lng()) > 180))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        Category category = categories.findByCode(in.categoryCode())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY"));

        JobRequest r = new JobRequest();
        r.client = client;
        r.category = category;
        r.description = in.description().trim();
        r.city = isBlank(in.city()) ? client.city : in.city().trim();
        r.neighborhood = isBlank(in.neighborhood()) ? null : in.neighborhood().trim();
        if (in.lat() != null && in.lng() != null) {
            r.lat = in.lat();
            r.lng = in.lng();
        }
        r.address = isBlank(in.address()) ? null : in.address().trim();
        r.whenPref = in.whenPref() == null ? WhenPref.FLEXIBLE : in.whenPref();
        PricingService.Range range = pricing.range(category);
        r.priceMin = range.min();
        r.priceMax = range.max();
        r.budget = in.budget();
        pricing.check(r, r.budget);
        r.status = RequestStatus.OPEN;
        requests.save(r);
        media.attachToRequest(client.id, r.id, in.mediaIds());
        audit.logUser(client, "REQUEST_CREATED", "REQUEST", r.id, category.code + " · " + r.budget + " MAD");
        // active artisans of this trade who work around this place hear about it right away
        for (ArtisanProfile a : artisans.findByCategoryId(category.id)) {
            // only artisans who can actually answer: approved, not blocked or suspended, subscription OK
            if (a.status == ArtisanStatus.APPROVED && !a.user.blocked && !a.user.suspended() && billing.canWork(a) && matches(a, r)) {
                notifications.notify(a.user.id, "NEW_REQUEST", r.id, null, null, category.code);
            }
        }
        return ownerView(r, Map.of());
    }

    /** tab = "active" (OPEN, ACCEPTED), "done" (DONE, CANCELLED) or anything else for all of them. */
    @Transactional(readOnly = true)
    public List<RequestDto> mine(AuthUser me, String tab) {
        Map<Long, Mapper.Rating> ratings = mapper.allRatings();
        List<RequestDto> out = new ArrayList<>();
        for (JobRequest r : requests.findByClientIdOrderByCreatedAtDesc(me.userId())) {
            boolean active = r.status == RequestStatus.OPEN || r.status == RequestStatus.ACCEPTED;
            if ("active".equals(tab) && !active) {
                continue;
            }
            if ("done".equals(tab) && active) {
                continue;
            }
            out.add(ownerView(r, ratings));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public RequestDto get(AuthUser me, Long id) {
        JobRequest r = requests.findById(id).orElseThrow(this::notFound);
        if (r.client.id.equals(me.userId())) {
            return ownerView(r, mapper.allRatings());
        }
        if (me.role() == Role.ARTISAN) {
            ArtisanProfile a = artisanOf(me);
            Offer mine = offers.findByRequestIdAndArtisanId(r.id, a.id).orElse(null);
            boolean visible = mine != null
                    || (r.status == RequestStatus.OPEN && a.status == ArtisanStatus.APPROVED
                    && r.category.id.equals(a.category.id) && matches(a, r));
            if (visible) {
                return artisanView(r, a, mine);
            }
        }
        throw notFound();
    }

    /** The client accepts the artisan's current price (not possible while the artisan has to answer a counter-offer). */
    public RequestDto accept(AuthUser me, Long requestId, Long offerId) {
        requests.lockById(requestId);              // concurrent accepts: the second one sees REQUEST_NOT_OPEN
        JobRequest r = ownedRequest(me, requestId);
        Offer chosen = openOffer(r, offerId);
        if ("CLIENT".equals(chosen.lastBy)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_ARTISAN");
        }
        close(r, chosen, false);
        return ownerView(r, mapper.allRatings());
    }

    /** The client proposes another price to one artisan. */
    public RequestDto counter(AuthUser me, Long requestId, Long offerId, CounterOffer in) {
        JobRequest r = ownedRequest(me, requestId);
        Offer o = openOffer(r, offerId);
        if ("CLIENT".equals(o.lastBy)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_ARTISAN");
        }
        propose(o, in.price(), "CLIENT");
        notifications.notify(o.artisan.user.id, "OFFER_COUNTERED", r.id, r.client.fullName, r.client.fullName,
                String.valueOf(o.price));
        audit.logUser(r.client, "OFFER_COUNTERED", "REQUEST", r.id, o.artisan.nameFr + " · " + o.price + " MAD");
        return ownerView(r, mapper.allRatings());
    }

    /** Accepts an offer: the request is ACCEPTED, the other offers are declined, phones are shown to both sides. */
    private void close(JobRequest r, Offer chosen, boolean byArtisan) {
        for (Offer o : offers.findByRequestIdOrderByPriceAsc(r.id)) {
            o.status = (o.id.equals(chosen.id)) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED;
            if (!o.id.equals(chosen.id)) {
                notifications.notify(o.artisan.user.id, "OFFER_REJECTED", r.id, r.client.fullName, r.client.fullName, null);
            } else if (!byArtisan) {
                notifications.notify(o.artisan.user.id, "OFFER_ACCEPTED", r.id, r.client.fullName, r.client.fullName, null);
            }
        }
        r.status = RequestStatus.ACCEPTED;
        r.acceptedOfferId = chosen.id;
        r.doneCode = JobService.newCode();       // only the client sees it; the artisan types it to close the job
        r.codeAttempts = 0;
        if (byArtisan) {
            notifications.notify(r.client.id, "OFFER_AGREED", r.id, chosen.artisan.nameAr, chosen.artisan.nameFr,
                    String.valueOf(chosen.price));
            audit.logUser(chosen.artisan.user, "OFFER_AGREED", "REQUEST", r.id, chosen.price + " MAD");
        } else {
            audit.logUser(r.client, "OFFER_ACCEPTED", "REQUEST", r.id, chosen.artisan.nameFr + " · " + chosen.price + " MAD");
        }
    }

    private Offer openOffer(JobRequest r, Long offerId) {
        if (r.status != RequestStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_OPEN");
        }
        Offer o = offers.findById(offerId)
                .filter(x -> x.request.id.equals(r.id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"));
        if (o.status != OfferStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_CLOSED");
        }
        return o;
    }

    private void propose(Offer o, int price, String by) {
        if (o.rounds >= Mapper.MAX_ROUNDS) {
            throw new ApiException(HttpStatus.CONFLICT, "NEGOTIATION_OVER");
        }
        if (price == o.price) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_PRICE");
        }
        pricing.check(o.request, price);
        o.price = price;
        o.lastBy = by;
        o.rounds = o.rounds + 1;
    }

    public RequestDto cancel(AuthUser me, Long requestId) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.OPEN && r.status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_CLOSED");
        }
        r.status = RequestStatus.CANCELLED;
        for (Offer o : offers.findByRequestIdOrderByPriceAsc(r.id)) {
            if (o.status == OfferStatus.PENDING) {
                o.status = OfferStatus.REJECTED;
            }
            notifications.notify(o.artisan.user.id, "REQUEST_CANCELLED", r.id, r.client.fullName, r.client.fullName, null);
        }
        audit.logUser(r.client, "REQUEST_CANCELLED", "REQUEST", r.id, null);
        return ownerView(r, mapper.allRatings());
    }

    /** The client confirms the job was done (same as the client's button on the job screen). */
    public RequestDto complete(AuthUser me, Long requestId) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        jobs.confirmByClient(me, requestId);
        return ownerView(r, mapper.allRatings());
    }

    public RequestDto review(AuthUser me, Long requestId, ReviewRequest in) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_DONE");
        }
        if (reviews.existsByRequestId(r.id) || r.reviewRemoved) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVIEWED");
        }
        Offer accepted = acceptedOffer(r);
        Review review = new Review();
        review.artisan = accepted.artisan;
        review.client = r.client;
        review.requestId = r.id;
        review.rating = in.rating();
        review.body = isBlank(in.body()) ? null : in.body().trim();
        reviews.save(review);
        audit.logUser(r.client, "REVIEW_ADDED", "REQUEST", r.id, String.valueOf(in.rating()));
        notifications.notify(accepted.artisan.user.id, "REVIEW_RECEIVED", r.id, r.client.fullName, r.client.fullName,
                String.valueOf(in.rating()));
        return ownerView(r, mapper.allRatings());
    }

    // ------------------------------------------------------------------ artisan side

    /** Open requests of the artisan's trade, around him (his radius, or his city if no position), nearest first. */
    @Transactional(readOnly = true)
    public List<RequestDto> openForArtisan(AuthUser me) {
        ArtisanProfile a = approvedArtisan(me);
        List<RequestDto> out = new ArrayList<>();
        if (a.user.suspended() || !billing.canWork(a)) {
            return out;                           // the app shows why (suspension banner / plan page)
        }
        for (JobRequest r : requests.findByStatusAndCategoryIdOrderByCreatedAtDesc(RequestStatus.OPEN, a.category.id)) {
            if (!matches(a, r)) {
                continue;
            }
            Offer mine = offers.findByRequestIdAndArtisanId(r.id, a.id).orElse(null);
            out.add(artisanView(r, a, mine));
        }
        out.sort(java.util.Comparator.comparing(RequestDto::distanceKm, java.util.Comparator.nullsLast(Double::compare)));
        return out;
    }

    /** Requests the artisan answered. The client's phone appears only once his offer is accepted. */
    @Transactional(readOnly = true)
    public List<RequestDto> myJobs(AuthUser me) {
        ArtisanProfile a = artisanOf(me);
        List<RequestDto> out = new ArrayList<>();
        for (Offer o : offers.findByArtisanIdOrderByCreatedAtDesc(a.id)) {
            out.add(artisanView(o.request, a, o));
        }
        return out;
    }

    public OfferDto offer(AuthUser me, Long requestId, CreateOffer in) {
        ArtisanProfile a = approvedArtisan(me);
        StrikeService.requireActive(a.user);
        JobRequest r = requests.findById(requestId).orElseThrow(this::notFound);
        if (r.status != RequestStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_OPEN");
        }
        if (!r.category.id.equals(a.category.id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WRONG_CATEGORY");
        }
        if (offers.findByRequestIdAndArtisanId(r.id, a.id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_EXISTS");
        }
        if (!matches(a, r)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TOO_FAR");
        }
        pricing.check(r, in.price());
        billing.requireActive(a);
        Offer o = new Offer();
        o.request = r;
        o.artisan = a;
        o.price = in.price();
        o.firstPrice = in.price();
        o.lastBy = "ARTISAN";
        o.etaText = isBlank(in.etaText()) ? null : in.etaText().trim();
        o.message = isBlank(in.message()) ? null : in.message().trim();
        o.status = OfferStatus.PENDING;
        offers.save(o);
        audit.logUser(a.user, "OFFER_SENT", "REQUEST", r.id, o.price + " MAD");
        notifications.notify(r.client.id, "OFFER_RECEIVED", r.id, a.nameAr, a.nameFr, String.valueOf(o.price));
        return mapper.offer(o, mapper.ratingOf(a.id), false);
    }

    /** The artisan answers a client's counter-offer with another price. */
    public RequestDto artisanCounter(AuthUser me, Long offerId, CounterOffer in) {
        ArtisanProfile a = approvedArtisan(me);
        Offer o = myOpenOffer(a, offerId);
        if (!"CLIENT".equals(o.lastBy)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_CLIENT");
        }
        propose(o, in.price(), "ARTISAN");
        JobRequest r = o.request;
        notifications.notify(r.client.id, "OFFER_COUNTERED", r.id, a.nameAr, a.nameFr, String.valueOf(o.price));
        audit.logUser(a.user, "OFFER_COUNTERED", "REQUEST", r.id, o.price + " MAD");
        return artisanView(r, a, o);
    }

    /** The artisan agrees to the client's price: the deal is done, like when the client accepts. */
    public RequestDto artisanAgree(AuthUser me, Long offerId) {
        ArtisanProfile a = approvedArtisan(me);
        Offer o = myOpenOffer(a, offerId);
        if (!"CLIENT".equals(o.lastBy)) {
            throw new ApiException(HttpStatus.CONFLICT, "WAITING_CLIENT");
        }
        close(o.request, o, true);
        return artisanView(o.request, a, o);
    }

    private Offer myOpenOffer(ArtisanProfile a, Long offerId) {
        Offer o = offers.findById(offerId)
                .filter(x -> x.artisan.id.equals(a.id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"));
        return openOffer(o.request, o.id);
    }

    // ------------------------------------------------------------------ helpers

    /** Within the artisan's radius when both positions are known, otherwise same city. */
    static boolean matches(ArtisanProfile a, JobRequest r) {
        Double d = distance(a, r);
        if (d != null) {
            return d <= Math.max(1, a.radiusKm);
        }
        return Cities.same(a.city, r.city);
    }

    /**
     * Distance from the artisan to the client's point rounded to 2 decimals (the same ~1 km grid the artisan is
     * shown). Using the exact point let an artisan who moves his own position triangulate the client's home.
     */
    static Double distance(ArtisanProfile a, JobRequest r) {
        if (a.lat == null || a.lng == null || r.lat == null || r.lng == null) {
            return null;
        }
        return Geo.km(a.lat, a.lng, coarse(r.lat), coarse(r.lng));
    }

    static double coarse(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private ArtisanProfile approvedArtisan(AuthUser me) {
        ArtisanProfile a = artisanOf(me);
        if (a.status != ArtisanStatus.APPROVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_NOT_APPROVED");
        }
        return a;
    }

    private RequestDto ownerView(JobRequest r, Map<Long, Mapper.Rating> ratings) {
        List<Offer> all = offers.findByRequestIdOrderByPriceAsc(r.id);
        List<OfferDto> visible = new ArrayList<>();
        for (Offer o : all) {
            if (r.status == RequestStatus.OPEN || o.status == OfferStatus.ACCEPTED) {
                Mapper.Rating rating = ratings.get(o.artisan.id);
                if (rating == null) {
                    rating = mapper.ratingOf(o.artisan.id);
                }
                visible.add(mapper.offer(o, rating, o.status == OfferStatus.ACCEPTED));
            }
        }
        boolean reviewed = r.status == RequestStatus.DONE && (reviews.existsByRequestId(r.id) || r.reviewRemoved);
        return mapper.request(r, all.size(), reviewed, visible, null, null, true, null, r.client.id);
    }

    private RequestDto artisanView(JobRequest r, ArtisanProfile a, Offer mine) {
        OfferDto myOffer = mine == null ? null : mapper.offer(mine, mapper.ratingOf(a.id), false);
        boolean accepted = mine != null && mine.status == OfferStatus.ACCEPTED;
        String clientPhone = accepted ? r.client.phone : null;
        Double d = distance(a, r);
        // whole kilometres, rounded up, from the coarse point: about 1 km of uncertainty, like the map pin
        Double km = d == null ? null : (double) Math.max(1, (long) Math.ceil(d));
        return mapper.request(r, offers.countByRequestId(r.id), false, List.of(), myOffer, clientPhone, accepted, km, a.user.id);
    }

    private Offer acceptedOffer(JobRequest r) {
        if (r.acceptedOfferId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        return offers.findById(r.acceptedOfferId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED"));
    }

    private JobRequest ownedRequest(AuthUser me, Long id) {
        JobRequest r = requests.findById(id).orElseThrow(this::notFound);
        if (!r.client.id.equals(me.userId())) {
            throw notFound();   // do not reveal that someone else's request exists
        }
        return r;
    }

    private ArtisanProfile artisanOf(AuthUser me) {
        if (me.role() != Role.ARTISAN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        return artisans.findByUserId(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY"));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
