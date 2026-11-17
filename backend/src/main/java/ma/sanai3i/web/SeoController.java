package ma.sanai3i.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.sanai3i.common.Cities;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.ArtisanStatus;
import ma.sanai3i.domain.Category;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.service.PricingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Public pages for search engines (server-rendered HTML): one page per trade and city (no artisan names: clients
 * meet artisans only through offers), an index, sitemap.xml and robots.txt. The app itself (/app/) uses hash routes that Google does not index.
 */
@RestController
@Transactional(readOnly = true)
public class SeoController {
    private final ArtisanProfileRepository artisans;
    private final CategoryRepository categories;
    private final PricingService pricing;
    private final String base;
    private final ObjectMapper json = new ObjectMapper();

    public SeoController(ArtisanProfileRepository artisans, CategoryRepository categories, PricingService pricing,
                         @Value("${app.public-url:https://snai3i.elguennouni.site}") String base) {
        this.artisans = artisans;
        this.categories = categories;
        this.pricing = pricing;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    // ------------------------------------------------------------------ pages

    @GetMapping(value = "/artisans", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() {
        StringBuilder body = new StringBuilder("<h1>Artisans par métier et par ville <span class=ar>· الحرف حسب المدينة</span></h1>"
                + "<p class=lead>Peintres, menuisiers, mécaniciens, électriciens, plombiers… vérifiés par Brikol. "
                + "<span class=ar>صباغ، نجار، ميكانيسيان، كهربائي، بلومبي… موثقين من بريكول.</span></p><div class=grid>");
        for (Map.Entry<String, List<ArtisanProfile>> e : groups().entrySet()) {
            ArtisanProfile first = e.getValue().get(0);
            body.append("<a class=tile href=\"").append(e.getKey()).append("\"><b>").append(esc(first.category.nameFr))
                    .append(" à ").append(esc(first.city)).append("</b><span class=ar>").append(esc(first.category.nameAr))
                    .append(" · ").append(esc(first.city)).append("</span><small>").append(e.getValue().size())
                    .append(e.getValue().size() == 1 ? " artisan vérifié" : " artisans vérifiés").append("</small></a>");
        }
        body.append("</div>");
        return page("Artisans vérifiés au Maroc | Brikol", "Trouvez un artisan vérifié près de chez vous : peintre, menuisier, mécanicien, électricien, plombier.",
                base + "/artisans", body.toString(), null);
    }

    @GetMapping(value = "/artisans/{code}/{city}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tradeCity(@PathVariable String code, @PathVariable String city) {
        Category c = categories.findByCode(code).orElse(null);
        if (c == null || !city.matches("[a-zA-Z]{2,30}")) {
            return notFound();
        }
        String slug = city.toLowerCase(Locale.ROOT);
        Map<String, List<ArtisanProfile>> all = groups();
        List<ArtisanProfile> list = all.getOrDefault("/artisans/" + code + "/" + slug, List.of());
        String cityName = list.isEmpty() ? slug.substring(0, 1).toUpperCase(Locale.ROOT) + slug.substring(1) : list.get(0).city;
        String canonical = base + "/artisans/" + code + "/" + slug;
        String newUrl = "/app/#/new?cat=" + c.code;

        // No names here on purpose: clients meet artisans only through the offers they receive in the app.
        StringBuilder body = new StringBuilder();
        body.append("<p class=crumb><a href=\"/artisans\">Métiers</a> › ").append(esc(c.nameFr)).append("</p>")
                .append("<h1>").append(esc(c.nameFr)).append(" à ").append(esc(cityName))
                .append(" <span class=ar>· ").append(esc(c.nameAr)).append(" فـ").append(esc(cityName)).append("</span></h1>");
        if (list.isEmpty()) {
            body.append("<p class=lead>Les premiers artisans de ce métier arrivent bientôt dans votre ville. "
                    + "Publiez votre demande : les artisans proches la recevront dès leur inscription.</p>");
        } else {
            body.append("<p class=lead><b>").append(list.size()).append(list.size() == 1 ? " artisan vérifié" : " artisans vérifiés")
                    .append("</b> de ce métier travaillent à ").append(esc(cityName))
                    .append(". Décrivez votre besoin avec une photo ou un message vocal, ils vous envoient leurs prix. "
                            + "Gratuit, paiement en espèces, sans intermédiaire.</p>");
        }
        body.append("<a class=cta href=\"").append(newUrl).append("\">Publier ma demande · كتب طلبك</a>");

        PricingService.Range range = pricing.range(c);
        if (range.typicalLow() != null && range.typicalHigh() != null) {
            body.append("<div class=price><span>Prix habituel sur Brikol</span><b>").append(range.typicalLow()).append(" – ")
                    .append(range.typicalHigh()).append(" DH</b><small>Selon ").append(range.samples())
                    .append(" offres acceptées. Le prix final dépend du travail : vous comparez et négociez.</small></div>");
        }

        body.append("<h2>Comment ça marche <span class=ar>· كيفاش كتخدم</span></h2><ol class=steps>")
                .append("<li><b>Montrez le problème</b><span>Photos, vidéo ou message vocal, et le quartier.</span></li>")
                .append("<li><b>Recevez des offres</b><span>Seuls les artisans ").append(esc(c.nameFr.toLowerCase(Locale.ROOT)))
                .append(" proches de chez vous voient votre demande.</span></li>")
                .append("<li><b>Comparez et négociez</b><span>Note, distance et prix de chaque offre. Vous pouvez proposer un autre prix.</span></li>")
                .append("<li><b>Payez en espèces</b><span>Directement à l’artisan, une fois le travail fait. Puis laissez un avis.</span></li></ol>")
                .append("<h2>Des artisans vérifiés <span class=ar>· صنايعية موثّقين</span></h2>")
                .append("<p>Chaque artisan envoie sa pièce d’identité et des photos de ses réalisations. Notre équipe vérifie le dossier avant "
                        + "d’activer le compte. Votre adresse exacte et votre numéro ne sont visibles que par l’artisan dont vous acceptez l’offre.</p>");

        StringBuilder sameCity = new StringBuilder();
        StringBuilder sameTrade = new StringBuilder();
        for (Map.Entry<String, List<ArtisanProfile>> e : all.entrySet()) {
            ArtisanProfile f = e.getValue().get(0);
            if (e.getKey().equals("/artisans/" + code + "/" + slug)) {
                continue;
            }
            if (citySlug(f.city).equals(slug)) {
                sameCity.append("<a class=tile href=\"").append(e.getKey()).append("\">").append(esc(f.category.nameFr))
                        .append("<span class=ar>").append(esc(f.category.nameAr)).append("</span></a>");
            } else if (f.category.code.equals(code)) {
                sameTrade.append("<a class=tile href=\"").append(e.getKey()).append("\">").append(esc(c.nameFr)).append(" à ")
                        .append(esc(f.city)).append("</a>");
            }
        }
        if (sameCity.length() > 0) {
            body.append("<h2>Autres métiers à ").append(esc(cityName)).append("</h2><div class=grid>").append(sameCity).append("</div>");
        }
        if (sameTrade.length() > 0) {
            body.append("<h2>").append(esc(c.nameFr)).append(" dans d’autres villes</h2><div class=grid>").append(sameTrade).append("</div>");
        }

        String title = c.nameFr + " à " + cityName + " : artisans vérifiés, devis en quelques minutes | Brikol";
        String desc = "Besoin d’un(e) " + c.nameFr.toLowerCase(Locale.ROOT) + " à " + cityName
                + " ? Publiez votre demande sur Brikol, recevez les offres d’artisans vérifiés proches de chez vous, négociez et payez en espèces.";
        if (list.isEmpty()) {
            // a valid trade without artisans there yet: useful for visitors, not indexed
            return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                    .body(shell(title, desc, canonical, body.toString(), null).replace("<head>", "<head><meta name=robots content=noindex>"));
        }
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Service");
        ld.put("serviceType", c.nameFr);
        ld.put("name", c.nameFr + " à " + cityName);
        ld.put("url", canonical);
        ld.put("areaServed", Map.of("@type", "City", "name", cityName));
        ld.put("provider", Map.of("@type", "Organization", "name", "Brikol", "url", base + "/"));
        if (range.typicalLow() != null && range.typicalHigh() != null) {
            ld.put("offers", Map.of("@type", "AggregateOffer", "priceCurrency", "MAD", "lowPrice", range.typicalLow(), "highPrice", range.typicalHigh()));
        }
        return page(title, desc, canonical, body.toString(), ld);
    }

    /** Old artisan pages: send visitors and search engines to the trade + city page. */
    @GetMapping(value = {"/pro/{id}", "/pro/{id}/{slug}"})
    public ResponseEntity<Void> profile(@PathVariable Long id) {
        ArtisanProfile a = artisans.findById(id).filter(x -> x.status == ArtisanStatus.APPROVED).orElse(null);
        String to = a == null || a.city == null || citySlug(a.city).isEmpty() ? "/artisans" : "/artisans/" + a.category.code + "/" + citySlug(a.city);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(java.net.URI.create(to)).build();
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        x.append(url(base + "/")).append(url(base + "/artisans"));
        for (Map.Entry<String, List<ArtisanProfile>> e : groups().entrySet()) {
            x.append(url(base + e.getKey()));
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

    private static String url(String loc) {
        return "  <url><loc>" + esc(loc) + "</loc></url>\n";
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
                        "<h1>Page introuvable</h1><p><a href=\"/artisans\">Voir les métiers</a></p>", null));
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
                + "<footer>© Brikol · Paiement en espèces, sans intermédiaire · <a href=\"/artisans\">Tous les métiers</a></footer></body></html>";
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
            + ".price{display:flex;flex-direction:column;gap:2px;margin:18px 0 4px;padding:14px 16px;border-radius:16px;background:#EEF8C8}.price b{font-size:22px}.price small{color:#3E5A4D}"
            + ".steps{padding:0;margin:0;list-style:none;counter-reset:s;display:grid;gap:10px}.steps li{counter-increment:s;display:flex;flex-direction:column;padding:12px 14px 12px 52px;position:relative;background:#fff;border:1px solid #E4E8E1;border-radius:16px}"
            + ".steps li::before{content:counter(s);position:absolute;left:14px;top:12px;width:26px;height:26px;border-radius:13px;background:#CDEE3B;display:grid;place-items:center;font-weight:800;font-size:13px}.steps span{color:#3E5A4D;font-size:14px}"
            + "footer{text-align:center;color:#5B6B63;font-size:13px;padding:24px 16px}footer a{color:#0F6B3C}";
}
