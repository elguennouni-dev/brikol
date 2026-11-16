package ma.sanai3i.service;

import ma.sanai3i.dto.NotificationDto;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Keeps the open live connections (Server-Sent Events) of each user and pushes notifications to them. */
@Component
public class NotificationHub {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;   // the web app reconnects by itself

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> remove(userId, emitter));
        try {
            // an immediate first event makes proxies flush the response headers
            emitter.send(SseEmitter.event().name("hello").data("ok"));
        } catch (IOException | IllegalStateException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    public void publish(Long userId, NotificationDto dto) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    /** A comment line every 25 s keeps idle connections open through proxies and detects dead ones. */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    /** Number of open live connections (admin dashboard). */
    public long connections() {
        return emitters.values().stream().mapToLong(List::size).sum();
    }

    /** Closes every live connection of a user (used when he is blocked). */
    public void disconnect(Long userId) {
        List<SseEmitter> list = emitters.remove(userId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (RuntimeException e) {
                    // already closed
                }
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
