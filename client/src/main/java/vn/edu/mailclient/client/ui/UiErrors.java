package vn.edu.mailclient.client.ui;
import java.awt.Component;
import javax.swing.JOptionPane;
final class UiErrors {
    private UiErrors() {}
    static void show(Component parent, Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        JOptionPane.showMessageDialog(parent, cause.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
}
