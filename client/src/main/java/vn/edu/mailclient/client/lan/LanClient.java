package vn.edu.mailclient.client.lan;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;

/** Lightweight UDP LAN mail transport. Never sends Gmail App Password to the LAN server. */
public final class LanClient {
    private final String host;
    private final int port;
    public LanClient(String host, int port) { this.host = host; this.port = port; }
    public static String enc(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    public static String dec(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    public String call(String... parts) throws Exception {
        String request = String.join("|", parts);
        byte[] bytes = request.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), port));
            byte[] buffer = new byte[60000];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            String value = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
            if (value.startsWith("ERR|")) throw new IllegalStateException(dec(value.substring(4)));
            return value;
        }
    }
    public String register(String user, String password) throws Exception { return call("REGISTER", enc(user), enc(password)); }
    public String login(String user, String password) throws Exception { return call("LOGIN", enc(user), enc(password)); }
    public String send(String token, String to, String subject, String body) throws Exception {
        return call("SEND", token, enc(to), enc(subject), enc(body));
    }
    public List<String[]> mailbox(String token, String folder) throws Exception {
        String response = call("MAILBOX", token, folder);
        List<String[]> out = new ArrayList<>();
        String[] lines = response.split("\\n");
        for (int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split("\\|",-1);
            if (fields.length==5) for (int j=0;j<5;j++) fields[j]=dec(fields[j]);
            if (fields.length==5) out.add(fields);
        }
        return out;
    }
    public List<String> online(String token) throws Exception {
        String result=call("ONLINE",token);
        List<String> users=new ArrayList<>();
        String[] lines=result.split("\\n");
        for(int i=1;i<lines.length;i++) if(!lines[i].isBlank()) users.add(dec(lines[i]));
        return users;
    }
}
