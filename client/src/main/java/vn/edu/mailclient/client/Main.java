package vn.edu.mailclient.client;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.SwingUtilities;
import vn.edu.mailclient.client.ui.MainFrame;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
