package vn.edu.mailclient.client.ui;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import vn.edu.mailclient.common.*;
import vn.edu.mailclient.service.*;

public final class Pop3Panel extends JPanel {
    private final DefaultListModel<MailItem> model = new DefaultListModel<>();
    private final JList<MailItem> mails = new JList<>(model);
    private final JLabel count = MailStyles.muted("Chưa tải thư từ server");
    private final JLabel subject = MailStyles.title("Chọn một email");
    private final JLabel sender = MailStyles.muted("Người gửi: —");
    private final JLabel recipient = MailStyles.muted("Người nhận: —");
    private final JLabel date = MailStyles.muted("Ngày: —");
    private final JTextArea body = new JTextArea();
    private final JLabel status = MailStyles.muted("Sẵn sàng");
    public Pop3Panel(Supplier<MailAccount> accountProvider) {
        super(new BorderLayout(0, 14));
        setOpaque(false);

        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JPanel titleText = new JPanel(new GridLayout(2, 1, 0, 4));
        titleText.setOpaque(false);
        titleText.add(MailStyles.title("Hộp thư đến"));
        titleText.add(MailStyles.muted("Đọc email trực tiếp qua POP3, không qua server trung gian"));
        titleBar.add(titleText, BorderLayout.WEST);
        JButton refresh = MailStyles.primary("↻  Làm mới");
        titleBar.add(refresh, BorderLayout.EAST);
        add(titleBar, BorderLayout.NORTH);

        JPanel listCard = MailStyles.card(new BorderLayout(0, 12));
        JPanel listHead = new JPanel(new GridLayout(2, 1, 0, 6));
        listHead.setOpaque(false);
        listHead.add(MailStyles.title("Danh sách email"));
        listHead.add(count);
        listCard.add(listHead, BorderLayout.NORTH);

        mails.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mails.setFixedCellHeight(76);
        mails.setBorder(new EmptyBorder(4, 4, 4, 4));
        mails.setCellRenderer(new MailCellRenderer());
        JScrollPane listScroll = new JScrollPane(mails);
        listScroll.setBorder(BorderFactory.createLineBorder(MailStyles.BORDER));
        listCard.add(listScroll, BorderLayout.CENTER);

        JPanel messageCard = MailStyles.card(new BorderLayout(0, 12));
        JPanel detailHeading = new JPanel(new BorderLayout());
        detailHeading.setOpaque(false);
        detailHeading.add(MailStyles.title("Nội dung email"), BorderLayout.WEST);
        detailHeading.add(status, BorderLayout.EAST);

        JPanel metadata = new JPanel();
        metadata.setOpaque(false);
        metadata.setLayout(new BoxLayout(metadata, BoxLayout.Y_AXIS));
        metadata.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, MailStyles.BORDER),
                new EmptyBorder(14, 4, 14, 4)));
        metadata.add(subject);
        metadata.add(Box.createVerticalStrut(12));
        metadata.add(sender);
        metadata.add(Box.createVerticalStrut(6));
        metadata.add(recipient);
        metadata.add(Box.createVerticalStrut(6));
        metadata.add(date);

        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        body.setForeground(MailStyles.TEXT);
        body.setBorder(new EmptyBorder(15, 15, 15, 15));
        body.setText("Chọn một email trong danh sách bên trái để xem nội dung.");
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(BorderFactory.createLineBorder(MailStyles.BORDER));
        messageCard.add(bodyScroll, BorderLayout.CENTER);

        // NORTH must contain both title and message metadata; replacing the first slot would hide the title.
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(detailHeading, BorderLayout.NORTH);
        north.add(metadata, BorderLayout.CENTER);
        messageCard.add(north, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, messageCard);
        split.setResizeWeight(0.40);
        split.setDividerSize(12);
        split.setBorder(null);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        mails.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            MailItem item = mails.getSelectedValue();
            if (item == null) return;
            subject.setText("<html>" + escape(item.subject().isBlank() ? "(Không có tiêu đề)" : item.subject()) + "</html>");
            sender.setText("Từ: " + item.from());
            recipient.setText("Đến: " + item.to());
            date.setText("Ngày: " + item.sentAt());
            body.setText(item.body());
            body.setCaretPosition(0);
            status.setText("Đã chọn email #" + item.number());
        });
        refresh.addActionListener(e -> {
            final MailAccount account;
            try { account = accountProvider.get(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu tài khoản", JOptionPane.WARNING_MESSAGE); return; }
            refresh.setEnabled(false);
            status.setText("Đang kết nối...");
            new SwingWorker<List<MailItem>, Void>() {
                private String warning = "";
                @Override protected List<MailItem> doInBackground() throws Exception {
                    List<MailItem> items = new Pop3Service().inbox(account);
                    MailDatabase db = new MailDatabase();
                    if (db.enabled()) {
                        for (MailItem item : items) {
                            try { db.save(account, "RECEIVED", item); }
                            catch (Exception ex) { warning = "Không lưu được MySQL: " + ex.getMessage(); break; }
                        }
                    }
                    return items;
                }
                @Override protected void done() {
                    refresh.setEnabled(true);
                    try {
                        List<MailItem> items = get();
                        model.clear();
                        for (MailItem item : items) model.addElement(item);
                        count.setText("Đã nhận " + items.size() + " email từ POP3 Server");
                        status.setText(warning.isEmpty() ? "Đã cập nhật" : warning);
                        if (!model.isEmpty()) mails.setSelectedIndex(0);
                    } catch (Exception ex) {
                        status.setText("Kết nối thất bại");
                        UiErrors.show(Pop3Panel.this, ex);
                    }
                }
            }.execute();
        });
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class MailCellRenderer extends JPanel implements ListCellRenderer<MailItem> {
        private final JLabel from = new JLabel();
        private final JLabel subject = new JLabel();
        private final JLabel timestamp = new JLabel();
        MailCellRenderer() {
            super(new BorderLayout(8, 2));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            JPanel center = new JPanel(new GridLayout(2, 1));
            center.setOpaque(false);
            from.setFont(new Font("Segoe UI", Font.BOLD, 13));
            subject.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            center.add(from);
            center.add(subject);
            timestamp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            add(center, BorderLayout.CENTER);
            add(timestamp, BorderLayout.SOUTH);
        }
        @Override public Component getListCellRendererComponent(JList<? extends MailItem> list, MailItem value, int index, boolean selected, boolean focused) {
            from.setText(value.from());
            subject.setText(value.subject().isBlank() ? "(Không có tiêu đề)" : value.subject());
            timestamp.setText(value.sentAt());
            setBackground(selected ? MailStyles.PALE : Color.WHITE);
            from.setForeground(MailStyles.TEXT);
            subject.setForeground(selected ? MailStyles.BLUE : MailStyles.MUTED);
            timestamp.setForeground(MailStyles.MUTED);
            return this;
        }
    }
}
