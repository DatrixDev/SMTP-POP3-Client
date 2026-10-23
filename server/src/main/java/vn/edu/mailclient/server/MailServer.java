package vn.edu.mailclient.server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Lab LAN UDP mail server; bind to trusted private network only (UDP is not encrypted). */
public final class MailServer {
    private static final Map<String,String> sessions = new ConcurrentHashMap<>();
    private static final Map<String,Long> seen = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();
    private static String url,user,pass;
    private static String env(String key,String fallback) {
        String v=System.getenv(key);
        if(v!=null&&!v.isBlank()) return v;
        for(Path path:List.of(Path.of(".env"),Path.of("../.env"))) {
            if(!Files.isRegularFile(path)) continue;
            try {
                for(String line:Files.readAllLines(path)) {
                    if(line.trim().startsWith(key+"=")) return line.substring(line.indexOf('=')+1).trim().replaceAll("^[\\\"']|[\\\"']$","");
                }
            } catch(Exception ignored){}
            break;
        }
        return fallback;
    }
    private static Connection db() throws SQLException {return DriverManager.getConnection(url,user,pass);}
    private static String enc(String s) {return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s) {return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);}
    private static String err(String s) {return "ERR|"+enc(s);}
    private static String hash(String pwd,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pwd.toCharArray(),salt,120000,256);
        try {return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}
        finally {spec.clearPassword();}
    }
    private static String auth(String token) {
        String name=sessions.get(token);
        if(name==null) throw new IllegalArgumentException("Bạn chưa đăng nhập");
        seen.put(name,System.currentTimeMillis());
        return name;
    }
    private static String run(String req) throws Exception {
        String[] a=req.split("\\|",-1);
        if(a.length==0) return err("Yêu cầu trống");
        try(Connection c=db()) {
            switch(a[0]) {
                case "PING":return "OK|PONG";
                case "REGISTER": {
                    if(a.length!=3) return err("Thiếu thông tin");
                    String name=dec(a[1]).trim().toLowerCase(Locale.ROOT),password=dec(a[2]);
                    if(!name.matches("[a-z0-9_.-]{3,40}")||password.length()<6) return err("Tên 3-40 ký tự a-z/0-9/._- và mật khẩu tối thiểu 6 ký tự");
                    byte[] salt=new byte[16];random.nextBytes(salt);
                    try(PreparedStatement st=c.prepareStatement("INSERT INTO lan_users(username,password_salt,password_hash) VALUES(?,?,?)")) {
                        st.setString(1,name);st.setString(2,Base64.getEncoder().encodeToString(salt));st.setString(3,hash(password,salt));st.executeUpdate();
                    }
                    return "OK|REGISTERED";
                }
                case "LOGIN": {
                    if(a.length!=3)return err("Thiếu thông tin");
                    String name=dec(a[1]).trim().toLowerCase(Locale.ROOT);
                    try(PreparedStatement st=c.prepareStatement("SELECT password_salt,password_hash FROM lan_users WHERE username=?")) {
                        st.setString(1,name);
                        try(ResultSet rs=st.executeQuery()) {
                            if(!rs.next())return err("Sai tài khoản hoặc mật khẩu");
                            byte[] salt=Base64.getDecoder().decode(rs.getString(1));
                            if(!MessageDigest.isEqual(hash(dec(a[2]),salt).getBytes(StandardCharsets.UTF_8),rs.getString(2).getBytes(StandardCharsets.UTF_8)))return err("Sai tài khoản hoặc mật khẩu");
                        }
                    }
                    String token=UUID.randomUUID().toString();sessions.put(token,name);seen.put(name,System.currentTimeMillis());
                    return "OK|"+token;
                }
                case "SEND": {
                    if(a.length!=5)return err("Thiếu thông tin");
                    String sender=auth(a[1]),to=dec(a[2]).trim().toLowerCase(Locale.ROOT),subject=dec(a[3]),body=dec(a[4]);
                    if(subject.length()>500||body.length()>16000)return err("Thư quá dài");
                    try(PreparedStatement st=c.prepareStatement("INSERT INTO lan_messages(sender,recipient,subject,body) VALUES(?,?,?,?)")) {
                        st.setString(1,sender);st.setString(2,to);st.setString(3,subject);st.setString(4,body);st.executeUpdate();
                    }
                    return "OK|SENT";
                }
                case "MAILBOX": {
                    if(a.length!=3)return err("Thiếu thông tin");
                    String name=auth(a[1]),folder=a[2];
                    if(!folder.equals("INBOX")&&!folder.equals("SENT"))return err("Hộp thư không hợp lệ");
                    String sql="SELECT id,sender,recipient,subject,body FROM lan_messages WHERE "+(folder.equals("INBOX")?"recipient":"sender")+"=? ORDER BY id DESC LIMIT 30";
                    StringBuilder out=new StringBuilder("OK");
                    try(PreparedStatement st=c.prepareStatement(sql)) {
                        st.setString(1,name);
                        try(ResultSet rs=st.executeQuery()) {
                            while(rs.next()) {
                                String row=enc(rs.getString(1))+"|"+enc(rs.getString(2))+"|"+enc(rs.getString(3))+"|"+enc(rs.getString(4))+"|"+enc(rs.getString(5));
                                if(out.length()+row.length()+1>50000)break;
                                out.append("\n").append(row);
                            }
                        }
                    }
                    return out.toString();
                }
                case "ONLINE": {
                    auth(a[1]);StringBuilder out=new StringBuilder("OK");
                    long now=System.currentTimeMillis();
                    try(PreparedStatement st=c.prepareStatement("SELECT username FROM lan_users ORDER BY username");ResultSet rs=st.executeQuery()) {
                        while(rs.next()) {
                            String name=rs.getString(1);
                            if(now-seen.getOrDefault(name,0L)<120000)out.append("\n").append(enc(name));
                        }
                    }
                    return out.toString();
                }
                case "USERS": {
                    auth(a[1]);StringBuilder out=new StringBuilder("OK");
                    try(PreparedStatement st=c.prepareStatement("SELECT username FROM lan_users ORDER BY username");ResultSet rs=st.executeQuery()) {
                        while(rs.next())out.append("\n").append(enc(rs.getString(1)));
                    }
                    return out.toString();
                }
                case "LOGOUT": {sessions.remove(a[1]);return "OK|BYE";}
                default:return err("Lệnh không hỗ trợ");
            }
        } catch(SQLException ex) {
            if(ex.getErrorCode()==1062)return err("Tài khoản đã tồn tại");
            if(ex.getErrorCode()==1452)return err("Người nhận chưa đăng ký");
            throw ex;
        }
    }
    public static void main(String[] args) throws Exception {
        url=env("MAIL_DB_URL","jdbc:mysql://127.0.0.1:3307/smtp_pop3_client");
        user=env("MAIL_DB_USER",env("MYSQL_USER","mailapp"));
        pass=env("MAIL_DB_PASSWORD",env("MYSQL_PASSWORD",""));
        String bind=env("LAN_BIND","127.0.0.1");
        int port=Integer.parseInt(env("LAN_PORT","2023"));
        try(Connection c=db();Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lan_users (username VARCHAR(40) PRIMARY KEY,password_salt VARCHAR(64) NOT NULL,password_hash VARCHAR(128) NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS lan_messages (id BIGINT AUTO_INCREMENT PRIMARY KEY,sender VARCHAR(40) NOT NULL,recipient VARCHAR(40) NOT NULL,subject VARCHAR(500),body TEXT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(sender) REFERENCES lan_users(username),FOREIGN KEY(recipient) REFERENCES lan_users(username))");
        }
        try(DatagramSocket socket=new DatagramSocket(new InetSocketAddress(bind,port))) {
            System.out.println("Mail LAN UDP server: "+bind+":"+port);
            byte[] buffer=new byte[60000];
            while(true) {
                DatagramPacket p=new DatagramPacket(buffer,buffer.length);socket.receive(p);
                String response;
                try {response=run(new String(p.getData(),p.getOffset(),p.getLength(),StandardCharsets.UTF_8));}
                catch(Exception ex) {response=err(ex.getMessage()==null?"Server error":ex.getMessage());System.err.println(ex);}
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(bytes,bytes.length,p.getSocketAddress()));
            }
        }
    }
}
