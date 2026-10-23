package vn.edu.mailclient.client;

import vn.edu.mailclient.client.fx.MailFxApp;

/** Plain Java launcher avoids JavaFX module-path launcher restrictions with mvn exec:java. */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        javafx.application.Application.launch(MailFxApp.class, args);
    }
}
