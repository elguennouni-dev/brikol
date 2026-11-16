package ma.sanai3i.repo;

import ma.sanai3i.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface RatingRow {
        Long getArtisanId();

        Double getAverage();

        Long getTotal();
    }

    List<Review> findByArtisanIdOrderByCreatedAtDesc(Long artisanId);

    boolean existsByRequestId(Long requestId);

    long countByArtisanId(Long artisanId);

    @Query("select avg(r.rating) from Review r where r.artisan.id = :artisanId")
    Double averageRating(@Param("artisanId") Long artisanId);

    @Query("select r.artisan.id as artisanId, avg(r.rating) as average, count(r) as total from Review r group by r.artisan.id")
    List<RatingRow> ratingSummaries();
}
