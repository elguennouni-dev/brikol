package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Notification;
import ma.sanai3i.dto.NotificationDto;
import ma.sanai3i.repo.NotificationRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository repo;
    private final NotificationHub hub;
    private final ma.sanai3i.push.PushService push;

    public NotificationService(NotificationRepository repo, NotificationHub hub, ma.sanai3i.push.PushService push) {
        this.repo = repo;
        this.hub = hub;
        this.push = push;
    }

    /** Stores the notification and, once the surrounding transaction is committed, pushes it live. */
    public void notify(Long userId, String kind, Long requestId, String actorAr, String actorFr, String detail) {
        Notification n = new Notification();
        n.userId = userId;
        n.kind = kind;
        n.requestId = requestId;
        n.actorAr = actorAr;
        n.actorFr = actorFr;
        n.detail = detail;
        repo.save(n);
        NotificationDto dto = toDto(n);
        String name = actorFr != null ? actorFr : actorAr;
        afterCommit(() -> {
            hub.publish(userId, dto);
            push.send(userId, kind, name, detail, "/app/#/notifications");
        });
    }

    /** Runs the action once the current transaction is committed (or now when there is none). */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Transactional(readOnly = true)
    public NotificationDto.Page list(AuthUser me) {
        List<NotificationDto> items = repo.findTop50ByUserIdOrderByIdDesc(me.userId()).stream()
                .map(NotificationService::toDto).toList();
        return new NotificationDto.Page(items, repo.countByUserIdAndSeenFalse(me.userId()));
    }

    public void markAllRead(AuthUser me) {
        repo.markAllRead(me.userId());
    }

    public void markRead(AuthUser me, Long id) {
        Notification n = repo.findByIdAndUserId(id, me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND"));
        n.seen = true;
    }

    static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.id, n.kind, n.requestId, n.actorAr, n.actorFr, n.detail, n.seen, n.createdAt);
    }
}
