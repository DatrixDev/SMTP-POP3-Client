package vn.edu.mailclient.client.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import vn.edu.mailclient.client.config.MailConfig;
import vn.edu.mailclient.common.MailAccount;
import vn.edu.mailclient.common.MailItem;
import vn.edu.mailclient.service.MailDatabase;
import vn.edu.mailclient.service.Pop3Service;
import vn.edu.mailclient.service.ImapService;
import vn.edu.mailclient.client.lan.LanClient;
import vn.edu.mailclient.service.SmtpService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MailFxApp extends Application {
    private final ExecutorService workers = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-worker");
        t.setDaemon(true);
        return t;
    });
    private final BorderPane root = new BorderPane();
    private final Label accountLabel = new Label();
    private final Label status = new Label("Sẵn sàng");
    private final Label inboxCount = new Label("Nhấn Làm mới để tải email");
    private final ListView<MailItem> mailList = new ListView<>();
    private final Label detailSubject = new Label("Chọn một email để đọc");
    private final Label detailFrom = new Label("Người gửi: —");
    private final Label detailTo = new Label("Người nhận: —");
    private final Label detailDate = new Label("Ngày: —");
    private final TextArea detailBody = new TextArea();
    private final TextField email = new TextField(), smtpHost = new TextField(), smtpPort = new TextField();
    private final TextField popHost = new TextField(), popPort = new TextField();
    private final PasswordField password = new PasswordField();
    private Button inboxNav, composeNav, settingsNav, lanNav;
    private String lanToken;
    private String lanUser;
    private Stage stage;
    private final Preferences syncState = Preferences.userNodeForPackage(MailFxApp.class);
    private Timeline imapPolling;
    private boolean imapSyncBusy = false;
    private int inboxOffset = 0;
    private final ComboBox<String> receiveMode = new ComboBox<>(FXCollections.observableArrayList("POP3 (bài lab)", "IMAP (thư mới)"));
    private static final int PAGE_SIZE = 20;
    private final Label pageLabel = new Label("Trang 1");

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        MailConfig cfg = MailConfig.load();
        email.setText(cfg.email()); password.setText(cfg.password());
        smtpHost.setText(cfg.smtpHost()); smtpPort.setText(String.valueOf(cfg.smtpPort()));
        popHost.setText(cfg.popHost()); popPort.setText(String.valueOf(cfg.popPort()));
        accountLabel.setText(cfg.email().isBlank() ? "Chưa cấu hình tài khoản" : cfg.email());

        root.setTop(header());
        root.setLeft(sidebar());
        root.setBottom(footer());
        root.setStyle("-fx-background-color: #f3f6fb;");
        Scene scene = new Scene(root, 1280, 800);
        stage.setScene(scene);
        stage.setMinWidth(980); stage.setMinHeight(640);
        stage.setTitle("SMTP / POP3 Mail • JavaFX");
        showPage(cfg.email().isBlank() || cfg.password().isBlank() ? "settings" : "inbox");
        stage.show();
        imapPolling = new Timeline(new KeyFrame(Duration.seconds(45), e -> pollImap()));
        imapPolling.setCycleCount(Timeline.INDEFINITE);
        imapPolling.play();
    }

    private HBox header() {
        Label logo = new Label("✉  SMTP / POP3 Mail");
        logo.setFont(Font.font("System", FontWeight.BOLD, 23)); logo.setTextFill(Color.WHITE);
        Label mark = new Label("●  SMTP + POP3");
        mark.setTextFill(Color.web("#65e6b0")); mark.setStyle("-fx-font-weight: bold;");
        accountLabel.setTextFill(Color.WHITE);
        HBox right = new HBox(18, mark, accountLabel); right.setAlignment(Pos.CENTER_RIGHT);
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        HBox h = new HBox(20, logo, space, right);
        h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(20, 28, 20, 28));
        h.setStyle("-fx-background-color: #0c2b59;");
        return h;
    }

    private VBox sidebar() {
        Label section = new Label("HỘP THƯ"); section.setTextFill(Color.web("#a9c5e6"));
        section.setStyle("-fx-font-weight: bold;");
        inboxNav = nav("▣   Hộp thư đến", "inbox");
        composeNav = nav("✎   Soạn thư mới", "compose");
        settingsNav = nav("⚙   Cấu hình server", "settings");
        lanNav = nav("◉   Mail nội bộ LAN", "lan");
        VBox side = new VBox(12, section, inboxNav, composeNav, lanNav, settingsNav);
        side.setPadding(new Insets(28, 14, 20, 14)); side.setPrefWidth(235);
        side.setStyle("-fx-background-color: #0c2b59;");
        return side;
    }

    private Button nav(String label, String page) {
        Button b = new Button(label);
        b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 15 14;");
        b.setOnAction(e -> showPage(page));
        return b;
    }

    private void showPage(String page) {
        if (inboxNav != null) {
            highlight(inboxNav, page.equals("inbox"));
            highlight(composeNav, page.equals("compose"));
            highlight(settingsNav, page.equals("settings"));
            highlight(lanNav, page.equals("lan"));
        }
        root.setCenter(switch (page) {
            case "compose" -> composePage();
            case "settings" -> settingsPage();
            case "lan" -> lanPage();
            default -> inboxPage();
        });
    }

    private void highlight(Button button, boolean active) {
        button.setStyle("-fx-background-color: " + (active ? "#1677f2" : "transparent")
                + "; -fx-background-radius: 9; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 15 14;");
    }

    private VBox page(String heading, String description) {
        Label title = new Label(heading); title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(Color.web("#122a4e"));
        Label sub = new Label(description); sub.setTextFill(Color.web("#72839b"));
        VBox pane = new VBox(8, title, sub);
        pane.setPadding(new Insets(25));
        return pane;
    }

    private Pane inboxPage() {
        VBox wrapper = page("Hộp thư đến", "Đọc thư trực tiếp từ Gmail qua giao thức POP3");
        if (receiveMode.getValue() == null) receiveMode.getSelectionModel().selectFirst();
        receiveMode.setOnAction(e -> { inboxOffset = 0; inboxCount.setText("Nhấn Làm mới để tải thư qua " + receiveMode.getValue()); });
        Button refresh = primary("↻  Làm mới");
        Button previous = new Button("‹ Trước");
        Button next = new Button("Tiếp ›");
        previous.setOnAction(e -> { inboxOffset = Math.max(0, inboxOffset - PAGE_SIZE); refreshInbox(refresh); });
        next.setOnAction(e -> { inboxOffset += PAGE_SIZE; refreshInbox(refresh); });
        refresh.setOnAction(e -> { inboxOffset = 0; refreshInbox(refresh); });
        pageLabel.setText("Trang " + (inboxOffset / PAGE_SIZE + 1));
        HBox paging = new HBox(9, previous, pageLabel, next);
        paging.setAlignment(Pos.CENTER_RIGHT);
        HBox heading = new HBox(20, wrapper, new Region(), receiveMode, refresh);
        HBox.setHgrow(heading.getChildren().get(1), Priority.ALWAYS);
        heading.setAlignment(Pos.CENTER_LEFT); heading.setPadding(new Insets(0, 22, 0, 0));

        Label listTitle = new Label("Danh sách email"); listTitle.setFont(Font.font("System", FontWeight.BOLD, 19));
        inboxCount.setTextFill(Color.web("#6f829e"));
        mailList.setPlaceholder(new Label("Chưa có email. Nhấn Làm mới."));
        mailList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(MailItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label from = new Label(item.from()); from.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                Label subject = new Label(item.subject().isBlank() ? "(Không có tiêu đề)" : item.subject());
                subject.setTextFill(Color.web("#356bb5"));
                Label date = new Label(item.sentAt()); date.setTextFill(Color.web("#8290a3"));
                VBox vbox = new VBox(5, from, subject, date);
                vbox.setMinWidth(0);
                // Bind labels to the actual cell width so long sender names do not cause horizontal scrolling.
                vbox.prefWidthProperty().bind(widthProperty().subtract(24));
                for (Label l : new Label[]{from, subject, date}) {
                    l.setMaxWidth(Double.MAX_VALUE); l.setTextOverrun(OverrunStyle.ELLIPSIS);
                }
                vbox.setPadding(new Insets(10, 7, 10, 7));
                setGraphic(vbox); setText(null);
            }
        });
        VBox left = new VBox(12, listTitle, inboxCount, mailList, paging);
        left.setMinWidth(0);
        left.setPadding(new Insets(22)); left.setStyle(cardStyle());
        VBox.setVgrow(mailList, Priority.ALWAYS);

        Label detailTitle = new Label("Nội dung email"); detailTitle.setFont(Font.font("System", FontWeight.BOLD, 19));
        detailSubject.setFont(Font.font("System", FontWeight.BOLD, 20));
        detailSubject.setWrapText(true);
        detailBody.setEditable(false); detailBody.setWrapText(true);
        detailBody.setPromptText("Chọn email bên trái để xem nội dung");
        VBox right = new VBox(13, detailTitle, new Separator(), detailSubject, detailFrom, detailTo,
                detailDate, new Separator(), detailBody);
        right.setPadding(new Insets(22)); right.setStyle(cardStyle());
        right.setMinWidth(0);
        for (Label label : new Label[]{detailFrom, detailTo, detailDate}) {
            label.setWrapText(true);
            label.setMaxWidth(Double.MAX_VALUE);
        }
        detailSubject.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(detailBody, Priority.ALWAYS);
        mailList.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item == null) return;
            detailSubject.setText(item.subject().isBlank() ? "(Không có tiêu đề)" : item.subject());
            detailFrom.setText("Từ: " + item.from());
            detailTo.setText("Đến: " + item.to());
            detailDate.setText("Ngày: " + item.sentAt());
            detailBody.setText(item.body()); detailBody.positionCaret(0);
        });
        SplitPane split = new SplitPane(left, right);
        split.setMinWidth(0);
        split.setDividerPositions(0.38); split.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox content = new VBox(0, heading, split);
        content.setPadding(new Insets(0, 20, 20, 20));
        return content;
    }

    private void refreshInbox(Button refresh) {
        final MailAccount acc;
        try { acc = account(); } catch (Exception ex) { error(ex); return; }
        boolean imap = receiveMode.getValue() != null && receiveMode.getValue().startsWith("IMAP");
        int offset = inboxOffset;
        refresh.setDisable(true); status.setText("Đang tải email từ " + (imap ? "IMAP" : "POP3") + "...");
        Task<List<MailItem>> task = new Task<>() {
            @Override protected List<MailItem> call() throws Exception {
                List<MailItem> items = imap ? new ImapService().inbox(acc, offset, PAGE_SIZE)
                                            : new Pop3Service().inbox(acc, offset, PAGE_SIZE);
                MailDatabase db = new MailDatabase();
                if (db.enabled()) {
                    for (MailItem item : items) {
                        try { db.save(acc, "RECEIVED", item); }
                        catch (Exception ex) { System.err.println("MySQL: " + ex.getMessage()); break; }
                    }
                }
                return items;
            }
        };
        task.setOnSucceeded(e -> {
            refresh.setDisable(false);
            List<MailItem> items = task.getValue();
            mailList.setItems(FXCollections.observableArrayList(items));
            inboxCount.setText("Đã tải " + items.size() + " email từ " + (imap ? "IMAP" : "POP3"));
            pageLabel.setText("Trang " + (inboxOffset / PAGE_SIZE + 1));
            status.setText("Đã cập nhật hộp thư");
            if (!items.isEmpty()) mailList.getSelectionModel().selectFirst();
        });
        task.setOnFailed(e -> { refresh.setDisable(false); status.setText("Tải email thất bại"); error(task.getException()); });
        workers.submit(task);
    }

    /** Background IMAP UID polling; keeps the POP3 lab path untouched. */
    private void pollImap() {
        if (imapSyncBusy || receiveMode.getValue() == null || !receiveMode.getValue().startsWith("IMAP")) return;
        if (root.getCenter() == null || !root.getCenter().getClass().equals(VBox.class)) return;
        final MailAccount acc;
        try { acc = account(); } catch (Exception ignored) { return; }
        final String key = "imap.uid." + acc.email().toLowerCase(java.util.Locale.ROOT);
        final long previous = syncState.getLong(key, 0L);
        imapSyncBusy = true;
        Task<ImapService.SyncBatch> task = new Task<>() {
            @Override protected ImapService.SyncBatch call() throws Exception {
                return new ImapService().sinceUid(acc, previous, 100);
            }
        };
        task.setOnSucceeded(e -> {
            imapSyncBusy = false;
            ImapService.SyncBatch batch = task.getValue();
            if (batch.lastUid() > previous) syncState.putLong(key, batch.lastUid());
            if (!batch.messages().isEmpty()) {
                status.setText("IMAP: có " + batch.messages().size() + " thư mới");
                if (inboxOffset == 0) {
                    java.util.List<MailItem> updated = new java.util.ArrayList<>(batch.messages());
                    java.util.Collections.reverse(updated);
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    for (MailItem item : updated) ids.add(item.messageId());
                    for (MailItem item : mailList.getItems()) {
                        if (!ids.contains(item.messageId())) updated.add(item);
                    }
                    mailList.setItems(FXCollections.observableArrayList(updated.subList(0, Math.min(PAGE_SIZE, updated.size()))));
                }
            }
        });
        task.setOnFailed(e -> { imapSyncBusy = false; status.setText("IMAP: đồng bộ thất bại: " + task.getException().getMessage()); });
        workers.submit(task);
    }

    private Pane composePage() {
        VBox page = page("Soạn thư mới", "Gửi email bằng SMTP qua Gmail");
        TextField to = new TextField(), subject = new TextField();
        to.setPromptText("nguoihan@example.com");
        subject.setPromptText("Tiêu đề email");
        TextArea body = new TextArea(); body.setPromptText("Nhập nội dung email...");
        body.setWrapText(true);
        Button send = primary("✉  Gửi email");
        send.setOnAction(e -> {
            final MailAccount acc;
            try {
                acc = account();
                if (to.getText().isBlank() || body.getText().isBlank())
                    throw new IllegalArgumentException("Nhập người nhận và nội dung email.");
            } catch (Exception ex) { error(ex); return; }
            String receiver = to.getText().trim(), title = subject.getText(), content = body.getText();
            send.setDisable(true); status.setText("Đang gửi email...");
            Task<Void> task = new Task<>() {
                @Override protected Void call() throws Exception {
                    new SmtpService().send(acc, receiver, title, content);
                    try {
                        new MailDatabase().save(acc, "SENT",
                            new MailItem(0, "", acc.email(), receiver, title, content, ""));
                    } catch (Exception ex) { System.err.println("MySQL: " + ex.getMessage()); }
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                send.setDisable(false); status.setText("Đã gửi email thành công");
                new Alert(Alert.AlertType.INFORMATION, "Email đã được gửi qua SMTP.").showAndWait();
            });
            task.setOnFailed(ev -> { send.setDisable(false); status.setText("Gửi thất bại"); error(task.getException()); });
            workers.submit(task);
        });
        VBox card = new VBox(12, new Label("Người nhận"), to, new Label("Tiêu đề"), subject,
                new Label("Nội dung email"), body, send);
        card.setPadding(new Insets(24)); card.setStyle(cardStyle());
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox outer = new VBox(18, page, card); outer.setPadding(new Insets(0, 28, 28, 28));
        VBox.setVgrow(card, Priority.ALWAYS);
        return outer;
    }

    private Pane lanPage() {
        VBox outer = page("Mail nội bộ LAN", "Đăng ký, đăng nhập, gửi và nhận thư qua UDP Server + MySQL Docker");
        TextField host = new TextField("127.0.0.1");
        TextField port = new TextField("2023");
        TextField username = new TextField(lanUser == null ? "" : lanUser);
        PasswordField pwd = new PasswordField();
        TextField receiver = new TextField(), subject = new TextField();
        TextArea body = new TextArea(); body.setPrefRowCount(4);
        TextArea output = new TextArea(); output.setEditable(false); output.setWrapText(true);
        output.setPrefRowCount(12);
        Label identity = new Label(lanUser == null ? "Chưa đăng nhập" : "Đã đăng nhập: " + lanUser);
        Button register = primary("Đăng ký"), login = primary("Đăng nhập");
        Button send = primary("Gửi LAN"), inbox = primary("Hộp thư đến");
        Button sent = primary("Đã gửi"), contacts = primary("Danh bạ / Online");
        register.setOnAction(e -> lanAction(output, () -> new LanClient(host.getText().trim(), Integer.parseInt(port.getText().trim()))
            .register(username.getText(), pwd.getText()), value -> output.setText("Đăng ký thành công. Hãy đăng nhập.")));
        login.setOnAction(e -> lanAction(output, () -> new LanClient(host.getText().trim(), Integer.parseInt(port.getText().trim()))
            .login(username.getText(), pwd.getText()), value -> {
                lanToken = value.substring(3); lanUser = username.getText().trim().toLowerCase();
                identity.setText("Đã đăng nhập: " + lanUser); output.setText("Đăng nhập thành công");
            }));
        send.setOnAction(e -> lanAction(output, () -> {
            requireLanLogin();
            return new LanClient(host.getText().trim(), Integer.parseInt(port.getText().trim()))
                .send(lanToken, receiver.getText(), subject.getText(), body.getText());
        }, value -> output.setText("Đã gửi thư LAN tới " + receiver.getText())));
        inbox.setOnAction(e -> lanMailbox(output, host.getText(), port.getText(), "INBOX"));
        sent.setOnAction(e -> lanMailbox(output, host.getText(), port.getText(), "SENT"));
        contacts.setOnAction(e -> lanAction(output, () -> {
            requireLanLogin();
            LanClient client = new LanClient(host.getText().trim(), Integer.parseInt(port.getText().trim()));
            String all = client.call("USERS", lanToken);
            String online = client.call("ONLINE", lanToken);
            return all + "\t" + online;
        }, value -> {
            String[] parts = value.split("\t",2);
            StringBuilder b = new StringBuilder("DANH BẠ\n");
            for (String line : parts[0].split("\n")) if (!line.equals("OK") && !line.isBlank()) b.append(LanClient.dec(line)).append("\n");
            b.append("\nONLINE (hoạt động gần đây)\n");
            if(parts.length>1)for(String line:parts[1].split("\n"))if(!line.equals("OK")&&!line.isBlank())b.append(LanClient.dec(line)).append("\n");
            output.setText(b.toString());
        }));
        GridPane fields = new GridPane(); fields.setHgap(10); fields.setVgap(8);
        fields.addRow(0,new Label("Server IP"),host,new Label("UDP port"),port);
        fields.addRow(1,new Label("Tên tài khoản"),username,new Label("Mật khẩu"),pwd);
        HBox buttons = new HBox(10,register,login,identity);
        HBox actions = new HBox(10,send,inbox,sent,contacts);
        VBox card = new VBox(12,fields,buttons,new Separator(),new Label("Người nhận (username)"),receiver,
            new Label("Tiêu đề"),subject,new Label("Nội dung"),body,actions,new Label("Kết quả"),output);
        card.setPadding(new Insets(22));card.setStyle(cardStyle());
        outer.getChildren().add(card);
        ScrollPane scroll = new ScrollPane(outer);scroll.setFitToWidth(true);
        return new StackPane(scroll);
    }

    @FunctionalInterface private interface LanCall { String run() throws Exception; }
    private void requireLanLogin() {
        if (lanToken == null) throw new IllegalStateException("Hãy đăng nhập LAN trước");
    }
    private void lanAction(TextArea output, LanCall action, java.util.function.Consumer<String> success) {
        Task<String> task = new Task<>() { @Override protected String call() throws Exception { return action.run(); } };
        task.setOnSucceeded(e -> success.accept(task.getValue()));
        task.setOnFailed(e -> { output.setText("Lỗi: " + task.getException().getMessage()); });
        workers.submit(task);
    }
    private void lanMailbox(TextArea output,String host,String port,String folder) {
        lanAction(output, () -> {
            requireLanLogin();
            List<String[]> rows = new LanClient(host.trim(),Integer.parseInt(port.trim())).mailbox(lanToken,folder);
            StringBuilder b = new StringBuilder(folder + " (" + rows.size() + " thư)\n\n");
            for (String[] row : rows) b.append("#").append(row[0]).append(" | ").append(row[1]).append(" → ")
                .append(row[2]).append("\n").append(row[3]).append("\n").append(row[4]).append("\n----------\n");
            return b.toString();
        },output::setText);
    }

    private Pane settingsPage() {
        VBox page = page("Cấu hình tài khoản & server", "Tự nạp từ .env khi khởi động; có thể chỉnh tạm trong phiên chạy");
        email.setPromptText("Gmail của bạn");
        password.setPromptText("App Password");
        GridPane grid = new GridPane(); grid.setHgap(18); grid.setVgap(15);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);
        field(grid, "Địa chỉ email", email, 0, 0);
        field(grid, "App Password", password, 1, 0);
        field(grid, "SMTP host", smtpHost, 0, 1);
        field(grid, "SMTP port", smtpPort, 1, 1);
        field(grid, "POP3 host", popHost, 0, 2);
        field(grid, "POP3 port", popPort, 1, 2);
        Label hint = new Label("Gmail: SMTP 587 (STARTTLS), POP3 995 (SSL).\n"
                + "Thay đổi tại đây chỉ áp dụng đến khi đóng ứng dụng.\n"
                + "Muốn lưu cho lần chạy sau, hãy sửa .env ở thư mục gốc.");
        hint.setTextFill(Color.web("#687b97"));
        Button apply = primary("✓  Áp dụng");
        apply.setOnAction(e -> {
            try { MailAccount acc = account(); accountLabel.setText(acc.email()); showPage("inbox"); }
            catch (Exception ex) { error(ex); }
        });
        VBox card = new VBox(25, grid, hint, apply);
        card.setPadding(new Insets(26)); card.setStyle(cardStyle());
        VBox outer = new VBox(18, page, card); outer.setPadding(new Insets(0, 28, 28, 28));
        return outer;
    }

    private void field(GridPane grid, String label, Control control, int col, int row) {
        Label l = new Label(label); l.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(7, l, control);
        if (control instanceof TextInputControl t) t.setPrefHeight(38);
        grid.add(box, col, row);
    }

    private MailAccount account() {
        return new MailAccount(email.getText().trim(), password.getText(),
                smtpHost.getText().trim(), Integer.parseInt(smtpPort.getText().trim()),
                popHost.getText().trim(), Integer.parseInt(popPort.getText().trim()));
    }

    private HBox footer() {
        status.setTextFill(Color.WHITE);
        Label right = new Label("JavaFX • JavaMail • MySQL (tùy chọn)");
        right.setTextFill(Color.web("#cadbf0"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(status, spacer, right);
        bar.setPadding(new Insets(11, 25, 11, 25));
        bar.setStyle("-fx-background-color: #0c2b59;");
        return bar;
    }

    private Button primary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1677f2; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-background-radius: 10; -fx-padding: 12 22; -fx-cursor: hand;");
        return b;
    }

    private String cardStyle() {
        return "-fx-background-color: white; -fx-background-radius: 12;"
                + "-fx-border-color: #dce5f2; -fx-border-radius: 12;";
    }

    private void error(Throwable ex) {
        ex.printStackTrace();
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        new Alert(Alert.AlertType.ERROR, cause.getMessage() == null ? cause.toString() : cause.getMessage()).showAndWait();
    }

    @Override public void stop() { if (imapPolling != null) imapPolling.stop(); workers.shutdownNow(); }
}
