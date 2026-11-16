package ma.sanai3i.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window counters (one server). key = action + IP or phone. */
@Component
public class RateLimiter {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final boolean enabled;

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Records one hit and returns false when the limit for the window is already reached. */
    public boolean allow(String key, int max, long windowMs) {
        if (!enabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < now - windowMs) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** Counts without recording (e.g. failed logins recorded separately). */
    public int count(String key, long windowMs) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        synchronized (q) {
            return (int) q.stream().filter(t -> t >= now - windowMs).count();
        }
    }

    public void record(String key) {
        if (enabled) {
            Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (q) {
                q.addLast(System.currentTimeMillis());
            }
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    @Scheduled(fixedRate = 10 * 60 * 1000L)
    public void cleanup() {
        long old = System.currentTimeMillis() - 2 * 60 * 60 * 1000L;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < old;
            }
        });
    }
}
