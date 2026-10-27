package vn.edu.mailclient.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/** One-page administration dashboard for the classroom mail server. */
public final class ServerDashboard {
    private static final Color BLUE=new Color(18,91,190), BG=new Color(246,248,252);
    private static final Color BORDER=new Color(222,228,238), TEXT=new Color(30,41,59);
    private static final Color MUTED=new Color(100,116,139), GREEN=new Color(21,145,98), RED=new Color(220,65,65);
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JFrame frame=new JFrame("UDP MAIL SERVER");
    private final DefaultTableModel accounts=model("STT","Tên đăng nhập","Hộp thư","Trạng thái");
    private final DefaultTableModel mail=model("ID","From","To","Nội dung / Subject","Sent at");
    private final DefaultTableModel events=model("Thời gian","Sự kiện","Thông tin");
    private final JTable accountTable=table(accounts),mailTable=table(mail),eventTable=table(events);
    private final JLabel accountCount=metric("0"),serviceCount=metric("0"),emailCount=metric("0"),uptime=metric("00:00:00");
    private final JLabel dbStatus=pill("● DB: CHECKING",new Color(25,111,181));
    private final JLabel serverStatus=pill("● ĐÃ DỪNG",RED),serverInfo=new JLabel(),eventCount=new JLabel("0 sự kiện");
    private final String url,user,password;
    private final Instant startedAt=Instant.now();
    private LocalMailProtocols protocols;
    private int smtpPort=2525,popPort=2110;
    private JButton startButton,stopButton;

    public ServerDashboard(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
    public void configureProtocols(LocalMailProtocols protocols,int smtpPort,int popPort){this.protocols=protocols;this.smtpPort=smtpPort;this.popPort=popPort;}

    public void show(){SwingUtilities.invokeLater(()->{
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);frame.setSize(1420,880);frame.setMinimumSize(new Dimension(1050,700));
        frame.setLocationRelativeTo(null);frame.setLayout(new BorderLayout());frame.add(header(),BorderLayout.NORTH);frame.add(content(),BorderLayout.CENTER);
        frame.setVisible(true);updateServerStatus();refresh();new Timer(1000,e->updateClock()).start();new Timer(15000,e->refresh()).start();
    });}

    private JComponent header(){
        JPanel h=new JPanel(new BorderLayout(24,10));h.setBackground(BLUE);h.setBorder(new EmptyBorder(18,28,18,28));
        JLabel icon=new JLabel("▤",SwingConstants.CENTER);icon.setFont(new Font("Segoe UI Symbol",Font.BOLD,28));icon.setForeground(Color.WHITE);
        icon.setOpaque(true);icon.setBackground(new Color(52,125,213));icon.setPreferredSize(new Dimension(54,54));
        JLabel title=new JLabel("UDP MAIL SERVER");title.setForeground(Color.WHITE);title.setFont(new Font("Segoe UI",Font.BOLD,22));
        JLabel sub=new JLabel("Client ↔ Server · SMTP / POP3 / UDP · MySQL localhost");sub.setForeground(new Color(205,226,255));
        JPanel names=transparent(new GridLayout(2,1,0,3));names.add(title);names.add(sub);
        JPanel brand=transparent(new BorderLayout(14,0));brand.add(icon,BorderLayout.WEST);brand.add(names,BorderLayout.CENTER);
        serverInfo.setForeground(new Color(220,235,255));serverInfo.setFont(new Font("Segoe UI",Font.PLAIN,13));
        JPanel left=transparent(new BorderLayout(0,12));left.add(brand,BorderLayout.CENTER);left.add(serverInfo,BorderLayout.SOUTH);
        startButton=action("▶  Khởi động",new Color(69,143,225));stopButton=action("■  Dừng Server",new Color(235,72,72));
        startButton.addActionListener(e->changeServerState(true));stopButton.addActionListener(e->changeServerState(false));
        JPanel states=transparent(new FlowLayout(FlowLayout.RIGHT,10,0));states.add(dbStatus);states.add(serverStatus);
        JPanel controls=transparent(new FlowLayout(FlowLayout.RIGHT,10,0));controls.add(startButton);controls.add(stopButton);
        JPanel right=transparent(new GridLayout(2,1,0,10));right.add(states);right.add(controls);
        h.add(left,BorderLayout.CENTER);h.add(right,BorderLayout.EAST);return h;
    }

    private JComponent content(){
        JPanel body=new JPanel();body.setBackground(BG);body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setBorder(new EmptyBorder(18,24,22,24));
        JPanel cards=new JPanel(new GridLayout(1,4,16,0));cards.setOpaque(false);cards.setMaximumSize(new Dimension(Integer.MAX_VALUE,105));
        cards.add(statCard("♟",accountCount,"Tài khoản","Tổng số tài khoản",new Color(220,235,255),new Color(24,119,200)));
        cards.add(statCard("▣",serviceCount,"Dịch vụ online","SMTP và POP3",new Color(213,247,233),GREEN));
        cards.add(statCard("✉",emailCount,"Email (DB)","Bản ghi local_mail",new Color(207,247,242),new Color(22,146,133)));
        cards.add(statCard("◷",uptime,"Uptime","Thời gian hoạt động",new Color(255,245,199),new Color(230,158,17)));
        body.add(cards);body.add(Box.createVerticalStrut(16));
        body.add(section("♟  Danh sách người dùng",accountTable,158,button("＋ Tạo tài khoản",e->addAccount()),button("↻ Làm mới",e->refresh())));
        body.add(Box.createVerticalStrut(13));
        body.add(section("▰  Database emails (MySQL · local_mail)",mailTable,210,button("Xem nội dung",e->showSelectedMessage()),button("↻ Làm mới DB",e->refresh())));
        body.add(Box.createVerticalStrut(13));
        body.add(section("◴  Nhật ký hoạt động",eventTable,190,eventCount,button("Xóa log",e->{events.setRowCount(0);updateEventCount();})));
        JScrollPane scroll=new JScrollPane(body);scroll.setBorder(null);scroll.getVerticalScrollBar().setUnitIncrement(16);scroll.getViewport().setBackground(BG);return scroll;
    }

    private JPanel statCard(String symbol,JLabel value,String name,String note,Color tile,Color accent){
        JLabel icon=new JLabel(symbol,SwingConstants.CENTER);icon.setFont(new Font("Segoe UI Symbol",Font.BOLD,25));icon.setForeground(accent);
        icon.setOpaque(true);icon.setBackground(tile);icon.setPreferredSize(new Dimension(55,62));
        JLabel label=new JLabel(name);label.setFont(new Font("Segoe UI",Font.BOLD,14));label.setForeground(TEXT);
        JLabel sub=new JLabel(note);sub.setFont(new Font("Segoe UI",Font.PLAIN,12));sub.setForeground(MUTED);
        JPanel copy=transparent(new GridLayout(3,1,0,1));copy.add(value);copy.add(label);copy.add(sub);
        JPanel card=new JPanel(new BorderLayout(15,0));card.setBackground(Color.WHITE);card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),new EmptyBorder(16,18,16,18)));card.add(icon,BorderLayout.WEST);card.add(copy);return card;
    }

    private JPanel section(String title,JTable table,int height,JComponent... actions){
        JLabel heading=new JLabel(title);heading.setFont(new Font("Segoe UI",Font.BOLD,15));heading.setForeground(TEXT);
        JPanel tools=transparent(new FlowLayout(FlowLayout.RIGHT,8,0));for(JComponent action:actions)tools.add(action);
        JPanel top=transparent(new BorderLayout());top.add(heading,BorderLayout.WEST);top.add(tools,BorderLayout.EAST);
        JScrollPane pane=new JScrollPane(table);pane.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel section=transparent(new BorderLayout(0,9));section.add(top,BorderLayout.NORTH);section.add(pane);section.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));
        section.setPreferredSize(new Dimension(1200,height));return section;
    }

    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static JTable table(DefaultTableModel model){
        JTable t=new JTable(model);t.setRowHeight(31);t.setFont(new Font("Segoe UI",Font.PLAIN,13));t.setForeground(TEXT);t.setGridColor(new Color(235,239,245));
        t.setSelectionBackground(new Color(219,234,254));t.setSelectionForeground(TEXT);t.getTableHeader().setFont(new Font("Segoe UI",Font.BOLD,13));
        t.getTableHeader().setBackground(new Color(238,242,247));t.getTableHeader().setPreferredSize(new Dimension(0,31));
        DefaultTableCellRenderer renderer=new DefaultTableCellRenderer();renderer.setBorder(new EmptyBorder(0,8,0,8));t.setDefaultRenderer(Object.class,renderer);return t;
    }
    private static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    private static JLabel metric(String text){JLabel l=new JLabel(text);l.setFont(new Font("Segoe UI",Font.BOLD,22));l.setForeground(TEXT);return l;}
    private static JLabel pill(String text,Color color){JLabel l=new JLabel(text);l.setOpaque(true);l.setBackground(color);l.setForeground(Color.WHITE);l.setFont(new Font("Segoe UI",Font.BOLD,12));l.setBorder(new EmptyBorder(8,14,8,14));return l;}
    private static JButton action(String text,Color bg){JButton b=new JButton(text);b.setBackground(bg);b.setForeground(Color.WHITE);b.setFont(new Font("Segoe UI",Font.BOLD,12));b.setFocusPainted(false);b.setBorder(new EmptyBorder(9,15,9,15));return b;}
    private static JButton button(String text,java.awt.event.ActionListener listener){JButton b=new JButton(text);b.addActionListener(listener);b.setFocusPainted(false);b.setFont(new Font("Segoe UI",Font.PLAIN,12));b.setBackground(Color.WHITE);b.setForeground(MUTED);b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),new EmptyBorder(6,11,6,11)));return b;}
    private Connection db()throws SQLException{return DriverManager.getConnection(url,user,password);}
    private void updateClock(){long s=Duration.between(startedAt,Instant.now()).getSeconds();uptime.setText(String.format("%02d:%02d:%02d",s/3600,s/60%60,s%60));}

    private void updateServerStatus(){
        boolean running=protocols!=null&&protocols.isRunning();serverStatus.setText(running?"● ĐANG HOẠT ĐỘNG":"● ĐÃ DỪNG");serverStatus.setBackground(running?GREEN:RED);
        serviceCount.setText(running?"2":"0");if(startButton!=null)startButton.setEnabled(!running);if(stopButton!=null)stopButton.setEnabled(running);
        String host="127.0.0.1";try{host=InetAddress.getLocalHost().getHostAddress();}catch(Exception ignored){}
        serverInfo.setText("IP Server:  "+host+"     SMTP:  "+smtpPort+"     POP3:  "+popPort+"     UDP:  2023");
    }
    private void changeServerState(boolean start){if(protocols==null){log("Server chưa được cấu hình");return;}startButton.setEnabled(false);stopButton.setEnabled(false);
        new Thread(()->{try{if(start)protocols.start(smtpPort,popPort);else protocols.stop();log(start?"SMTP/POP3 started":"SMTP/POP3 stopped");}catch(Exception ex){error(ex);}finally{SwingUtilities.invokeLater(this::updateServerStatus);}},"server-lifecycle").start();}

    public void log(String message){SwingUtilities.invokeLater(()->{String type=message.startsWith("ERROR")?"ERROR":message.startsWith("SMTP")?"SMTP":message.startsWith("POP3")?"POP3":"SERVER";
        events.insertRow(0,new Object[]{LocalTime.now().format(CLOCK),type,message});while(events.getRowCount()>300)events.removeRow(events.getRowCount()-1);updateEventCount();});}
    private void updateEventCount(){eventCount.setText(events.getRowCount()+" sự kiện");}

    private void refresh(){new Thread(()->{try(Connection c=db()){
        java.util.List<Object[]> users=new java.util.ArrayList<>(),messages=new java.util.ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT u.username,(SELECT COUNT(*) FROM local_mail m WHERE m.recipient=u.username) FROM lan_users u ORDER BY u.username")){int i=1;while(r.next())users.add(new Object[]{i++,r.getString(1),r.getInt(2),"● Offline"});}
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,sender,recipient,LEFT(raw_message,160),created_at FROM local_mail ORDER BY id DESC LIMIT 200")){while(r.next())messages.add(new Object[]{r.getLong(1),r.getString(2),r.getString(3)+"@localhost",r.getString(4),r.getString(5)});}
        SwingUtilities.invokeLater(()->{accounts.setRowCount(0);for(Object[] row:users)accounts.addRow(row);mail.setRowCount(0);for(Object[] row:messages)mail.addRow(row);
            accountCount.setText(String.valueOf(users.size()));emailCount.setText(String.valueOf(messages.size()));dbStatus.setText("● DB: CONNECTED");dbStatus.setBackground(GREEN);updateServerStatus();});
    }catch(Exception ex){SwingUtilities.invokeLater(()->{dbStatus.setText("● DB: DISCONNECTED");dbStatus.setBackground(RED);});log("ERROR DB: "+ex.getMessage());}},"dashboard-refresh").start();}

    private void showSelectedMessage(){int row=mailTable.getSelectedRow();if(row<0){JOptionPane.showMessageDialog(frame,"Hãy chọn một email.");return;}long id=Long.parseLong(mailTable.getValueAt(row,0).toString());
        try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT raw_message FROM local_mail WHERE id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(r.next()){JTextArea area=new JTextArea(r.getString(1),24,75);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);JOptionPane.showMessageDialog(frame,new JScrollPane(area),"Nội dung email #"+id,JOptionPane.INFORMATION_MESSAGE);log("READ email #"+id);}}}catch(Exception ex){error(ex);}}

    private void addAccount(){JTextField username=new JTextField();JPasswordField pwd=new JPasswordField();JPanel fields=new JPanel(new GridLayout(0,1,5,5));fields.add(new JLabel("Tên tài khoản (ví dụ: alice)"));fields.add(username);fields.add(new JLabel("Mật khẩu (ít nhất 6 ký tự)"));fields.add(pwd);
        if(JOptionPane.showConfirmDialog(frame,fields,"Tạo mailbox @localhost",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String name=username.getText().trim().toLowerCase(java.util.Locale.ROOT),plain=new String(pwd.getPassword());if(!name.matches("[a-z0-9_.-]{3,40}")||plain.length()<6){JOptionPane.showMessageDialog(frame,"Tên gồm 3–40 ký tự và mật khẩu từ 6 ký tự.");return;}
        try{byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);PBEKeySpec spec=new PBEKeySpec(plain.toCharArray(),salt,120000,256);byte[] hash;try{hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
            try(Connection c=db();PreparedStatement s=c.prepareStatement("INSERT INTO lan_users(username,password_salt,password_hash) VALUES(?,?,?)")){s.setString(1,name);s.setString(2,Base64.getEncoder().encodeToString(salt));s.setString(3,Base64.getEncoder().encodeToString(hash));s.executeUpdate();}log("Tạo mailbox: "+name+"@localhost");refresh();
        }catch(Exception ex){error(ex);}}
    private void error(Exception ex){log("ERROR: "+ex.getMessage());SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,ex.getMessage(),"Lỗi",JOptionPane.ERROR_MESSAGE));}
}
