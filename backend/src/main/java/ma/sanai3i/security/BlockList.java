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
