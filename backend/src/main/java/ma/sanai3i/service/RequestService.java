package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.common.Cities;
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

    public RequestService(JobRequestRepository requests, OfferRepository offers, UserRepository users,
                          CategoryRepository categories, ArtisanProfileRepository artisans,
                          ReviewRepository reviews, Mapper mapper, NotificationService notifications,
                          AuditService audit) {
        this.requests = requests;
        this.offers = offers;
        this.users = users;
        this.categories = categories;
        this.artisans = artisans;
        this.reviews = reviews;
        this.mapper = mapper;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ client side

    public RequestDto create(AuthUser me, CreateRequest in) {
        if (me.role() != Role.CLIENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CLIENT_ONLY");
        }
        User client = users.findById(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNKNOWN_USER"));
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
        r.budget = in.budget();
        r.status = RequestStatus.OPEN;
        requests.save(r);
        audit.logUser(client, "REQUEST_CREATED", "REQUEST", r.id, category.code + " · " + r.budget + " MAD");
        // artisans of this trade and city hear about it right away
        for (ArtisanProfile a : artisans.findByCategoryId(category.id)) {
            if (Cities.same(a.city, r.city)) {
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
                    || (r.status == RequestStatus.OPEN && r.category.id.equals(a.category.id));
            if (visible) {
                return artisanView(r, a, mine);
            }
        }
        throw notFound();
    }

    public RequestDto accept(AuthUser me, Long requestId, Long offerId) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_OPEN");
        }
        List<Offer> all = offers.findByRequestIdOrderByPriceAsc(r.id);
        Offer chosen = all.stream()
                .filter(o -> o.id.equals(offerId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND"));
        for (Offer o : all) {
            o.status = (o == chosen) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED;
            notifications.notify(o.artisan.user.id, o == chosen ? "OFFER_ACCEPTED" : "OFFER_REJECTED", r.id,
                    r.client.fullName, r.client.fullName, null);
        }
        r.status = RequestStatus.ACCEPTED;
        r.acceptedOfferId = chosen.id;
        audit.logUser(r.client, "OFFER_ACCEPTED", "REQUEST", r.id, chosen.artisan.nameFr + " · " + chosen.price + " MAD");
        return ownerView(r, mapper.allRatings());
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

    /** The client confirms the job was done. The artisan's "jobs done" counter goes up. */
    public RequestDto complete(AuthUser me, Long requestId) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.ACCEPTED) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_ACCEPTED");
        }
        Offer accepted = acceptedOffer(r);
        accepted.artisan.jobsDone = accepted.artisan.jobsDone + 1;
        r.status = RequestStatus.DONE;
        audit.logUser(r.client, "JOB_DONE", "REQUEST", r.id, null);
        notifications.notify(accepted.artisan.user.id, "JOB_DONE", r.id, r.client.fullName, r.client.fullName, null);
        return ownerView(r, mapper.allRatings());
    }

    public RequestDto review(AuthUser me, Long requestId, ReviewRequest in) {
        JobRequest r = ownedRequest(me, requestId);
        if (r.status != RequestStatus.DONE) {
            throw new ApiException(HttpStatus.CONFLICT, "REQUEST_NOT_DONE");
        }
        if (reviews.existsByRequestId(r.id)) {
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

    /** Open requests in the artisan's trade and city, with his own offer if he already answered. */
    @Transactional(readOnly = true)
    public List<RequestDto> openForArtisan(AuthUser me) {
        ArtisanProfile a = artisanOf(me);
        List<RequestDto> out = new ArrayList<>();
        for (JobRequest r : requests.findByStatusAndCategoryIdOrderByCreatedAtDesc(RequestStatus.OPEN, a.category.id)) {
            if (!Cities.same(r.city, a.city)) {
                continue;
            }
            Offer mine = offers.findByRequestIdAndArtisanId(r.id, a.id).orElse(null);
            out.add(artisanView(r, a, mine));
        }
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
        ArtisanProfile a = artisanOf(me);
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
        Offer o = new Offer();
        o.request = r;
        o.artisan = a;
        o.price = in.price();
        o.etaText = isBlank(in.etaText()) ? null : in.etaText().trim();
        o.message = isBlank(in.message()) ? null : in.message().trim();
        o.status = OfferStatus.PENDING;
        offers.save(o);
        audit.logUser(a.user, "OFFER_SENT", "REQUEST", r.id, o.price + " MAD");
        notifications.notify(r.client.id, "OFFER_RECEIVED", r.id, a.nameAr, a.nameFr, String.valueOf(o.price));
        return mapper.offer(o, mapper.ratingOf(a.id), false);
    }

    // ------------------------------------------------------------------ helpers

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
        boolean reviewed = r.status == RequestStatus.DONE && reviews.existsByRequestId(r.id);
        return mapper.request(r, all.size(), reviewed, visible, null, null);
    }

    private RequestDto artisanView(JobRequest r, ArtisanProfile a, Offer mine) {
        OfferDto myOffer = mine == null ? null : mapper.offer(mine, mapper.ratingOf(a.id), false);
        boolean accepted = mine != null && mine.status == OfferStatus.ACCEPTED;
        String clientPhone = accepted ? r.client.phone : null;
        return mapper.request(r, offers.countByRequestId(r.id), false, List.of(), myOffer, clientPhone, accepted);
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
