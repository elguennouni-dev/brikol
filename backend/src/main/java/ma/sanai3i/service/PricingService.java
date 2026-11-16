package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.Category;
import ma.sanai3i.domain.JobRequest;
import ma.sanai3i.repo.OfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Price range of a trade. Hard bounds come from the category (set by the admin). When the trade has enough history
 * (at least 5 accepted offers in the last 12 months) the range is narrowed around real prices:
 * min = max(category min, 60% of the 10th percentile), max = min(category max, 160% of the 90th percentile).
 * The range is frozen on each request when it is created; every budget, offer and counter-offer must stay inside it.
 */
@Service
@Transactional(readOnly = true)
public class PricingService {
    public static final int MIN_SAMPLES = 5;

    public record Range(int min, int max, Integer typicalLow, Integer typicalHigh, int samples) {
    }

    private final OfferRepository offers;

    public PricingService(OfferRepository offers) {
        this.offers = offers;
    }

    public Range range(Category c) {
        int hardMin = Math.max(0, c.minPrice);
        int hardMax = Math.max(hardMin + 10, c.maxPrice);
        List<Integer> prices = new ArrayList<>(offers.acceptedPricesSince(c.id, Instant.now().minus(365, ChronoUnit.DAYS)));
        if (prices.size() < MIN_SAMPLES) {
            return new Range(hardMin, hardMax, null, null, prices.size());
        }
        Collections.sort(prices);
        int p10 = percentile(prices, 0.10);
        int p90 = percentile(prices, 0.90);
        int low = Math.max(hardMin, round10(p10 * 0.6));
        int high = Math.min(hardMax, round10(p90 * 1.6));
        if (high - low < 50) {                    // too narrow to be useful: keep the trade bounds
            low = hardMin;
            high = hardMax;
        }
        return new Range(low, high, percentile(prices, 0.25), percentile(prices, 0.75), prices.size());
    }

    /** Throws 400 PRICE_OUT_OF_RANGE when the price is outside the range of the request. */
    public void check(JobRequest r, int price) {
        int min = r.priceMin != null ? r.priceMin : r.category.minPrice;
        int max = r.priceMax != null ? r.priceMax : r.category.maxPrice;
        if (price < min || price > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_OUT_OF_RANGE");
        }
    }

    private static int percentile(List<Integer> sorted, double p) {
        int i = (int) Math.round(p * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, i)));
    }

    private static int round10(double v) {
        return (int) (Math.round(v / 10.0) * 10);
    }
}
