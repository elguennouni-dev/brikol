package ma.sanai3i;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat, appointment, the artisan rating the client, reports, subscription / featured, public SEO pages. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DealsAndTrustTest {

    @Autowired
    MockMvc mvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private String body(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String phone, String password) throws Exception {
        return "Bearer " + JsonPath.read(body(post("/api/auth/login").contentType(JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}")), "$.token");
    }

    private long newRequest(String client) throws Exception {
        String r = body(post("/api/requests").header("Authorization", client).contentType(JSON)
                .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre une cuisine\",\"budget\":1000,\"lat\":33.5899,\"lng\":-7.6039}"));
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    @Test
    void chatAppointmentAndMutualReviews() throws Exception {
        String client = "Bearer " + JsonPath.read(body(post("/api/auth/register").contentType(JSON)
                .content("{\"phone\":\"0612340001\",\"password\":\"secret12\",\"fullName\":\"Laila Deal\",\"city\":\"Casablanca\"}")), "$.token");
        String hassan = login("0611000001", "demo1234");
        long id = newRequest(client);
        long offerId = ((Number) JsonPath.read(body(post("/api/requests/" + id + "/offers").header("Authorization", hassan)
                .contentType(JSON).content("{\"price\":900}")), "$.id")).longValue();
        // no chat before acceptance
        mvc.perform(get("/api/requests/" + id + "/messages").header("Authorization", client)).andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/accept").header("Authorization", client)).andExpect(status().isOk());

        mvc.perform(post("/api/requests/" + id + "/messages").header("Authorization", client).contentType(JSON)
                        .content("{\"body\":\"Bonjour, vous pouvez passer demain ?\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mine").value(true));
        String jobs = body(get("/api/artisan/jobs").header("Authorization", hassan));
        List<Number> unread = JsonPath.read(jobs, "$[?(@.id == " + id + ")].unreadMessages");
        assertEquals(1, unread.get(0).intValue());
        mvc.perform(get("/api/requests/" + id + "/messages").header("Authorization", hassan))
                .andExpect(jsonPath("$.messages[0].mine").value(false))
                .andExpect(jsonPath("$.otherName").value("Laila D."))
                .andExpect(jsonPath("$.myRole").value("ARTISAN"));
        String other = login("0611000002", "demo1234");
        mvc.perform(get("/api/requests/" + id + "/messages").header("Authorization", other)).andExpect(status().isNotFound());

        // appointment: proposed by the client, confirmed by the artisan
        String at = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString();
        mvc.perform(post("/api/requests/" + id + "/appointment").header("Authorization", client).contentType(JSON)
                .content("{\"at\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests/" + id + "/appointment").header("Authorization", client).contentType(JSON)
                        .content("{\"at\":\"" + at + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentBy").value("CLIENT"))
                .andExpect(jsonPath("$.appointmentConfirmed").value(false));
        mvc.perform(post("/api/requests/" + id + "/appointment/confirm").header("Authorization", client))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WAITING_OTHER"));
        mvc.perform(post("/api/requests/" + id + "/appointment/confirm").header("Authorization", hassan))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentConfirmed").value(true));
        mvc.perform(get("/api/requests/" + id).header("Authorization", client))
                .andExpect(jsonPath("$.appointmentConfirmed").value(true));

        // the artisan rates the client once the job is done
        mvc.perform(post("/api/requests/" + id + "/client-review").header("Authorization", hassan).contentType(JSON)
                .content("{\"rating\":4}")).andExpect(status().isConflict());
        mvc.perform(post("/api/requests/" + id + "/complete").header("Authorization", client)).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/client-review").header("Authorization", client).contentType(JSON)
                .content("{\"rating\":4}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/" + id + "/client-review").header("Authorization", hassan).contentType(JSON)
                .content("{\"rating\":4,\"body\":\"Cliente sérieuse\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/requests/" + id + "/client-review").header("Authorization", hassan).contentType(JSON)
                .content("{\"rating\":5}")).andExpect(status().isConflict());
        // other artisans see the client's rating on her next request
        long next = newRequest(client);
        String open = body(get("/api/artisan/requests").header("Authorization", other));
        List<Number> count = JsonPath.read(open, "$[?(@.id == " + next + ")].clientReviewCount");
        assertEquals(1, count.get(0).intValue());
    }

    @Test
    void reportsAreHandledByAdmins() throws Exception {
        String client = login("0600000001", "demo1234");
        String admin = login("0699000000", "admin12345");
        String list = body(get("/api/artisans").param("q", "Hassan"));
        Number hassanId = JsonPath.read(list, "$[0].id");
        String report = "{\"targetType\":\"ARTISAN\",\"targetId\":" + hassanId + ",\"reason\":\"NO_SHOW\",\"details\":\"Pas venu au rendez-vous\"}";
        mvc.perform(post("/api/reports").header("Authorization", client).contentType(JSON).content(report)).andExpect(status().isCreated());
        mvc.perform(post("/api/reports").header("Authorization", client).contentType(JSON).content(report))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        mvc.perform(post("/api/reports").header("Authorization", client).contentType(JSON)
                .content("{\"targetType\":\"ARTISAN\",\"targetId\":" + hassanId + ",\"reason\":\"WHATEVER\"}")).andExpect(status().isBadRequest());
        String open = body(get("/api/admin/reports").header("Authorization", admin));
        List<Number> ids = JsonPath.read(open, "$[?(@.details == 'Pas venu au rendez-vous')].id");
        assertEquals(1, ids.size());
        mvc.perform(get("/api/admin/stats").header("Authorization", admin)).andExpect(jsonPath("$.openReports").isNumber());
        mvc.perform(post("/api/admin/reports/" + ids.get(0)).header("Authorization", admin).contentType(JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"Avertissement envoyé\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/notifications").header("Authorization", client))
                .andExpect(jsonPath("$.items[*].kind", hasItem("REPORT_HANDLED")));
        mvc.perform(get("/api/admin/reports").header("Authorization", client)).andExpect(status().isForbidden());
    }

    @Test
    void subscriptionAndFeatured() throws Exception {
        String admin = login("0699000000", "admin12345");
        String omar = login("0611000002", "demo1234");
        String client = login("0600000001", "demo1234");
        Number omarId = JsonPath.read(body(get("/api/artisans").param("q", "Omar")), "$[0].id");
        try {
            mvc.perform(put("/api/admin/monetization").header("Authorization", admin).contentType(JSON)
                    .content("{\"enabled\":true,\"subscriptionPrice\":99,\"trialDays\":0,\"featuredPrice\":49,\"paymentInstructions\":\"CashPlus 0600000000\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
            long id = newRequest(client);
            mvc.perform(post("/api/requests/" + id + "/offers").header("Authorization", omar).contentType(JSON).content("{\"price\":800}"))
                    .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
            mvc.perform(get("/api/artisan/me/plan").header("Authorization", omar))
                    .andExpect(jsonPath("$.monetization").value(true)).andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.instructions").value("CashPlus 0600000000"));
            mvc.perform(post("/api/admin/artisans/" + omarId + "/payments").header("Authorization", admin).contentType(JSON)
                            .content("{\"kind\":\"SUBSCRIPTION\",\"months\":1,\"amount\":99,\"method\":\"cashplus\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.payments.length()").value(1));
            mvc.perform(post("/api/requests/" + id + "/offers").header("Authorization", omar).contentType(JSON).content("{\"price\":800}"))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/admin/artisans/" + omarId + "/payments").header("Authorization", admin).contentType(JSON)
                    .content("{\"kind\":\"FEATURED\",\"months\":1,\"amount\":49,\"method\":\"virement\"}")).andExpect(jsonPath("$.featured").value(true));
            mvc.perform(get("/api/artisans").param("category", "paint"))
                    .andExpect(jsonPath("$[0].nameFr").value("Omar Chraibi")).andExpect(jsonPath("$[0].featured").value(true));
            mvc.perform(get("/api/admin/payments").header("Authorization", admin)).andExpect(jsonPath("$[0].artisanName").isNotEmpty());
        } finally {
            mvc.perform(put("/api/admin/monetization").header("Authorization", admin).contentType(JSON)
                    .content("{\"enabled\":false,\"subscriptionPrice\":99,\"trialDays\":30,\"featuredPrice\":49,\"paymentInstructions\":\"\"}"));
        }
    }

    @Test
    void publicPagesForSearchEngines() throws Exception {
        Number hassanId = JsonPath.read(body(get("/api/artisans").param("q", "Hassan")), "$[0].id");
        mvc.perform(get("/artisans")).andExpect(status().isOk()).andExpect(content().string(containsString("/artisans/paint/casablanca")));
        mvc.perform(get("/artisans/paint/casablanca")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hassan"))).andExpect(content().string(containsString("rel=canonical")));
        mvc.perform(get("/pro/" + hassanId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(containsString("AggregateRating")));
        mvc.perform(get("/pro/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/artisans/paint/atlantis")).andExpect(status().isOk()).andExpect(content().string(containsString("noindex")));
        mvc.perform(get("/artisans/nothing/casablanca")).andExpect(status().isNotFound());
        mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andExpect(content().string(containsString("/pro/" + hassanId)));
        mvc.perform(get("/robots.txt")).andExpect(content().string(containsString("Sitemap:")));
    }
}
