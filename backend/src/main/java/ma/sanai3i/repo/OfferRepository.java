package ma.sanai3i.repo;

import ma.sanai3i.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    boolean existsByArtisanIdAndRequestClientId(Long artisanId, Long clientId);

    boolean existsByArtisanUserIdAndRequestClientId(Long artisanUserId, Long clientId);

    boolean existsByRequestIdAndArtisanUserId(Long requestId, Long artisanUserId);

    boolean existsByArtisanIdAndRequestClientIdAndStatus(Long artisanId, Long clientId, ma.sanai3i.domain.OfferStatus status);

    List<Offer> findByRequestIdOrderByPriceAsc(Long requestId);

    List<Offer> findByArtisanIdOrderByCreatedAtDesc(Long artisanId);

    Optional<Offer> findByRequestIdAndArtisanId(Long requestId, Long artisanId);

    long countByRequestId(Long requestId);

    long countByArtisanId(Long artisanId);

    @org.springframework.data.jpa.repository.Query("select o.price from Offer o where o.status = ma.sanai3i.domain.OfferStatus.ACCEPTED "
            + "and o.request.category.id = :categoryId and o.createdAt > :since")
    List<Integer> acceptedPricesSince(@org.springframework.data.repository.query.Param("categoryId") Long categoryId,
                                      @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
