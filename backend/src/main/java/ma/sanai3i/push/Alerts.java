package ma.sanai3i.push;

import ma.sanai3i.domain.User;
import ma.sanai3i.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Important events also go out by WhatsApp (or SMS), because many artisans never open notifications in an app but
 * all of them read WhatsApp. app.alerts.channel: none (default), whatsapp, sms, or log (tests).
 *
 * WhatsApp through Twilio: messages started by a business must use an approved template. Create one "utility"
 * template with a single variable, e.g. "Brikol: {{1}}", and put its Content SID (HX...) in
 * TWILIO_WHATSAPP_TEMPLATE. Without it the text is sent as a plain message, which WhatsApp only delivers when the
 * person wrote to the number in the last 24 hours (fine for the Twilio sandbox).
 */
@Component
public class Alerts {
    private static final Logger log = LoggerFactory.getLogger(Alerts.class);

    /** Events worth a WhatsApp message: someone has to act or come. */
    public static final Set<String> IMPORTANT = Set.of("OFFER_ACCEPTED", "OFFER_AGREED", "APPOINTMENT_REMINDER", "ARTISAN_ARRIVED",
            "JOB_FINISHED", "EXTRA_PROPOSED", "ADVANCE_DECLARED", "ARTISAN_WITHDREW", "WARRANTY_OPENED", "WARRANTY_NOT_FIXED",
            "ACCOUNT_SUSPENDED", "CASE_RESOLVED");

    private final UserRepository users;
    private final TransactionTemplate tx;
    private final String channel;
    private final String sid;
    private final String token;
    private final String smsFrom;
    private final String waFrom;
    private final String waTemplate;
    private final String siteUrl;
    private final RestClient http = RestClient.create();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    public Alerts(UserRepository users, TransactionTemplate tx,
                  @Value("${app.alerts.channel:none}") String channel,
                  @Value("${app.sms.twilio.account-sid:}") String sid,
                  @Value("${app.sms.twilio.auth-token:}") String token,
                  @Value("${app.sms.twilio.from:}") String smsFrom,
                  @Value("${app.alerts.whatsapp-from:}") String waFrom,
                  @Value("${app.alerts.whatsapp-template:}") String waTemplate,
                  @Value("${app.public-url:https://snai3i.elguennouni.site}") String siteUrl) {
        this.users = users;
        this.tx = tx;
        this.channel = channel == null ? "none" : channel.trim().toLowerCase();
        this.sid = sid;
        this.token = token;
        this.smsFrom = smsFrom;
        this.waFrom = waFrom;
        this.waTemplate = waTemplate;
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        if (!"none".equals(this.channel) && !"log".equals(this.channel) && (sid.isBlank() || token.isBlank())) {
            log.warn("ALERTS_CHANNEL={} but TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN are missing: alerts off", this.channel);
        }
    }

    public boolean enabled() {
        return switch (channel) {
            case "log" -> true;
            case "whatsapp" -> !sid.isBlank() && !token.isBlank() && !waFrom.isBlank();
            case "sms" -> !sid.isBlank() && !token.isBlank() && !smsFrom.isBlank();
            default -> false;
        };
    }

    /** Fire and forget; never breaks the action that caused it. */
    public void send(Long userId, String kind, String name, String value, Long requestId) {
        if (!IMPORTANT.contains(kind) || !enabled()) {
            return;
        }
        pool.submit(() -> {
            try {
                User u = tx.execute(s -> users.findById(userId).orElse(null));
                if (u == null || u.phone == null || u.blocked) {
                    return;
                }
                String link = siteUrl + (requestId == null ? "/app/" : "/app/#/job/" + requestId);
                String text = "بريكول: " + PushTexts.text(kind, "ar", name, value) + "\n" + link;
                deliver(u.phone, text);
            } catch (RuntimeException e) {
                log.warn("Alert {} to user {} failed: {}", kind, userId, e.getMessage());
            }
        });
    }

    private void deliver(String phone, String text) {
        if ("log".equals(channel)) {
            log.info("ALERT to {}: {}", phone, text);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if ("whatsapp".equals(channel)) {
            form.add("To", "whatsapp:" + phone);
            form.add("From", waFrom.startsWith("whatsapp:") ? waFrom : "whatsapp:" + waFrom);
            if (waTemplate.isBlank()) {
                form.add("Body", text);
            } else {
                form.add("ContentSid", waTemplate);
                form.add("ContentVariables", "{\"1\":" + json(text) + "}");
            }
        } else {
            form.add("To", phone);
            form.add(smsFrom.startsWith("MG") ? "MessagingServiceSid" : "From", smsFrom);
            form.add("Body", text);
        }
        String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        try {
            http.post().uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", sid)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Twilio {} to {} failed: {}", channel, phone, e.getMessage());
        }
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> b.append(c < 0x20 ? ' ' : c);
            }
        }
        return b.append('"').toString();
    }
}
