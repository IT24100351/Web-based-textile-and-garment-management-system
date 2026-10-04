package lk.ac.sliit.tgms.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;

class JdbcInventoryMaterialRepositoryGeneratedKeyTests {

    @Test
    void acceptsMySqlGeneratedKeyColumnLabel() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(
                List.of(Map.of("GENERATED_KEY", BigInteger.valueOf(42))));

        assertThat(JdbcInventoryMaterialRepository.generatedId(keyHolder)).isEqualTo(42L);
    }

    @Test
    void rejectsMissingGeneratedKey() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        assertThatThrownBy(() -> JdbcInventoryMaterialRepository.generatedId(keyHolder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database did not return an inventory material ID.");
    }
}
