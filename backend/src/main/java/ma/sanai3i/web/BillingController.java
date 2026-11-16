package ma.sanai3i.web;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.BillingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BillingController {
    private final BillingService billing;
    private final ArtisanProfileRepository artisans;

    public BillingController(BillingService billing, ArtisanProfileRepository artisans) {
        this.billing = billing;
        this.artisans = artisans;
    }

    @GetMapping("/api/artisan/me/plan")
    public BillingService.Plan myPlan(@AuthenticationPrincipal AuthUser me) {
        return billing.plan(me);
    }

    @GetMapping("/api/admin/artisans/{id}/plan")
    public BillingService.Plan plan(@PathVariable Long id) {
        return billing.plan(artisans.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ARTISAN_NOT_FOUND")));
    }

    @PostMapping("/api/admin/artisans/{id}/payments")
    public BillingService.Plan record(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @RequestBody BillingService.PaymentInput in) {
        return billing.record(me, id, in);
    }

    @GetMapping("/api/admin/payments")
    public List<BillingService.PaymentRow> payments() {
        return billing.recent();
    }

    @GetMapping("/api/admin/monetization")
    public BillingService.Monetization monetization() {
        return billing.monetization();
    }

    @PutMapping("/api/admin/monetization")
    public BillingService.Monetization update(@AuthenticationPrincipal AuthUser me, @RequestBody BillingService.Monetization in) {
        return billing.updateMonetization(me, in);
    }
}
