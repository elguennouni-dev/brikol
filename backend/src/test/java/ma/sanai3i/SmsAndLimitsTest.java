package ma.sanai3i;

import com.jayway.jsonpath.JsonPath;
import ma.sanai3i.sms.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SMS codes (sign-up, forgotten password) with a fake SMS provider, and login brute-force protection. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class SmsAndLimitsTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    SmsSender sms;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private final List<String> sent = new ArrayList<>();

    private String lastCode() {
        Matcher m = Pattern.compile("(\\d{6})").matcher(sent.get(sent.size() - 1));
        return m.find() ? m.group(1) : "";
    }

    @Test
    void signUpNeedsSmsCodeAndPasswordCanBeReset() throws Exception {
        when(sms.enabled()).thenReturn(true);
        doAnswer(inv -> sent.add(inv.getArgument(1))).when(sms).send(anyString(), anyString());

        mvc.perform(get("/api/public/config")).andExpect(jsonPath("$.smsEnabled").value(true));
        String reg = "{\"phone\":\"0611223344\",\"password\":\"secret12\",\"fullName\":\"Otp User\",\"city\":\"Casablanca\"%s}";
        mvc.perform(post("/api/auth/register").contentType(JSON).content(String.format(reg, "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_REQUIRED"));
        mvc.perform(post("/api/auth/otp").contentType(JSON).content("{\"phone\":\"0611223344\",\"purpose\":\"REGISTER\",\"lang\":\"fr\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/otp").contentType(JSON).content("{\"phone\":\"0611223344\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_WAIT"));
        mvc.perform(post("/api/auth/register").contentType(JSON).content(String.format(reg, ",\"otp\":\"000000\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        mvc.perform(post("/api/auth/register").contentType(JSON).content(String.format(reg, ",\"otp\":\"" + lastCode() + "\"")))
                .andExpect(status().isCreated());
        // a code is used once
        mvc.perform(post("/api/auth/register").contentType(JSON).content(String.format(reg.replace("0611223344", "0611223355"), ",\"otp\":\"" + lastCode() + "\"")))
                .andExpect(status().isBadRequest());
        // registering an existing number: same answer as a new one (no account discovery); the owner gets a warning SMS
        mvc.perform(post("/api/auth/otp").contentType(JSON).content("{\"phone\":\"0600000001\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isNoContent());
        assertTrue(sent.get(sent.size() - 1).contains("حساب"), "warning SMS, no code");
        // and the registration itself cannot tell (the code check comes first)
        mvc.perform(post("/api/auth/register").contentType(JSON).content(String.format(reg.replace("0611223344", "0600000001"), ",\"otp\":\"123456\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));

        // forgotten password
        int before = sent.size();
        mvc.perform(post("/api/auth/otp").contentType(JSON).content("{\"phone\":\"0698765432\",\"purpose\":\"RESET\"}"))
                .andExpect(status().isNoContent());
        assertEquals(before, sent.size(), "unknown number: same answer, no SMS");
        mvc.perform(post("/api/auth/otp").contentType(JSON).content("{\"phone\":\"0611000008\",\"purpose\":\"RESET\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset").contentType(JSON)
                        .content("{\"phone\":\"0611000008\",\"otp\":\"" + lastCode() + "\",\"password\":\"nouveau99\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0611000008\",\"password\":\"nouveau99\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void tooManyWrongPasswordsLockTheNumber() throws Exception {
        when(sms.enabled()).thenReturn(false);
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0600000003\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"phone\":\"0600000003\",\"password\":\"demo1234\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        String body = mvc.perform(get("/api/public/config")).andReturn().getResponse().getContentAsString();
        assertEquals(false, JsonPath.read(body, "$.smsEnabled"));
    }

    /** Failures from one address do not lock the owner out from another address. */
    @Test
    void lockoutIsPerAddress() throws Exception {
        when(sms.enabled()).thenReturn(false);
        mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr("192.0.2.50"); return r; }).contentType(JSON)
                .content("{\"phone\":\"0612370001\",\"password\":\"secret12\",\"fullName\":\"Lock Test\",\"city\":\"Casablanca\"}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }).contentType(JSON)
                    .content("{\"phone\":\"0612370001\",\"password\":\"wrongpass\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }).contentType(JSON)
                .content("{\"phone\":\"0612370001\",\"password\":\"secret12\"}")).andExpect(status().isTooManyRequests());
        // a forged X-Forwarded-For does not change the address (only a trusted proxy may set it)
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }).header("X-Forwarded-For", "1.2.3.4")
                .contentType(JSON).content("{\"phone\":\"0612370001\",\"password\":\"secret12\"}")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("198.51.100.20"); return r; }).contentType(JSON)
                .content("{\"phone\":\"0612370001\",\"password\":\"secret12\"}")).andExpect(status().isOk());
    }
}
