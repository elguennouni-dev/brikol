package ma.sanai3i.repo;

import ma.sanai3i.domain.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    List<Offer> findByRequestIdOrderByPriceAsc(Long requestId);

    List<Offer> findByArtisanIdOrderByCreatedAtDesc(Long artisanId);

    Optional<Offer> findByRequestIdAndArtisanId(Long requestId, Long artisanId);

    long countByRequestId(Long requestId);

    long countByArtisanId(Long artisanId);
}
