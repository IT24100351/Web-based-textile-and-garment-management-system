package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class JdbcInventoryMaterialRepository implements InventoryMaterialRepository 
{

    private static final RowMapper<InventoryMaterial> MATERIAL_MAPPER =
            (resultSet, rowNumber) -> mapMaterial(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryMaterialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long create(
            Long sourceMaterialSupplyId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO inventory_materials
                                (source_material_supply_id, material_code, material_name,
                                 material_description, material_type, unit_of_measure,
                                 current_quantity, low_stock_threshold)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            new String[] {"id"});
                    if (sourceMaterialSupplyId == null) {
                        statement.setNull(1, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(1, sourceMaterialSupplyId);
                    }
                    statement.setString(2, materialCode);
                    statement.setString(3, materialName);
                    statement.setString(4, materialDescription);
                    statement.setString(5, materialType.name());
                    statement.setString(6, unitOfMeasure);
                    statement.setBigDecimal(7, currentQuantity);
                    statement.setBigDecimal(8, lowStockThreshold);
                    return statement;
                    
                },
                keyHolder);
        return generatedId(keyHolder);
    }

    @Override
    public Optional<InventoryMaterial> findById(long materialId) 
    {
        return jdbcTemplate.query(
                        """
                        SELECT id, source_material_supply_id, material_code, material_name,
                               material_description, material_type, unit_of_measure,
                               current_quantity, low_stock_threshold, status,
                               created_at, updated_at
                        FROM inventory_materials
                        WHERE id = ?
                        """,
                        MATERIAL_MAPPER,
                        materialId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<InventoryMaterial> findByMaterialCode(String materialCode)
     {
        return jdbcTemplate.query(
                        """
                        SELECT id, source_material_supply_id, material_code, material_name,
                               material_description, material_type, unit_of_measure,
                               current_quantity, low_stock_threshold, status,
                               created_at, updated_at
                        FROM inventory_materials
                        WHERE material_code = ?
                        """,
                        MATERIAL_MAPPER,
                        materialCode)
                .stream()
                .findFirst();
    }

    @Override
    public List<InventoryMaterial> findAll(InventoryMaterialQuery filter) 
    {
        StringBuilder query = new StringBuilder("""
                SELECT id, source_material_supply_id, material_code, material_name,
                       material_description, material_type, unit_of_measure,
                       current_quantity, low_stock_threshold, status,
                       created_at, updated_at
                FROM inventory_materials
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        if (filter.search() != null) {
            query.append("""
                     AND (LOWER(material_code) LIKE ?
                          OR LOWER(material_name) LIKE ?
                          OR LOWER(COALESCE(material_description, '')) LIKE ?
                          OR LOWER(unit_of_measure) LIKE ?)
                    """);
            String searchPattern = "%" + escapeLike(filter.search().toLowerCase()) + "%";
            for (int index = 0; index < 4; index++) {
                parameters.add(searchPattern);
            }
        }
        if (filter.status() != null) {
            query.append(" AND status = ?");
            parameters.add(filter.status().name());
        }
        if (filter.materialType() != null) {
            query.append(" AND material_type = ?");
            parameters.add(filter.materialType().name());
        }
        query.append(" ORDER BY created_at DESC, id DESC");

        return jdbcTemplate.query(query.toString(), MATERIAL_MAPPER, parameters.toArray());
    }

    private String escapeLike(String value) 
    {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public int updateMetadata(
            long materialId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal lowStockThreshold,
            InventoryMaterialStatus status) {
        return jdbcTemplate.update(
                """
                UPDATE inventory_materials
                SET material_code = ?,
                    material_name = ?,
                    material_description = ?,
                    material_type = ?,
                    unit_of_measure = ?,
                    low_stock_threshold = ?,
                    status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status <> 'DISCONTINUED'
                """,
                materialCode,
                materialName,
                materialDescription,
                materialType.name(),
                unitOfMeasure,
                lowStockThreshold,
                status.name(),
                materialId);
    }

    @Override
    public int archive(long materialId) 
    {
        return jdbcTemplate.update(
                """
                UPDATE inventory_materials
                SET status = 'DISCONTINUED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status <> 'DISCONTINUED'
                """,
                materialId);
    }

    @Override
    public int consumeStockIfAvailable(long materialId, BigDecimal quantity) 
    {
        return jdbcTemplate.update(
                """
                UPDATE inventory_materials
                SET current_quantity = current_quantity - ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                  AND current_quantity >= ?
                """,
                quantity,
                materialId,
                quantity);
    }

    private static InventoryMaterial mapMaterial(ResultSet resultSet) throws SQLException 
    {
        Long sourceMaterialSupplyId = resultSet.getLong("source_material_supply_id");
        if (resultSet.wasNull()) {
            sourceMaterialSupplyId = null;
        }
        return new InventoryMaterial(
                resultSet.getLong("id"),
                sourceMaterialSupplyId,
                resultSet.getString("material_code"),
                resultSet.getString("material_name"),
                resultSet.getString("material_description"),
                InventoryMaterialType.valueOf(resultSet.getString("material_type")),
                resultSet.getString("unit_of_measure"),
                resultSet.getBigDecimal("current_quantity"),
                resultSet.getBigDecimal("low_stock_threshold"),
                InventoryMaterialStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private long generatedId(KeyHolder keyHolder) 
    {
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
                            "Database did not return an inventory material ID."));
        }
        throw new IllegalStateException("Database did not return an inventory material ID.");
    }
}
