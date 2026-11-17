package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Role;
import ma.sanai3i.dto.Dto.MediaDto;
import ma.sanai3i.repo.MediaRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stores uploaded photos, videos and voice notes on disk (app.uploads-dir), metadata in the media table. */
@Service
@Transactional
public class MediaService {
    private static final long MB = 1024L * 1024L;
    private static final Map<String, String> IMAGE = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
    private static final Map<String, String> VIDEO = Map.of("video/mp4", "mp4", "video/quicktime", "mov", "video/webm", "webm",
            "video/3gpp", "3gp");
    private static final Map<String, String> AUDIO = Map.of("audio/webm", "weba", "audio/ogg", "ogg", "audio/mp4", "m4a",
            "audio/x-m4a", "m4a", "audio/aac", "aac", "audio/mpeg", "mp3", "audio/wav", "wav", "audio/x-wav", "wav");
    private static final Set<String> PURPOSES = Set.of(Media.REQUEST, Media.AVATAR, Media.PORTFOLIO, Media.ID_FRONT, Media.ID_BACK,
            Media.JOB_BEFORE, Media.JOB_AFTER, Media.RECEIPT, Media.CASE);
    /** Files anyone involved in a job may send. */
    private static final Set<String> ANY_ROLE = Set.of(Media.RECEIPT, Media.CASE);
    private static final int MAX_PER_DAY = 80;

    private final MediaRepository repo;
    private final Path root;

    public MediaService(MediaRepository repo, @Value("${app.uploads-dir:./uploads}") String dir) {
        this.repo = repo;
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public MediaDto upload(AuthUser me, MultipartFile file, String purpose) {
        String p = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!PURPOSES.contains(p)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        if (!ANY_ROLE.contains(p) && (Media.REQUEST.equals(p) ? me.role() != Role.CLIENT : me.role() != Role.ARTISAN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY");
        }
        if (repo.countByOwnerIdAndCreatedAtAfter(me.userId(), Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_PER_DAY) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_FILES");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        int semi = mime.indexOf(';');           // "audio/webm;codecs=opus"
        if (semi > 0) {
            mime = mime.substring(0, semi).trim();
        }
        String kind;
        String ext;
        long limit;
        if (IMAGE.containsKey(mime)) {
            kind = "IMAGE";
            ext = IMAGE.get(mime);
            limit = 10 * MB;
        } else if (VIDEO.containsKey(mime) && Media.REQUEST.equals(p)) {
            kind = "VIDEO";
            ext = VIDEO.get(mime);
            limit = 60 * MB;
        } else if (AUDIO.containsKey(mime) && Media.REQUEST.equals(p)) {
            kind = "AUDIO";
            ext = AUDIO.get(mime);
            limit = 15 * MB;
        } else {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
        }
        if (file.getSize() > limit) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_BIG");
        }
        checkSignature(file, kind, mime);

        String id = UUID.randomUUID().toString();
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        String rel = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + id + "." + ext;
        Path target = root.resolve(rel).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED");
        }
        Media m = new Media();
        m.id = id;
        m.ownerId = me.userId();
        m.purpose = p;
        m.kind = kind;
        m.mime = mime;
        m.sizeBytes = file.getSize();
        m.fileName = rel;
        repo.save(m);
        return dto(m);
    }

    /** Rejects files whose first bytes do not match an image (and anything that looks like HTML/script). */
    private static void checkSignature(MultipartFile file, String kind, String mime) {
        byte[] h = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(h, 0, h.length);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE");
        }
        if (n > 0 && (h[0] == '<' || h[0] == '{')) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
        }
        if (!"IMAGE".equals(kind)) {
            return;
        }
        boolean ok = switch (mime) {
            case "image/jpeg" -> n >= 3 && (h[0] & 0xff) == 0xFF && (h[1] & 0xff) == 0xD8 && (h[2] & 0xff) == 0xFF;
            case "image/png" -> n >= 4 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            case "image/webp" -> n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            default -> false;
        };
        if (!ok) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
        }
    }

    public static MediaDto dto(Media m) {
        String url = m.isPrivate() ? "/api/media/" + m.id : "/media/" + m.id;
        return new MediaDto(m.id, m.kind, m.mime, url);
    }

    @Transactional(readOnly = true)
    public List<MediaDto> ofRequest(Long requestId) {
        return ofRequest(requestId, Media.REQUEST);
    }

    @Transactional(readOnly = true)
    public List<MediaDto> ofRequest(Long requestId, String purpose) {
        return repo.findByRequestIdOrderByCreatedAtAsc(requestId).stream().filter(m -> purpose.equals(m.purpose))
                .map(MediaService::dto).toList();
    }

    /** Links one of the uploader's unused files of that purpose to a request (job evidence). */
    public Media attach(Long ownerId, Long requestId, String id, String purpose) {
        Media m = owned(ownerId, id, purpose);
        if (m.requestId != null && !m.requestId.equals(requestId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID");
        }
        m.requestId = requestId;
        return m;
    }

    /** Links uploaded files to a new request. Only the uploader's own, unused REQUEST files are accepted. */
    public void attachToRequest(Long ownerId, Long requestId, Collection<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            Media m = owned(ownerId, id, Media.REQUEST);
            if (m.requestId != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID");
            }
            m.requestId = requestId;
        }
    }

    /** Returns the owner's file of that purpose (used by the artisan file). */
    public Media owned(Long ownerId, String id, String purpose) {
        Media m = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID"));
        if (!m.ownerId.equals(ownerId) || !purpose.equals(m.purpose)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID");
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Media find(String id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    public Resource resource(Media m) {
        Path f = root.resolve(m.fileName).normalize();
        if (!f.startsWith(root) || !Files.isReadable(f)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        return new FileSystemResource(f);
    }

    public List<MediaDto> dtos(List<Media> list) {
        List<MediaDto> out = new ArrayList<>();
        list.forEach(m -> out.add(dto(m)));
        return out;
    }
}
