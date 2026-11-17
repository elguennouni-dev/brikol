package ma.sanai3i;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Media uploads, price range, negotiation, artisan activation and distance matching. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.uploads-dir=target/test-uploads")
class MarketplaceRulesTest {

    @Autowired
    MockMvc mvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    /** A real (tiny) JPEG: uploads are decoded and re-encoded by the server. */
    static final byte[] JPEG = tinyJpeg();

    static byte[] tinyJpeg() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpeg", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String body(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String phone, String password) throws Exception {
        return "Bearer " + JsonPath.read(body(post("/api/auth/login").contentType(JSON)
                .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}")), "$.token");
    }

    private String register(String phone, String extra) throws Exception {
        String r = mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"secret12\",\"fullName\":\"Test User\",\"city\":\"Casablanca\"" + extra + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return "Bearer " + JsonPath.read(r, "$.token");
    }

    private String upload(String auth, String purpose, String mime, byte[] bytes) throws Exception {
        String r = mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", "f", mime, bytes))
                        .param("purpose", purpose).header("Authorization", auth))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(r, "$.id");
    }

    @Test
    void mediaPriceRangeAndNegotiation() throws Exception {
        mvc.perform(get("/api/public/pricing/paint")).andExpect(status().isOk())
                .andExpect(jsonPath("$.min").isNumber()).andExpect(jsonPath("$.max").isNumber());
        String client = register("0655001100", "");

        // uploads: a real JPEG is accepted, an HTML file disguised as an image is not
        String photo = upload(client, "REQUEST", "image/jpeg", JPEG);
        String voice = upload(client, "REQUEST", "audio/webm", new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3});
        mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", "x.jpg", "image/jpeg",
                        "<html><script>alert(1)</script>".getBytes())).param("purpose", "REQUEST").header("Authorization", client))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/media/" + photo)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));

        // the budget must be inside the price range of the trade
        mvc.perform(post("/api/requests").header("Authorization", client).contentType(JSON)
                        .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre une chambre\",\"budget\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICE_OUT_OF_RANGE"));
        String created = mvc.perform(post("/api/requests").header("Authorization", client).contentType(JSON)
                        .content("{\"categoryCode\":\"paint\",\"description\":\"Peindre une chambre\",\"budget\":1000,"
                                + "\"lat\":33.5899,\"lng\":-7.6039,\"mediaIds\":[\"" + photo + "\",\"" + voice + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].kind").value("IMAGE"))
                .andExpect(jsonPath("$.media[1].kind").value("AUDIO"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        int min = JsonPath.read(created, "$.priceMin");
        // a file can only be used once
        mvc.perform(post("/api/requests").header("Authorization", client).contentType(JSON)
                        .content("{\"categoryCode\":\"paint\",\"description\":\"Encore\",\"budget\":1000,\"mediaIds\":[\"" + photo + "\"]}"))
                .andExpect(status().isBadRequest());

        // negotiation: artisan 1400 -> client 1100 -> artisan 1250 -> client 1200 -> artisan agrees
        String hassan = login("0611000001", "demo1234");
        String offer = mvc.perform(post("/api/requests/" + id + "/offers").header("Authorization", hassan).contentType(JSON)
                .content("{\"price\":1400}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long offerId = ((Number) JsonPath.read(offer, "$.id")).longValue();
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/counter").header("Authorization", client)
                .contentType(JSON).content("{\"price\":" + Math.max(1, min - 1) + "}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/counter").header("Authorization", client)
                        .contentType(JSON).content("{\"price\":1100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.offers[0].lastBy").value("CLIENT"))
                .andExpect(jsonPath("$.offers[0].price").value(1100));
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/accept").header("Authorization", client))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WAITING_ARTISAN"));
        mvc.perform(post("/api/artisan/offers/" + offerId + "/counter").header("Authorization", hassan)
                        .contentType(JSON).content("{\"price\":1250}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.myOffer.lastBy").value("ARTISAN"))
                .andExpect(jsonPath("$.myOffer.firstPrice").value(1400));
        mvc.perform(post("/api/requests/" + id + "/offers/" + offerId + "/counter").header("Authorization", client)
                .contentType(JSON).content("{\"price\":1200}")).andExpect(status().isOk());
        mvc.perform(post("/api/artisan/offers/" + offerId + "/agree").header("Authorization", hassan))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.myOffer.status").value("ACCEPTED")).andExpect(jsonPath("$.clientPhone").isNotEmpty());
        mvc.perform(get("/api/requests/" + id).header("Authorization", client))
                .andExpect(jsonPath("$.offers[0].price").value(1200)).andExpect(jsonPath("$.offers[0].artisanPhone").isNotEmpty());
        mvc.perform(get("/api/notifications").header("Authorization", client))
                .andExpect(jsonPath("$.items[*].kind", hasItem("OFFER_AGREED")));
    }

    @Test
    void artisanMustBeApprovedAndNearby() throws Exception {
        String art = register("0677009900", ",\"role\":\"ARTISAN\",\"categoryCode\":\"plumb\"");
        mvc.perform(get("/api/me").header("Authorization", art)).andExpect(jsonPath("$.artisanStatus").value("DRAFT"));
        mvc.perform(get("/api/artisan/requests").header("Authorization", art))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ARTISAN_NOT_APPROVED"));
        mvc.perform(post("/api/artisan/me/onboarding/submit").header("Authorization", art))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ONBOARDING_INCOMPLETE"));
        mvc.perform(put("/api/artisan/me/onboarding").header("Authorization", art).contentType(JSON).content("{\"cin\":\"12AB\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CIN_INVALID"));

        String avatar = upload(art, "AVATAR", "image/jpeg", JPEG);
        String front = upload(art, "ID_FRONT", "image/jpeg", JPEG);
        String back = upload(art, "ID_BACK", "image/jpeg", JPEG);
        String w1 = upload(art, "PORTFOLIO", "image/jpeg", JPEG);
        String w2 = upload(art, "PORTFOLIO", "image/jpeg", JPEG);
        // based in Rabat, 10 km around: a Casablanca request is too far
        String file = "{\"fullName\":\"Karim Plombier\",\"cin\":\"be 123456\",\"yearsExperience\":9,"
                + "\"bio\":\"Plombier depuis 9 ans, salles de bain, fuites et chauffe-eau.\",\"city\":\"Rabat\","
                + "\"lat\":34.0209,\"lng\":-6.8416,\"radiusKm\":10,\"avatarId\":\"" + avatar + "\",\"idFrontId\":\"" + front
                + "\",\"idBackId\":\"" + back + "\",\"portfolioIds\":[\"" + w1 + "\",\"" + w2 + "\"]}";
        mvc.perform(put("/api/artisan/me/onboarding").header("Authorization", art).contentType(JSON).content(file))
                .andExpect(status().isOk()).andExpect(jsonPath("$.missing.length()").value(0))
                .andExpect(jsonPath("$.cin").value("BE123456"));
        mvc.perform(post("/api/artisan/me/onboarding/submit").header("Authorization", art))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

        // the ID card is private
        mvc.perform(get("/media/" + front)).andExpect(status().isNotFound());
        String other = register("0644990011", "");
        mvc.perform(get("/api/media/" + front).header("Authorization", other)).andExpect(status().isNotFound());
        String admin = login("0699000000", "admin12345");
        mvc.perform(get("/api/media/" + front).header("Authorization", admin)).andExpect(status().isOk());

        String list = body(get("/api/admin/artisan-files").header("Authorization", admin));
        List<Number> ids = JsonPath.read(list, "$[?(@.phone == '+212677009900')].artisanId");
        assertEquals(1, ids.size());
        long artisanId = ids.get(0).longValue();
        mvc.perform(get("/api/admin/artisan-files/" + artisanId).header("Authorization", admin))
                .andExpect(jsonPath("$.file.idFront.url").value("/api/media/" + front))
                .andExpect(jsonPath("$.file.portfolio.length()").value(2));
        mvc.perform(post("/api/admin/artisan-files/" + artisanId + "/reject").header("Authorization", admin)
                .contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/artisan-files/" + artisanId + "/reject").header("Authorization", admin)
                        .contentType(JSON).content("{\"reason\":\"Photo de la CIN floue\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.file.status").value("REJECTED"));
        String front2 = upload(art, "ID_FRONT", "image/jpeg", JPEG);
        mvc.perform(put("/api/artisan/me/onboarding").header("Authorization", art).contentType(JSON)
                .content("{\"idFrontId\":\"" + front2 + "\"}")).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(post("/api/artisan/me/onboarding/submit").header("Authorization", art)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/artisan-files/" + artisanId + "/approve").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.file.status").value("APPROVED"));

        // approved, but Casablanca is ~90 km from Rabat: not shown
        String client = register("0633887766", "");
        String req = body(post("/api/requests").header("Authorization", client).contentType(JSON)
                .content("{\"categoryCode\":\"plumb\",\"description\":\"Fuite sous l evier\",\"budget\":300,\"lat\":33.5899,\"lng\":-7.6039}"));
        long reqId = ((Number) JsonPath.read(req, "$.id")).longValue();
        String visible = body(get("/api/artisan/requests").header("Authorization", art));
        List<Object> hits = JsonPath.read(visible, "$[?(@.id == " + reqId + ")]");
        assertTrue(hits.isEmpty(), "too far: must not be listed");
        mvc.perform(post("/api/requests/" + reqId + "/offers").header("Authorization", art).contentType(JSON)
                .content("{\"price\":300}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOO_FAR"));
        // moves to Casablanca (Maarif), 15 km: shown, with the distance
        // approved: a new place waits for the team (it could be used to triangulate clients), then applies
        mvc.perform(put("/api/artisan/me/onboarding").header("Authorization", art).contentType(JSON)
                        .content("{\"lat\":33.5731,\"lng\":-7.5898,\"radiusKm\":15}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingChanges.lat").value(33.5731)).andExpect(jsonPath("$.lat").value(34.0209));
        String stillFar = body(get("/api/artisan/requests").header("Authorization", art));
        assertTrue(((List<Object>) JsonPath.read(stillFar, "$[?(@.id == " + reqId + ")]")).isEmpty());
        mvc.perform(post("/api/admin/artisan-files/" + artisanId + "/changes/approve").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.file.pendingChanges").doesNotExist());
        String near = body(get("/api/artisan/requests").header("Authorization", art));
        List<Number> dist = JsonPath.read(near, "$[?(@.id == " + reqId + ")].distanceKm");
        assertEquals(1, dist.size());
        assertTrue(dist.get(0).doubleValue() < 5);
        mvc.perform(get("/api/artisans").header("Authorization", login("0699000000", "admin12345")).param("q", "Karim Plombier"))
                .andExpect(jsonPath("$[*].nameFr", hasItem("Karim Plombier")));
        mvc.perform(get("/api/artisans").header("Authorization", login("0699000000", "admin12345"))).andExpect(jsonPath("$[*].nameFr", not(hasItem("Test User"))));
    }
}
