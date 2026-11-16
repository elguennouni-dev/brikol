package ma.sanai3i.service;

import ma.sanai3i.common.ApiException;
import ma.sanai3i.domain.ArtisanProfile;
import ma.sanai3i.domain.Role;
import ma.sanai3i.domain.ServiceItem;
import ma.sanai3i.dto.Dto.ArtisanDetail;
import ma.sanai3i.dto.Dto.ServiceRequest;
import ma.sanai3i.dto.Dto.UpdateArtisanProfile;
import ma.sanai3i.repo.ArtisanProfileRepository;
import ma.sanai3i.repo.ReviewRepository;
import ma.sanai3i.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What an artisan edits on his own profile. */
@Service
@Transactional
public class ArtisanService {
    private final ArtisanProfileRepository artisans;
    private final ReviewRepository reviews;
    private final Mapper mapper;

    public ArtisanService(ArtisanProfileRepository artisans, ReviewRepository reviews, Mapper mapper) {
        this.artisans = artisans;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ArtisanDetail profile(AuthUser me) {
        return detail(profileOf(me));
    }

    public ArtisanDetail update(AuthUser me, UpdateArtisanProfile in) {
        ArtisanProfile a = profileOf(me);
        if (in.availableToday() != null) {
            a.availableToday = in.availableToday();
        }
        if (in.bioAr() != null) {
            a.bioAr = in.bioAr().trim();
        }
        if (in.bioFr() != null) {
            a.bioFr = in.bioFr().trim();
        }
        if (in.whatsapp() != null && !in.whatsapp().isBlank()) {
            a.whatsapp = in.whatsapp().trim();
        }
        return detail(a);
    }

    public ArtisanDetail addService(AuthUser me, ServiceRequest in) {
        ArtisanProfile a = profileOf(me);
        ServiceItem s = new ServiceItem();
        s.artisan = a;
        s.labelAr = in.labelAr().trim();
        s.labelFr = in.labelFr().trim();
        s.price = in.price();
        s.unitAr = in.unitAr() == null ? "" : in.unitAr().trim();
        s.unitFr = in.unitFr() == null ? "" : in.unitFr().trim();
        a.services.add(s);
        artisans.save(a);
        return detail(a);
    }

    public ArtisanDetail deleteService(AuthUser me, Long serviceId) {
        ArtisanProfile a = profileOf(me);
        boolean removed = a.services.removeIf(s -> s.id.equals(serviceId));
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND");
        }
        return detail(a);
    }

    private ArtisanDetail detail(ArtisanProfile a) {
        return mapper.detail(a, mapper.ratingOf(a.id), reviews.findByArtisanIdOrderByCreatedAtDesc(a.id));
    }

    private ArtisanProfile profileOf(AuthUser me) {
        if (me.role() != Role.ARTISAN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY");
        }
        return artisans.findByUserId(me.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ARTISAN_ONLY"));
    }
}
