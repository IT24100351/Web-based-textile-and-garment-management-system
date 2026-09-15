package lk.ac.sliit.tgms.supplier;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMaterialSupplyRepository implements MaterialSupplyRepository {

    private static final RowMapper<MaterialSupply> SUPPLY_MAPPER = (resultSet, rowNumber) ->
            mapSupply(resultSet);

    private static final RowMapper<MaterialSupplyListItem> LIST_ITEM_MAPPER =
            (resultSet, rowNumber) -> new MaterialSupplyListItem(
                    mapSupply(resultSet), resultSet.getString("supplier_business_name"));

    private static MaterialSupply mapSupply(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new MaterialSupply(
                    resultSet.getLong("id"),
                    resultSet.getLong("supplier_id"),
                    resultSet.getString("material_code"),
                    resultSet.getString("material_name"),
                    resultSet.getString("material_description"),
                    resultSet.getBigDecimal("quantity"),
                    resultSet.getString("unit_of_measure"),
                    resultSet.getBigDecimal("unit_price"),
                    resultSet.getInt("delivery_lead_time_days"),
                    resultSet.getString("delivery_notes"),
                    MaterialSupplyStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());
    }

    private final JdbcTemplate jdbcTemplate;

    public JdbcMaterialSupplyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(
            long supplierId,
            String materialCode,
            String materialName,
            String materialDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO material_supplies
                                (supplier_id, material_code, material_name,
                                 material_description, quantity, unit_of_measure,
                                 unit_price, delivery_lead_time_days, delivery_notes)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            new String[] {"id"});
                    statement.setLong(1, supplierId);
                    statement.setString(2, materialCode);
                    statement.setString(3, materialName);
                    statement.setString(4, materialDescription);
                    statement.setBigDecimal(5, quantity);
                    statement.setString(6, unitOfMeasure);
                    statement.setBigDecimal(7, unitPrice);
                    statement.setInt(8, deliveryLeadTimeDays);
                    statement.setString(9, deliveryNotes);
                    return statement;
                },
                keyHolder);
        return generatedId(keyHolder);
    }

    @Override
    public Optional<MaterialSupply> findById(long supplyId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, supplier_id, material_code, material_name,
                               material_description, quantity, unit_of_measure, unit_price,
                               delivery_lead_time_days, delivery_notes, status,
                               created_at, updated_at
                        FROM material_supplies
                        WHERE id = ?
                        """,
                        SUPPLY_MAPPER,
                        supplyId)
                .stream()
                .findFirst();
    }

    @Override
    public int updateDetails(
            long supplyId,
            long supplierId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes) {
        return jdbcTemplate.update(
                """
                UPDATE material_supplies
                SET quantity = ?, unit_price = ?, delivery_lead_time_days = ?,
                    delivery_notes = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND supplier_id = ?
                """,
                quantity,
                unitPrice,
                deliveryLeadTimeDays,
                deliveryNotes,
                supplyId,
                supplierId);
    }

    @Override
    public int archive(long supplyId, long supplierId) {
        return jdbcTemplate.update(
                """
                UPDATE material_supplies
                SET status = 'DISCONTINUED', updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND supplier_id = ? AND status <> 'DISCONTINUED'
                """,
                supplyId,
                supplierId);
    }

    @Override
    public List<MaterialSupplyListItem> findAll(MaterialSupplyQuery filter) {
        StringBuilder query = new StringBuilder("""
                SELECT ms.id, ms.supplier_id, ms.material_code, ms.material_name,
                       ms.material_description, ms.quantity, ms.unit_of_measure,
                       ms.unit_price, ms.delivery_lead_time_days, ms.delivery_notes,
                       ms.status, ms.created_at, ms.updated_at,
                       sp.business_name AS supplier_business_name
                FROM material_supplies ms
                JOIN supplier_profiles sp ON sp.id = ms.supplier_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        if (filter.supplierId() != null) {
            query.append(" AND ms.supplier_id = ?");
            parameters.add(filter.supplierId());
        }
        if (filter.search() != null) {
            query.append("""
                     AND (LOWER(ms.material_code) LIKE ?
                          OR LOWER(ms.material_name) LIKE ?
                          OR LOWER(COALESCE(ms.material_description, '')) LIKE ?
                          OR LOWER(ms.unit_of_measure) LIKE ?
                          OR LOWER(COALESCE(ms.delivery_notes, '')) LIKE ?)
                    """);
            String searchPattern = "%" + escapeLike(filter.search().toLowerCase()) + "%";
            for (int index = 0; index < 5; index++) {
                parameters.add(searchPattern);
            }
        }
        if (filter.status() != null) {
            query.append(" AND ms.status = ?");
            parameters.add(filter.status().name());
        }
        query.append(" ORDER BY ms.created_at DESC, ms.id DESC");

        return jdbcTemplate.query(query.toString(), LIST_ITEM_MAPPER, parameters.toArray());
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            return keys.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("id"))
                    .map(Map.Entry::getValue)
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .findFirst()
                    .map(Number::longValue)
                    .orElseThrow(() -> new IllegalStateException(
                            "Database did not return a material supply ID."));
        }
        throw new IllegalStateException("Database did not return a material supply ID.");
    }
}
