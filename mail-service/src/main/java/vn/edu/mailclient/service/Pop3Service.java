package vn.edu.mailclient.service;

import java.io.IOException;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;

/** JavaMail POP3 Store/Folder/Message based on textbook Listing 7.4. */
public final class Pop3Service {
    public List<MailItem> inbox(MailAccount account) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.pop3s.ssl.enable", "true");
        props.put("mail.pop3s.connectiontimeout", "10000");
        props.put("mail.pop3s.timeout", "20000");
        props.put("mail.pop3s.writetimeout", "20000");
        Session session = Session.getInstance(props);
        try (StoreHolder holder = new StoreHolder(session.getStore("pop3s"))) {
            holder.store.connect(account.popHost(), account.popPort(), account.email(), account.password());
            Folder folder = holder.store.getFolder("INBOX");
            if (folder == null) throw new MessagingException("INBOX not available");
            folder.open(Folder.READ_ONLY);
            try {
                Message[] messages = folder.getMessages();
                List<MailItem> items = new ArrayList<>();
                // Download only the newest 20 messages to avoid loading an entire mailbox.
                for (int i = messages.length - 1; i >= Math.max(0, messages.length - 20); i--) {
                    Message m = messages[i];
                    String mid = "";
                    if (m instanceof javax.mail.internet.MimeMessage mm) {
                        String[] ids = mm.getHeader("Message-ID");
                        if (ids != null && ids.length > 0) mid = ids[0];
                    }
                    items.add(new MailItem(m.getMessageNumber(), mid,
                            m.getFrom() == null ? "" : InternetAddresses(m.getFrom()),
                            m.getAllRecipients() == null ? "" : InternetAddresses(m.getAllRecipients()),
                            Objects.toString(m.getSubject(), ""), extractText(m),
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
        for (Address item : a) join.add(item.toString());
        return join.toString();
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
