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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owner (super admin) and staff with permissions; nobody becomes admin by promotion. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StaffTest {

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

    @Test
    void ownerManagesStaffWithPermissions() throws Exception {
        String owner = login("0699000000", "admin12345");
        String me = body(get("/api/me").header("Authorization", owner));
        Number ownerId = JsonPath.read(me, "$.id");
        mvc.perform(get("/api/me").header("Authorization", owner)).andExpect(jsonPath("$.superAdmin").value(true));

        // promoting an existing user is gone
        mvc.perform(post("/api/admin/users/1/role").header("Authorization", owner).contentType(JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().is4xxClientError());

        // a staff member who checks artisan files only
        String created = body(post("/api/admin/staff").header("Authorization", owner).contentType(JSON)
                .content("{\"fullName\":\"Nadia Verif\",\"phone\":\"0655110022\",\"password\":\"verif12345\",\"permissions\":[\"FILES\"]}"));
        Number staffId = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/admin/staff").header("Authorization", owner).contentType(JSON)
                        .content("{\"fullName\":\"Client\",\"phone\":\"0600000001\",\"password\":\"verif12345\",\"permissions\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
        String staff = login("0655110022", "verif12345");
        mvc.perform(get("/api/me").header("Authorization", staff))
                .andExpect(jsonPath("$.superAdmin").value(false)).andExpect(jsonPath("$.permissions[0]").value("FILES"));
        mvc.perform(get("/api/admin/stats").header("Authorization", staff)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/artisan-files").header("Authorization", staff)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", staff))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NO_PERMISSION"));
        mvc.perform(get("/api/admin/reports").header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/monetization").header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/staff").header("Authorization", staff))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OWNER_ONLY"));

        // more permissions: still cannot touch the owner
        mvc.perform(put("/api/admin/staff/" + staffId).header("Authorization", owner).contentType(JSON)
                .content("{\"permissions\":[\"FILES\",\"USERS\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", staff)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/" + ownerId + "/block").header("Authorization", staff).contentType(JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/staff/" + ownerId + "/demote").header("Authorization", owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_TARGET_OWNER"));

        // block, unblock, reset password
        mvc.perform(post("/api/admin/staff/" + staffId + "/block").header("Authorization", owner)).andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(get("/api/admin/stats").header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/staff/" + staffId + "/unblock").header("Authorization", owner)).andExpect(jsonPath("$.blocked").value(false));
        String temp = JsonPath.read(body(post("/api/admin/staff/" + staffId + "/reset-password").header("Authorization", owner)), "$.password");
        login("0655110022", temp);

        // out of the team: back to a normal account, no administration at all
        mvc.perform(post("/api/admin/staff/" + staffId + "/demote").header("Authorization", owner)).andExpect(status().isNoContent());
        // the password reset and the demotion signed the old token out
        mvc.perform(get("/api/admin/stats").header("Authorization", staff)).andExpect(status().isUnauthorized());
        String fresh = login("0655110022", temp);
        mvc.perform(get("/api/me").header("Authorization", fresh)).andExpect(jsonPath("$.role").value("CLIENT"));

        // a second staff account deleted completely
        String second = body(post("/api/admin/staff").header("Authorization", owner).contentType(JSON)
                .content("{\"fullName\":\"Temp Staff\",\"phone\":\"0655110033\",\"password\":\"temp123456\",\"permissions\":[\"AUDIT\"]}"));
        Number secondId = JsonPath.read(second, "$.id");
        mvc.perform(delete("/api/admin/staff/" + secondId).header("Authorization", owner)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0655110033\",\"password\":\"temp123456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
