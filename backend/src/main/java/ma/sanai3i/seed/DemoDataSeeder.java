package ma.sanai3i.seed;

import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.domain.Offer;
import ma.sanai3i.domain.OfferStatus;
import ma.sanai3i.domain.RequestStatus;
import ma.sanai3i.domain.Review;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.ServiceItem;
import ma.sanai3i.domain.User;
import ma.sanai3i.domain.WhenPref;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.CategoryRepository;
import ma.sanai3i.repo.JobRequestRepository;
import ma.sanai3i.repo.OfferRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.repo.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo data for development (app.seed=true, on by default in the "dev" profile). Does nothing when users exist.
 * Demo accounts get app.seed-password (set to "demo1234" in the dev profile only). Without it they get a random password
 * nobody knows, so demo data can be shown on a public server without opening known logins.
 *   client  Amine Hasnaoui   0600000001
 *   artisan Hassan (painter) 0611000001, Omar 0611000002, Anas 0611000003 (painters), others up to 0611000009
 */
@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
@org.springframework.core.annotation.Order(1)
public class DemoDataSeeder implements ApplicationRunner {
    private final String password;

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ArtisanProfileRepository artisans;
    private final JobRequestRepository requests;
    private final OfferRepository offers;
    private final ReviewRepository reviews;
    private final PasswordEncoder encoder;

    private String hash;
    private final Map<String, Category> cats = new HashMap<>();

    public DemoDataSeeder(UserRepository users, CategoryRepository categories, ArtisanProfileRepository artisans,
                          JobRequestRepository requests, OfferRepository offers, ReviewRepository reviews,
                          PasswordEncoder encoder, @org.springframework.beans.factory.annotation.Value("${app.seed-password:}") String password) {
        this.password = password == null || password.isBlank() ? DemoPasswordGuard.randomPassword() : password;
        this.users = users;
        this.categories = categories;
        this.artisans = artisans;
        this.requests = requests;
        this.offers = offers;
        this.reviews = reviews;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // only an empty database (the first admin, created at start-up, does not count)
        if (users.count() > users.countByRole(ma.sanai3i.domain.Role.ADMIN)) {
            return;
        }
        hash = encoder.encode(password);
        for (Category c : categories.findAll()) {
            cats.put(c.code, c);
        }

        User amine = client("+212600000001", "Amine Hasnaoui");
        User fatima = client("+212600000002", "Fatima Zahra Bennani");
        User mourad = client("+212600000003", "Mourad Kabbaj");

        ArtisanProfile hassan = artisan("+212611000001", "paint", "حسن الإدريسي", "Hassan El Idrissi", "صباغ", "Peintre",
                "المعاريف", "Maârif", 12, 340, true, true,
                "صباغ محترف فالدار البيضاء. كنخدم الشقق والفيلات والمحلات، بخدمة نقية وفالوقت لي تافقنا عليه.",
                "Peintre professionnel à Casablanca. Appartements, villas et commerces, travail soigné et délais respectés.",
                new Object[][]{
                        {"صباغة داخلية", "Peinture intérieure", 35, "/م²", "/m²"},
                        {"واجهة خارجية", "Façade extérieure", 55, "/م²", "/m²"},
                        {"الجبس والتشطيب", "Enduit et finition", 45, "/م²", "/m²"},
                        {"التدلاكت", "Tadelakt", 250, "/م²", "/m²"}});
        ArtisanProfile omar = artisan("+212611000002", "paint", "عمر الشرايبي", "Omar Chraibi", "صباغ", "Peintre",
                "عين الشق", "Aïn Chock", 7, 120, true, true,
                "صباغ للشقق والمحلات، أثمنة واضحة ومعاينة مجانية.",
                "Peintre pour appartements et commerces, prix clairs et visite gratuite.",
                new Object[][]{
                        {"صباغة داخلية", "Peinture intérieure", 30, "/م²", "/m²"},
                        {"إصلاح الحيطان", "Réparation des murs", 40, "/م²", "/m²"}});
        ArtisanProfile anas = artisan("+212611000003", "paint", "أنس الفاسي", "Anas Fassi", "صباغ", "Peintre",
                "بوركون", "Bourgogne", 5, 70, false, false,
                "صباغ شاب كيخدم بسرعة ونقاوة.",
                "Jeune peintre, travail rapide et propre.",
                new Object[][]{
                        {"صباغة داخلية", "Peinture intérieure", 32, "/م²", "/m²"}});
        ArtisanProfile karim = artisan("+212611000004", "wood", "كريم بناني", "Karim Bennani", "نجار", "Menuisier",
                "الحي الحسني", "Hay Hassani", 9, 230, true, true,
                "نجار كيخدم الخشب على المقاس: دواليب، أبواب، كوزينة. كنجي للمعاينة وكنعطيك الثمن قبل ما نبدا.",
                "Menuisier sur mesure : placards, portes, cuisines. Je me déplace pour le devis et confirme le prix avant de commencer.",
                new Object[][]{
                        {"دواليب على المقاس", "Placards sur mesure", 2500, "/م", "/ml"},
                        {"أبواب وشبابيك", "Portes et fenêtres", 900, "", ""},
                        {"إصلاح الأثاث", "Réparation de meubles", 150, "", ""},
                        {"كوزينة بالخشب", "Cuisine en bois", 8000, "", ""}});
        ArtisanProfile youssef = artisan("+212611000005", "mech", "يوسف العمراني", "Youssef Amrani", "ميكانيسيان", "Mécanicien auto",
                "عين السبع", "Aïn Sebaâ", 11, 520, true, false,
                "ميكانيسيان فالسيارات. كنصلح فالورشة وكنجي ديبانّاج حتى لدارك فالدار البيضاء.",
                "Mécanicien automobile. Réparations à l’atelier et dépannage à domicile sur Casablanca.",
                new Object[][]{
                        {"تغيار الزيت", "Vidange", 150, "", ""},
                        {"الفرامل", "Freins", 250, "", ""},
                        {"فحص إلكتروني", "Diagnostic électronique", 100, "", ""},
                        {"ديبانّاج فالدار", "Dépannage à domicile", 200, "", ""}});
        ArtisanProfile rachid = artisan("+212611000006", "metal", "رشيد الوزاني", "Rachid Ouazzani", "حداد", "Ferronnier",
                "سيدي مومن", "Sidi Moumen", 15, 210, true, true,
                "حداد كيخدم الأبواب والشبابيك والحواجز بالحديد. لحام ودهن وتركيب فعين المكان.",
                "Ferronnier : portes, fenêtres, garde-corps. Soudure, peinture et pose sur place.",
                new Object[][]{
                        {"أبواب الحديد", "Portes en fer", 1200, "", ""},
                        {"حواجز ودرابزين", "Garde-corps et rampes", 350, "/م", "/ml"},
                        {"لحام فعين المكان", "Soudure sur place", 150, "", ""}});
        ArtisanProfile said = artisan("+212611000007", "elec", "سعيد التازي", "Said Tazi", "كهربائي", "Électricien",
                "كوتييه", "Gauthier", 8, 410, true, true,
                "كهربائي مختص فالشقق والفيلات. تركيب، إصلاح العطب، وتجديد الضو.",
                "Électricien pour appartements et villas. Installation, dépannage et mise aux normes.",
                new Object[][]{
                        {"البحث عن عطب", "Recherche de panne", 100, "", ""},
                        {"بريزات ومفاتيح", "Prises et interrupteurs", 30, "/نقطة", "/point"},
                        {"إضاءة LED", "Éclairage LED", 60, "/نقطة", "/point"},
                        {"لوحة الكهرباء", "Tableau électrique", 600, "", ""}});
        ArtisanProfile mohamed = artisan("+212611000008", "plumb", "محمد برادة", "Mohamed Berrada", "بلومبي", "Plombier",
                "الواحة", "Oasis", 10, 260, false, false,
                "بلومبي كيصلح التسريب وكيركب السخانات والحمامات. كنجي فنفس النهار فالغالب.",
                "Plombier : fuites, chauffe-eau, salles de bain. Intervention le jour même dans la plupart des cas.",
                new Object[][]{
                        {"تسريب الما", "Fuite d’eau", 150, "", ""},
                        {"سخان الما", "Chauffe-eau", 250, "", ""},
                        {"تسدّاد البيبة", "Débouchage", 120, "", ""},
                        {"حمّام كامل", "Salle de bain complète", 2500, "", ""}});
        ArtisanProfile brahim = artisan("+212611000009", "tile", "براهيم العلوي", "Brahim Alaoui", "معلم الزليج", "Maâlem zelliji",
                "برنوصي", "Bernoussi", 18, 190, true, true,
                "معلم زليج بخبرة 18 عام. الزليج المغربي التقليدي والكارو العصري.",
                "Maâlem zelliji avec 18 ans d’expérience. Zellige traditionnel et carrelage moderne.",
                new Object[][]{
                        {"زليج مغربي", "Pose de zellige", 250, "/م²", "/m²"},
                        {"كارو (كاريلاج)", "Carrelage", 70, "/م²", "/m²"},
                        {"الفاصل والتشطيب", "Joints et finitions", 30, "/م²", "/m²"}});

        // Reviews (mostly 5 stars, a few 4)
        ArtisanProfile[] all = {hassan, omar, anas, karim, youssef, rachid, said, mohamed, brahim};
        String[] bodyAr = {"خدمة نقية وفالوقت. جا فالموعد وخلا الدار نقية.", "ثمن معقول وشرح ليا كلشي قبل ما يبدا.", "خدمة مزيانة، غنعاود نتعامل معاه."};
        String[] bodyFr = {"Travail soigné et dans les temps.", "Prix correct, tout m’a été expliqué avant de commencer.", "Très bon travail, je referai appel à lui."};
        User[] clients = {fatima, mourad, amine};
        for (int i = 0; i < all.length; i++) {
            for (int j = 0; j < 2 + (i % 2); j++) {
                int rating = ((i + j) % 4 == 3) ? 4 : 5;
                review(all[i], clients[(i + j) % 3], null, rating, (i % 2 == 0) ? bodyFr[j] : bodyAr[j]);
            }
        }

        // 1) Open request with three offers (the "Mes demandes" screen of the design)
        JobRequest paint = request(amine, "paint", "صباغة صالون وجوج بيوت، تقريبا 90 متر مربع. Peinture salon et 2 chambres, environ 90 m².",
                "Maârif", WhenPref.WEEK, 3000, RequestStatus.OPEN);
        offer(paint, hassan, 2800, "TOMORROW", OfferStatus.PENDING);
        offer(paint, omar, 2500, "WEEK", OfferStatus.PENDING);
        offer(paint, anas, 3100, "TODAY", OfferStatus.PENDING);

        // 2) Request already accepted
        JobRequest leak = request(amine, "plumb", "الما كيسرب فالمطبخ. Fuite d’eau dans la cuisine.",
                "Maârif", WhenPref.NOW, 300, RequestStatus.ACCEPTED);
        Offer accepted = offer(leak, mohamed, 250, "TODAY", OfferStatus.ACCEPTED);
        leak.acceptedOfferId = accepted.id;

        // 3) Finished and reviewed job
        JobRequest door = request(amine, "metal", "باب الحديد ديال الدار. Porte d’entrée en fer.",
                "Maârif", WhenPref.FLEXIBLE, 2200, RequestStatus.DONE);
        Offer doneOffer = offer(door, rachid, 2000, "WEEK", OfferStatus.ACCEPTED);
        door.acceptedOfferId = doneOffer.id;
        review(rachid, amine, door.id, 5, "Porte solide et bien finie, livrée à la date prévue.");
    }

    private User client(String phone, String fullName) {
        User u = new User();
        u.phone = phone;
        u.passwordHash = hash;
        u.fullName = fullName;
        u.role = Role.CLIENT;
        u.city = "Casablanca";
        return users.save(u);
    }

    private ArtisanProfile artisan(String phone, String category, String nameAr, String nameFr,
                                   String tradeAr, String tradeFr, String hoodAr, String hoodFr,
                                   int years, int jobs, boolean verified, boolean availableToday,
                                   String bioAr, String bioFr, Object[][] services) {
        User u = new User();
        u.phone = phone;
        u.passwordHash = hash;
        u.fullName = nameFr;
        u.role = Role.ARTISAN;
        u.city = "Casablanca";
        users.save(u);

        ArtisanProfile a = new ArtisanProfile();
        a.user = u;
        a.category = cats.get(category);
        a.nameAr = nameAr;
        a.nameFr = nameFr;
        a.tradeAr = tradeAr;
        a.tradeFr = tradeFr;
        a.neighborhoodAr = hoodAr;
        a.neighborhoodFr = hoodFr;
        a.city = "Casablanca";
        a.bioAr = bioAr;
        a.bioFr = bioFr;
        a.yearsExperience = years;
        a.jobsDone = jobs;
        a.verified = verified;
        a.availableToday = availableToday;
        a.status = ma.sanai3i.domain.ArtisanStatus.APPROVED;
        a.whatsapp = phone;
        for (Object[] s : services) {
            ServiceItem item = new ServiceItem();
            item.artisan = a;
            item.labelAr = (String) s[0];
            item.labelFr = (String) s[1];
            item.price = (Integer) s[2];
            item.unitAr = (String) s[3];
            item.unitFr = (String) s[4];
            a.services.add(item);
        }
        return artisans.save(a);
    }

    private JobRequest request(User client, String category, String description, String hood,
                               WhenPref when, int budget, RequestStatus status) {
        JobRequest r = new JobRequest();
        r.client = client;
        r.category = cats.get(category);
        r.description = description;
        r.city = "Casablanca";
        r.neighborhood = hood;
        r.whenPref = when;
        r.budget = budget;
        r.status = status;
        return requests.save(r);
    }

    private Offer offer(JobRequest r, ArtisanProfile a, int price, String eta, OfferStatus status) {
        Offer o = new Offer();
        o.request = r;
        o.artisan = a;
        o.price = price;
        o.etaText = eta;
        o.status = status;
        return offers.save(o);
    }

    private void review(ArtisanProfile a, User client, Long requestId, int rating, String body) {
        Review rv = new Review();
        rv.artisan = a;
        rv.client = client;
        rv.requestId = requestId;
        rv.rating = rating;
        rv.body = body;
        reviews.save(rv);
    }
}
