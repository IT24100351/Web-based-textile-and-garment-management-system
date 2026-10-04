package lk.ac.sliit.tgms.search;

import lk.ac.sliit.tgms.auth.InvalidSessionException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SharedSearchController {
    private final SharedSearchService sharedSearchService;

    public SharedSearchController(SharedSearchService sharedSearchService) {
        this.sharedSearchService = sharedSearchService;
    }

    @GetMapping
    public SharedSearchPage search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return sharedSearchService.search(currentUserId(jwt), search, page, size);
    }

    private long currentUserId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new InvalidSessionException();
        }
    }
}
