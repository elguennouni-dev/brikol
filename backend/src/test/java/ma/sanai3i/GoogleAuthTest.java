package ma.sanai3i;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.security.GoogleVerifier;
import ma.sanai3i.security.GoogleVerifier.GoogleIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Google is replaced by a mock: the real verifier checks the token signature with Google's keys. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class GoogleAuthTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    GoogleVerifier google;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void signUpThenSignInWithGoogle() throws Exception {
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_INVALID")).when(google).verify(anyString());
        doReturn(new GoogleIdentity("g-123", "Yasmine@Gmail.com", true, "Yasmine Alami")).when(google).verify("tok-new");
        doReturn(new GoogleIdentity("g-999", "x@y.com", false, "X")).when(google).verify("tok-unverified");

        mvc.perform(get("/api/public/config")).andExpect(jsonPath("$.googleClientId").isNotEmpty());

        mvc.perform(post("/api/auth/google").contentType(JSON).content("{\"credential\":\"forged\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("GOOGLE_INVALID"));
        mvc.perform(post("/api/auth/google").contentType(JSON).content("{\"credential\":\"tok-unverified\"}"))
                .andExpect(status().isUnauthorized());

        // first time: the app must ask for the phone number and the role
        mvc.perform(post("/api/auth/google").contentType(JSON).content("{\"credential\":\"tok-new\"}"))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_NEEDED"));
        mvc.perform(post("/api/auth/google").contentType(JSON)
                        .content("{\"credential\":\"tok-new\",\"phone\":\"0612121212\",\"role\":\"ARTISAN\",\"categoryCode\":\"elec\",\"neighborhood\":\"Gauthier\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Yasmine Alami"))
                .andExpect(jsonPath("$.user.role").value("ARTISAN"))
                .andExpect(jsonPath("$.user.phone").value("+212612121212"));

        // next time: signed in directly, no questions
        mvc.perform(post("/api/auth/google").contentType(JSON).content("{\"credential\":\"tok-new\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.phone").value("+212612121212"));

        // a Google account cannot take a phone number that already exists
        doReturn(new GoogleIdentity("g-456", "other@gmail.com", true, "Other")).when(google).verify("tok-other");
        mvc.perform(post("/api/auth/google").contentType(JSON)
                        .content("{\"credential\":\"tok-other\",\"phone\":\"0600000001\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
    }
}
