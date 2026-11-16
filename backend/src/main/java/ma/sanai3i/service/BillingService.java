package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.ArtisanStatus;
import ma.sanai3i.domain.Payment;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.User;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.PaymentRepository;
import ma.sanai3i.repo.UserRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Artisan monthly subscription + "featured" placement. Money is paid outside the app (CashPlus, transfer...) and an
 * admin records it. Off by default (monetizationEnabled): then everything is free. When on, a new artisan gets
 * trialDays free from his first offer (or from activation), then needs an active subscription to send offers.
 */
@Service
@Transactional
public class BillingService {
    public record Plan(boolean monetization, boolean active, Instant subscriptionUntil, long daysLeft, boolean featured,
                       Instant featuredUntil, int subscriptionPrice, int featuredPrice, int trialDays, String instructions,
                       List<PaymentRow> payments) {
    }

    public record PaymentRow(Long id, Long artisanId, String artisanName, String kind, int months, int amount, String method,
                             String note, Instant createdAt) {
    }

    public record PaymentInput(String kind, int months, int amount, String method, String note) {
    }

    public record Monetization(boolean enabled, int subscriptionPrice, int trialDays, int featuredPrice, String paymentInstructions) {
    }

    private final SettingsService settings;
    private final ArtisanProfileRepository artisans;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public BillingService(SettingsService settings, ArtisanProfileRepository artisans, PaymentRepository payments,
                          UserRepository users, NotificationService notifications, AuditService audit) {
        this.settings = settings;
        this.artisans = artisans;
        this.payments = payments;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Called before an artisan sends an offer. Starts the free trial the first time. */
    public void requireActive(ArtisanProfile a) {
        if (!settings.monetizationEnabled()) {
            return;
        }
        if (a.subscriptionUntil == null) {
            a.subscriptionUntil = Instant.now().plus(settings.intValue("trialDays", 30), ChronoUnit.DAYS);
        }
        if (a.subscriptionUntil.isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_REQUIRED");
        }
    }

    public Plan plan(AuthUser me) {
        ArtisanProfile a = artisans.findByUserId(me.userId()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY"));
        return plan(a);
    }

    public Plan plan(ArtisanProfile a) {
        boolean on = settings.monetizationEnabled();
        Instant now = Instant.now();
        if (on && a.subscriptionUntil == null && a.status == ArtisanStatus.APPROVED) {
            a.subscriptionUntil = now.plus(settings.intValue("trialDays", 30), ChronoUnit.DAYS);
        }
        boolean active = !on || (a.subscriptionUntil != null && a.subscriptionUntil.isAfter(now));
        long days = a.subscriptionUntil == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(now, a.subscriptionUntil));
        boolean featured = a.featuredUntil != null && a.featuredUntil.isAfter(now);
        return new Plan(on, active, a.subscriptionUntil, days, featured, a.featuredUntil, settings.intValue("subscriptionPrice", 99),
                settings.intValue("featuredPrice", 49), settings.intValue("trialDays", 30), settings.text("paymentInstructions"),
                payments.findByArtisanIdOrderByCreatedAtDesc(a.id).stream().map(p -> row(p, a)).toList());
    }

    /** Admin records a payment and extends the subscription or the featured placement. */
    public Plan record(AuthUser admin, Long artisanId, PaymentInput in) {
        ArtisanProfile a = artisans.findById(artisanId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTISAN_NOT_FOUND"));
        String kind = "FEATURED".equalsIgnoreCase(in.kind()) ? "FEATURED" : "SUBSCRIPTION";
        if (in.months() < 1 || in.months() > 24 || in.amount() < 0 || in.amount() > 1_000_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        Instant now = Instant.now();
        if ("FEATURED".equals(kind)) {
            Instant from = a.featuredUntil != null && a.featuredUntil.isAfter(now) ? a.featuredUntil : now;
            a.featuredUntil = from.plus(30L * in.months(), ChronoUnit.DAYS);
        } else {
            Instant from = a.subscriptionUntil != null && a.subscriptionUntil.isAfter(now) ? a.subscriptionUntil : now;
            a.subscriptionUntil = from.plus(30L * in.months(), ChronoUnit.DAYS);
        }
        Payment p = new Payment();
        p.artisanId = a.id;
        p.kind = kind;
        p.months = in.months();
        p.amount = in.amount();
        p.method = in.method() == null || in.method().isBlank() ? "CASH" : in.method().trim().toUpperCase(Locale.ROOT);
        if (p.method.length() > 30) {
            p.method = p.method.substring(0, 30);
        }
        p.note = in.note() == null || in.note().isBlank() ? null : in.note().trim();
        p.recordedBy = admin.userId();
        payments.save(p);
        notifications.notify(a.user.id, "SUBSCRIPTION_EXTENDED", null, null, null, kind);
        User who = users.findById(admin.userId()).orElse(null);
        audit.logUser(who, "PAYMENT_RECORDED", "USER", a.user.id, kind + " · " + in.months() + " · " + in.amount() + " MAD");
        return plan(a);
    }

    @Transactional(readOnly = true)
    public List<PaymentRow> recent() {
        return payments.findTop100ByOrderByCreatedAtDesc().stream()
                .map(p -> row(p, artisans.findById(p.artisanId).orElse(null))).toList();
    }

    public Monetization monetization() {
        return new Monetization(settings.monetizationEnabled(), settings.intValue("subscriptionPrice", 99),
                settings.intValue("trialDays", 30), settings.intValue("featuredPrice", 49), settings.text("paymentInstructions"));
    }

    public Monetization updateMonetization(AuthUser admin, Monetization in) {
        settings.set("monetizationEnabled", String.valueOf(in.enabled()));
        settings.set("subscriptionPrice", String.valueOf(Math.max(0, in.subscriptionPrice())));
        settings.set("trialDays", String.valueOf(Math.max(0, Math.min(365, in.trialDays()))));
        settings.set("featuredPrice", String.valueOf(Math.max(0, in.featuredPrice())));
        String text = in.paymentInstructions() == null ? "" : in.paymentInstructions().trim();
        settings.set("paymentInstructions", text.length() > 450 ? text.substring(0, 450) : text);
        User who = users.findById(admin.userId()).orElse(null);
        audit.logUser(who, "MONETIZATION_CHANGED", "SYSTEM", null, "enabled=" + in.enabled());
        return monetization();
    }

    /** Daily: tells artisans whose subscription ends in 3 days. */
    @Scheduled(cron = "0 0 9 * * *")
    public void endingSoon() {
        if (!settings.monetizationEnabled()) {
            return;
        }
        Instant from = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant to = from.plus(1, ChronoUnit.DAYS);
        for (ArtisanProfile a : artisans.findBySubscriptionUntilBetween(from, to)) {
            notifications.notify(a.user.id, "SUBSCRIPTION_ENDING", null, null, null, a.subscriptionUntil.toString());
        }
    }

    private static PaymentRow row(Payment p, ArtisanProfile a) {
        return new PaymentRow(p.id, p.artisanId, a == null ? null : a.user.fullName, p.kind, p.months, p.amount, p.method,
                p.note, p.createdAt);
    }

    static boolean isAdmin(User u) {
        return u != null && u.role == Role.ADMIN;
    }
}
