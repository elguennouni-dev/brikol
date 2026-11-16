package ma.sanai3i.web;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Role;
import ma.sanai3i.dto.Dto.MediaDto;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** multipart/form-data: file + purpose (REQUEST, AVATAR, PORTFOLIO, ID_FRONT, ID_BACK). */
    @PostMapping("/api/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto upload(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file,
                           @RequestParam("purpose") String purpose) {
        return media.upload(me, file, purpose);
    }

    /** Public files (request photos, artisan avatar and portfolio). The id is a random UUID. Supports Range (video). */
    @GetMapping("/media/{id}")
    public ResponseEntity<Resource> publicFile(@PathVariable String id) {
        Media m = media.find(id);
        if (m.isPrivate()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return file(m, CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }

    /** Private files (ID card): the owner and administrators only. */
    @GetMapping("/api/media/{id}")
    public ResponseEntity<Resource> privateFile(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        Media m = media.find(id);
        if (!m.ownerId.equals(me.userId()) && me.role() != Role.ADMIN) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return file(m, CacheControl.noStore());
    }

    private ResponseEntity<Resource> file(Media m, CacheControl cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(m.mime))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(media.resource(m));
    }
}
