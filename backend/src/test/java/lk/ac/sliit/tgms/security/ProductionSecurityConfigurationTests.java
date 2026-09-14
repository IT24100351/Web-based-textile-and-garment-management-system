package lk.ac.sliit.tgms.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.ac.sliit.tgms.config.ProductionSecurityConfiguration;
import org.junit.jupiter.api.Test;

class ProductionSecurityConfigurationTests {

    @Test
    void secureCookieAndExactHttpsOriginAreAccepted() {
        assertThatCode(() -> new ProductionSecurityConfiguration(true, "https://tgms.example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void insecureProductionCookieIsRejected() {
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(false, "https://tgms.example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure attribute");
    }

    @Test
    void nonHttpsOrNonOriginProductionClientConfigurationIsRejected() {
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(true, "http://tgms.example.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(true, "https://tgms.example.com/"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(true, "https://tgms.example.com/app"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(true, "https://*.example.com"))
                .isInstanceOf(IllegalStateException.class);
    }
}
