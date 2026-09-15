package lk.ac.sliit.tgms.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails closed when the application is started with the {@code prod} profile using browser/session
 * settings that are inappropriate for an HTTPS production deployment.
 */
@Configuration
@Profile("prod")
public class ProductionSecurityConfiguration {

    public ProductionSecurityConfiguration(
            @Value("${tgms.auth.cookie-secure}") boolean cookieSecure,
            @Value("${tgms.client-origin}") String clientOrigin) {
        if (!cookieSecure) {
            throw new IllegalStateException(
                    "Production requires the TGMS session cookie to use the Secure attribute.");
        }
        validateHttpsOrigin(clientOrigin);
    }

    private void validateHttpsOrigin(String clientOrigin) {
        try {
            URI origin = URI.create(clientOrigin == null ? "" : clientOrigin.trim());
            boolean originOnly = "https".equalsIgnoreCase(origin.getScheme())
                    && origin.getHost() != null
                    && origin.getUserInfo() == null
                    && origin.getQuery() == null
                    && origin.getFragment() == null
                    && (origin.getPath() == null || origin.getPath().isEmpty());
            if (!originOnly) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Production CLIENT_ORIGIN must be one exact HTTPS origin without a path, query, wildcard, or credentials.");
        }
    }
}
