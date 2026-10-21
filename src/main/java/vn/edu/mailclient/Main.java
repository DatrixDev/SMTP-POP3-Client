package vn.edu.mailclient;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.*;

/** Desktop UI for SMTP sending and POP3 inbox listing. */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(Main::show);
    }
    private static void show() {
        JFrame frame = new JFrame("SMTP & POP3 Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("SMTP - Gửi thư", smtpPanel());
        tabs.addTab("POP3 - Hộp thư", pop3Panel());
        frame.add(tabs);
        frame.setSize(760, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    private static JPanel smtpPanel() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField host = new JTextField("smtp.gmail.com");
        JTextField port = new JTextField("587");
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JTextField to = new JTextField();
        JTextField subject = new JTextField();
        fields.add(new JLabel("SMTP host")); fields.add(host);
        fields.add(new JLabel("Port (STARTTLS)")); fields.add(port);
        fields.add(new JLabel("Email gửi")); fields.add(user);
        fields.add(new JLabel("App password")); fields.add(pass);
        fields.add(new JLabel("Email nhận")); fields.add(to);
        fields.add(new JLabel("Tiêu đề")); fields.add(subject);
        JTextArea body = new JTextArea(12, 50);
        root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        root.add(fields, BorderLayout.NORTH);
        root.add(new JScrollPane(body), BorderLayout.CENTER);
        JButton send = new JButton("Gửi email");
        root.add(send, BorderLayout.SOUTH);
        send.addActionListener(e -> {
            send.setEnabled(false);
            String password = new String(pass.getPassword());
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    new SmtpClient().send(host.getText().trim(), Integer.parseInt(port.getText().trim()),
                            user.getText().trim(), password, to.getText().trim(),
                            subject.getText(), body.getText());
                    return null;
                }
                @Override protected void done() {
                    send.setEnabled(true);
                    try { get(); JOptionPane.showMessageDialog(root, "Gửi thư thành công"); }
                    catch (Exception ex) { showError(root, ex); }
                }
            }.execute();
        });
        return root;
    }
    private static JPanel pop3Panel() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField host = new JTextField("pop.gmail.com");
        JTextField port = new JTextField("995");
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        fields.add(new JLabel("POP3 host")); fields.add(host);
        fields.add(new JLabel("Port (SSL)")); fields.add(port);
        fields.add(new JLabel("Email")); fields.add(user);
        fields.add(new JLabel("App password")); fields.add(pass);
        root.add(fields, BorderLayout.NORTH);
        DefaultListModel<Pop3Client.MailInfo> model = new DefaultListModel<>();
        root.add(new JScrollPane(new JList<>(model)), BorderLayout.CENTER);
        JButton load = new JButton("Tải danh sách email");
        root.add(load, BorderLayout.SOUTH);
        load.addActionListener(e -> {
            load.setEnabled(false);
            String password = new String(pass.getPassword());
            new SwingWorker<List<Pop3Client.MailInfo>, Void>() {
                @Override protected List<Pop3Client.MailInfo> doInBackground() throws Exception {
                    return new Pop3Client().listInbox(host.getText().trim(),
                            Integer.parseInt(port.getText().trim()), user.getText().trim(), password);
                }
                @Override protected void done() {
                    load.setEnabled(true);
                    try { model.clear(); for (Pop3Client.MailInfo m : get()) model.addElement(m); }
                    catch (Exception ex) { showError(root, ex); }
                }
            }.execute();
        });
        return root;
    }
    private static void showError(JPanel panel, Exception error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        JOptionPane.showMessageDialog(panel, cause.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
