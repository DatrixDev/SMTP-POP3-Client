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
    private final ExecutorService cacheWorkers = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail-cache-worker");
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
    private Button inboxNav, composeNav, settingsNav, lanNav, starredNav, sentNav, draftNav, categoriesNav;
    private final java.util.Map<String,Button> navigation = new java.util.LinkedHashMap<>();
    private final Preferences localMail = Preferences.userNodeForPackage(MailFxApp.class).node("local-mail");
    private final TextField searchField = new TextField();
    private final javafx.collections.ObservableList<MailItem> allInbox = FXCollections.observableArrayList();
    private final java.util.Set<String> starred = new java.util.HashSet<>();
    private final Preferences mailFlags = Preferences.userNodeForPackage(MailFxApp.class).node("mail-flags");
    private final java.util.Set<String> readMessages = new java.util.HashSet<>();
    private final java.util.Map<String,String> popBodyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private long bodyRequestSequence = 0;
    private boolean showStarredOnly = false;
    private boolean showUnreadOnly = false;
    private String selectedCategory = "Tất cả";
    private String lanToken;
    private String lanUser;
    private Stage stage;
    private final java.util.Map<String, MailAccount> sessionAccounts = new java.util.LinkedHashMap<>();
    private final ComboBox<String> accountSwitcher = new ComboBox<>();
    private final Preferences syncState = Preferences.userNodeForPackage(MailFxApp.class);
    private Timeline imapPolling;
    private boolean imapSyncBusy = false;
    private int inboxOffset = 0;
    private final ComboBox<String> receiveMode = new ComboBox<>(FXCollections.observableArrayList("IMAP (Gmail - nhanh)", "POP3 (bài lab)"));
    private static final int PAGE_SIZE = 20;
    private final Label pageLabel = new Label("Trang 1");

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        MailConfig cfg = MailConfig.load();
        email.setText(cfg.email()); password.setText(cfg.password());
        smtpHost.setText(cfg.smtpHost()); smtpPort.setText(String.valueOf(cfg.smtpPort()));
        popHost.setText(cfg.popHost()); popPort.setText(String.valueOf(cfg.popPort()));
        accountLabel.setText(cfg.email().isBlank() ? "Chưa cấu hình tài khoản" : cfg.email());
        if (!cfg.email().isBlank() && !cfg.password().isBlank()) {
            sessionAccounts.put(cfg.email(), new MailAccount(cfg.email(), cfg.password(), cfg.smtpHost(), cfg.smtpPort(), cfg.popHost(), cfg.popPort()));
            accountSwitcher.getItems().add(cfg.email());
            accountSwitcher.setValue(cfg.email());
        }

        root.setTop(header());
        root.setLeft(sidebar());
        root.setBottom(footer());
        root.setStyle("-fx-background-color: #f7f9fd;");
        Scene scene = new Scene(root, 1280, 800);
        stage.setScene(scene);
        stage.setMinWidth(980); stage.setMinHeight(640);
        stage.setTitle("Mail Client • Gmail IMAP / SMTP");
        receiveMode.getSelectionModel().selectFirst();
        showPage(cfg.email().isBlank() || cfg.password().isBlank() ? "settings" : "inbox");
        stage.show();
        imapPolling = new Timeline(new KeyFrame(Duration.seconds(7), e -> pollImap()));
        imapPolling.setCycleCount(Timeline.INDEFINITE);
        imapPolling.play();
    }

    private HBox header() {
        Label menu = new Label("☰");menu.setStyle("-fx-font-size: 21px; -fx-text-fill: #5f6368;");
        Label logo = new Label("✉  MailSpace");logo.setFont(Font.font("System",FontWeight.BOLD,25));
        logo.setTextFill(Color.web("#202124"));
        searchField.setPromptText("⌕   Tìm kiếm trong thư");
        searchField.setPrefWidth(360);searchField.setMaxWidth(580);searchField.setPrefHeight(46);
        HBox.setHgrow(searchField,Priority.ALWAYS);
        searchField.setStyle("-fx-background-color: #e9eef6; -fx-background-radius: 26; -fx-font-size: 14px; -fx-padding: 0 18;");
        searchField.textProperty().addListener((o,a,b) -> refreshMailFilter());
        Region expand = new Region();HBox.setHgrow(expand,Priority.ALWAYS);
        Button settings=iconButton("⚙","Cấu hình SMTP / POP3");settings.setOnAction(e->showPage("settings"));
        accountLabel.setTextFill(Color.web("#5f6368"));
        accountSwitcher.setPromptText("Chọn tài khoản");
        accountSwitcher.setPrefWidth(205);
        accountSwitcher.setOnAction(e -> {
            String selected = accountSwitcher.getValue();
            MailAccount acc = sessionAccounts.get(selected);
            if (acc == null || selected.equals(email.getText().trim())) return;
            email.setText(acc.email()); password.setText(acc.password());
            smtpHost.setText(acc.smtpHost()); smtpPort.setText(String.valueOf(acc.smtpPort()));
            popHost.setText(acc.popHost()); popPort.setText(String.valueOf(acc.popPort()));
            accountLabel.setText(acc.email());
            inboxOffset = 0; allInbox.clear(); popBodyCache.clear();
            showPage("inbox");
        });
        Button addAccount = iconButton("＋", "Thêm tài khoản Gmail");
        addAccount.setOnAction(e -> {
            email.clear(); password.clear(); accountLabel.setText("Thêm tài khoản");
            smtpHost.setText("smtp.gmail.com"); smtpPort.setText("587");
            popHost.setText("imap.gmail.com"); popPort.setText("993");
            showPage("settings");
        });
        HBox h=new HBox(16,menu,logo,searchField,expand,settings,accountSwitcher,addAccount);
        h.setAlignment(Pos.CENTER_LEFT);h.setPadding(new Insets(10,24,10,18));
        h.setStyle("-fx-background-color: white; -fx-border-color: #e7edf6; -fx-border-width: 0 0 1 0;");
        return h;
    }

    private VBox sidebar() {
        composeNav = new Button("✎    Soạn thư");
        composeNav.setMaxWidth(190);composeNav.setPrefHeight(52);
        composeNav.setStyle("-fx-background-color: #c2e7ff; -fx-background-radius: 17; -fx-text-fill: #001d35; -fx-font-weight: bold; -fx-font-size: 14px;");
        composeNav.setFocusTraversable(false);
        composeNav.setOnAction(e -> showPage("compose"));
        inboxNav=nav("▣    Hộp thư đến","inbox");
        starredNav=nav("☆    Có gắn sao","starred");
        sentNav=nav("➤    Đã gửi","sent");
        draftNav=nav("▤    Thư nháp","drafts");
        categoriesNav=nav("⌄    Danh mục","categories");
        lanNav=nav("◉    Mail nội bộ LAN","lan");
        settingsNav=nav("⚙    Cài đặt SMTP / POP3","settings");
        Label folders=new Label("KHÔNG GIAN LÀM VIỆC");folders.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #444746;");
        VBox side=new VBox(8,composeNav,inboxNav,starredNav,sentNav,draftNav,categoriesNav,new Separator(),folders,lanNav,settingsNav);
        side.setPadding(new Insets(10,10,10,10));side.setPrefWidth(225);side.setMinWidth(205);
        side.setStyle("-fx-background-color: #f0f4fb; -fx-border-color: #e3e9f3; -fx-border-width: 0 1 0 0;");
        return side;
    }
    private Button nav(String label,String page) {
        Button b = new Button(label);
        navigation.put(page,b);
        b.setMaxWidth(Double.MAX_VALUE);b.setPrefWidth(205);b.setAlignment(Pos.CENTER_LEFT);
        b.setFocusTraversable(false);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #444746; -fx-font-size: 14px; -fx-padding: 10 16;");
        b.setOnAction(e -> {
            showStarredOnly = "starred".equals(page);
            showPage(page);
        });
        return b;
    }

    private void showPage(String page) {
        if (inboxNav != null) {
            navigation.put("inbox",inboxNav);
            navigation.put("compose",composeNav);
            navigation.put("settings",settingsNav);
            navigation.put("lan",lanNav);
            for (var entry : navigation.entrySet()) highlight(entry.getValue(),entry.getKey().equals(page));
        }
        if (!page.equals("starred")) showStarredOnly=false;
        showUnreadOnly = false;
        if (!"categories".equals(page)) selectedCategory = "Tất cả";
        root.setCenter(switch (page) {
            case "compose" -> composePage();
            case "starred" -> inboxPage();
            case "sent" -> sentPage();
            case "drafts" -> mysqlDraftsPage();
            case "categories" -> categoryPage();
            case "settings" -> settingsPage();
            case "lan" -> lanPage();
            default -> inboxPage();
        });
        if (page.equals("inbox")) javafx.application.Platform.runLater(() -> refreshInbox(null));
    }

    private void highlight(Button button, boolean active) {
        if (button == composeNav) {
            button.setStyle("-fx-background-color: "+(active?"#a8d8ff":"#c2e7ff")
                    +"; -fx-background-radius: 17; -fx-text-fill: #001d35; -fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-cursor: hand;");
        } else {
            button.setStyle("-fx-background-color: " + (active ? "#d3e3fd" : "transparent")
                    + "; -fx-background-radius: 22; -fx-text-fill: #202124; -fx-font-size: 14px; -fx-font-weight: " + (active?"bold":"normal")
                    + "; -fx-padding: 11 14; -fx-border-color: transparent; -fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-cursor: hand;");
        }
    }

    private VBox page(String heading, String description) {
        Label title = new Label(heading); title.setFont(Font.font("System", FontWeight.BOLD, 26));
        title.setTextFill(Color.web("#122a4e"));
        Label sub = new Label(description); sub.setTextFill(Color.web("#72839b"));
        VBox pane = new VBox(8, title, sub);
        pane.setPadding(new Insets(25));
        return pane;
    }

    private String categoryOf(MailItem item) {
        String sender = item.from().toLowerCase(java.util.Locale.ROOT);
        String subject = item.subject().toLowerCase(java.util.Locale.ROOT);
        if (sender.contains("noreply") || sender.contains("no-reply") || subject.contains("mã xác nhận")
                || subject.contains("verification") || subject.contains("security")) return "Thông báo";
        if (subject.contains("sale") || subject.contains("discount") || subject.contains("khuyến mãi")
                || subject.contains("ưu đãi") || subject.contains("promotion")) return "Quảng cáo";
        if (subject.contains("invoice") || subject.contains("receipt") || subject.contains("hóa đơn")
                || subject.contains("đơn hàng")) return "Giao dịch";
        return "Chính";
    }

    private Pane categoryPage() {
        VBox page = new VBox(10);
        page.setPadding(new Insets(12, 20, 12, 10));
        ComboBox<String> choices = new ComboBox<>(FXCollections.observableArrayList(
            "Tất cả", "Chính", "Thông báo", "Quảng cáo", "Giao dịch", "Chưa đọc"));
        choices.setValue(selectedCategory);
        choices.setOnAction(e -> { selectedCategory = choices.getValue(); refreshMailFilter(); });
        page.getChildren().addAll(new Label("Lọc email theo danh mục (quy tắc trên máy)"), choices, inboxPage());
        VBox.setVgrow(page.getChildren().get(2), Priority.ALWAYS);
        refreshMailFilter();
        return page;
    }

    private Pane inboxPage() {
        if (receiveMode.getValue() == null) receiveMode.getSelectionModel().selectFirst();
        receiveMode.setOnAction(e -> { inboxOffset = 0; inboxCount.setText("Đang tải thư..."); if (root.getCenter() != null) refreshInbox(null); });
        Button refresh = iconButton("↻", "Làm mới");
        refresh.setOnAction(e -> { inboxOffset = 0; refreshInbox(refresh); });
        Button previous = iconButton("‹", "Trang trước");
        Button next = iconButton("›", "Trang sau");
        previous.setOnAction(e -> { inboxOffset = Math.max(0,inboxOffset - PAGE_SIZE); refreshInbox(refresh); });
        next.setOnAction(e -> { inboxOffset += PAGE_SIZE; refreshInbox(refresh); });
        Label count = pageLabel;
        HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label selectIcon = new Label("☐"); selectIcon.setStyle("-fx-font-size: 20px; -fx-text-fill: #5f6368;");
        HBox toolbar = new HBox(18, selectIcon, refresh, receiveMode, spacer, inboxCount, previous, count, next);
        toolbar.setAlignment(Pos.CENTER_LEFT); toolbar.setPadding(new Insets(12,18,12,18));
        toolbar.setStyle("-fx-background-color: white; -fx-border-color: #edf0f3; -fx-border-width: 0 0 1 0;");
        inboxCount.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 12px;");

        mailList.setPlaceholder(new Label("Đang tải hoặc chưa có thư..."));
        mailList.setFixedCellSize(48);
        mailList.setStyle("-fx-background-color: white; -fx-border-width: 0;");
        mailList.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(MailItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null);setText(null);return; }
                Label check = new Label("□"); check.setMinWidth(22);check.setStyle("-fx-font-family: Segoe UI Symbol; -fx-font-size: 19px; -fx-text-fill: #6b7280;");
                Label star = new Label(starred.contains(mailKey(item)) ? "★" : "☆");
                star.setMinWidth(24);star.setStyle("-fx-font-family: Segoe UI Symbol; -fx-font-size: 21px;");
                star.setStyle("-fx-font-family: Segoe UI Symbol; -fx-font-size: 21px; -fx-text-fill: "+(starred.contains(mailKey(item))?"#fbbc04":"#7b8794")+";");
                star.setOnMouseClicked(e -> {
                    String key = mailKey(item);
                    if (!starred.add(key)) starred.remove(key);
                    mailFlags.putBoolean("star." + Integer.toHexString(key.hashCode()), starred.contains(key));
                    refreshMailFilter(); e.consume();
                });
                Label sender = new Label(item.from());
                sender.setPrefWidth(235);sender.setMinWidth(235);
                sender.setTextOverrun(OverrunStyle.ELLIPSIS);
                sender.setStyle("-fx-text-fill: #202124; -fx-font-weight: bold; -fx-font-size: 13px;");
                Label preview = new Label((item.subject().isBlank() ? "(Không có tiêu đề)" : item.subject())+
                    "  -  "+item.body().replace('\n',' ').replace('\r',' '));
                preview.setTextOverrun(OverrunStyle.ELLIPSIS);preview.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(preview,Priority.ALWAYS);
                preview.setStyle("-fx-text-fill: #202124; -fx-font-size: 13px;");
                Label date = new Label(item.sentAt()); date.setPrefWidth(140);date.setMinWidth(140);
                date.setTextOverrun(OverrunStyle.ELLIPSIS);
                date.setAlignment(Pos.CENTER_RIGHT);date.setStyle("-fx-text-fill: #5f6368; -fx-font-size: 11px;");
                HBox line = new HBox(12,check,star,sender,preview,date);
                line.setAlignment(Pos.CENTER_LEFT);line.setPadding(new Insets(0,18,0,18));
                line.setStyle("-fx-background-color: "+(isSelected()?"#d3e3fd":readMessages.contains(mailKey(item))?"#f2f6fc":"#ffffff")+";");
                line.prefWidthProperty().bind(widthProperty().subtract(16));
                setGraphic(line);setText(null);
                setStyle("-fx-padding: 0; -fx-background-color: transparent;");
            }
        });
        mailList.setOnMouseClicked(e -> {
            MailItem item = mailList.getSelectionModel().getSelectedItem();
            if (item != null && e.getClickCount() >= 2) showMessageDialog(item);
        });
        mailList.getSelectionModel().selectedItemProperty().addListener((obs,old,item) -> {
            if (item != null) {
                showMessagePreview(item);
                status.setText("Đang xem: "+item.subject());
            }
        });
        refreshMailFilter();
        VBox.setVgrow(mailList,Priority.ALWAYS);
        detailSubject.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: #202124;");
        detailSubject.setWrapText(true);
        detailFrom.setStyle("-fx-font-weight: bold; -fx-text-fill: #202124;");
        detailTo.setStyle("-fx-text-fill: #5f6368;");
        detailDate.setStyle("-fx-text-fill: #5f6368;");
        detailBody.setEditable(false);detailBody.setWrapText(true);
        detailBody.setStyle("-fx-control-inner-background: white; -fx-font-size: 14px; -fx-border-color: transparent;");
        VBox.setVgrow(detailBody,Priority.ALWAYS);
        Button expand = iconButton("⛶", "Mở thư trong cửa sổ");
        expand.setOnAction(e -> {
            MailItem item=mailList.getSelectionModel().getSelectedItem();
            if(item!=null)showMessageDialog(item);
        });
        HBox messageHeader = new HBox(12,detailFrom,new Region(),detailDate,expand);
        HBox.setHgrow(messageHeader.getChildren().get(1),Priority.ALWAYS);
        messageHeader.setAlignment(Pos.CENTER_LEFT);
        VBox preview = new VBox(12,detailSubject,new Separator(),messageHeader,detailTo,detailBody);
        preview.setPadding(new Insets(22,25,14,25));
        preview.setStyle("-fx-background-color: white;");
        SplitPane split = new SplitPane(mailList,preview);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.50);
        split.setStyle("-fx-background-color: white; -fx-box-border: transparent;");
        VBox.setVgrow(split,Priority.ALWAYS);
        VBox container = new VBox(toolbar,split);
        container.setStyle("-fx-background-color: white; -fx-background-radius: 16 16 0 0;");
        VBox.setVgrow(container,Priority.ALWAYS);
        VBox outer = new VBox(container);outer.setPadding(new Insets(0,16,10,0));
        VBox.setVgrow(container,Priority.ALWAYS);
        return outer;
    }

    private void showMessagePreview(MailItem item) {
        readMessages.add(mailKey(item));
        mailFlags.putBoolean("read." + Integer.toHexString(mailKey(item).hashCode()), true);
        detailSubject.setText(item.subject().isBlank()?"(Không có tiêu đề)":item.subject());
        detailFrom.setText(item.from());
        detailTo.setText("Đến: "+item.to());
        detailDate.setText(item.sentAt());
        String key = mailKey(item);
        String cached = popBodyCache.get(key);
        detailBody.setText(cached != null ? cached : item.body());
        boolean pop3 = receiveMode.getValue() == null || !receiveMode.getValue().startsWith("IMAP");
        long request = ++bodyRequestSequence;
        if (cached == null && item.body().isBlank()) {
            detailBody.setText("Đang tải nội dung thư...");
            final MailAccount acc;
            try { acc = account(); } catch (Exception ex) { detailBody.setText(ex.getMessage()); return; }
            Task<String> task = new Task<>() {
                @Override protected String call() throws Exception {
                    return pop3 ? new Pop3Service().body(acc, item.number())
                                : new ImapService().body(acc, item.number());
                }
            };
            task.setOnSucceeded(e -> {
                String body = task.getValue();
                popBodyCache.put(key, body);
                cacheWorkers.submit(() -> {
                    try {
                        new MailDatabase().save(acc, "RECEIVED",
                            new MailItem(item.number(), item.messageId(), item.from(), item.to(),
                                item.subject(), body, item.sentAt(), item.receivedAt()));
                    } catch (Exception ex) { System.err.println("MySQL body cache: " + ex.getMessage()); }
                });
                if (request == bodyRequestSequence) detailBody.setText(body);
            });
            task.setOnFailed(e -> {
                if (request == bodyRequestSequence) detailBody.setText("Không tải được nội dung: " + task.getException().getMessage());
            });
            workers.submit(task);
        }
        mailList.refresh();
    }

    private Button iconButton(String title,String tooltip) {
        Button b = new Button(title);b.setTooltip(new Tooltip(tooltip));
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #5f6368; -fx-font-size: 20px; -fx-cursor: hand;");
        return b;
    }
    private String mailKey(MailItem item) {
        return item.messageId().isBlank() ? item.from()+"|"+item.subject()+"|"+item.sentAt() : item.messageId();
    }
    private void refreshMailFilter() {
        String query = searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        mailList.setItems(allInbox.filtered(item -> {
            boolean matches = query.isBlank() || (item.from()+" "+item.to()+" "+item.subject()+" "+item.body()).toLowerCase(java.util.Locale.ROOT).contains(query);
            return matches && (!showStarredOnly || starred.contains(mailKey(item)))
                    && ("Tất cả".equals(selectedCategory) ||
                        ("Chưa đọc".equals(selectedCategory) ? !readMessages.contains(mailKey(item)) :
                            selectedCategory.equals(categoryOf(item))));
        }));
        mailList.refresh();
    }
    private void showMessageDialog(MailItem item) {
        readMessages.add(mailKey(item));
        mailFlags.putBoolean("read." + Integer.toHexString(mailKey(item).hashCode()), true);
        Dialog<Void> dialog = new Dialog<>();dialog.setTitle(item.subject().isBlank()?"Thư":item.subject());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(900,660);
        Label title = new Label(item.subject());title.setWrapText(true);title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold;");
        Label from = new Label(item.from());from.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label to = new Label("Đến: "+item.to());to.setStyle("-fx-text-fill: #5f6368;");
        Label date = new Label(item.sentAt());date.setStyle("-fx-text-fill: #5f6368;");
        TextArea body = new TextArea(popBodyCache.getOrDefault(mailKey(item), item.body()));body.setWrapText(true);body.setEditable(false);
        body.setStyle("-fx-control-inner-background: white; -fx-font-size: 14px;");
        VBox.setVgrow(body,Priority.ALWAYS);
        VBox content = new VBox(18,title,new Separator(),new HBox(12,from,date),to,body);
        content.setPadding(new Insets(28));dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
        refreshMailFilter();
    }
    private String saveLocalMessage(String folder,String to,String subject,String body) {
        String id=java.util.UUID.randomUUID().toString();
        String value=java.util.Base64.getEncoder().encodeToString(to.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                +"|"+java.util.Base64.getEncoder().encodeToString(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                +"|"+java.util.Base64.getEncoder().encodeToString(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        localMail.put(folder+"."+id,value);
        return id;
    }
    private String decodeLocal(String encoded) {
        return new String(java.util.Base64.getDecoder().decode(encoded),java.nio.charset.StandardCharsets.UTF_8);
    }
    private Pane mysqlDraftsPage() {
        VBox wrapper = new VBox(12);
        wrapper.setPadding(new Insets(16, 22, 16, 22));
        VBox heading = page("Thư nháp", "Thư nháp lưu trên MySQL");
        VBox entries = new VBox(2);
        Label loading = new Label("Đang tải thư nháp từ MySQL...");
        entries.getChildren().add(loading);
        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        wrapper.getChildren().addAll(heading, scroll);
        // Preserve access to drafts created before MySQL synchronization was added.
        Button legacy = iconButton("▤", "Xem thư nháp cũ lưu trên máy");
        legacy.setText("Xem thư nháp lưu trên máy");
        legacy.setOnAction(e -> root.setCenter(localFolderPage("draft", "Thư nháp trên máy")));
        wrapper.getChildren().add(legacy);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        final MailAccount acc;
        try { acc = account(); }
        catch (Exception ex) { entries.getChildren().setAll(new Label("Chưa cấu hình tài khoản: " + ex.getMessage())); return wrapper; }
        Task<java.util.List<MailItem>> task = new Task<>() {
            @Override protected java.util.List<MailItem> call() throws Exception {
                return new MailDatabase().drafts(acc);
            }
        };
        task.setOnSucceeded(e -> {
            entries.getChildren().clear();
            java.util.List<MailItem> drafts = task.getValue();
            if (drafts.isEmpty()) { entries.getChildren().add(new Label("Chưa có thư nháp trong MySQL.")); return; }
            for (MailItem draft : drafts) {
                Label label = new Label((draft.subject() == null || draft.subject().isBlank() ? "(Không có tiêu đề)" : draft.subject())
                    + "  —  " + draft.to());
                label.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(label, Priority.ALWAYS);
                Button edit = iconButton("✎", "Tiếp tục soạn");
                edit.setOnAction(ev -> showComposeWith(draft.to(), draft.subject(), draft.body()));
                HBox line = new HBox(12, label, edit);
                line.setPadding(new Insets(12));
                line.setStyle("-fx-background-color: white; -fx-border-color: #edf0f3; -fx-border-width: 0 0 1 0;");
                entries.getChildren().add(line);
            }
        });
        task.setOnFailed(e -> entries.getChildren().setAll(new Label(
            "Không đọc được thư nháp MySQL: " + task.getException().getMessage())));
        cacheWorkers.submit(task);
        return wrapper;
    }

    private Pane localFolderPage(String folder,String title) {
        VBox heading=page(title,folder.equals("sent")?"Thư đã gửi qua SMTP":"Thư nháp của bạn");
        VBox entries=new VBox(1);entries.setPadding(new Insets(0,22,20,22));
        entries.setStyle("-fx-background-color: white; -fx-background-radius: 14;");
        try {
            java.util.List<String> keys=new java.util.ArrayList<>();
            for(String key:localMail.keys())if(key.startsWith(folder+"."))keys.add(key);
            java.util.Collections.reverse(keys);
            for(String key:keys) {
                String[] parts=localMail.get(key,"").split("\\|",-1);
                if(parts.length!=3)continue;
                String recipient=decodeLocal(parts[0]),subject=decodeLocal(parts[1]),body=decodeLocal(parts[2]);
                Label person=new Label(recipient.isBlank()?"Chưa có người nhận":recipient);
                person.setMinWidth(220);person.setPrefWidth(220);
                person.setTextOverrun(OverrunStyle.ELLIPSIS);
                person.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #202124;");
                Label snippet=new Label((subject.isBlank()?"(Không có tiêu đề)":subject)
                        +"  —  "+body.replace('\n',' ').replace('\r',' '));
                snippet.setTextOverrun(OverrunStyle.ELLIPSIS);
                snippet.setMaxWidth(Double.MAX_VALUE);
                snippet.setStyle("-fx-font-size: 13px; -fx-text-fill: #444746;");
                HBox.setHgrow(snippet,Priority.ALWAYS);
                Button open=iconButton(folder.equals("draft")?"✎":"↗",folder.equals("draft")?"Tiếp tục soạn":"Xem thư");
                open.setOnAction(e -> {
                    if(folder.equals("draft"))showComposeWith(recipient,subject,body);
                    else {
                        Dialog<Void> dialog=new Dialog<>();
                        dialog.setTitle("Thư đã gửi");
                        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                        Label titleLabel=new Label(subject.isBlank()?"(Không có tiêu đề)":subject);
                        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
                        Label recipientLabel=new Label("Đến: "+recipient);
                        TextArea content=new TextArea(body);content.setEditable(false);content.setWrapText(true);
                        content.setPrefSize(650,340);
                        VBox details=new VBox(12,titleLabel,recipientLabel,new Separator(),content);
                        details.setPadding(new Insets(20));
                        dialog.getDialogPane().setContent(details);
                        dialog.showAndWait();
                    }
                });
                Button delete=iconButton("✕","Xóa thư khỏi danh sách trên máy");
                delete.setOnAction(e -> {localMail.remove(key);showPage(folder.equals("draft")?"drafts":"sent");});
                HBox line=new HBox(15,person,snippet,open,delete);
                line.setAlignment(Pos.CENTER_LEFT);
                line.setPadding(new Insets(11,16,11,16));
                line.setMinHeight(56);
                line.setStyle("-fx-background-color: white; -fx-border-color: #edf0f3; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
                line.setOnMouseClicked(e -> {
                    if(e.getTarget() instanceof Button)return;
                    open.fire();
                });
                entries.getChildren().add(line);
            }
        } catch(Exception ex) {status.setText("Không đọc được thư đã lưu: "+ex.getMessage());}
        if(entries.getChildren().isEmpty()) {
            Label empty=new Label(folder.equals("sent")?"Chưa có email đã gửi.":"Chưa có thư nháp.");
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #5f6368;");
            VBox.setMargin(empty,new Insets(28));
            entries.getChildren().add(empty);
        }
        ScrollPane scroll=new ScrollPane(entries);scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox box=new VBox(heading,scroll);VBox.setVgrow(scroll,Priority.ALWAYS);return box;
    }
    private void showComposeWith(String recipient,String subject,String content) {
        showPage("compose");
        Pane p=(Pane)root.getCenter();
        VBox card=(VBox)((VBox)p).getChildren().get(1);
        TextField to=(TextField)card.getChildren().get(1);
        TextField sub=(TextField)card.getChildren().get(3);
        TextArea body=(TextArea)card.getChildren().get(5);
        to.setText(recipient);sub.setText(subject);body.setText(content);
    }
    private Pane sentPage() {
        if (popHost.getText().trim().equalsIgnoreCase("127.0.0.1")
                || popHost.getText().trim().equalsIgnoreCase("localhost")) return localFolderPage("sent", "Đã gửi");
        VBox wrapper = new VBox(12);
        wrapper.setPadding(new Insets(18, 24, 18, 24));
        VBox heading = page("Đã gửi", "Đồng bộ thư đã gửi trực tiếp từ Gmail bằng IMAP");
        VBox entries = new VBox(2);
        entries.getChildren().add(new Label("Đang tải Gmail Sent Mail..."));
        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Button reload = primary("↻ Đồng bộ thư đã gửi");
        reload.setOnAction(e -> loadImapSent(entries));
        Button legacy = new Button("Thư đã gửi lưu trên máy");
        legacy.setOnAction(e -> root.setCenter(localFolderPage("sent", "Đã gửi trên máy")));
        wrapper.getChildren().addAll(heading, new HBox(12, reload, legacy), scroll);
        loadImapSent(entries);
        return wrapper;
    }
    private void loadImapSent(VBox entries) {
        final MailAccount acc;
        try { acc = account(); } catch (Exception ex) { entries.getChildren().setAll(new Label(ex.getMessage())); return; }
        entries.getChildren().setAll(new Label("Đang đồng bộ thư đã gửi..."));
        Task<List<MailItem>> task = new Task<>() {
            @Override protected List<MailItem> call() throws Exception {
                return new ImapService().sent(acc, 0, 50);
            }
        };
        task.setOnSucceeded(e -> {
            entries.getChildren().clear();
            for (MailItem item : task.getValue()) {
                Button row = new Button("➤  " + item.to() + "   •   " + item.subject());
                row.setMaxWidth(Double.MAX_VALUE);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color: white; -fx-padding: 16; -fx-font-size: 14px;");
                row.setOnAction(ev -> showMessageDialog(item));
                entries.getChildren().add(row);
            }
            if (task.getValue().isEmpty()) entries.getChildren().add(new Label("Chưa có thư đã gửi."));
        });
        task.setOnFailed(e -> entries.getChildren().setAll(new Label("Không đồng bộ được: " + task.getException().getMessage())));
        workers.submit(task);
    }
    private Pane draftsPage() {return localFolderPage("draft","Thư nháp");}
    private void refreshInbox(Button refresh) {
        final MailAccount acc;
        try { acc = account(); } catch (Exception ex) { error(ex); return; }
        boolean imap = receiveMode.getValue() != null && receiveMode.getValue().startsWith("IMAP");
        int offset = inboxOffset;
        if (refresh != null) refresh.setDisable(true); status.setText("Đang tải email từ " + (imap ? "IMAP" : "POP3") + "...");
        Task<List<MailItem>> task = new Task<>() {
            @Override protected List<MailItem> call() throws Exception {
                List<MailItem> items = imap ? new ImapService().inbox(acc, offset, PAGE_SIZE)
                                            : new Pop3Service().inbox(acc, offset, PAGE_SIZE);
                cacheWorkers.submit(() -> {
                    try {
                        MailDatabase db = new MailDatabase();
                        if (db.enabled()) for (MailItem item : items) {
                            try { db.save(acc, "RECEIVED", item); }
                            catch (Exception ex) { System.err.println("MySQL: " + ex.getMessage()); break; }
                        }
                    } catch (Exception ex) { System.err.println("MySQL cache: " + ex.getMessage()); }
                });
                return items;
            }
        };
        task.setOnSucceeded(e -> {
            if (refresh != null) refresh.setDisable(false);
            List<MailItem> items = task.getValue();
            for (MailItem item : items) {
                String key = mailKey(item);
                if (mailFlags.getBoolean("star." + Integer.toHexString(key.hashCode()), false)) starred.add(key);
                if (mailFlags.getBoolean("read." + Integer.toHexString(key.hashCode()), false)) readMessages.add(key);
            }
            allInbox.setAll(items); refreshMailFilter();
            inboxCount.setText("Đã tải " + items.size() + " email từ " + (imap ? "IMAP" : "POP3"));
            pageLabel.setText("Trang " + (inboxOffset / PAGE_SIZE + 1));
            status.setText("Đã cập nhật hộp thư");
            if (!items.isEmpty()) mailList.getSelectionModel().selectFirst();
        });
        task.setOnFailed(e -> { if (refresh != null) refresh.setDisable(false); status.setText("Tải email thất bại"); error(task.getException()); });
        workers.submit(task);
    }

    /** Background IMAP UID polling; keeps the POP3 lab path untouched. */
    private void pollImap() {
        if (imapSyncBusy || receiveMode.getValue() == null || !receiveMode.getValue().startsWith("IMAP")) return;
        if (root.getCenter() == null || !root.getCenter().getClass().equals(VBox.class)) return;
        if (popHost.getText().trim().equalsIgnoreCase("127.0.0.1") || popHost.getText().trim().equalsIgnoreCase("localhost")) return;
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
            if (!batch.messages().isEmpty()) cacheWorkers.submit(() -> {
                MailDatabase db = new MailDatabase();
                for (MailItem item : batch.messages()) {
                    try { db.save(acc, "RECEIVED", item); }
                    catch (Exception ex) { System.err.println("IMAP cache: " + ex.getMessage()); break; }
                }
            });
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
                    allInbox.setAll(updated.subList(0, Math.min(PAGE_SIZE, updated.size()))); refreshMailFilter();
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
        java.util.List<java.io.File> attachments = new java.util.ArrayList<>();
        Label attachmentLabel = new Label("Chưa có tệp đính kèm");
        Button addAttachment = iconButton("📎", "Chọn tệp đính kèm");
        addAttachment.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Chọn tệp đính kèm");
            java.util.List<java.io.File> selected = chooser.showOpenMultipleDialog(stage);
            if (selected != null) {
                for (java.io.File file : selected) if (!attachments.contains(file)) attachments.add(file);
                attachmentLabel.setText(attachments.size() + " tệp: " + attachments.stream().map(java.io.File::getName)
                    .collect(java.util.stream.Collectors.joining(", ")));
            }
        });
        Button clearAttachments = iconButton("✕", "Bỏ tệp đính kèm");
        clearAttachments.setOnAction(e -> { attachments.clear(); attachmentLabel.setText("Chưa có tệp đính kèm"); });
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
                    new SmtpService().send(acc, receiver, title, content, java.util.List.copyOf(attachments));
                    try {
                        new MailDatabase().save(acc, "SENT",
                            new MailItem(0, "", acc.email(), receiver, title, content, java.time.Instant.now().toString()));
                    } catch (Exception ex) { System.err.println("MySQL: " + ex.getMessage()); }
                    return null;
                }
            };
            task.setOnSucceeded(ev -> {
                send.setDisable(false); status.setText("Đã gửi email thành công");
                saveLocalMessage("sent",receiver,title,content);
                new Alert(Alert.AlertType.INFORMATION, "Email đã được gửi qua SMTP.").showAndWait();
                showPage("sent");
            });
            task.setOnFailed(ev -> { send.setDisable(false); status.setText("Gửi thất bại"); error(task.getException()); });
            workers.submit(task);
        });
        Button saveDraft=primary("Lưu nháp");
        saveDraft.setOnAction(e -> {
            String draftId = saveLocalMessage("draft",to.getText(),subject.getText(),body.getText());
            try {
                MailAccount acc = account();
                String recipient = to.getText(), title = subject.getText(), content = body.getText();
                cacheWorkers.submit(() -> {
                    try { new MailDatabase().saveDraft(acc, draftId, recipient, title, content); }
                    catch (Exception ex) { System.err.println("Draft MySQL: " + ex.getMessage()); }
                });
            } catch (Exception ex) { System.err.println("Draft local only: " + ex.getMessage()); }
            status.setText("Đã lưu thư nháp");showPage("drafts");
        });
        VBox card = new VBox(12, new Label("Người nhận"), to, new Label("Tiêu đề"), subject,
                new Label("Nội dung email"), body, new HBox(12,send,saveDraft),
                new HBox(10,addAttachment,clearAttachments,attachmentLabel));
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
        Label hint = new Label("Gmail: SMTP 587 (STARTTLS), IMAP 993 (SSL).\n"
                + "Thay đổi tại đây chỉ áp dụng đến khi đóng ứng dụng.\n"
                + "Muốn lưu cho lần chạy sau, hãy sửa .env ở thư mục gốc.");
        hint.setTextFill(Color.web("#687b97"));
        Button localPreset = primary("Dùng localhost");
        localPreset.setOnAction(e -> {
            smtpHost.setText("127.0.0.1"); smtpPort.setText("2525");
            popHost.setText("127.0.0.1"); popPort.setText("2110");
            email.setText("alice@localhost"); password.clear();
            status.setText("Tạo tài khoản trên Server Dashboard rồi nhập mật khẩu");
        });
        Button gmailPreset = new Button("Dùng Gmail");
        gmailPreset.setOnAction(e -> {
            smtpHost.setText("smtp.gmail.com"); smtpPort.setText("587");
            popHost.setText("pop.gmail.com"); popPort.setText("995");
        });
        Button apply = primary("✓  Áp dụng");
        apply.setOnAction(e -> {
            try { MailAccount acc = account(); accountLabel.setText(acc.email());
                sessionAccounts.put(acc.email(), acc);
                if (!accountSwitcher.getItems().contains(acc.email())) accountSwitcher.getItems().add(acc.email());
                accountSwitcher.setValue(acc.email());
                inboxOffset = 0; allInbox.clear(); popBodyCache.clear();
                showPage("inbox"); }
            catch (Exception ex) { error(ex); }
        });
        VBox card = new VBox(25, new HBox(12, localPreset, gmailPreset), grid, hint, apply);
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
        bar.setStyle("-fx-background-color: #f6f8fc;");
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

    @Override public void stop() { if (imapPolling != null) imapPolling.stop(); workers.shutdownNow(); cacheWorkers.shutdownNow(); }
}
