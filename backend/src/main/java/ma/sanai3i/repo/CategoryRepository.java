package ma.sanai3i.repo;

import ma.sanai3i.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCode(String code);

    List<Category> findAllByOrderBySortOrderAsc();
}
