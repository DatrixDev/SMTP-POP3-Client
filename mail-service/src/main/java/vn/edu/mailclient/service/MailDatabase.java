package vn.edu.mailclient.service;

import java.sql.*;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;

/** Optional local MySQL cache. Credentials come from environment, never source code. */
public final class MailDatabase {
    private final String url = System.getenv("MAIL_DB_URL");
    private final String user = System.getenv("MAIL_DB_USER");
    private final String pass = System.getenv("MAIL_DB_PASSWORD");
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
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO mail_messages(account_id,direction,message_id,sender,recipients,subject,body) VALUES(?,?,?,?,?,?,?)")) {
                s.setLong(1, id); s.setString(2, direction); s.setString(3, item.messageId());
                s.setString(4, item.from()); s.setString(5, item.to()); s.setString(6, item.subject());
                s.setString(7, item.body()); s.executeUpdate();
            }
        }
    }
}
