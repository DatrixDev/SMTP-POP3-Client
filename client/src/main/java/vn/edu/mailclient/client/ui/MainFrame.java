package vn.edu.mailclient.client.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import vn.edu.mailclient.common.MailAccount;

public final class MainFrame extends JFrame {
    private final JTextField email = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JTextField smtpHost = new JTextField("smtp.gmail.com");
    private final JTextField smtpPort = new JTextField("587");
    private final JTextField popHost = new JTextField("pop.gmail.com");
    private final JTextField popPort = new JTextField("995");
    private final CardLayout pages = new CardLayout();
    private final JPanel workspace = new JPanel(pages);
    private final JLabel footer = new JLabel("Sẵn sàng  •  SMTP / POP3 JavaMail");
    private final JLabel identity = new JLabel("Chưa cấu hình tài khoản");
    private JButton inboxNav;
    private JButton composeNav;
    private JButton settingsNav;
    private final Pop3Panel inbox;
    private final SmtpPanel compose;

    public MainFrame() {
        super("SMTP / POP3 Mail Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new Dimension(960, 610));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(MailStyles.BACKGROUND);
        setContentPane(root);

        inbox = new Pop3Panel(this::account);
        compose = new SmtpPanel(this::account);
        workspace.setOpaque(false);
        workspace.add(inbox, "inbox");
        workspace.add(compose, "compose");
        workspace.add(settingsPanel(), "settings");

        root.add(header(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(17, 17, 17, 17));
        content.add(workspace, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);
        root.add(statusBar(), BorderLayout.SOUTH);
        navigate("inbox");
    }

    private JComponent header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(MailStyles.NAVY);
        header.setBorder(new EmptyBorder(16, 24, 16, 24));
        JLabel logo = new JLabel("✉  SMTP / POP3 Mail");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 25));
        header.add(logo, BorderLayout.WEST);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 4));
        info.setOpaque(false);
        JLabel server = new JLabel("●  SMTP + POP3");
        server.setForeground(new Color(0x63E6A2));
        server.setFont(new Font("Segoe UI", Font.BOLD, 13));
        identity.setForeground(Color.WHITE);
        identity.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        info.add(server);
        info.add(identity);
        header.add(info, BorderLayout.EAST);
        return header;
    }

    private JComponent sidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(MailStyles.NAVY);
        side.setBorder(new EmptyBorder(24, 13, 22, 13));
        side.setPreferredSize(new Dimension(225, 0));

        JLabel section = new JLabel("HỘP THƯ");
        section.setForeground(new Color(0xAFC7E7));
        section.setFont(new Font("Segoe UI", Font.BOLD, 12));
        section.setBorder(new EmptyBorder(0, 10, 16, 0));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(section);

        inboxNav = navButton("▣   Hộp thư đến");
        composeNav = navButton("✎   Soạn thư mới");
        settingsNav = navButton("⚙   Cấu hình server");
        side.add(inboxNav);
        side.add(Box.createVerticalStrut(8));
        side.add(composeNav);
        side.add(Box.createVerticalStrut(8));
        side.add(settingsNav);
        side.add(Box.createVerticalGlue());

        JButton newMail = MailStyles.primary("✎   Viết email");
        newMail.setAlignmentX(Component.LEFT_ALIGNMENT);
        newMail.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        side.add(newMail);
        inboxNav.addActionListener(e -> navigate("inbox"));
        composeNav.addActionListener(e -> navigate("compose"));
        settingsNav.addActionListener(e -> navigate("settings"));
        newMail.addActionListener(e -> navigate("compose"));
        return side;
    }
    private JButton navButton(String text) {
        JButton btn = new JButton(text);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setForeground(Color.WHITE);
        btn.setBackground(MailStyles.NAVY);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorder(new EmptyBorder(13, 16, 13, 12));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
    private void navigate(String name) {
        pages.show(workspace, name);
        navActive(inboxNav, "inbox".equals(name));
        navActive(composeNav, "compose".equals(name));
        navActive(settingsNav, "settings".equals(name));
        footer.setText(switch (name) {
            case "compose" -> "SMTP  •  Soạn và gửi email";
            case "settings" -> "Kết nối  •  Cấu hình Gmail hoặc máy chủ thư khác";
            default -> "POP3  •  Nhận và đọc email";
        });
    }
    private void navActive(JButton button, boolean active) {
        button.setBackground(active ? MailStyles.BLUE : MailStyles.NAVY);
        button.setOpaque(active);
    }

    private JComponent settingsPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(MailStyles.title("Cấu hình tài khoản & Mail Server"), BorderLayout.NORTH);
        heading.add(MailStyles.muted("Sử dụng SMTP / POP3 của Gmail hoặc dịch vụ mail khác. Không lưu mật khẩu."), BorderLayout.SOUTH);
        wrapper.add(heading, BorderLayout.NORTH);

        JPanel card = MailStyles.card(new BorderLayout(0, 18));
        JPanel fields = new JPanel(new GridLayout(0, 2, 18, 18));
        fields.setOpaque(false);
        fields.add(MailStyles.field("Địa chỉ email", email));
        fields.add(MailStyles.field("App password", password));
        fields.add(MailStyles.field("SMTP server", smtpHost));
        fields.add(MailStyles.field("SMTP port (587 STARTTLS / 465 SSL)", smtpPort));
        fields.add(MailStyles.field("POP3 server", popHost));
        fields.add(MailStyles.field("POP3 port (SSL)", popPort));
        card.add(fields, BorderLayout.NORTH);

        JTextArea hint = new JTextArea(
                "• Gmail: smtp.gmail.com:587 và pop.gmail.com:995.\n" +
                "• Cần bật quyền POP3 nếu nhà cung cấp yêu cầu.\n" +
                "• Chỉ nhập mật khẩu ứng dụng trên máy cá nhân, không đưa lên GitHub.\n" +
                "• MySQL lưu lịch sử cục bộ; SMTP và POP3 vẫn kết nối trực tiếp tới Mail Server.");
        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setForeground(MailStyles.MUTED);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        hint.setBorder(new EmptyBorder(15, 0, 0, 0));
        card.add(hint, BorderLayout.CENTER);

        JButton apply = MailStyles.primary("Áp dụng tài khoản");
        apply.addActionListener(e -> {
            try {
                MailAccount current = account();
                identity.setText(current.email());
                navigate("inbox");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu cấu hình", JOptionPane.WARNING_MESSAGE);
            }
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(apply);
        card.add(actions, BorderLayout.SOUTH);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }
    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(MailStyles.NAVY);
        bar.setBorder(new EmptyBorder(10, 20, 10, 20));
        footer.setForeground(Color.WHITE);
        bar.add(footer, BorderLayout.WEST);
        JLabel right = new JLabel("JavaMail  •  MySQL (tùy chọn)");
        right.setForeground(new Color(0xCBDCF2));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }
    private MailAccount account() {
        return new MailAccount(email.getText().trim(), new String(password.getPassword()),
                smtpHost.getText().trim(), Integer.parseInt(smtpPort.getText().trim()),
                popHost.getText().trim(), Integer.parseInt(popPort.getText().trim()));
    }
}
