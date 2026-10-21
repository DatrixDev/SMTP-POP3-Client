package vn.edu.mailclient.common;

public record MailItem(int number, String messageId, String from, String to, String subject, String body, String sentAt) {
    @Override public String toString() { return subject + " — " + from; }
}
