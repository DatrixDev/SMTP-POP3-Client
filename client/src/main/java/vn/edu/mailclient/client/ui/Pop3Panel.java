package vn.edu.mailclient.client.ui;

import java.awt.*;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import vn.edu.mailclient.common.*;
import vn.edu.mailclient.service.*;

public final class Pop3Panel extends JPanel {
    public Pop3Panel(Supplier<MailAccount> accountProvider) {
        super(new BorderLayout(8,8));
        setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        DefaultListModel<MailItem> model = new DefaultListModel<>();
        JList<MailItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextArea details = new JTextArea();
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(list),new JScrollPane(details));
        split.setResizeWeight(.4);
        JButton refresh = new JButton("Tải thư từ POP3");
        JLabel status = new JLabel("Chưa kết nối");
        JPanel top = new JPanel(new BorderLayout());
        top.add(status,BorderLayout.CENTER); top.add(refresh,BorderLayout.EAST);
        add(top,BorderLayout.NORTH); add(split,BorderLayout.CENTER);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            MailItem item = list.getSelectedValue();
            details.setText(item == null ? "" : "Từ: " + item.from() + "\nĐến: " + item.to()
                    + "\nNgày: " + item.sentAt() + "\nTiêu đề: " + item.subject() + "\n\n" + item.body());
            details.setCaretPosition(0);
        });
        refresh.addActionListener(e -> {
            final MailAccount account;
            try { account = accountProvider.get(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this,ex.getMessage()); return; }
            refresh.setEnabled(false); status.setText("Đang tải...");
            new SwingWorker<List<MailItem>,Void>() {
                private String warning = "";
                @Override protected List<MailItem> doInBackground() throws Exception {
                    List<MailItem> mails = new Pop3Service().inbox(account);
                    MailDatabase db = new MailDatabase();
                    if (db.enabled()) for (MailItem item : mails) {
                        try { db.save(account,"RECEIVED",item); }
                        catch (Exception ex) { warning = "Lưu MySQL thất bại: " + ex.getMessage(); break; }
                    }
                    return mails;
                }
                @Override protected void done() {
                    refresh.setEnabled(true);
                    try {
                        List<MailItem> mails = get();
                        model.clear(); details.setText("");
                        for (MailItem mail : mails) model.addElement(mail);
                        status.setText("Đã tải " + mails.size() + " thư" + (warning.isEmpty() ? "" : " | " + warning));
                    } catch (Exception ex) { status.setText("Tải thư thất bại"); UiErrors.show(Pop3Panel.this,ex); }
                }
            }.execute();
        });
    }
}
