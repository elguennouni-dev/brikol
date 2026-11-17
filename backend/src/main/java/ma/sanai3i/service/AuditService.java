package ma.sanai3i.service;

import jakarta.servlet.http.HttpServletRequest;
import ma.sanai3i.domain.AuditLog;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes the system trail. Runs in its own transaction, so a failed login or a rolled-back action is still recorded. */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long actorId, String actorName, String action, String targetType, Long targetId, String detail) {
        try {
            AuditLog a = new AuditLog();
            a.actorId = actorId;
            a.actorName = cut(actorName, 160);
            a.action = action;
            a.targetType = targetType;
            a.targetId = targetId;
            a.detail = cut(detail, 500);
            a.ip = cut(clientIp(), 64);
            repo.save(a);
        } catch (RuntimeException e) {
            // the trail must never break the action it describes
        }
    }

    // Its own annotation: calling log() from here does not go through the Spring proxy.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUser(User actor, String action, String targetType, Long targetId, String detail) {
        log(actor == null ? null : actor.id, actor == null ? null : actor.fullName, action, targetType, targetId, detail);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();        // already resolved from trusted proxies by Tomcat
        }
        return null;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
