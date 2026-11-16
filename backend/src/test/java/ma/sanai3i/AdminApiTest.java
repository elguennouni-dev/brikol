package ma.sanai3i;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin account comes from application-dev.yml (0699000000 / admin12345). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminApiTest {

    @Autowired
    MockMvc mvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private String body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String token(String phone, String password) throws Exception {
        return JsonPath.read(body(post("/api/auth/login").contentType(JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}")), "$.token");
    }

    @Test
    void adminAreaIsClosedToOthers() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().is4xxClientError());
        String client = token("0600000001", "demo1234");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
        String admin = token("0699000000", "admin12345");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users", greaterThanOrEqualTo(10)));
    }

    @Test
    void publicRegistrationCannotCreateAdmin() throws Exception {
        String reg = body(post("/api/auth/register").contentType(JSON)
                .content("{\"phone\":\"0644556677\",\"password\":\"secret12\",\"fullName\":\"Sneaky\",\"city\":\"Fes\",\"role\":\"ADMIN\"}"));
        org.junit.jupiter.api.Assertions.assertEquals("CLIENT", JsonPath.read(reg, "$.user.role"));
    }

    @Test
    void blockUnblockVerifyBroadcastAndAudit() throws Exception {
        String admin = token("0699000000", "admin12345");
        String auth = "Bearer " + admin;
        String reg = body(post("/api/auth/register").contentType(JSON)
                .content("{\"phone\":\"0633221100\",\"password\":\"secret12\",\"fullName\":\"Block Me\",\"city\":\"Rabat\"}"));
        String victim = JsonPath.read(reg, "$.token");
        Number id = JsonPath.read(reg, "$.user.id");

        mvc.perform(post("/api/admin/users/" + id + "/block").header("Authorization", auth)
                        .contentType(JSON).content("{\"reason\":\"spam\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + victim))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0633221100\",\"password\":\"secret12\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));

        mvc.perform(post("/api/admin/users/" + id + "/unblock").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(false));
        String again = token("0633221100", "secret12");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + again)).andExpect(status().isOk());

        // an admin cannot be targeted, nor can the admin act on self
        mvc.perform(get("/api/admin/users").header("Authorization", auth).param("role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].role").value("ADMIN"));
        Number adminId = JsonPath.read(body(get("/api/admin/users").header("Authorization", auth).param("role", "ADMIN")), "$.items[0].id");
        mvc.perform(post("/api/admin/users/" + adminId + "/block").header("Authorization", auth)
                        .contentType(JSON).content("{}"))
                .andExpect(status().isConflict());

        // verify an artisan
        String artisans = body(get("/api/admin/users").header("Authorization", auth).param("role", "ARTISAN"));
        Number artisanId = JsonPath.read(artisans, "$.items[0].artisanId");
        mvc.perform(post("/api/admin/artisans/" + artisanId + "/verified").header("Authorization", auth)
                        .contentType(JSON).content("{\"verified\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));

        // broadcast reaches clients
        mvc.perform(post("/api/admin/broadcast").header("Authorization", auth).contentType(JSON)
                        .content("{\"audience\":\"CLIENTS\",\"message\":\"Maintenance ce soir\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent", greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + again))
                .andExpect(jsonPath("$.items[*].kind").value(hasItem("ANNOUNCEMENT")));

        // audit trail
        String audit = body(get("/api/admin/audit").header("Authorization", auth).param("size", "100"));
        for (String a : new String[]{"USER_BLOCKED", "USER_UNBLOCKED", "LOGIN_BLOCKED", "ARTISAN_VERIFIED", "BROADCAST"}) {
            org.junit.jupiter.api.Assertions.assertTrue(audit.contains(a), "audit should contain " + a);
        }
        mvc.perform(get("/api/admin/export/users.csv").header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void closedRegistrationsBlockSignUp() throws Exception {
        String auth = "Bearer " + token("0699000000", "admin12345");
        mvc.perform(put("/api/admin/settings").header("Authorization", auth).contentType(JSON)
                .content("{\"registrationsOpen\":false,\"bannerMessage\":\"Bientôt\"}")).andExpect(status().isOk());
        try {
            mvc.perform(post("/api/auth/register").contentType(JSON)
                            .content("{\"phone\":\"0622110099\",\"password\":\"secret12\",\"fullName\":\"Late\",\"city\":\"Fes\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REGISTRATIONS_CLOSED"));
            mvc.perform(get("/api/public/config")).andExpect(jsonPath("$.bannerMessage").value("Bientôt"));
        } finally {
            mvc.perform(put("/api/admin/settings").header("Authorization", auth).contentType(JSON)
                    .content("{\"registrationsOpen\":true,\"bannerMessage\":\"\"}")).andExpect(status().isOk());
        }
    }
}
