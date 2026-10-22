package vn.edu.mailclient.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads local .env for the desktop client without adding a dependency. */
public record MailConfig(String email, String password, String smtpHost, int smtpPort,
                         String popHost, int popPort) {
    public static MailConfig load() {
        Map<String, String> values = new HashMap<>();
        Path current = Path.of("").toAbsolutePath();
        // Works from repository root or from the client/ Maven module.
        Path[] candidates = {current.resolve(".env"), current.resolve("../.env").normalize()};
        for (Path path : candidates) {
            if (!Files.isRegularFile(path)) continue;
            try {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    if (trimmed.startsWith("export ")) trimmed = trimmed.substring(7).trim();
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) continue;
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(key, value);
                }
            } catch (IOException ex) {
                System.err.println("Cannot read mail config from " + path + ": " + ex.getMessage());
            }
            break;
        }
        return new MailConfig(
                get(values, "MAIL_EMAIL", ""),
                get(values, "MAIL_APP_PASSWORD", ""),
                get(values, "MAIL_SMTP_HOST", "smtp.gmail.com"),
                port(get(values, "MAIL_SMTP_PORT", "587"), 587),
                get(values, "MAIL_POP3_HOST", "pop.gmail.com"),
                port(get(values, "MAIL_POP3_PORT", "995"), 995));
    }

    private static String get(Map<String, String> values, String key, String fallback) {
        String env = System.getenv(key);
        return env != null && !env.isBlank() ? env : values.getOrDefault(key, fallback);
    }

    private static int port(String text, int fallback) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 && value <= 65535 ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
