package ma.sanai3i.dto;

import java.time.Instant;
import java.util.List;

public record NotificationDto(Long id, String kind, Long requestId, String actorAr, String actorFr, String detail,
                              boolean read, Instant createdAt) {

    public record Page(List<NotificationDto> items, long unread) {
    }
}
