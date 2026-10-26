package vn.edu.mailclient.service;

import java.sql.*;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;

/** Optional local MySQL cache. Credentials come from environment, never source code. */
public final class MailDatabase {
    private static String setting(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        java.nio.file.Path cwd = java.nio.file.Path.of("").toAbsolutePath();
        for (java.nio.file.Path path : java.util.List.of(cwd.resolve(".env"), cwd.resolve("../.env").normalize())) {
            if (!java.nio.file.Files.isRegularFile(path)) continue;
            try {
                for (String line : java.nio.file.Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) continue;
                    int equals = trimmed.indexOf('=');
                    if (equals < 0 || !trimmed.substring(0, equals).trim().equals(key)) continue;
                    String value = trimmed.substring(equals + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!value.isBlank()) return value;
                }
            } catch (java.io.IOException ex) {
                System.err.println("Cannot read database configuration: " + ex.getMessage());
            }
            break;
        }
        return fallback;
    }

    private final String url = setting("MAIL_DB_URL", "jdbc:mysql://127.0.0.1:"
            + setting("MYSQL_PORT", "3307") + "/smtp_pop3_client");
    private final String user = setting("MAIL_DB_USER", setting("MYSQL_USER", "mailapp"));
    private final String pass = setting("MAIL_DB_PASSWORD", setting("MYSQL_PASSWORD", ""));
    public boolean enabled() { return url != null && !url.isBlank(); }
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user == null ? "" : user, pass == null ? "" : pass);
    }
    private long accountId(Connection c, MailAccount account) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO mail_accounts(email,smtp_host,smtp_port,pop3_host,pop3_port) VALUES(?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE smtp_host=VALUES(smtp_host),smtp_port=VALUES(smtp_port)," +
                "pop3_host=VALUES(pop3_host),pop3_port=VALUES(pop3_port)")) {
            s.setString(1, account.email()); s.setString(2, account.smtpHost()); s.setInt(3, account.smtpPort());
            s.setString(4, account.popHost()); s.setInt(5, account.popPort()); s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement("SELECT id FROM mail_accounts WHERE email=?")) {
            s.setString(1, account.email());
            try (ResultSet rs = s.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        }
        throw new SQLException("Cannot find mail account");
    }
    public void save(MailAccount account, String direction, MailItem item) throws SQLException {
        if (!enabled()) return;
        try (Connection c = connect()) {
            long id = accountId(c, account);
            // POP3 refresh must not append a second copy of the same received message.
            // Message-ID is the primary identity; older mail without it uses a content match.
            if ("RECEIVED".equals(direction)) {
                boolean hasId = item.messageId() != null && !item.messageId().isBlank();
                String sql = hasId
                        ? "SELECT 1 FROM mail_messages WHERE account_id=? AND direction='RECEIVED' AND message_id=? LIMIT 1"
                        : "SELECT 1 FROM mail_messages WHERE account_id=? AND direction='RECEIVED' AND (message_id IS NULL OR message_id='') AND sender <=> ? AND recipients <=> ? AND subject <=> ? AND body <=> ? LIMIT 1";
                try (PreparedStatement check = c.prepareStatement(sql)) {
                    check.setLong(1, id);
                    if (hasId) check.setString(2, item.messageId());
                    else {
                        check.setString(2, item.from());
                        check.setString(3, item.to());
                        check.setString(4, item.subject());
                        check.setString(5, item.body());
                    }
                    try (ResultSet existing = check.executeQuery()) {
                        if (existing.next()) return;
                    }
                }
            }
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO mail_messages(account_id,direction,message_id,sender,recipients,subject,body) VALUES(?,?,?,?,?,?,?)")) {
                s.setLong(1, id); s.setString(2, direction); s.setString(3, item.messageId());
                s.setString(4, item.from()); s.setString(5, item.to()); s.setString(6, item.subject());
                s.setString(7, item.body()); s.executeUpdate();
            }
        }
    }
}
