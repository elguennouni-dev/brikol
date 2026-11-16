package ma.sanai3i.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.sanai3i.common.Cities;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.ArtisanStatus;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.Media;
import ma.sanai3i.domain.Review;
import ma.sanai3i.domain.ServiceItem;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.MediaRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.service.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Public pages for search engines (server-rendered HTML): one page per active artisan, one per trade and city,
 * an index, sitemap.xml and robots.txt. The app itself (/app/) uses hash routes that Google does not index.
 */
@RestController
@Transactional(readOnly = true)
public class SeoController {
    private final ArtisanProfileRepository artisans;
    private final CategoryRepository categories;
    private final ReviewRepository reviews;
    private final MediaRepository media;
    private final Mapper mapper;
    private final String base;
    private final ObjectMapper json = new ObjectMapper();

    public SeoController(ArtisanProfileRepository artisans, CategoryRepository categories, ReviewRepository reviews,
                         MediaRepository media, Mapper mapper,
                         @Value("${app.public-url:https://snai3i.elguennouni.site}") String base) {
        this.artisans = artisans;
        this.categories = categories;
        this.reviews = reviews;
        this.media = media;
        this.mapper = mapper;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    // ------------------------------------------------------------------ pages

    @GetMapping(value = "/artisans", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        StringBuilder body = new StringBuilder("<h1>Artisans au Maroc <span class=ar>· الحرفيين فالمغرب</span></h1>"
                + "<p class=lead>Peintres, menuisiers, mécaniciens, électriciens, plombiers… vérifiés par Brikol. "
                + "<span class=ar>صباغ، نجار، ميكانيسيان، كهربائي، بلومبي… موثقين من بريكول.</span></p><div class=grid>");
        for (Map.Entry<String, List<ArtisanProfile>> e : groups().entrySet()) {
            ArtisanProfile first = e.getValue().get(0);
            body.append("<a class=tile href=\"").append(e.getKey()).append("\"><b>").append(esc(first.category.nameFr))
                    .append(" à ").append(esc(first.city)).append("</b><span class=ar>").append(esc(first.category.nameAr))
                    .append(" · ").append(esc(first.city)).append("</span><small>").append(e.getValue().size())
                    .append(" artisan(s)</small></a>");
        }
        body.append("</div>");
        return page("Artisans vérifiés au Maroc | Brikol", "Trouvez un artisan vérifié près de chez vous : peintre, menuisier, mécanicien, électricien, plombier.",
                base + "/artisans", body.toString(), null);
    }

    @GetMapping(value = "/artisans/{code}/{city}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tradeCity(@PathVariable String code, @PathVariable String city) {
        Category c = categories.findByCode(code).orElse(null);
        List<ArtisanProfile> list = c == null ? List.of() : groups().getOrDefault("/artisans/" + code + "/" + city.toLowerCase(Locale.ROOT), List.of());
        if (c == null || !city.matches("[a-zA-Z]{2,30}")) {
            return notFound();
        }
        if (list.isEmpty()) {
            // a valid trade without artisans there yet: useful page for visitors, not indexed
            String name = city.substring(0, 1).toUpperCase(Locale.ROOT) + city.substring(1).toLowerCase(Locale.ROOT);
            String body = "<p class=crumb><a href=\"/artisans\">Artisans</a> › " + esc(c.nameFr) + "</p><h1>" + esc(c.nameFr) + " à " + esc(name)
                    + " <span class=ar>· " + esc(c.nameAr) + "</span></h1><p class=lead>Les premiers artisans de ce métier arrivent bientôt dans votre ville. "
                    + "Publiez votre demande : les artisans proches la recevront dès leur inscription.</p>"
                    + "<a class=cta href=\"/app/#/new\">Publier ma demande · كتب طلبك</a>";
            return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                    .body(shell(c.nameFr + " à " + name + " | Brikol", "Trouvez un " + c.nameFr.toLowerCase(Locale.ROOT) + " à " + name + " sur Brikol.",
                            base + "/artisans/" + code + "/" + city.toLowerCase(Locale.ROOT), body, null).replace("<head>", "<head><meta name=robots content=noindex>"));
        }
        Map<Long, Mapper.Rating> ratings = mapper.allRatings();
        String cityName = list.get(0).city;
        StringBuilder body = new StringBuilder();
        body.append("<p class=crumb><a href=\"/artisans\">Artisans</a> › ").append(esc(c.nameFr)).append("</p>")
                .append("<h1>").append(esc(c.nameFr)).append(" à ").append(esc(cityName))
                .append(" <span class=ar>· ").append(esc(c.nameAr)).append(" فـ").append(esc(cityName)).append("</span></h1>")
                .append("<p class=lead>").append(list.size()).append(" artisan(s) vérifié(s). Publiez votre demande gratuitement et recevez des offres. Paiement en espèces, sans intermédiaire.</p>")
                .append("<a class=cta href=\"/app/#/new\">Publier ma demande · كتب طلبك</a><div class=list>");
        for (ArtisanProfile a : list) {
            Mapper.Rating r = ratings.getOrDefault(a.id, Mapper.NO_RATING);
            body.append("<a class=card href=\"").append(profileUrl(a)).append("\">")
                    .append(a.avatarId == null ? "<span class=av>" + esc(initial(a.nameFr)) + "</span>" : "<img class=av src=\"/media/" + a.avatarId + "\" alt=\"\" loading=lazy>")
                    .append("<span class=info><b>").append(esc(a.nameFr)).append(a.verified ? " ✓" : "").append("</b><small>")
                    .append(esc(a.tradeFr)).append(" · ").append(esc(nz(a.neighborhoodFr, a.city))).append(" · ")
                    .append(a.yearsExperience).append(" ans d’expérience</small>")
                    .append(r.count() > 0 ? "<small>★ " + r.average() + " (" + r.count() + " avis)</small>" : "")
                    .append("</span></a>");
        }
        body.append("</div>");
        String title = c.nameFr + " à " + cityName + " : " + list.size() + " artisans vérifiés | Brikol";
        String desc = "Trouvez un(e) " + c.nameFr.toLowerCase(Locale.ROOT) + " à " + cityName + " sur Brikol : profils vérifiés, avis clients, offres en quelques minutes.";
        return page(title, desc, base + "/artisans/" + code + "/" + city.toLowerCase(Locale.ROOT), body.toString(), null);
    }

    @GetMapping(value = {"/pro/{id}", "/pro/{id}/{slug}"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> profile(@PathVariable Long id) {
        ArtisanProfile a = artisans.findById(id).filter(x -> x.status == ArtisanStatus.APPROVED).orElse(null);
        if (a == null) {
            return notFound();
        }
        Mapper.Rating r = mapper.ratingOf(a.id);
        List<Review> rv = reviews.findByArtisanIdOrderByCreatedAtDesc(a.id);
        List<Media> works = media.findByArtisanIdAndPurposeOrderByCreatedAtAsc(a.id, Media.PORTFOLIO);
        String listUrl = "/artisans/" + a.category.code + "/" + citySlug(a.city);
        StringBuilder body = new StringBuilder();
        body.append("<p class=crumb><a href=\"/artisans\">Artisans</a> › <a href=\"").append(listUrl).append("\">")
                .append(esc(a.category.nameFr)).append(" à ").append(esc(a.city)).append("</a></p>")
                .append("<div class=head>")
                .append(a.avatarId == null ? "<span class=\"av big\">" + esc(initial(a.nameFr)) + "</span>" : "<img class=\"av big\" src=\"/media/" + a.avatarId + "\" alt=\"" + esc(a.nameFr) + "\">")
                .append("<div><h1>").append(esc(a.nameFr)).append(a.verified ? " <span class=ok>✓ vérifié</span>" : "").append("</h1>")
                .append("<p>").append(esc(a.tradeFr)).append(" · ").append(esc(nz(a.neighborhoodFr, ""))).append(" ").append(esc(a.city))
                .append(" <span class=ar>· ").append(esc(a.tradeAr)).append("</span></p>")
                .append("<p>").append(a.yearsExperience).append(" ans d’expérience · ").append(a.jobsDone).append(" travaux réalisés")
                .append(r.count() > 0 ? " · ★ " + r.average() + " (" + r.count() + " avis)" : "").append("</p></div></div>")
                .append("<a class=cta href=\"/app/#/artisan/").append(a.id).append("\">Contacter dans l’application · تواصل معاه</a>");
        if (a.bioFr != null && !a.bioFr.isBlank()) {
            body.append("<h2>Présentation</h2><p dir=auto>").append(esc(a.bioFr)).append("</p>");
        }
        if (!a.services.isEmpty()) {
            body.append("<h2>Services et prix</h2><ul class=services>");
            for (ServiceItem s : a.services) {
                body.append("<li><span>").append(esc(s.labelFr)).append("</span><b>dès ").append(s.price).append(" DH").append(esc(nz(s.unitFr, ""))).append("</b></li>");
            }
            body.append("</ul>");
        }
        if (!works.isEmpty()) {
            body.append("<h2>Réalisations</h2><div class=works>");
            for (Media m : works) {
                body.append("<img src=\"/media/").append(m.id).append("\" alt=\"Réalisation de ").append(esc(a.nameFr)).append("\" loading=lazy>");
            }
            body.append("</div>");
        }
        if (!rv.isEmpty()) {
            body.append("<h2>Avis des clients</h2>");
            for (Review x : rv.subList(0, Math.min(10, rv.size()))) {
                body.append("<div class=review><b>").append("★".repeat(Math.max(0, x.rating))).append("</b> ")
                        .append(esc(Mapper.shortName(x.client.fullName))).append(x.body == null ? "" : "<p dir=auto>" + esc(x.body) + "</p>").append("</div>");
            }
        }
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "HomeAndConstructionBusiness");
        ld.put("name", a.nameFr);
        ld.put("description", a.tradeFr + " à " + a.city);
        ld.put("url", base + profileUrl(a));
        if (a.avatarId != null) {
            ld.put("image", base + "/media/" + a.avatarId);
        }
        ld.put("address", Map.of("@type", "PostalAddress", "addressLocality", a.city, "addressCountry", "MA"));
        if (a.lat != null && a.lng != null) {
            ld.put("geo", Map.of("@type", "GeoCoordinates", "latitude", Math.round(a.lat * 100) / 100.0, "longitude", Math.round(a.lng * 100) / 100.0));
        }
        if (r.count() > 0) {
            ld.put("aggregateRating", Map.of("@type", "AggregateRating", "ratingValue", r.average(), "reviewCount", r.count()));
        }
        String title = a.nameFr + " – " + a.tradeFr + " à " + a.city + " | Brikol";
        String desc = a.tradeFr + " à " + a.city + ", " + a.yearsExperience + " ans d’expérience"
                + (r.count() > 0 ? ", note " + r.average() + "/5 (" + r.count() + " avis)" : "") + ". Contactez-le sur Brikol.";
        return page(title, desc, base + profileUrl(a), body.toString(), ld);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        x.append(url(base + "/")).append(url(base + "/artisans"));
        for (Map.Entry<String, List<ArtisanProfile>> e : groups().entrySet()) {
            x.append(url(base + e.getKey()));
            for (ArtisanProfile a : e.getValue()) {
                x.append(url(base + profileUrl(a)));
            }
        }
        x.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).body(x.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: " + base + "/sitemap.xml\n";
    }

    // ------------------------------------------------------------------ helpers

    /** "/artisans/{code}/{city}" -> active artisans, in a stable order. */
    private Map<String, List<ArtisanProfile>> groups() {
        Map<String, List<ArtisanProfile>> out = new LinkedHashMap<>();
        for (ArtisanProfile a : artisans.findAllByOrderByVerifiedDescIdAsc()) {
            if (a.status != ArtisanStatus.APPROVED || a.city == null || citySlug(a.city).isEmpty()) {
                continue;
            }
            out.computeIfAbsent("/artisans/" + a.category.code + "/" + citySlug(a.city), k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    private static String citySlug(String city) {
        return Cities.key(city).replaceAll("[^a-z]", "");
    }

    private static String profileUrl(ArtisanProfile a) {
        String slug = Normalizer.normalize(a.nameFr + "-" + a.tradeFr + "-" + a.city, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "/pro/" + a.id + (slug.isEmpty() ? "" : "/" + slug);
    }

    private static String url(String loc) {
        return "  <url><loc>" + esc(loc) + "</loc></url>\n";
    }

    private static String initial(String name) {
        return name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(ch);
            }
        }
        return b.toString();
    }

    private ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                .body(shell("Page introuvable | Brikol", "", base + "/artisans",
                        "<h1>Page introuvable</h1><p><a href=\"/artisans\">Voir les artisans</a></p>", null));
    }

    private ResponseEntity<String> page(String title, String desc, String canonical, String body, Map<String, Object> ld) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic())
                .body(shell(title, desc, canonical, body, ld));
    }

    private String shell(String title, String desc, String canonical, String body, Map<String, Object> ld) {
        String jsonLd = "";
        if (ld != null) {
            try {
                jsonLd = "<script type=\"application/ld+json\">" + json.writeValueAsString(ld).replace("</", "<\\/") + "</script>";
            } catch (JsonProcessingException e) {
                jsonLd = "";
            }
        }
        return "<!doctype html><html lang=fr><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<title>" + esc(title) + "</title><meta name=description content=\"" + esc(desc) + "\">"
                + "<link rel=canonical href=\"" + esc(canonical) + "\"><link rel=icon href=\"/app/brand/favicon.png\">"
                + "<meta property=og:title content=\"" + esc(title) + "\"><meta property=og:description content=\"" + esc(desc) + "\">"
                + "<meta property=og:type content=website><meta property=og:url content=\"" + esc(canonical) + "\">"
                + "<meta property=og:image content=\"" + base + "/img/brikol-logo.png\">" + jsonLd
                + "<style>" + CSS + "</style></head><body><header><a href=\"/\"><img src=\"/img/brikol-logo.png\" alt=\"Brikol\" height=40></a>"
                + "<a class=open href=\"/app/\">Ouvrir l’app</a></header><main>" + body + "</main>"
                + "<footer>© Brikol · Paiement en espèces, sans intermédiaire · <a href=\"/artisans\">Tous les artisans</a></footer></body></html>";
    }

    private static final String CSS = "body{margin:0;font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;background:#F4F6F2;color:#0E2B22;line-height:1.5}"
            + "header{display:flex;justify-content:space-between;align-items:center;padding:12px max(16px,calc(50% - 440px));background:#fff;border-bottom:1px solid #E4E8E1}"
            + "header img{display:block}.open{font-weight:700;color:#0F6B3C;text-decoration:none}"
            + "main{max-width:880px;margin:0 auto;padding:20px 16px 40px}h1{font-size:28px;margin:8px 0;text-wrap:balance}h2{font-size:19px;margin:28px 0 8px}"
            + ".ar{font-family:Tajawal,system-ui,sans-serif;color:#3E6B52;font-weight:600}.lead{color:#3E5A4D}.crumb{font-size:13px;color:#5B6B63}.crumb a{color:#0F6B3C}"
            + ".cta{display:inline-block;margin:12px 0;padding:12px 22px;border-radius:24px;background:#CDEE3B;color:#0E2B22;font-weight:800;text-decoration:none}"
            + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:10px}.tile{display:flex;flex-direction:column;gap:2px;padding:14px 16px;border-radius:16px;background:#fff;border:1px solid #E4E8E1;color:inherit;text-decoration:none}"
            + ".tile small,.card small{color:#5B6B63}.list{display:flex;flex-direction:column;gap:10px}.card{display:flex;gap:12px;align-items:center;padding:12px 14px;border-radius:16px;background:#fff;border:1px solid #E4E8E1;color:inherit;text-decoration:none}"
            + ".info{display:flex;flex-direction:column}.av{width:52px;height:52px;border-radius:26px;background:#D6F0DF;color:#0E5A36;display:grid;place-items:center;font-weight:800;object-fit:cover;flex:none}"
            + ".av.big{width:84px;height:84px;border-radius:42px;font-size:28px}.head{display:flex;gap:16px;align-items:center}.head p{margin:2px 0;color:#3E5A4D}.ok{font-size:14px;color:#17A05D}"
            + ".services{list-style:none;padding:0;margin:0;background:#fff;border-radius:16px;border:1px solid #E4E8E1}.services li{display:flex;justify-content:space-between;gap:12px;padding:10px 14px;border-top:1px solid #E4E8E1}.services li:first-child{border-top:none}"
            + ".works{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px}.works img{width:100%;aspect-ratio:1;object-fit:cover;border-radius:12px}"
            + ".review{background:#fff;border:1px solid #E4E8E1;border-radius:14px;padding:10px 14px;margin:8px 0}.review b{color:#F2A413}.review p{margin:4px 0 0}"
            + "footer{text-align:center;color:#5B6B63;font-size:13px;padding:24px 16px}footer a{color:#0F6B3C}";
}
