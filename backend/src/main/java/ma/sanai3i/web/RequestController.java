package ma.sanai3i.web;

import jakarta.validation.Valid;
import ma.sanai3i.dto.Dto.CreateOffer;
import ma.sanai3i.dto.Dto.CreateRequest;
import ma.sanai3i.dto.Dto.OfferDto;
import ma.sanai3i.dto.Dto.RequestDto;
import ma.sanai3i.dto.Dto.ReviewRequest;
import ma.sanai3i.security.AuthUser;
import ma.sanai3i.service.RequestService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    // ---- client ----

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRequest body) {
        return service.create(me, body);
    }

    @GetMapping("/requests/mine")
    public List<RequestDto> mine(@AuthenticationPrincipal AuthUser me,
                                 @RequestParam(required = false) String tab) {
        return service.mine(me, tab);
    }

    @GetMapping("/requests/{id}")
    public RequestDto get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me, id);
    }

    @PostMapping("/requests/{id}/offers/{offerId}/accept")
    public RequestDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @PathVariable Long offerId) {
        return service.accept(me, id, offerId);
    }

    @PostMapping("/requests/{id}/cancel")
    public RequestDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.cancel(me, id);
    }

    @PostMapping("/requests/{id}/complete")
    public RequestDto complete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.complete(me, id);
    }

    @PostMapping("/requests/{id}/review")
    public RequestDto review(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                             @Valid @RequestBody ReviewRequest body) {
        return service.review(me, id, body);
    }

    // ---- artisan ----

    @PostMapping("/requests/{id}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferDto offer(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                          @Valid @RequestBody CreateOffer body) {
        return service.offer(me, id, body);
    }

    @GetMapping("/artisan/requests")
    public List<RequestDto> openForMe(@AuthenticationPrincipal AuthUser me) {
        return service.openForArtisan(me);
    }

    @GetMapping("/artisan/jobs")
    public List<RequestDto> myJobs(@AuthenticationPrincipal AuthUser me) {
        return service.myJobs(me);
    }
}
