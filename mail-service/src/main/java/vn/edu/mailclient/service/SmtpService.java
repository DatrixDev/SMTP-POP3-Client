package vn.edu.mailclient.service;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import vn.edu.mailclient.common.MailAccount;

/** JavaMail SMTP implementation based on textbook Listing 7.3. */
public final class SmtpService {
    public void send(MailAccount account, String recipients, String subject, String body) throws MessagingException {
        send(account, recipients, subject, body, java.util.List.of());
    }

    public void send(MailAccount account, String recipients, String subject, String body,
                     java.util.List<java.io.File> attachments) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", account.smtpHost());
        props.put("mail.smtp.port", Integer.toString(account.smtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "12000");
        props.put("mail.smtp.writetimeout", "12000");
        if (account.smtpHost().equalsIgnoreCase("localhost") || account.smtpHost().equals("127.0.0.1")) {
            // Classroom loopback server does not use TLS; never use this mode over a network.
            props.put("mail.smtp.starttls.enable", "false");
        } else if (account.smtpPort() == 465) {
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
        if (attachments == null || attachments.isEmpty()) {
            message.setText(body, "UTF-8");
        } else {
            MimeMultipart multipart = new MimeMultipart("mixed");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(body, "UTF-8");
            multipart.addBodyPart(textPart);
            for (java.io.File file : attachments) {
                if (!file.isFile() || !file.canRead())
                    throw new MessagingException("Cannot read attachment: " + file);
                MimeBodyPart attachment = new MimeBodyPart();
                try { attachment.attachFile(file); }
                catch (java.io.IOException ex) { throw new MessagingException("Cannot attach: " + file, ex); }
                multipart.addBodyPart(attachment);
            }
            message.setContent(multipart);
        }
        message.setSentDate(new java.util.Date());
        Transport.send(message);
    }
}
