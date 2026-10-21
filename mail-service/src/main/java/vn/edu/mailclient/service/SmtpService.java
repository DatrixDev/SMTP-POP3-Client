package vn.edu.mailclient.service;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import vn.edu.mailclient.common.MailAccount;

/** JavaMail SMTP implementation based on textbook Listing 7.3. */
public final class SmtpService {
    public void send(MailAccount account, String recipients, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", account.smtpHost());
        props.put("mail.smtp.port", Integer.toString(account.smtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "20000");
        if (account.smtpPort() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        Session session = Session.getInstance(props, new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account.email(), account.password());
            }
        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(account.email()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients, true));
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8");
        message.setSentDate(new java.util.Date());
        Transport.send(message);
    }
}
