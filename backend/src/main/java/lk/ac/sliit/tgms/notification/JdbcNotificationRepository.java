package lk.ac.sliit.tgms.notification;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private static final RowMapper<OperationalNotification> NOTIFICATION_MAPPER = (resultSet, rowNumber) ->
            new OperationalNotification(
                    resultSet.getLong("id"),
                    resultSet.getLong("recipient_user_id"),
                    NotificationKind.valueOf(resultSet.getString("kind")),
                    resultSet.getString("title"),
                    resultSet.getString("message"),
                    NotificationSourceModule.valueOf(resultSet.getString("source_module")),
                    resultSet.getLong("source_record_id"),
                    resultSet.getTimestamp("read_at") == null
                            ? null
                            : resultSet.getTimestamp("read_at").toInstant(),
                    resultSet.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void create(
            long recipientUserId,
            NotificationKind kind,
            String title,
            String message,
            NotificationSourceModule sourceModule,
            long sourceRecordId) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications (
                    recipient_user_id, kind, title, message, source_module, source_record_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                recipientUserId,
                kind.name(),
                title,
                message,
                sourceModule.name(),
                sourceRecordId);
    }

    @Override
    public List<OperationalNotification> findForRecipient(
            long recipientUserId, boolean unreadOnly, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, recipient_user_id, kind, title, message,
                       source_module, source_record_id, read_at, created_at
                FROM notifications
                WHERE recipient_user_id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(recipientUserId);
        if (unreadOnly) {
            sql.append(" AND read_at IS NULL");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        parameters.add(limit);
        return jdbcTemplate.query(sql.toString(), NOTIFICATION_MAPPER, parameters.toArray());
    }

    @Override
    public long countUnread(long recipientUserId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND read_at IS NULL",
                Long.class,
                recipientUserId);
        return count == null ? 0L : count;
    }

    @Override
    public boolean markRead(long recipientUserId, long notificationId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE notifications
                SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
                WHERE id = ? AND recipient_user_id = ?
                """,
                notificationId,
                recipientUserId);
        return updated == 1;
    }

    @Override
    public int markAllRead(long recipientUserId) {
        return jdbcTemplate.update(
                """
                UPDATE notifications
                SET read_at = CURRENT_TIMESTAMP(6)
                WHERE recipient_user_id = ? AND read_at IS NULL
                """,
                recipientUserId);
    }
}
