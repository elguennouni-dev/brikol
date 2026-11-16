package ma.sanai3i.repo;

import ma.sanai3i.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRequestIdOrderByIdAsc(Long requestId);

    long countByRequestIdAndSenderIdNotAndReadAtIsNull(Long requestId, Long senderId);

    List<Message> findByRequestIdAndSenderIdNotAndReadAtIsNull(Long requestId, Long senderId);
}
