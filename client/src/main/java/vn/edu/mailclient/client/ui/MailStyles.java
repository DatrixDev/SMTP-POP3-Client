package vn.edu.mailclient.client.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Shared visual language inspired by the UDPMail layout, implemented independently. */
final class MailStyles {
    static final Color NAVY = new Color(0x082E63);
    static final Color BLUE = new Color(0x1477F8);
    static final Color BACKGROUND = new Color(0xF5F8FC);
    static final Color TEXT = new Color(0x071B41);
    static final Color MUTED = new Color(0x64748B);
    static final Color BORDER = new Color(0xD7E1EF);
    static final Color PALE = new Color(0xE7F2FF);
    static final Color GREEN = new Color(0x16A34A);
    private MailStyles() {}
    static JLabel title(String value) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", Font.BOLD, 22));
        label.setForeground(TEXT);
        return label;
    }
    static JLabel muted(String value) {
        JLabel label = new JLabel(value);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(MUTED);
        return label;
    }
    static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(18, 20, 18, 20)));
        return panel;
    }
    static JButton primary(String value) {
        JButton button = new JButton(value);
        button.setBackground(BLUE);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    static JButton secondary(String value) {
        JButton button = new JButton(value);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    static JPanel field(String caption, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel label = new JLabel(caption);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(TEXT);
        panel.add(label, BorderLayout.NORTH);
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setPreferredSize(new Dimension(110, 36));
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }
}
