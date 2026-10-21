package vn.edu.mailclient.common;

public record MailAccount(String email, String password, String smtpHost, int smtpPort, String popHost, int popPort) {
    public MailAccount {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("App password is required");
    }
}
