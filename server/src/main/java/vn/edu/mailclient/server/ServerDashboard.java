package vn.edu.mailclient.server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Base64;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local server administration window. Not a replacement for the mail client. */
public final class ServerDashboard {
    private static final Color NAVY=new Color(18,43,78),BLUE=new Color(26,115,232);
    private final JFrame frame=new JFrame("Mail Server • Quản trị");
    private final DefaultTableModel accounts=new DefaultTableModel(new String[]{"Tài khoản","Hộp thư"},0);
    private final DefaultTableModel mail=new DefaultTableModel(new String[]{"ID","Người gửi","Người nhận","Chủ đề","Thời gian"},0);
    private final JTextArea events=new JTextArea();
    private final String url,user,password;
    public ServerDashboard(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
    private Connection db()throws SQLException{return DriverManager.getConnection(url,user,password);}
    public void log(String message){SwingUtilities.invokeLater(()->{events.append(message+"\n");events.setCaretPosition(events.getDocument().getLength());});}
    public void show(){
        SwingUtilities.invokeLater(()->{
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100,730);frame.setMinimumSize(new Dimension(850,550));
            frame.setLocationRelativeTo(null);
            JPanel header=new JPanel(new BorderLayout());header.setBackground(NAVY);header.setBorder(BorderFactory.createEmptyBorder(20,25,20,25));
            JLabel title=new JLabel("✉  LOCAL MAIL SERVER");title.setForeground(Color.WHITE);title.setFont(new Font("SansSerif",Font.BOLD,23));header.add(title,BorderLayout.WEST);
            JLabel ports=new JLabel("SMTP : 2525    •    POP3 : 2110    •    MySQL");ports.setForeground(new Color(168,221,255));header.add(ports,BorderLayout.EAST);
            JTabbedPane tabs=new JTabbedPane();
            tabs.addTab("Tổng quan",overview());
            tabs.addTab("Tài khoản",accountPanel());
            tabs.addTab("Thư trên Server",mailPanel());
            events.setEditable(false);events.setFont(new Font("Monospaced",Font.PLAIN,13));tabs.addTab("Nhật ký",new JScrollPane(events));
            frame.add(header,BorderLayout.NORTH);frame.add(tabs,BorderLayout.CENTER);
            frame.getContentPane().setBackground(new Color(244,247,252));
            frame.setVisible(true);refresh();
        });
    }
    private JPanel overview(){
        JPanel p=new JPanel(new BorderLayout(15,15));p.setBorder(BorderFactory.createEmptyBorder(25,25,25,25));
        JTextArea info=new JTextArea("SMTP Server: localhost:2525 (không TLS, chỉ máy này)\n"+
            "POP3 Server: localhost:2110 (không SSL, chỉ máy này)\n"+
            "Email nội bộ: tennguoidung@localhost\n"+
            "Tài khoản: tạo ở tab Tài khoản hoặc đăng ký qua UDP LAN\n"+
            "Gmail: Client kết nối trực tiếp SMTP/POP3 Google, không qua server này.\n\n"+
            "Dữ liệu thư SMTP được lưu trong bảng local_mail của MySQL.\n"+
            "Đây là server thực hành, KHÔNG mở cổng ra Internet.");
        info.setEditable(false);info.setOpaque(false);info.setFont(new Font("SansSerif",Font.PLAIN,17));info.setLineWrap(true);info.setWrapStyleWord(true);
        p.add(info,BorderLayout.CENTER);
        JButton refresh=button("↻  Làm mới dữ liệu");refresh.addActionListener(e->refresh());p.add(refresh,BorderLayout.SOUTH);return p;
    }
    private JPanel accountPanel(){
        JPanel p=new JPanel(new BorderLayout(12,12));p.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JTable table=new JTable(accounts);table.setRowHeight(32);p.add(new JScrollPane(table),BorderLayout.CENTER);
        JButton add=button("+  Tạo tài khoản localhost");add.addActionListener(e->addAccount());
        JButton refresh=button("Làm mới");refresh.addActionListener(e->refresh());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(add);actions.add(refresh);p.add(actions,BorderLayout.NORTH);return p;
    }
    private JPanel mailPanel(){
        JPanel p=new JPanel(new BorderLayout(12,12));p.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        JTable table=new JTable(mail);table.setRowHeight(32);p.add(new JScrollPane(table),BorderLayout.CENTER);
        JButton refresh=button("↻  Làm mới danh sách thư");refresh.addActionListener(e->refresh());
        JButton detail=button("Xem nội dung");detail.addActionListener(e->{
            int row=table.getSelectedRow();if(row<0)return;
            long id=Long.parseLong(table.getValueAt(row,0).toString());
            try(Connection c=db();PreparedStatement s=c.prepareStatement("SELECT raw_message FROM local_mail WHERE id=?")){
                s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(r.next()){
                    JTextArea area=new JTextArea(r.getString(1),22,65);area.setEditable(false);
                    JOptionPane.showMessageDialog(frame,new JScrollPane(area),"Nội dung thư #"+id,JOptionPane.INFORMATION_MESSAGE);
                }}
            }catch(Exception ex){error(ex);}
        });
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(refresh);actions.add(detail);p.add(actions,BorderLayout.NORTH);return p;
    }
    private JButton button(String name){JButton b=new JButton(name);b.setBackground(BLUE);b.setForeground(Color.WHITE);b.setFocusPainted(false);return b;}
    private void addAccount(){
        JTextField username=new JTextField();JPasswordField pwd=new JPasswordField();
        JPanel fields=new JPanel(new GridLayout(0,1,5,5));fields.add(new JLabel("Tên tài khoản (ví dụ: alice)"));fields.add(username);
        fields.add(new JLabel("Mật khẩu (ít nhất 6 ký tự)"));fields.add(pwd);
        if(JOptionPane.showConfirmDialog(frame,fields,"Tạo mailbox @localhost",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String name=username.getText().trim().toLowerCase(java.util.Locale.ROOT),password=new String(pwd.getPassword());
        if(!name.matches("[a-z0-9_.-]{3,40}")||password.length()<6){JOptionPane.showMessageDialog(frame,"Tên 3–40 ký tự và mật khẩu từ 6 ký tự");return;}
        try{
            byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
            PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,120000,256);
            byte[] hash;try{hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
            try(Connection c=db();PreparedStatement s=c.prepareStatement("INSERT INTO lan_users(username,password_salt,password_hash) VALUES(?,?,?)")){
                s.setString(1,name);s.setString(2,Base64.getEncoder().encodeToString(salt));s.setString(3,Base64.getEncoder().encodeToString(hash));s.executeUpdate();
            }
            log("Tạo mailbox: "+name+"@localhost");refresh();
        }catch(Exception ex){error(ex);}
    }
    private void refresh(){
        new Thread(()->{
            try(Connection c=db()){
                java.util.List<Object[]> users=new java.util.ArrayList<>(),messages=new java.util.ArrayList<>();
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT u.username,(SELECT COUNT(*) FROM local_mail m WHERE m.recipient=u.username) FROM lan_users u ORDER BY u.username")){while(r.next())users.add(new Object[]{r.getString(1)+"@localhost",r.getInt(2)});}
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,sender,recipient,LEFT(raw_message,80),created_at FROM local_mail ORDER BY id DESC LIMIT 200")){while(r.next())messages.add(new Object[]{r.getLong(1),r.getString(2),r.getString(3)+"@localhost",r.getString(4),r.getString(5)});}
                SwingUtilities.invokeLater(()->{accounts.setRowCount(0);for(Object[] a:users)accounts.addRow(a);mail.setRowCount(0);for(Object[] a:messages)mail.addRow(a);});
            }catch(Exception ex){error(ex);}
        },"dashboard-refresh").start();
    }
    private void error(Exception ex){log("ERROR: "+ex.getMessage());SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,ex.getMessage(),"Lỗi",JOptionPane.ERROR_MESSAGE));}
}
