package ma.sanai3i.repo;

import ma.sanai3i.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    java.util.Optional<OtpCode> findFirstByPhoneAndPurposeAndUsedFalseOrderByIdDesc(String phone, String purpose);

    long countByPhoneAndCreatedAtAfter(String phone, java.time.Instant since);

    java.util.Optional<OtpCode> findFirstByPhoneOrderByIdDesc(String phone);
}
