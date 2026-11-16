package ma.sanai3i.push;

import com.google.auth.oauth2.GoogleCredentials;
import ma.sanai3i.domain.DeviceToken;
import ma.sanai3i.repo.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Push notifications through Firebase Cloud Messaging (HTTP v1). Off when FCM_CREDENTIALS is empty.
 * Sends in a background thread; tokens that Firebase reports as unknown are removed.
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final DeviceTokenRepository tokens;
    private final TransactionTemplate tx;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final RestClient http = RestClient.create();
    private final GoogleCredentials credentials;
    private final String projectId;
    private final String siteUrl;

    public PushService(DeviceTokenRepository tokens, TransactionTemplate tx,
                       @Value("${app.push.credentials-file:}") String file,
                       @Value("${app.public-url:https://snai3i.elguennouni.site}") String siteUrl) {
        this.tokens = tokens;
        this.tx = tx;
        this.siteUrl = siteUrl;
        GoogleCredentials c = null;
        String pid = null;
        if (file != null && !file.isBlank()) {
            try (InputStream in = new FileInputStream(file)) {
                byte[] json = in.readAllBytes();
                c = GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(json)).createScoped(List.of(SCOPE));
                pid = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("project_id").asText(null);
            } catch (IOException e) {
                log.warn("Push off: cannot read FCM credentials {}: {}", file, e.getMessage());
                c = null;
            }
        }
        this.credentials = c;
        this.projectId = pid;
    }

    public boolean enabled() {
        return credentials != null && projectId != null;
    }

    /** Fire-and-forget push to every device of the user. */
    public void send(Long userId, String kind, String name, String value, String link) {
        if (!enabled()) {
            return;
        }
        pool.submit(() -> {
            try {
                List<DeviceToken> list = tx.execute(s -> tokens.findByUserId(userId));
                if (list == null || list.isEmpty()) {
                    return;
                }
                credentials.refreshIfExpired();
                String access = credentials.getAccessToken().getTokenValue();
                for (DeviceToken d : list) {
                    deliver(access, d, PushTexts.text(kind, d.lang, name, value), link);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Push to user {} failed: {}", userId, e.getMessage());
            }
        });
    }

    private void deliver(String access, DeviceToken d, String body, String link) {
        String title = "Brikol";
        Map<String, Object> data = Map.of("link", link == null ? "/app/" : link, "title", title, "body", body);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", d.token);
        message.put("data", data);
        if ("ANDROID".equals(d.platform)) {
            message.put("android", Map.of("priority", "high"));
        } else {
            message.put("webpush", Map.of(
                    "headers", Map.of("Urgency", "high"),
                    "notification", Map.of("title", title, "body", body, "icon", "/app/icons/icon-192.png"),
                    "fcm_options", Map.of("link", siteUrl + (link == null ? "/app/" : link))));
        }
        try {
            http.post().uri("https://fcm.googleapis.com/v1/projects/{p}/messages:send", projectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 400) {
                tx.executeWithoutResult(s -> tokens.findByToken(d.token).ifPresent(tokens::delete));
            }
        } catch (RestClientException e) {
            log.debug("Push delivery failed: {}", e.getMessage());
        }
    }
}
