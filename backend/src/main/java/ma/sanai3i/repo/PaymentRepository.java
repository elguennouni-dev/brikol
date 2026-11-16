package ma.sanai3i.repo;

import ma.sanai3i.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByArtisanIdOrderByCreatedAtDesc(Long artisanId);

    List<Payment> findTop100ByOrderByCreatedAtDesc();
}
