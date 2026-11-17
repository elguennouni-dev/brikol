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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression tests for the security audit fixes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityTest {

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

    @Test
    void artisanProfilesAreNotPublic() throws Exception {
        String admin = login("0699000000", "admin12345");
        String hassan = login("0611000001", "demo1234");
        String client = register("0612360001", "Sara Secu");
        long hassanId = ((Number) JsonPath.read(body(get("/api/artisans").param("q", "Hassan").header("Authorization", admin)), "$[0].id")).longValue();

        // no more anonymous directory with names and phone numbers
        mvc.perform(get("/api/artisans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/artisans/" + hassanId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/artisans").header("Authorization", client)).andExpect(status().isForbidden());
        // a client sees a profile only once that artisan sent them an offer, and the phone only once it is accepted
        mvc.perform(get("/api/artisans/" + hassanId).header("Authorization", client)).andExpect(status().isNotFound());
        long id = ((Number) JsonPath.read(body(post("/api/requests").header("Authorization", client).contentType(JSON)
                .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre la chambre\",\"budget\":1000,\"lat\":33.5899,\"lng\":-7.6039}")), "$.id")).longValue();
        long offerId = ((Number) JsonPath.read(body(post("/api/requests/" + id + "/offers").header("Authorization", hassan)
                .contentType(JSON).content("{\"price\":900}")), "$.id")).longValue();
        mvc.perform(get("/api/artisans/" + hassanId).header("Authorization", client))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.whatsapp").doesNotExist());
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/accept").header("Authorization", client));
        mvc.perform(get("/api/artisans/" + hassanId).header("Authorization", client)).andExpect(jsonPath("$.phone").value("+212611000001"));
        mvc.perform(get("/api/artisans/" + hassanId).header("Authorization", hassan)).andExpect(status().isOk());
    }

    @Test
    void passwordResetSignsOutOldTokens() throws Exception {
        String admin = login("0699000000", "admin12345");
        String user = register("0612360002", "Old Token");
        long userId = ((Number) JsonPath.read(body(get("/api/me").header("Authorization", user)), "$.id")).longValue();
        mvc.perform(get("/api/me").header("Authorization", user)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/" + userId + "/reset-password").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization", user)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION"));
        // a token for an account that does not exist is refused too
        mvc.perform(get("/api/me").header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordsAndLongBcryptInputAreRefused() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"phone\":\"0612360003\",\"password\":\"abc123\",\"fullName\":\"Short\",\"city\":\"Casablanca\"}"))
                .andExpect(status().isBadRequest());
        String arabic = "كلمةسرطويلةجدابزافبزافبزافبزافبزافبزافبزاف";   // < 72 characters but > 72 bytes
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"phone\":\"0612360004\",\"password\":\"" + arabic + "\",\"fullName\":\"Long\",\"city\":\"Casablanca\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_WEAK"));
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0612360099\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void securityHeadersAndNoInlineScripts() throws Exception {
        mvc.perform(get("/app/index.html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("'unsafe-inline' https://accounts.google.com/gsi/client"))))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string(not(containsString("<script>"))));
        mvc.perform(get("/index.html")).andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void csvExportNeutralisesFormulas() throws Exception {
        String admin = login("0699000000", "admin12345");
        register("0612360005", "=HYPERLINK(\\\"http://x\\\")");
        String csv = body(get("/api/admin/export/users.csv").header("Authorization", admin));
        assertTrue(csv.contains("\"'=HYPERLINK"), "formula must be prefixed with a quote");
    }
}
