package ma.sanai3i.web;

import jakarta.servlet.http.HttpServletResponse;
import ma.sanai3i.dto.NotificationDto;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.NotificationHub;
import ma.sanai3i.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationHub hub;

    public NotificationController(NotificationService service, NotificationHub hub) {
        this.service = service;
        this.hub = hub;
    }

    @GetMapping
    public NotificationDto.Page list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthUser me) {
        service.markAllRead(me);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.markRead(me, id);
        return ResponseEntity.noContent().build();
    }

    /** Live channel: the web app opens it once after login and receives every new notification as it happens. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser me, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");               // Nginx: do not buffer this response
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return hub.subscribe(me.userId());
    }
}
