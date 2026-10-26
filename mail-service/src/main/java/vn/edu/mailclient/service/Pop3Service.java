package vn.edu.mailclient.service;

import java.io.IOException;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.InternetAddress;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;

/** JavaMail POP3 Store/Folder/Message based on textbook Listing 7.4. */
public final class Pop3Service {
    public List<MailItem> inbox(MailAccount account) throws MessagingException, IOException {
        return inbox(account, 0, 20);
    }

    /** Page through the messages exposed by POP3, ordered by POP3 message number. */
    public List<MailItem> inbox(MailAccount account, int offset, int pageSize) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.pop3s.ssl.enable", "true");
        props.put("mail.pop3s.connectiontimeout", "10000");
        props.put("mail.pop3s.timeout", "20000");
        props.put("mail.pop3s.writetimeout", "20000");
        boolean local = account.popHost().equalsIgnoreCase("localhost") || account.popHost().equals("127.0.0.1");
        if (local) {
            props.put("mail.pop3.connectiontimeout", "10000");
            props.put("mail.pop3.timeout", "20000");
        }
        Session session = Session.getInstance(props);
        try (StoreHolder holder = new StoreHolder(session.getStore(local ? "pop3" : "pop3s"))) {
            holder.store.connect(account.popHost(), account.popPort(), account.email(), account.password());
            Folder folder = holder.store.getFolder("INBOX");
            if (folder == null) throw new MessagingException("INBOX not available");
            folder.open(Folder.READ_ONLY);
            try {
                Message[] messages = folder.getMessages();
                List<MailItem> items = new ArrayList<>();
                int start = messages.length - 1 - Math.max(0, offset);
                int end = Math.max(0, start - Math.max(1, Math.min(50, pageSize)) + 1);
                for (int i = start; i >= end; i--) {
                    Message m = messages[i];
                    String mid = "";
                    if (m instanceof javax.mail.internet.MimeMessage mm) {
                        String[] ids = mm.getHeader("Message-ID");
                        if (ids != null && ids.length > 0) mid = ids[0];
                    }
                    items.add(new MailItem(m.getMessageNumber(), mid,
                            m.getFrom() == null ? "" : InternetAddresses(m.getFrom()),
                            m.getAllRecipients() == null ? "" : InternetAddresses(m.getAllRecipients()),
                            decode(Objects.toString(m.getSubject(), "")), extractText(m),
                            m.getSentDate() == null ? "" : m.getSentDate().toString()));
                }
                return items;
            } finally {
                folder.close(false);
            }
        }
    }
    private static String InternetAddresses(Address[] a) {
        StringJoiner join = new StringJoiner(", ");
        for (Address item : a) {
            if (item instanceof InternetAddress ia) {
                String personal = ia.getPersonal();
                if (personal == null || personal.isBlank()) join.add(ia.getAddress());
                else join.add(decode(personal) + " <" + ia.getAddress() + ">");
            } else join.add(decode(item.toString()));
        }
        return join.toString();
    }
    private static String decode(String value) {
        try { return MimeUtility.decodeText(value); }
        catch (Exception ex) { return value; }
    }

    private static String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) return Objects.toString(part.getContent(), "");
        if (part.isMimeType("text/html")) return "[HTML email]\n" + Objects.toString(part.getContent(), "");
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof MimeMultipart multi) {
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < multi.getCount(); i++) {
                    BodyPart child = multi.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())) continue;
                    if (child.isMimeType("text/plain")) return extractText(child);
                    if (out.length() == 0) out.append(extractText(child));
                }
                return out.toString();
            }
        }
        return "";
    }
    private static final class StoreHolder implements AutoCloseable {
        final Store store;
        StoreHolder(Store store) { this.store = store; }
        @Override public void close() throws MessagingException {
            if (store.isConnected()) store.close();
        }
    }
}
