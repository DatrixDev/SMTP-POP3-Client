package vn.edu.mailclient.client.ui;

import java.awt.*;
import java.util.function.Supplier;
import javax.swing.*;
import vn.edu.mailclient.common.*;
import vn.edu.mailclient.service.*;

public final class SmtpPanel extends JPanel {
    public SmtpPanel(Supplier<MailAccount> accountProvider) {
        super(new BorderLayout(10,10));
        setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JPanel fields = new JPanel(new GridLayout(2,2,8,8));
        JTextField to = new JTextField();
        JTextField subject = new JTextField();
        fields.add(new JLabel("Đến (cách nhau bằng dấu phẩy)")); fields.add(to);
        fields.add(new JLabel("Tiêu đề")); fields.add(subject);
        JTextArea body = new JTextArea();
        body.setLineWrap(true); body.setWrapStyleWord(true);
        JButton send = new JButton("Gửi email");
        JLabel status = new JLabel("Sẵn sàng");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER); bottom.add(send, BorderLayout.EAST);
        add(fields, BorderLayout.NORTH); add(new JScrollPane(body), BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);
        send.addActionListener(e -> {
            final MailAccount account;
            try { account = accountProvider.get(); if (to.getText().isBlank()) throw new IllegalArgumentException("Nhập người nhận"); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); return; }
            send.setEnabled(false); status.setText("Đang gửi...");
            new SwingWorker<Void,Void>() {
                private String warning = "";
                @Override protected Void doInBackground() throws Exception {
                    new SmtpService().send(account,to.getText().trim(),subject.getText(),body.getText());
                    try {
                        new MailDatabase().save(account,"SENT",new MailItem(0,"",account.email(),to.getText().trim(),subject.getText(),body.getText(),""));
                    } catch (Exception ex) { warning = "Đã gửi, nhưng không lưu được MySQL: " + ex.getMessage(); }
                    return null;
                }
                @Override protected void done() {
                    send.setEnabled(true);
                    try { get(); status.setText(warning.isEmpty() ? "Đã gửi thành công" : warning); }
                    catch (Exception ex) { status.setText("Gửi thất bại"); UiErrors.show(SmtpPanel.this,ex); }
                }
            }.execute();
        });
    }
}
