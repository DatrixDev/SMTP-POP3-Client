package vn.edu.mailclient.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Loopback-only teaching SMTP/POP3 server backed by MySQL. No Internet relay. */
public final class LocalMailProtocols {
    private final String url, user, password;
    private final java.util.List<ServerSocket> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Set<Socket> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"mail-protocol-worker");t.setDaemon(true);return t;});
    private final java.util.function.Consumer<String> log;
    public LocalMailProtocols(String url,String user,String password,java.util.function.Consumer<String> log) {
        this.url=url;this.user=user;this.password=password;this.log=log;
    }
    private Connection db() throws SQLException {return DriverManager.getConnection(url,user,password);}
    public synchronized void start(int smtpPort,int popPort) throws Exception {
        if (running) return;
        try(Connection c=db();Statement st=c.createStatement()){
            st.executeUpdate("CREATE TABLE IF NOT EXISTS local_mail (id BIGINT AUTO_INCREMENT PRIMARY KEY, recipient VARCHAR(40) NOT NULL, sender VARCHAR(320) NOT NULL, raw_message MEDIUMTEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, INDEX(recipient))");
        }
        ServerSocket smtp = new ServerSocket();
        ServerSocket pop = new ServerSocket();
        try {
            smtp.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(),smtpPort));
            pop.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(),popPort));
            listeners.add(smtp);
            listeners.add(pop);
            running = true;
            serve(smtp,true);
            serve(pop,false);
        } catch(Exception ex) {
            try { smtp.close(); } catch(IOException ignored) {}
            try { pop.close(); } catch(IOException ignored) {}
            listeners.clear();
            running = false;
            throw ex;
        }
    }
    public synchronized void stop() {
        if (!running) return;
        running = false;
        for (ServerSocket listener : listeners) {
            try { listener.close(); } catch(IOException ignored) {}
        }
        listeners.clear();
        for (Socket client : clients) {
            try { client.close(); } catch(IOException ignored) {}
        }
        clients.clear();
        log.accept("SMTP/POP3 stopped");
    }
    public boolean isRunning() { return running; }
    private void serve(ServerSocket server,boolean smtp) {
        workers.execute(()->{
            log.accept((smtp?"SMTP":"POP3")+" localhost:"+server.getLocalPort()+" ready");
            while(running&&!server.isClosed()) try {
                Socket socket=server.accept();
                clients.add(socket);
                workers.execute(()->{
                    try { handle(socket,smtp); }
                    finally { clients.remove(socket); }
                });
            } catch(IOException ex) {
                if(running&&!server.isClosed()) log.accept("Listener error: "+ex.getMessage());
            }
        });
    }
    private void handle(Socket socket,boolean smtp) {
        try(socket){
            socket.setSoTimeout(120000);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            if(smtp) smtp(in,out); else pop(in,out);
        }catch(Exception ex){log.accept("Client disconnected: "+ex.getMessage());}
    }
    private static void send(BufferedWriter out,String line)throws IOException{out.write(line);out.write("\r\n");out.flush();}
    private static String mailbox(String addr) {
        String a=addr.trim().replace("<","").replace(">","").toLowerCase(Locale.ROOT);
        if(!a.endsWith("@localhost"))return null;
        String name=a.substring(0,a.length()-10);
        return name.matches("[a-z0-9_.-]{3,40}")?name:null;
    }
    private boolean exists(String name)throws SQLException{
        try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT 1 FROM lan_users WHERE username=?")){
            p.setString(1,name);try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }
    private boolean login(String name,String pwd)throws Exception{
        try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT password_salt,password_hash FROM lan_users WHERE username=?")){
            p.setString(1,name);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())return false;
                byte[] salt=Base64.getDecoder().decode(r.getString(1));
                PBEKeySpec spec=new PBEKeySpec(pwd.toCharArray(),salt,120000,256);
                try {
                    byte[] actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                    return MessageDigest.isEqual(actual,Base64.getDecoder().decode(r.getString(2)));
                }finally{spec.clearPassword();}
            }
        }
    }
    private void smtp(BufferedReader in,BufferedWriter out)throws Exception{
        send(out,"220 localhost ESMTP Classroom Mail");
        String sender=null,authUser=null;List<String> recipients=new ArrayList<>();String line;
        while((line=in.readLine())!=null){
            String cmd=line.toUpperCase(Locale.ROOT);
            if(cmd.startsWith("EHLO")||cmd.startsWith("HELO")){
                send(out,"250-localhost");send(out,"250-AUTH LOGIN");send(out,"250 8BITMIME");
            }else if(cmd.equals("AUTH LOGIN")){
                send(out,"334 VXNlcm5hbWU6");
                String rawUser=in.readLine();if(rawUser==null)break;
                send(out,"334 UGFzc3dvcmQ6");
                String rawPass=in.readLine();if(rawPass==null)break;
                try{
                    String account=new String(Base64.getDecoder().decode(rawUser),StandardCharsets.UTF_8);
                    String pwd=new String(Base64.getDecoder().decode(rawPass),StandardCharsets.UTF_8);
                    String name=mailbox(account);
                    if(name!=null&&login(name,pwd)){authUser=name;send(out,"235 Authentication successful");}
                    else send(out,"535 Authentication failed");
                }catch(Exception ex){send(out,"535 Authentication failed");}
            }else if(cmd.startsWith("MAIL FROM:")){
                String value=line.substring(10).trim();
                String name=mailbox(value);
                if(authUser==null)send(out,"530 Authentication required");
                else if(name==null||!name.equals(authUser))send(out,"553 Sender must match authenticated localhost account");
                else{sender=value;recipients.clear();send(out,"250 Sender OK");}
            }else if(cmd.startsWith("RCPT TO:")){
                String name=mailbox(line.substring(8));
                if(sender==null)send(out,"503 MAIL FROM required");
                else if(name==null||!exists(name))send(out,"550 Unknown local recipient");
                else{recipients.add(name);send(out,"250 Recipient OK");}
            }else if(cmd.equals("DATA")){
                if(sender==null||recipients.isEmpty()){send(out,"503 MAIL FROM and RCPT TO required");continue;}
                send(out,"354 End with <CRLF>.<CRLF>");
                StringBuilder msg=new StringBuilder();String part;boolean tooLong=false;
                while((part=in.readLine())!=null&&!part.equals(".")){
                    if(part.startsWith(".."))part=part.substring(1);
                    if(msg.length()+part.length()>1024*1024)tooLong=true;
                    if(!tooLong)msg.append(part).append("\r\n");
                }
                if(tooLong){send(out,"552 Message too large");continue;}
                try(Connection c=db();PreparedStatement p=c.prepareStatement("INSERT INTO local_mail(recipient,sender,raw_message) VALUES(?,?,?)")){
                    for(String to:recipients){p.setString(1,to);p.setString(2,sender);p.setString(3,msg.toString());p.addBatch();}
                    p.executeBatch();
                }
                log.accept("SMTP: "+sender+" -> "+String.join(", ",recipients));
                sender=null;recipients.clear();send(out,"250 Message queued");
            }else if(cmd.equals("RSET")){sender=null;recipients.clear();send(out,"250 Reset");}
            else if(cmd.equals("NOOP"))send(out,"250 OK");
            else if(cmd.equals("QUIT")){send(out,"221 Bye");break;}
            else send(out,"502 Command not implemented");
        }
    }
    private record Mail(long id,String raw){}
    private List<Mail> inbox(String name)throws SQLException{
        List<Mail> list=new ArrayList<>();
        try(Connection c=db();PreparedStatement p=c.prepareStatement("SELECT id,raw_message FROM local_mail WHERE recipient=? ORDER BY id")){
            p.setString(1,name);try(ResultSet r=p.executeQuery()){while(r.next())list.add(new Mail(r.getLong(1),r.getString(2)));}
        }
        return list;
    }
    private void pop(BufferedReader in,BufferedWriter out)throws Exception{
        send(out,"+OK localhost POP3 ready");
        String name=null,logged=null;List<Mail> messages=List.of();Set<Integer> deleted=new HashSet<>();String line;
        while((line=in.readLine())!=null){
            String[] args=line.trim().split("\\s+",2);String cmd=args[0].toUpperCase(Locale.ROOT);String arg=args.length>1?args[1]:"";
            if(cmd.equals("USER")){name=mailbox(arg);send(out,name!=null?"+OK User accepted":"-ERR Use username@localhost");}
            else if(cmd.equals("PASS")){
                if(name!=null&&login(name,arg)){logged=name;messages=inbox(name);send(out,"+OK Logged in");}
                else send(out,"-ERR Invalid credentials");
            }else if(cmd.equals("CAPA")){send(out,"+OK Capability list");send(out,"USER");send(out,"UIDL");send(out,"TOP");send(out,".");}
            else if(cmd.equals("QUIT")){
                if(logged!=null&&!deleted.isEmpty()){
                    try(Connection c=db();PreparedStatement p=c.prepareStatement("DELETE FROM local_mail WHERE id=? AND recipient=?")){
                        for(int i:deleted){p.setLong(1,messages.get(i-1).id());p.setString(2,logged);p.addBatch();}
                        p.executeBatch();
                    }
                }
                send(out,"+OK Bye");break;
            }else if(logged==null)send(out,"-ERR Login first");
            else if(cmd.equals("STAT")){
                int count=0,size=0;for(int i=0;i<messages.size();i++)if(!deleted.contains(i+1)){count++;size+=messages.get(i).raw().getBytes(StandardCharsets.UTF_8).length;}
                send(out,"+OK "+count+" "+size);
            }else if(cmd.equals("LIST")||cmd.equals("UIDL")){
                if(!arg.isBlank()){
                    int i=number(arg,messages.size());
                    if(i<1||deleted.contains(i))send(out,"-ERR No such message");
                    else send(out,"+OK "+i+" "+(cmd.equals("LIST")?messages.get(i-1).raw().getBytes(StandardCharsets.UTF_8).length:messages.get(i-1).id()));
                }else{
                    send(out,"+OK Listing follows");
                    for(int i=1;i<=messages.size();i++)if(!deleted.contains(i))send(out,i+" "+(cmd.equals("LIST")?messages.get(i-1).raw().getBytes(StandardCharsets.UTF_8).length:messages.get(i-1).id()));
                    send(out,".");
                }
            }else if(cmd.equals("RETR")||cmd.equals("TOP")){
                String[] parts=arg.split("\\s+");int i=number(parts[0],messages.size());
                if(i<1||deleted.contains(i)){send(out,"-ERR No such message");continue;}
                String raw=messages.get(i-1).raw();send(out,"+OK Message follows");
                String[] lines=raw.split("\\r?\\n",-1);boolean headers=true;int bodyLines=0;
                int maxBody=cmd.equals("TOP")&&parts.length>1?Math.max(0,number(parts[1],Integer.MAX_VALUE)):Integer.MAX_VALUE;
                for(String item:lines){
                    if(item.isEmpty()&&headers)headers=false;
                    else if(!headers&&bodyLines++>=maxBody)break;
                    send(out,item.startsWith(".")?"."+item:item);
                }
                send(out,".");
            }else if(cmd.equals("DELE")){
                int i=number(arg,messages.size());if(i<1||deleted.contains(i))send(out,"-ERR No such message");
                else{deleted.add(i);send(out,"+OK Marked");}
            }else if(cmd.equals("RSET")){deleted.clear();send(out,"+OK");}
            else if(cmd.equals("NOOP"))send(out,"+OK");
            else send(out,"-ERR Unsupported command");
        }
    }
    private static int number(String s,int max){try{int n=Integer.parseInt(s);return n>=0&&n<=max?n:-1;}catch(Exception ex){return -1;}}
}
