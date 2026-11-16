package ma.sanai3i.repo;

import ma.sanai3i.domain.ClientReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientReviewRepository extends JpaRepository<ClientReview, Long> {
    boolean existsByRequestId(Long requestId);

    List<ClientReview> findByClientIdOrderByCreatedAtDesc(Long clientId);

    long countByClientId(Long clientId);

    @org.springframework.data.jpa.repository.Query("select avg(r.rating) from ClientReview r where r.clientId = :clientId")
    Double averageRating(@org.springframework.data.repository.query.Param("clientId") Long clientId);
}
