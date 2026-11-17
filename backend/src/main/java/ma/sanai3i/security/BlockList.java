package ma.sanai3i.security;

import ma.sanai3i.domain.User;
import ma.sanai3i.repo.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ids of blocked users, kept in memory so every request can be refused without a database call. */
@Component
public class BlockList {
    private final Set<Long> ids = ConcurrentHashMap.newKeySet();
    private final UserRepository users;

    public BlockList(UserRepository users) {
        this.users = users;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        for (User u : users.findByBlockedTrue()) {
            ids.add(u.id);
        }
    }

    private final java.util.Map<Long, Integer> versions = new ConcurrentHashMap<>();

    /** False when the account no longer exists or the token was issued before a "sign out everywhere". */
    public boolean tokenValid(Long userId, int tokenVersion) {
        Integer v = versions.get(userId);
        if (v == null) {
            User u = users.findById(userId).orElse(null);
            if (u == null) {
                return false;
            }
            v = u.tokenVersion;
            versions.put(userId, v);
        }
        return v == tokenVersion;
    }

    /** Signs the user out of every device (the caller saves the entity). */
    public void revokeTokens(User u) {
        u.tokenVersion = u.tokenVersion + 1;
        Long id = u.id;
        versions.remove(id);
        ma.sanai3i.service.NotificationService.afterCommit(() -> versions.remove(id));
    }

    public void forget(Long userId) {
        versions.remove(userId);
    }

    public boolean isBlocked(Long userId) {
        return ids.contains(userId);
    }

    public void block(Long userId) {
        ids.add(userId);
    }

    public void unblock(Long userId) {
        ids.remove(userId);
    }
}
