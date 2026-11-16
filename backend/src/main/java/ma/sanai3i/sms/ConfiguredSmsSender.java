package ma.sanai3i.sms;

import ma.sanai3i.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** app.sms.provider: "twilio" (real SMS), "log" (written to the server log, for tests) or "none" (off). */
@Component
public class ConfiguredSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredSmsSender.class);

    private final String provider;
    private final String sid;
    private final String token;
    private final String from;
    private final RestClient http = RestClient.create();

    public ConfiguredSmsSender(@Value("${app.sms.provider:none}") String provider,
                               @Value("${app.sms.twilio.account-sid:}") String sid,
                               @Value("${app.sms.twilio.auth-token:}") String token,
                               @Value("${app.sms.twilio.from:}") String from) {
        this.provider = provider == null ? "none" : provider.trim().toLowerCase();
        this.sid = sid;
        this.token = token;
        this.from = from;
        if ("twilio".equals(this.provider) && (sid.isBlank() || token.isBlank() || from.isBlank())) {
            log.warn("SMS provider is twilio but TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM are missing: SMS off");
        }
    }

    @Override
    public boolean enabled() {
        return "log".equals(provider) || ("twilio".equals(provider) && !sid.isBlank() && !token.isBlank() && !from.isBlank());
    }

    @Override
    public void send(String phone, String text) {
        if ("log".equals(provider)) {
            log.info("SMS to {}: {}", phone, text);
            return;
        }
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DISABLED");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phone);
        form.add(from.startsWith("MG") ? "MessagingServiceSid" : "From", from);
        form.add("Body", text);
        String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        try {
            http.post().uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", sid)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Twilio SMS to {} failed: {}", phone, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS_FAILED");
        }
    }
}
