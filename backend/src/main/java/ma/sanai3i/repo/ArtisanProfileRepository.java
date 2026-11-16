package ma.sanai3i.repo;

import ma.sanai3i.domain.ArtisanProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtisanProfileRepository extends JpaRepository<ArtisanProfile, Long> {
    Optional<ArtisanProfile> findByUserId(Long userId);

    List<ArtisanProfile> findAllByOrderByVerifiedDescIdAsc();

    List<ArtisanProfile> findByCategory_CodeOrderByVerifiedDescIdAsc(String code);

    List<ArtisanProfile> findByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    long countByVerifiedTrue();
}
