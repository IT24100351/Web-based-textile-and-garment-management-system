package lk.ac.sliit.tgms.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.ac.sliit.tgms.auth.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminUserRepository implements AdminUserRepository {

    private static final String SELECT_USER = """
            SELECT id, email, full_name, role, is_active, created_at, updated_at
            FROM users
            """;

    private static final RowMapper<AdminUserAccount> USER_MAPPER = (resultSet, rowNumber) ->
            new AdminUserAccount(
                    resultSet.getLong("id"),
                    resultSet.getString("email"),
                    resultSet.getString("full_name"),
                    UserRole.valueOf(resultSet.getString("role")),
                    resultSet.getBoolean("is_active"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AdminUserAccount> findAll(AdminUserQuery query) {
        StringBuilder sql = new StringBuilder(SELECT_USER + " WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (query.search() != null) {
            sql.append(" AND (LOCATE(LOWER(?), LOWER(full_name)) > 0 "
                    + "OR LOCATE(LOWER(?), LOWER(email)) > 0)");
            parameters.add(query.search());
            parameters.add(query.search());
        }
        if (query.role() != null) {
            sql.append(" AND role = ?");
            parameters.add(query.role().name());
        }
        if (query.active() != null) {
            sql.append(" AND is_active = ?");
            parameters.add(query.active());
        }
        sql.append(" ORDER BY is_active DESC, full_name, id");

        return jdbcTemplate.query(sql.toString(), USER_MAPPER, parameters.toArray());
    }

    @Override
    public Optional<AdminUserAccount> findById(long userId) {
        return jdbcTemplate.query(SELECT_USER + " WHERE id = ?", USER_MAPPER, userId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AdminUserAccount> findByIdForUpdate(long userId) {
        return jdbcTemplate.query(SELECT_USER + " WHERE id = ? FOR UPDATE", USER_MAPPER, userId)
                .stream()
                .findFirst();
    }

    @Override
    public List<Long> lockActiveAdministratorIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE role = ? AND is_active = TRUE ORDER BY id FOR UPDATE",
                Long.class,
                UserRole.ADMINISTRATOR.name());
    }

    @Override
    public void updateRole(long userId, UserRole role) {
        jdbcTemplate.update(
                "UPDATE users SET role = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                role.name(),
                userId);
    }

    @Override
    public void updateActive(long userId, boolean active) {
        jdbcTemplate.update(
                "UPDATE users SET is_active = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                active,
                userId);
    }
}
