package vn.edu.mailclient.common;

public record MailItem(int number, String messageId, String from, String to, String subject, String body, String sentAt, String receivedAt) {
    public MailItem(int number, String messageId, String from, String to, String subject, String body, String sentAt) {
        this(number, messageId, from, to, subject, body, sentAt, "");
    }
    @Override public String toString() { return subject + " — " + from; }
}
