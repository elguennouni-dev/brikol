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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The job after acceptance: written agreement, extras, advances, closing code, warranty, problems, strikes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class JobProtectionTest {

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

    private String register(String phone, String name) throws Exception {
        return "Bearer " + JsonPath.read(body(post("/api/auth/register").contentType(JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"secret12\",\"fullName\":\"" + name + "\",\"city\":\"Casablanca\"}")), "$.token");
    }

    private long newRequest(String client) throws Exception {
        String r = body(post("/api/requests").header("Authorization", client).contentType(JSON)
                .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre le salon\",\"budget\":1000,\"lat\":33.5899,\"lng\":-7.6039}"));
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private long offer(String artisan, long requestId, int price) throws Exception {
        return ((Number) JsonPath.read(body(post("/api/requests/" + requestId + "/offers").header("Authorization", artisan)
                .contentType(JSON).content("{\"price\":" + price + "}")), "$.id")).longValue();
    }

    private long userId(String token) throws Exception {
        return ((Number) JsonPath.read(body(get("/api/me").header("Authorization", token)), "$.id")).longValue();
    }

    @Test
    void fullJobClosedWithTheClientsCode() throws Exception {
        String client = register("0612350001", "Nadia Job");
        String hassan = login("0611000001", "demo1234");
        String admin = login("0699000000", "admin12345");
        long id = newRequest(client);
        long offerId = offer(hassan, id, 900);
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/accept").header("Authorization", client)).andExpect(status().isOk());
        String job = "/api/requests/" + id + "/job";

        // the agreement is written; only the client sees the code
        String code = JsonPath.read(body(get(job).header("Authorization", client)), "$.code");
        assertTrue(code.matches("\\d{4}"));
        mvc.perform(get(job).header("Authorization", hassan))
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.agreedPrice").value(900))
                .andExpect(jsonPath("$.myRole").value("ARTISAN"));
        mvc.perform(get(job).header("Authorization", login("0611000002", "demo1234"))).andExpect(status().isNotFound());

        mvc.perform(post(job + "/arrived").header("Authorization", hassan)).andExpect(jsonPath("$.arrivedAt").exists());
        mvc.perform(post(job + "/arrived").header("Authorization", client)).andExpect(status().isForbidden());

        // an extra needs the client's yes; only the artisan can ask for one
        String v = body(post(job + "/items").header("Authorization", hassan).contentType(JSON)
                .content("{\"kind\":\"EXTRA\",\"label\":\"Enduit plafond\",\"amount\":150}"));
        long extraId = ((Number) JsonPath.read(v, "$.items[0].id")).longValue();
        mvc.perform(post(job + "/items/" + extraId + "/answer").header("Authorization", hassan).contentType(JSON).content("{\"accept\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(post(job + "/items").header("Authorization", client).contentType(JSON).content("{\"kind\":\"EXTRA\",\"amount\":50}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(job + "/items/" + extraId + "/answer").header("Authorization", client).contentType(JSON).content("{\"accept\":true}"))
                .andExpect(jsonPath("$.total").value(1050));

        // an advance declared by the client is counted once the artisan confirms it
        v = body(post(job + "/items").header("Authorization", client).contentType(JSON)
                .content("{\"kind\":\"ADVANCE\",\"label\":\"Peinture\",\"amount\":300}"));
        long advanceId = ((Number) JsonPath.read(v, "$.items[1].id")).longValue();
        assertEquals(1050, ((Number) JsonPath.read(v, "$.remaining")).intValue());
        mvc.perform(post(job + "/items/" + advanceId + "/answer").header("Authorization", hassan).contentType(JSON).content("{\"accept\":true}"))
                .andExpect(jsonPath("$.remaining").value(750));

        mvc.perform(post(job + "/finished").header("Authorization", hassan)).andExpect(jsonPath("$.finishedAt").exists());
        mvc.perform(get("/api/notifications").header("Authorization", client))
                .andExpect(jsonPath("$.items[0].kind").value("JOB_FINISHED")).andExpect(jsonPath("$.items[0].detail").value("750|" + code));

        // a wrong code is counted (and the count survives the error), the right one closes the job
        String wrong = String.format("%04d", (Integer.parseInt(code) + 1) % 10_000);
        mvc.perform(post(job + "/code").header("Authorization", hassan).contentType(JSON).content("{\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_WRONG"));
        mvc.perform(get(job).header("Authorization", hassan)).andExpect(jsonPath("$.codeTriesLeft").value(4));
        mvc.perform(post(job + "/code").header("Authorization", hassan).contentType(JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.doneBy").value("CODE"))
                .andExpect(jsonPath("$.warrantyUntil").exists());
        mvc.perform(get(job).header("Authorization", client)).andExpect(jsonPath("$.code").doesNotExist());

        // warranty: the problem came back; the artisan fixes it, the client confirms (or not)
        v = body(post(job + "/cases").header("Authorization", client).contentType(JSON)
                .content("{\"kind\":\"WARRANTY\",\"details\":\"Taches revenues\"}"));
        long warrantyId = ((Number) JsonPath.read(v, "$.cases[0].id")).longValue();
        mvc.perform(post(job + "/cases").header("Authorization", client).contentType(JSON).content("{\"kind\":\"WARRANTY\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get(job).header("Authorization", hassan)).andExpect(jsonPath("$.cases[0].kind").value("WARRANTY"));
        mvc.perform(post(job + "/cases/" + warrantyId + "/fixed").header("Authorization", hassan)).andExpect(jsonPath("$.cases[0].status").value("FIXED"));
        mvc.perform(post(job + "/cases/" + warrantyId + "/answer").header("Authorization", client).contentType(JSON).content("{\"accept\":false}"))
                .andExpect(jsonPath("$.cases[0].status").value("OPEN"));
        mvc.perform(post(job + "/cases/" + warrantyId + "/fixed").header("Authorization", hassan));
        mvc.perform(post(job + "/cases/" + warrantyId + "/answer").header("Authorization", client).contentType(JSON).content("{\"accept\":true}"))
                .andExpect(jsonPath("$.cases[0].status").value("RESOLVED"));

        // problems go to the team, each side with its own reasons
        mvc.perform(post(job + "/cases").header("Authorization", client).contentType(JSON).content("{\"kind\":\"PROBLEM\",\"reason\":\"UNPAID\"}"))
                .andExpect(status().isBadRequest());
        v = body(post(job + "/cases").header("Authorization", hassan).contentType(JSON)
                .content("{\"kind\":\"PROBLEM\",\"reason\":\"PRICE\",\"details\":\"Il refuse de payer l'enduit\"}"));
        List<Number> problemIds = JsonPath.read(v, "$.cases[?(@.kind == 'PROBLEM')].id");
        long caseId = problemIds.get(0).longValue();
        mvc.perform(get(job).header("Authorization", client)).andExpect(jsonPath("$.cases.length()").value(1));   // only the warranty
        mvc.perform(get("/api/admin/cases").header("Authorization", admin)).andExpect(jsonPath("$[*].id", hasItem((int) caseId)));
        mvc.perform(get("/api/admin/cases/" + caseId).header("Authorization", admin))
                .andExpect(jsonPath("$.agreedPrice").value(900)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.client.name").value("Nadia Job")).andExpect(jsonPath("$.artisan.phone").exists());
        mvc.perform(get("/api/admin/cases").header("Authorization", client)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/cases/" + caseId).header("Authorization", admin).contentType(JSON)
                        .content("{\"status\":\"RESOLVED\",\"resolution\":\"Appelé les deux, accord trouvé\",\"strikeClient\":\"OTHER\"}"))
                .andExpect(jsonPath("$.row.status").value("RESOLVED")).andExpect(jsonPath("$.client.recentStrikes").value(1));
    }

    @Test
    void artisanWithdrawsLateClientConfirmsAndSuspension() throws Exception {
        String client = register("0612350002", "Karim Job");
        String hassan = login("0611000001", "demo1234");
        String omar = login("0611000002", "demo1234");
        String admin = login("0699000000", "admin12345");
        long id = newRequest(client);
        long hassanOffer = offer(hassan, id, 950);
        long omarOffer = offer(omar, id, 900);
        mvc.perform(post("/api/requests/" + id + "/offers/" + omarOffer + "/accept").header("Authorization", client)).andExpect(status().isOk());
        String at = Instant.now().plus(3, ChronoUnit.HOURS).toString();
        mvc.perform(post("/api/requests/" + id + "/appointment").header("Authorization", client).contentType(JSON).content("{\"at\":\"" + at + "\"}"));
        mvc.perform(post("/api/requests/" + id + "/appointment/confirm").header("Authorization", omar)).andExpect(status().isOk());

        // the artisan drops out 3 hours before: the request is open again with the other offers, and he gets a strike
        mvc.perform(post("/api/requests/" + id + "/job/withdraw").header("Authorization", omar)).andExpect(status().isNoContent());
        mvc.perform(get("/api/requests/" + id).header("Authorization", client))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.offers[?(@.id == " + hassanOffer + ")].status").value("PENDING"));
        long omarId = userId(omar);
        String party = body(get("/api/admin/users/" + omarId + "/strikes").header("Authorization", admin));
        assertTrue(((Number) JsonPath.read(party, "$.recentStrikes")).intValue() >= 1);
        long strikeId = ((Number) JsonPath.read(party, "$.strikes[0].id")).longValue();
        mvc.perform(delete("/api/admin/strikes/" + strikeId).header("Authorization", admin)).andExpect(jsonPath("$.strikes[0].removed").value(true));

        // the client can close the job alone
        mvc.perform(post("/api/requests/" + id + "/offers/" + hassanOffer + "/accept").header("Authorization", client)).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/job/cases").header("Authorization", client).contentType(JSON)
                .content("{\"kind\":\"PROBLEM\",\"reason\":\"NO_SHOW\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/requests/" + id + "/job/confirm").header("Authorization", client))
                .andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.doneBy").value("CLIENT"));

        // three strikes in 30 days: suspended for 7 days
        String bad = register("0612350003", "Client Absent");
        long badId = userId(bad);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/admin/users/" + badId + "/strikes").header("Authorization", admin).contentType(JSON)
                    .content("{\"reason\":\"CLIENT_ABSENT\"}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/me").header("Authorization", bad)).andExpect(jsonPath("$.suspendedUntil", matchesPattern(".+")));
        mvc.perform(post("/api/requests").header("Authorization", bad).contentType(JSON)
                        .content("{\"categoryCode\":\"paint\",\"description\":\"Test\",\"budget\":1000}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }
}
