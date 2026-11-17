package ma.sanai3i.security;

import ma.sanai3i.domain.RevokedToken;
import ma.sanai3i.repo.RevokedTokenRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tokens signed out one by one (logout). Kept in memory for the filter, and in the database across restarts. */
@Component
public class TokenRevocations {
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final RevokedTokenRepository repo;

    public TokenRevocations(RevokedTokenRepository repo) {
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        for (RevokedToken t : repo.findByExpiresAtAfter(Instant.now())) {
            revoked.put(t.jti, t.expiresAt);
        }
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.containsKey(jti);
    }

    @Transactional
    public void revoke(AuthUser me) {
        if (me.tokenId() == null) {
            return;
        }
        Instant exp = me.expiresAt() == null ? Instant.now().plus(31, ChronoUnit.DAYS) : me.expiresAt();
        RevokedToken t = new RevokedToken();
        t.jti = me.tokenId();
        t.expiresAt = exp;
        repo.save(t);
        revoked.put(t.jti, exp);
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    @Transactional
    public void purge() {
        Instant now = Instant.now();
        revoked.values().removeIf(exp -> exp.isBefore(now));
        repo.deleteExpired(now);
    }
}
