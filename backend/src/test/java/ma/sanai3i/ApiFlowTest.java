package ma.sanai3i;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs on the in-memory H2 database of the "dev" profile, which also loads the demo data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiFlowTest {

    @Autowired
    MockMvc mvc;

    private static final String HASSAN = "0611000001";   // painter
    private static final String OMAR = "0611000002";     // painter
    private static final String DEMO_PASSWORD = "demo1234";

    @Test
    void categoriesAndArtisansArePublic() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(7)));
        mvc.perform(get("/api/artisans").header("Authorization", staff()).param("category", "paint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].categoryCode").value("paint"));
        mvc.perform(get("/api/artisans").header("Authorization", staff()).param("q", "zellig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void artisanDetailHasServicesAndReviews() throws Exception {
        String list = mvc.perform(get("/api/artisans").header("Authorization", staff()).param("q", "Hassan"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number id = JsonPath.read(list, "$[0].id");
        mvc.perform(get("/api/artisans/" + id.longValue()).header("Authorization", staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services", hasSize(4)))
                .andExpect(jsonPath("$.summary.reviewCount", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.phone").value("+212611000001"));
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerNormalizesPhoneAndRejectsDuplicates() throws Exception {
        String body = "{\"phone\":\"06 55 44 33 22\",\"password\":\"secret12\",\"fullName\":\"Nadia Test\",\"city\":\"Casablanca\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phone").value("+212655443322"))
                .andExpect(jsonPath("$.user.role").value("CLIENT"))
                .andExpect(jsonPath("$.token", notNullValue()));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+212655443322\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullJobFlow_requestOffersAcceptCompleteReview() throws Exception {
        // a new client posts a request
        String reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0677665544\",\"password\":\"secret12\",\"fullName\":\"Salma Flow\",\"city\":\"Casablanca\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String client = JsonPath.read(reg, "$.token");

        String created = mvc.perform(post("/api/requests").header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre un salon de 30 m2\",\"whenPref\":\"WEEK\",\"budget\":1500,\"city\":\"casablanca\",\"lat\":33.5899,\"lng\":-7.6039,\"address\":\"Rue Ibnou Mounir, Maârif\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lat").value(33.5899))
                .andExpect(jsonPath("$.address").value("Rue Ibnou Mounir, Maârif"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.offerCount").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long requestId = ((Number) JsonPath.read(created, "$.id")).longValue();

        // painters of the same city are told about the new request
        String hassanEarly = login(HASSAN);
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + hassanEarly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].kind", hasItem("NEW_REQUEST")));
        // before an accepted offer the artisan only sees an approximate place, without the street
        String openList = mvc.perform(get("/api/artisan/requests").header("Authorization", "Bearer " + hassanEarly))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.util.List<Number> lats = JsonPath.read(openList, "$[?(@.id == " + requestId + ")].lat");
        java.util.List<Object> addresses = JsonPath.read(openList, "$[?(@.id == " + requestId + ")].address");
        org.junit.jupiter.api.Assertions.assertEquals(1, lats.size());
        org.junit.jupiter.api.Assertions.assertEquals(33.59, lats.get(0).doubleValue(), 0.0001);
        org.junit.jupiter.api.Assertions.assertNull(addresses.get(0));

        // a client cannot send offers; two painters can
        mvc.perform(post("/api/requests/" + requestId + "/offers").header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1400}"))
                .andExpect(status().isForbidden());
        String hassan = login(HASSAN);
        String omar = login(OMAR);
        mvc.perform(post("/api/requests/" + requestId + "/offers").header("Authorization", "Bearer " + hassan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1400,\"etaText\":\"TOMORROW\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/requests/" + requestId + "/offers").header("Authorization", "Bearer " + hassan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1300}"))
                .andExpect(status().isConflict());
        String omarOffer = mvc.perform(post("/api/requests/" + requestId + "/offers").header("Authorization", "Bearer " + omar)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":1200,\"etaText\":\"TODAY\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long omarOfferId = ((Number) JsonPath.read(omarOffer, "$.id")).longValue();

        // the client was notified of both offers
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items[0].kind").value("OFFER_RECEIVED"));

        // the client sees both offers (cheapest first) and no phone yet
        mvc.perform(get("/api/requests/mine").param("tab", "active").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offers", hasSize(2)))
                .andExpect(jsonPath("$[0].offers[0].price").value(1200))
                .andExpect(jsonPath("$[0].offers[0].artisanPhone", nullValue()));

        // accept Omar's offer: his phone is revealed, Hassan's offer disappears from the list
        mvc.perform(post("/api/requests/" + requestId + "/offers/" + omarOfferId + "/accept")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.offers", hasSize(1)))
                .andExpect(jsonPath("$.offers[0].artisanPhone").value("+212611000002"));

        // the chosen artisan is told, the other one too
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + omar))
                .andExpect(jsonPath("$.items[0].kind").value("OFFER_ACCEPTED"));
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + hassan))
                .andExpect(jsonPath("$.items[0].kind").value("OFFER_REJECTED"));
        mvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.unread").value(0));

        // the accepted artisan now sees the client's phone, the other one does not
        mvc.perform(get("/api/requests/" + requestId).header("Authorization", "Bearer " + omar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientPhone").value("+212677665544"));
        mvc.perform(get("/api/requests/" + requestId).header("Authorization", "Bearer " + hassan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientPhone", nullValue()));

        // review is refused until the job is done
        mvc.perform(post("/api/requests/" + requestId + "/review").header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"body\":\"Top\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/requests/" + requestId + "/complete").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        review(client, requestId).andExpect(status().isOk()).andExpect(jsonPath("$.reviewed").value(true));
        review(client, requestId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REVIEWED"));

        // another client cannot see or touch this request
        String other = JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0600000002\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        mvc.perform(get("/api/requests/" + requestId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/" + requestId + "/cancel").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void artisanSeesOpenRequestsOfHisTradeAndCity() throws Exception {
        String hassan = login(HASSAN);
        mvc.perform(get("/api/artisan/requests").header("Authorization", "Bearer " + hassan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].categoryCode").isNotEmpty());
        // clients are refused on artisan endpoints
        String client = JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0600000001\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        mvc.perform(get("/api/artisan/requests").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
    }

    private ResultActions review(String token, long requestId) throws Exception {
        return mvc.perform(post("/api/requests/" + requestId + "/review").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"body\":\"Très bon travail\"}"));
    }

    private String staff() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0699000000\",\"password\":\"admin12345\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private String login(String phone) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }
}
