package vn.edu.mailclient.client.ui;

import java.awt.*;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import vn.edu.mailclient.common.*;
import vn.edu.mailclient.service.*;

public final class SmtpPanel extends JPanel {
    public SmtpPanel(Supplier<MailAccount> accountProvider) {
        super(new BorderLayout(0, 16));
        setOpaque(false);
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));
        header.setOpaque(false);
        header.add(MailStyles.title("Soạn email mới"));
        header.add(MailStyles.muted("Gửi email thật thông qua SMTP Server bằng JavaMail"));
        add(header, BorderLayout.NORTH);

        JPanel card = MailStyles.card(new BorderLayout(0, 18));
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 16));
        fields.setOpaque(false);
        JTextField to = new JTextField();
        JTextField subject = new JTextField();
        fields.add(MailStyles.field("Người nhận (có thể nhập nhiều email, cách nhau bằng dấu phẩy)", to));
        fields.add(MailStyles.field("Tiêu đề", subject));
        card.add(fields, BorderLayout.NORTH);

        JTextArea body = new JTextArea();
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        body.setBorder(new EmptyBorder(14, 16, 14, 16));
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(BorderFactory.createLineBorder(MailStyles.BORDER));
        JPanel message = new JPanel(new BorderLayout(0, 8));
        message.setOpaque(false);
        JLabel label = new JLabel("Nội dung email");
        label.setForeground(MailStyles.TEXT);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        message.add(label, BorderLayout.NORTH);
        message.add(bodyScroll, BorderLayout.CENTER);
        card.add(message, BorderLayout.CENTER);

        JButton send = MailStyles.primary("✉  Gửi email");
        JButton clear = MailStyles.secondary("Xóa nội dung");
        JLabel status = MailStyles.muted("Sẵn sàng gửi thư");
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(clear);
        buttons.add(send);
        actions.add(buttons, BorderLayout.EAST);
        card.add(actions, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        clear.addActionListener(e -> {
            to.setText("");
            subject.setText("");
            body.setText("");
            status.setText("Đã xóa nội dung");
        });
        send.addActionListener(e -> {
            final MailAccount account;
            try {
                account = accountProvider.get();
                if (to.getText().isBlank()) throw new IllegalArgumentException("Vui lòng nhập người nhận");
                if (body.getText().isBlank()) throw new IllegalArgumentException("Vui lòng nhập nội dung email");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
                return;
            }
            send.setEnabled(false);
            clear.setEnabled(false);
            status.setText("Đang gửi thư...");
            new SwingWorker<Void, Void>() {
                private String warning = "";
                @Override protected Void doInBackground() throws Exception {
                    new SmtpService().send(account, to.getText().trim(), subject.getText(), body.getText());
                    try {
                        new MailDatabase().save(account, "SENT",
                                new MailItem(0, "", account.email(), to.getText().trim(),
                                        subject.getText(), body.getText(), ""));
                    } catch (Exception ex) {
                        warning = "Đã gửi, nhưng không lưu được MySQL: " + ex.getMessage();
                    }
                    return null;
                }
                @Override protected void done() {
                    send.setEnabled(true);
                    clear.setEnabled(true);
                    try {
                        get();
                        status.setText(warning.isBlank() ? "✓  Gửi email thành công" : warning);
                        if (warning.isBlank()) JOptionPane.showMessageDialog(SmtpPanel.this,
                                "Email đã được gửi đến SMTP Server.", "Gửi thành công", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        status.setText("Gửi email thất bại");
                        UiErrors.show(SmtpPanel.this, ex);
                    }
                }
            }.execute();
        });
    }
}
