package vn.edu.mailclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

/** POP3 client using JavaMail Store/Folder/Message as in textbook Listing 7.4. */
public final class Pop3Client {
    public static final class MailInfo {
        public final int number;
        public final String from;
        public final String subject;
        public MailInfo(int number, String from, String subject) {
            this.number = number; this.from = from; this.subject = subject;
        }
        @Override public String toString() { return number + " | " + from + " | " + subject; }
    }

    public List<MailInfo> listInbox(String host, int port, String username, String password)
            throws MessagingException {
        Properties props = new Properties();
        props.put("mail.pop3s.host", host);
        props.put("mail.pop3s.port", Integer.toString(port));
        props.put("mail.pop3s.ssl.enable", "true");
        props.put("mail.pop3s.connectiontimeout", "10000");
        props.put("mail.pop3s.timeout", "15000");
        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;
        try {
            store = session.getStore("pop3s");
            store.connect(host, port, username, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            List<MailInfo> result = new ArrayList<>();
            Message[] messages = inbox.getMessages();
            for (int i = messages.length - 1; i >= 0; i--) {
                Message m = messages[i];
                result.add(new MailInfo(m.getMessageNumber(),
                        m.getFrom() == null ? "" : java.util.Arrays.toString(m.getFrom()),
                        m.getSubject() == null ? "" : m.getSubject()));
            }
            return result;
        } finally {
            if (inbox != null && inbox.isOpen()) inbox.close(false);
            if (store != null && store.isConnected()) store.close();
        }
    }
}
