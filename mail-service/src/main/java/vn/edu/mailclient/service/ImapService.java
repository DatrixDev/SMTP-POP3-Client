package vn.edu.mailclient.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.MimeMultipart;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;

/** Gmail IMAP inbox; POP3 remains available independently for lab demonstration. */
public final class ImapService {
    public List<MailItem> inbox(MailAccount account, int offset, int pageSize) throws MessagingException, IOException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.host", "imap.gmail.com");
        p.put("mail.imaps.port", "993");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "12000");
        p.put("mail.imaps.timeout", "20000");
        p.put("mail.imaps.writetimeout", "20000");
        Store store = Session.getInstance(p).getStore("imaps");
        Folder folder = null;
        try {
            store.connect("imap.gmail.com", account.email(), account.password().replaceAll("\\s", ""));
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            int count = folder.getMessageCount();
            int upper = count - Math.max(0, offset);
            if (upper < 1) return List.of();
            int lower = Math.max(1, upper - Math.max(1, Math.min(50, pageSize)) + 1);
            List<MailItem> items = new ArrayList<>();
            for (int n = upper; n >= lower; n--) {
                Message m = folder.getMessage(n);
                Address[] from = m.getFrom();
                Address[] to = m.getRecipients(Message.RecipientType.TO);
                String[] ids = m.getHeader("Message-ID");
                items.add(new MailItem(n, ids == null || ids.length == 0 ? "" : ids[0],
                    addresses(from), addresses(to), decode(m.getSubject()),
                    "", m.getSentDate() == null ? "" : m.getSentDate().toInstant().toString(),
                    m.getReceivedDate() == null ? "" : m.getReceivedDate().toInstant().toString()));
            }
            return items;
        } finally {
            if (folder != null && folder.isOpen()) try { folder.close(false); } catch (MessagingException ignored) {}
            try { store.close(); } catch (MessagingException ignored) {}
        }
    }

    /** Incremental Gmail sync using IMAP UID; caller stores last UID between runs. */
    public SyncBatch sinceUid(MailAccount account, long lastUid, int limit) throws MessagingException, IOException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "12000");
        p.put("mail.imaps.timeout", "20000");
        Store store = Session.getInstance(p).getStore("imaps");
        Folder folder = null;
        try {
            store.connect("imap.gmail.com", account.email(), account.password().replaceAll("\\s", ""));
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            UIDFolder uids = (UIDFolder) folder;
            long max = uids.getUIDNext() - 1;
            long start = Math.max(1, Math.max(lastUid + 1, max - Math.max(1, Math.min(limit, 100)) + 1));
            if (start > max) return new SyncBatch(lastUid, List.of());
            Message[] messages = uids.getMessagesByUID(start, max);
            List<MailItem> items = new ArrayList<>();
            long cursor = lastUid;
            for (Message m : messages) {
                if (m == null) continue;
                long uid = uids.getUID(m);
                cursor = Math.max(cursor, uid);
                Address[] from = m.getFrom();
                Address[] to = m.getRecipients(Message.RecipientType.TO);
                String[] ids = m.getHeader("Message-ID");
                Date date = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
                items.add(new MailItem(m.getMessageNumber(), ids == null || ids.length == 0 ? "" : ids[0],
                    addresses(from), addresses(to), decode(m.getSubject()), "",
                    m.getSentDate() == null ? "" : m.getSentDate().toInstant().toString(),
                    m.getReceivedDate() == null ? "" : m.getReceivedDate().toInstant().toString()));
            }
            return new SyncBatch(cursor, items);
        } finally {
            if (folder != null && folder.isOpen()) try { folder.close(false); } catch (MessagingException ignored) {}
            try { store.close(); } catch (MessagingException ignored) {}
        }
    }
    /** Fetch a single IMAP body on demand to keep inbox and UID sync fast. */
    public String body(MailAccount account, int messageNumber) throws MessagingException, IOException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "12000");
        p.put("mail.imaps.timeout", "20000");
        Store store = Session.getInstance(p).getStore("imaps");
        Folder folder = null;
        try {
            store.connect("imap.gmail.com", account.email(), account.password().replaceAll("\\s", ""));
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            if (messageNumber < 1 || messageNumber > folder.getMessageCount()) throw new MessagingException("Message not found");
            return text(folder.getMessage(messageNumber));
        } finally {
            if (folder != null && folder.isOpen()) try { folder.close(false); } catch (MessagingException ignored) {}
            try { store.close(); } catch (MessagingException ignored) {}
        }
    }

    public List<MailItem> sent(MailAccount account, int offset, int pageSize) throws MessagingException, IOException {
        Properties p = new Properties();
        p.put("mail.store.protocol", "imaps");
        p.put("mail.imaps.ssl.enable", "true");
        p.put("mail.imaps.connectiontimeout", "12000");
        p.put("mail.imaps.timeout", "20000");
        Store store = Session.getInstance(p).getStore("imaps");
        Folder folder = null;
        try {
            store.connect("imap.gmail.com", account.email(), account.password().replaceAll("\\s", ""));
            folder = store.getFolder("[Gmail]/Sent Mail");
            if (!folder.exists()) folder = store.getFolder("[Gmail]/Thư đã gửi");
            if (!folder.exists()) throw new MessagingException("Gmail Sent folder unavailable");
            folder.open(Folder.READ_ONLY);
            int upper = folder.getMessageCount() - Math.max(0, offset);
            if (upper < 1) return List.of();
            int lower = Math.max(1, upper - Math.max(1, Math.min(50, pageSize)) + 1);
            List<MailItem> items = new ArrayList<>();
            for (int n = upper; n >= lower; n--) {
                Message m = folder.getMessage(n);
                String[] ids = m.getHeader("Message-ID");
                items.add(new MailItem(n, ids == null || ids.length == 0 ? "" : ids[0],
                    addresses(m.getFrom()), addresses(m.getAllRecipients()), decode(m.getSubject()), text(m),
                    m.getSentDate() == null ? "" : m.getSentDate().toInstant().toString(),
                    m.getReceivedDate() == null ? "" : m.getReceivedDate().toInstant().toString()));
            }
            return items;
        } finally {
            if (folder != null && folder.isOpen()) try { folder.close(false); } catch (MessagingException ignored) {}
            try { store.close(); } catch (MessagingException ignored) {}
        }
    }
    public record SyncBatch(long lastUid, List<MailItem> messages) {}

    private static String decode(String s) {
        if (s == null) return "";
        try { return MimeUtility.decodeText(s); } catch (Exception ex) { return s; }
    }
    private static String addresses(Address[] values) {
        if (values == null || values.length == 0) return "";
        List<String> out = new ArrayList<>();
        for (Address a : values) {
            if (a instanceof InternetAddress ia) {
                String name = decode(ia.getPersonal());
                out.add(name.isBlank() ? ia.getAddress() : name + " <" + ia.getAddress() + ">");
            } else out.add(decode(a.toString()));
        }
        return String.join(", ", out);
    }
    private static String text(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) return String.valueOf(part.getContent());
        if (part.isMimeType("text/html")) return String.valueOf(part.getContent()).replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
            .replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&").trim();
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String html = "";
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) continue;
                if (bp.isMimeType("text/plain")) return text(bp);
                if (bp.isMimeType("text/html")) html = text(bp);
                if (bp.isMimeType("multipart/*")) {
                    String nested = text(bp);
                    if (!nested.isBlank()) return nested;
                }
            }
            return html;
        }
        return "";
    }
}
