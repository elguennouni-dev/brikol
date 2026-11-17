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
    // WebP is not accepted: it cannot be re-encoded here (the app converts every photo to JPEG before upload)
    private static final Map<String, String> IMAGE = Map.of("image/jpeg", "jpg", "image/png", "png");
    /** Larger pictures are refused before decoding (decompression bombs). */
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Map<String, String> VIDEO = Map.of("video/mp4", "mp4", "video/quicktime", "mov", "video/webm", "webm",
            "video/3gpp", "3gp");
    private static final Map<String, String> AUDIO = Map.of("audio/webm", "weba", "audio/ogg", "ogg", "audio/mp4", "m4a",
            "audio/x-m4a", "m4a", "audio/aac", "aac", "audio/mpeg", "mp3", "audio/wav", "wav", "audio/x-wav", "wav");
    private static final Set<String> PURPOSES = Set.of(Media.REQUEST, Media.AVATAR, Media.PORTFOLIO, Media.ID_FRONT, Media.ID_BACK,
            Media.JOB_BEFORE, Media.JOB_AFTER, Media.RECEIPT, Media.CASE);
    /** Files anyone involved in a job may send. */
    private static final Set<String> ANY_ROLE = Set.of(Media.RECEIPT, Media.CASE);
    @org.springframework.beans.factory.annotation.Value("${app.limits.uploads-per-day:60}")
    int maxPerDay = 60;
    @org.springframework.beans.factory.annotation.Value("${app.limits.uploads-per-hour:25}")
    int maxPerHour = 25;

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
        if (repo.countByOwnerIdAndCreatedAtAfter(me.userId(), Instant.now().minus(1, ChronoUnit.DAYS)) >= maxPerDay
                || repo.countByOwnerIdAndCreatedAtAfter(me.userId(), Instant.now().minus(1, ChronoUnit.HOURS)) >= maxPerHour) {
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
        // Photos are decoded and written again: drops EXIF (GPS position of the client's home...), and anything
        // glued after or inside the picture. Audio / video must not carry web markup.
        byte[] clean = "IMAGE".equals(kind) ? reencode(file, mime) : null;
        if (clean == null) {
            rejectMarkup(file);
        }

        String id = UUID.randomUUID().toString();
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        String rel = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + id + "." + ext;
        Path target = root.resolve(rel).normalize();
        try {
            Files.createDirectories(target.getParent());
            if (clean != null) {
                Files.write(target, clean);
            } else {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
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
        m.sizeBytes = clean != null ? clean.length : file.getSize();
        m.fileName = rel;
        repo.save(m);
        return dto(m);
    }

    /** Audio / video: the first bytes must be a known container (MP4/MOV/3GP/M4A, WebM, Ogg, MP3, WAV, AAC). */
    private static boolean mediaContainer(byte[] h, int n) {
        if (n < 4) {
            return false;
        }
        boolean ftyp = n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        boolean mov = n >= 8 && ((h[4] == 'm' && h[5] == 'o' && h[6] == 'o' && h[7] == 'v')
                || (h[4] == 'w' && h[5] == 'i' && h[6] == 'd' && h[7] == 'e') || (h[4] == 'm' && h[5] == 'd' && h[6] == 'a' && h[7] == 't'));
        boolean webm = (h[0] & 0xff) == 0x1A && (h[1] & 0xff) == 0x45 && (h[2] & 0xff) == 0xDF && (h[3] & 0xff) == 0xA3;
        boolean ogg = h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S';
        boolean id3 = h[0] == 'I' && h[1] == 'D' && h[2] == '3';
        boolean mpegFrame = (h[0] & 0xff) == 0xFF && ((h[1] & 0xff) & 0xE0) == 0xE0;     // MP3 and ADTS AAC
        boolean wav = n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'A' && h[10] == 'V' && h[11] == 'E';
        return ftyp || mov || webm || ogg || id3 || mpegFrame || wav;
    }

    /** Rejects files whose first bytes do not match an image (and anything that looks like HTML/script). */
    static byte[] reencode(MultipartFile file, String mime) {
        try (InputStream raw = file.getInputStream();
             javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(raw)) {
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(iis);
            if (iis == null || !readers.hasNext()) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
            }
            javax.imageio.ImageReader reader = readers.next();
            java.awt.image.BufferedImage img;
            try {
                reader.setInput(iis, true, true);              // ignore metadata
                long px = (long) reader.getWidth(0) * reader.getHeight(0);
                if (px <= 0 || px > MAX_PIXELS) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_BIG");
                }
                img = reader.read(0);
            } finally {
                reader.dispose();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            if ("image/png".equals(mime)) {
                javax.imageio.ImageIO.write(img, "png", out);
            } else {
                java.awt.image.BufferedImage rgb = img;
                if (img.getType() != java.awt.image.BufferedImage.TYPE_INT_RGB) {
                    rgb = new java.awt.image.BufferedImage(img.getWidth(), img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = rgb.createGraphics();
                    g.setColor(java.awt.Color.WHITE);
                    g.fillRect(0, 0, img.getWidth(), img.getHeight());
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                }
                javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
                javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
                p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(0.88f);
                try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(out)) {
                    w.setOutput(ios);
                    w.write(null, new javax.imageio.IIOImage(rgb, null, null), p);
                } finally {
                    w.dispose();
                }
            }
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
        }
    }

    /** Audio / video carrying HTML, SVG or script text (polyglots for hosting pages under our domain) are refused. */
    private static void rejectMarkup(MultipartFile file) {
        byte[] head = new byte[256 * 1024];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE");
        }
        String text = new String(head, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        for (String marker : new String[]{"<html", "<script", "<!doctype", "<svg", "<?xml", "<body", "<iframe", "javascript:"}) {
            if (text.contains(marker)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
            }
        }
    }

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
            if (!mediaContainer(h, n)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
            }
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
