package vn.edu.mailclient.client.ui;

import java.awt.*;
import javax.swing.*;
import vn.edu.mailclient.common.MailAccount;

public final class MainFrame extends JFrame {
    private final JTextField email = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JTextField smtpHost = new JTextField("smtp.gmail.com");
    private final JTextField smtpPort = new JTextField("587");
    private final JTextField popHost = new JTextField("pop.gmail.com");
    private final JTextField popPort = new JTextField("995");
    public MainFrame() {
        super("SMTP / POP3 Mail Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setMinimumSize(new Dimension(760, 540));
        setLocationRelativeTo(null);
        JPanel connection = new JPanel(new GridLayout(2, 6, 8, 4));
        connection.setBorder(BorderFactory.createTitledBorder("Kết nối tài khoản (không lưu mật khẩu)"));
        for (String label : new String[]{"Email", "App password", "SMTP host", "SMTP port", "POP3 host", "POP3 port"})
            connection.add(new JLabel(label));
        for (JComponent field : new JComponent[]{email,password,smtpHost,smtpPort,popHost,popPort}) connection.add(field);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Gửi email (SMTP)", new SmtpPanel(this::account));
        tabs.addTab("Hộp thư đến (POP3)", new Pop3Panel(this::account));
        add(connection, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }
    private MailAccount account() {
        return new MailAccount(email.getText().trim(), new String(password.getPassword()),
                smtpHost.getText().trim(), Integer.parseInt(smtpPort.getText().trim()),
                popHost.getText().trim(), Integer.parseInt(popPort.getText().trim()));
    }
}
