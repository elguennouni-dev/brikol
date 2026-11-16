package ma.sanai3i.service;

import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.Review;
import ma.sanai3i.domain.ServiceItem;
import ma.sanai3i.domain.User;
import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.ArtisanSummary;
import ma.sanai3i.dto.Dto.OfferDto;
import ma.sanai3i.dto.Dto.RequestDto;
import ma.sanai3i.dto.Dto.ReviewDto;
import ma.sanai3i.dto.Dto.ServiceDto;
import ma.sanai3i.dto.Dto.UserDto;
import ma.sanai3i.domain.Media;
import ma.sanai3i.repo.ClientReviewRepository;
import ma.sanai3i.repo.MediaRepository;
import ma.sanai3i.repo.MessageRepository;
import ma.sanai3i.repo.ReviewRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Entity to DTO conversion. Always called inside a transaction (lazy collections are read here). */
@Component
public class Mapper {
    public record Rating(double average, long count) {
    }

    public static final Rating NO_RATING = new Rating(0.0, 0L);

    /** Counter-offers allowed on one offer (client and artisan together). */
    public static final int MAX_ROUNDS = 6;

    private final ReviewRepository reviews;
    private final MediaRepository media;
    private final ClientReviewRepository clientReviews;
    private final MessageRepository messages;

    public Mapper(ReviewRepository reviews, MediaRepository media, ClientReviewRepository clientReviews,
                  MessageRepository messages) {
        this.reviews = reviews;
        this.media = media;
        this.clientReviews = clientReviews;
        this.messages = messages;
    }

    public Map<Long, Rating> allRatings() {
        Map<Long, Rating> map = new HashMap<>();
        for (ReviewRepository.RatingRow row : reviews.ratingSummaries()) {
            map.put(row.getArtisanId(), new Rating(round1(row.getAverage()), row.getTotal()));
        }
        return map;
    }

    public Rating ratingOf(Long artisanId) {
        long count = reviews.countByArtisanId(artisanId);
        if (count == 0) {
            return NO_RATING;
        }
        return new Rating(round1(reviews.averageRating(artisanId)), count);
    }

    private static double round1(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    /** "Fatima Zahra Bennani" becomes "Fatima B." so clients and artisans see a short name only. */
    public static String shortName(String full) {
        if (full == null || full.isBlank()) {
            return "";
        }
        String[] parts = full.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].substring(0, 1) + ".";
    }

    public UserDto user(User u, ArtisanProfile profile) {
        return new UserDto(u.id, u.phone, u.fullName, u.role, u.city, profile == null ? null : profile.id,
                profile == null ? null : profile.status.name());
    }

    public ArtisanSummary summary(ArtisanProfile a, Rating r) {
        ServiceItem cheapest = a.services.stream()
                .min(Comparator.comparingInt((ServiceItem s) -> s.price))
                .orElse(null);
        return new ArtisanSummary(
                a.id, a.nameAr, a.nameFr, a.tradeAr, a.tradeFr,
                a.neighborhoodAr, a.neighborhoodFr, a.city, a.category.code,
                r.average(), r.count(), a.yearsExperience, a.jobsDone,
                a.verified, a.availableToday,
                cheapest == null ? null : Integer.valueOf(cheapest.price),
                cheapest == null ? "" : cheapest.unitAr,
                cheapest == null ? "" : cheapest.unitFr,
                a.avatarId == null ? null : "/media/" + a.avatarId,
                a.featuredUntil != null && a.featuredUntil.isAfter(java.time.Instant.now()));
    }

    public ArtisanDetail detail(ArtisanProfile a, Rating r, List<Review> reviewList) {
        List<ServiceDto> services = a.services.stream()
                .map(s -> new ServiceDto(s.id, s.labelAr, s.labelFr, s.price, s.unitAr, s.unitFr))
                .toList();
        List<ReviewDto> reviewDtos = reviewList.stream()
                .limit(20)
                .map(x -> new ReviewDto(x.id, shortName(x.client.fullName), x.rating, x.body, x.createdAt))
                .toList();
        return new ArtisanDetail(summary(a, r), a.bioAr, a.bioFr, a.user.phone, a.whatsapp, services, reviewDtos,
                media.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO).stream().map(MediaService::dto).toList());
    }

    public OfferDto offer(Offer o, Rating rating, boolean withPhone) {
        ArtisanProfile a = o.artisan;
        return new OfferDto(
                o.id, o.request.id, a.id, a.nameAr, a.nameFr,
                rating.average(), rating.count(),
                o.price, o.etaText, o.message, o.status, o.createdAt,
                withPhone ? a.user.phone : null,
                o.lastBy, o.rounds, MAX_ROUNDS, o.firstPrice);
    }

    public RequestDto request(JobRequest r, long offerCount, boolean reviewed,
                              List<OfferDto> offers, OfferDto myOffer, String clientPhone) {
        return request(r, offerCount, reviewed, offers, myOffer, clientPhone, true);
    }

    /**
     * exactPlace = false (artisans before their offer is accepted): the point is rounded to about 1 km
     * and the street address is hidden, so a client's home is not shown to everyone.
     */
    public RequestDto request(JobRequest r, long offerCount, boolean reviewed,
                              List<OfferDto> offers, OfferDto myOffer, String clientPhone, boolean exactPlace) {
        return request(r, offerCount, reviewed, offers, myOffer, clientPhone, exactPlace, null, null);
    }

    /** viewerId: the user looking at it (unread chat messages); may be null. */
    public RequestDto request(JobRequest r, long offerCount, boolean reviewed, List<OfferDto> offers, OfferDto myOffer,
                              String clientPhone, boolean exactPlace, Double distanceKm, Long viewerId) {
        Double lat = r.lat;
        Double lng = r.lng;
        String address = r.address;
        if (!exactPlace) {
            lat = lat == null ? null : Math.round(lat * 100) / 100.0;
            lng = lng == null ? null : Math.round(lng * 100) / 100.0;
            address = null;
        }
        return new RequestDto(
                r.id, r.category.code, r.category.nameAr, r.category.nameFr,
                r.description, r.city, r.neighborhood,
                r.whenPref, r.budget, r.status, r.createdAt,
                shortName(r.client.fullName), clientPhone,
                offerCount, r.acceptedOfferId, reviewed,
                offers, myOffer, lat, lng, address,
                media.findByRequestIdOrderByCreatedAtAsc(r.id).stream().map(MediaService::dto).toList(),
                r.priceMin != null ? r.priceMin : r.category.minPrice,
                r.priceMax != null ? r.priceMax : r.category.maxPrice,
                distanceKm,
                round1(clientReviews.averageRating(r.client.id)), clientReviews.countByClientId(r.client.id),
                clientReviews.existsByRequestId(r.id),
                viewerId == null ? 0 : messages.countByRequestIdAndSenderIdNotAndReadAtIsNull(r.id, viewerId),
                r.appointmentAt, r.appointmentBy, r.appointmentConfirmed);
    }
}
