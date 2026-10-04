package lk.ac.sliit.tgms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    public static final String COOKIE_NAME = "TGMS_SESSION";

    private final AuthTokenService authTokenService;
    private final boolean secure;

    public AuthCookieService(
            AuthTokenService authTokenService,
            @Value("${tgms.auth.cookie-secure}") boolean secure) {
        this.authTokenService = authTokenService;
        this.secure = secure;
    }

    public String createSessionCookie(String token) {
        return baseCookie(token)
                .maxAge(authTokenService.sessionDuration())
                .build()
                .toString();
    }

    public String clearSessionCookie() {
        return baseCookie("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api");
    }
}
