package lk.ac.sliit.tgms.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;

class JdbcProductRepositoryGeneratedKeyTests {

    @Test
    void acceptsMySqlGeneratedKeyColumnLabel() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(
                List.of(Map.of("GENERATED_KEY", BigInteger.valueOf(42))));

        assertThat(JdbcProductRepository.generatedId(keyHolder)).isEqualTo(42L);
    }

    @Test
    void rejectsMissingGeneratedKey() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        assertThatThrownBy(() -> JdbcProductRepository.generatedId(keyHolder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database did not return a product ID.");
    }
}
