package lk.ac.sliit.tgms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;

class JdbcOrderRepositoryGeneratedKeyTests {

    @Test
    void acceptsMySqlGeneratedKeyColumnLabel() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(
                List.of(Map.of("GENERATED_KEY", BigInteger.valueOf(42))));

        assertThat(JdbcOrderRepository.generatedId(keyHolder)).isEqualTo(42L);
    }

    @Test
    void rejectsMissingGeneratedKey() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        assertThatThrownBy(() -> JdbcOrderRepository.generatedId(keyHolder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database did not return an order identifier.");
    }
}
