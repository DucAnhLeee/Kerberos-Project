package org.ADuck;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceServerForm extends JFrame {
    private final JTextField service = new JTextField("FileServer", 9);
    private final JPasswordField secret = new JPasswordField("FileServer-demo-key", 12);
    private final JTextField port = new JTextField("9999", 5);
    private final JTextField kdcHost = new JTextField("localhost", 9);
    private final JTextField kdcPort = new JTextField("8888", 5);
    private final JLabel state = new JLabel("Đã dừng");
    private final JTextArea log = new JTextArea(14, 65);
    private final DefaultTableModel tickets = new DefaultTableModel(new String[]{"Client", "Trạng thái", "Hết hạn"}, 0);
    private final DefaultTableModel rsa = new DefaultTableModel(new String[]{"Client", "Bit", "Public key"}, 0);
    private final DefaultTableModel requests = new DefaultTableModel(new String[]{"Thời gian", "Client", "Lệnh", "Bit", "Kết quả", "ms"}, 0);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, KeyPair>> keys = new ConcurrentHashMap<>();
    private final Set<String> authenticators = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private volatile ServerSocket server;
    private int connections;

    public ServiceServerForm() {
        super("Kerberos Service Server");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildControls(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Service Ticket", new JScrollPane(new JTable(tickets)));
        tabs.add("Khóa RSA", new JScrollPane(new JTable(rsa)));
        tabs.add("Yêu cầu RSA", new JScrollPane(new JTable(requests)));
        tabs.add("Log", new JScrollPane(log));
        add(tabs, BorderLayout.CENTER);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton start = new JButton("Bật"); JButton stop = new JButton("Tắt");
        start.addActionListener(e -> startServer()); stop.addActionListener(e -> stopServer());
        p.add(new JLabel("Dịch vụ")); p.add(service); p.add(new JLabel("Khóa bí mật")); p.add(secret);
        p.add(new JLabel("Cổng")); p.add(port); p.add(start); p.add(stop); p.add(state);
        p.add(new JLabel("KDC host/port")); p.add(kdcHost); p.add(kdcPort);
        return p;
    }

    private void startServer() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                server = new ServerSocket(Integer.parseInt(port.getText().trim())); running = true;
                SwingUtilities.invokeLater(() -> state.setText("Đang chạy " + lanIp() + ":" + port.getText()));
                while (running) { Socket socket = server.accept(); connections++; new Thread(() -> phucVu(socket)).start(); }
                return null;
            }
            protected void done() { try { get(); } catch (Exception ex) { ghi("Không mở được cổng: " + ex.getMessage(), false); state.setText("Lỗi/Đã dừng"); } }
        }.execute();
    }

    private void phucVu(Socket socket) {
        try (socket; BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String request = in.readLine(); if (request == null) return;
            String[] f = GiaoThuc.tachThongDiep(request);
            ghi("Client " + socket.getInetAddress().getHostAddress() + " gửi " + f[0] + " #" + connections, true);
            String response = f[0].equals("PING") ? "PONG|" + service.getText().trim() + "|" + GiaoThuc.VERSION : xuLy(f);
            out.write(response); out.newLine(); out.flush();
        } catch (Exception ex) { ghi("Lỗi client: " + ex.getMessage(), false); }
    }

    private String xuLy(String[] f) throws Exception {
        if (f[0].equals("AP-REQ")) return xuLyAP(f);
        if (f[0].equals("RSA-SVC")) return xuLyYeuCauDichVu(f);
        return "ERR|Lệnh không hỗ trợ";
    }

    private String xuLyAP(String[] f) throws Exception {
        SecretKey serviceKey = CryptoUtil.taoKhoaTuMatKhau(new String(secret.getPassword()));
        String[] ticket = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(f[1], serviceKey));
        String user = ticket[0]; String keyEncoded = ticket[1]; long expiry = Long.parseLong(ticket[2]);
        if (!ticket[3].equals(service.getText().trim())) throw new Exception("Service Ticket dành cho dịch vụ khác");
        String[] auth = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(f[2], khoa(keyEncoded)));
        if (!auth[0].equals(user) || expiry < System.currentTimeMillis() || !GiaoThuc.timestampHopLe(Long.parseLong(auth[1]))) throw new Exception("Ticket/Authenticator không hợp lệ hoặc hết hạn");
        String replayKey = user + "|" + auth[1]; if (!authenticators.add(replayKey)) throw new Exception("Replay Authenticator bị từ chối");
        if (!kiemTraKdc(user)) throw new Exception("Tài khoản hoặc dịch vụ không còn hợp lệ");
        sessions.put(user, new Session(keyEncoded, expiry));
        tickets.addRow(new Object[]{user, "Hợp lệ", new Date(expiry)});
        ghi("AP-REQ hợp lệ: " + user, true);
        return "AP-REP|" + CryptoUtil.maHoa(Long.toString(Long.parseLong(auth[1]) + 1), khoa(keyEncoded));
    }

    private String xuLyYeuCauDichVu(String[] f) throws Exception {
        String user = f[1]; Session session = sessions.get(user);
        if (session == null || session.expiry < System.currentTimeMillis()) return "RSA-ERR|Chưa xác thực hoặc ticket hết hạn";
        String body = CryptoUtil.giaiMa(f[2], khoa(session.key));
        String[] request = GiaoThuc.tachThongDiep(body);
        if (!request[0].equals(user) || !GiaoThuc.timestampHopLe(Long.parseLong(request[2]))) return "RSA-ERR|Timestamp hoặc user không hợp lệ";
        if (Long.parseLong(request[1]) <= session.sequence) return "RSA-ERR|Replay RSA bị từ chối";
        if (!kiemTraKdc(user)) return "RSA-ERR|User/dịch vụ bị khóa hoặc xóa";
        session.sequence = Long.parseLong(request[1]);
        String result = xuLyLenhRsa(user, request[3], Integer.parseInt(request[4]), request.length > 5 ? request[5] : "");
        return "RSA-RES|" + CryptoUtil.maHoa(result, khoa(session.key));
    }

    private String xuLyLenhRsa(String user, String command, int bits, String payload) throws Exception {
        long start = System.nanoTime(); String result; String outcome = "Thành công";
        try {
            if (command.equals("SINH_KHOA")) result = sinhKhoa(user, bits);
            else if (command.equals("MA_HOA")) result = maHoaRsa(user, bits, new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8));
            else if (command.equals("GIAI_MA")) result = Base64.getEncoder().encodeToString(giaiMaRsa(user, bits, payload).getBytes(StandardCharsets.UTF_8));
            else throw new Exception("Lệnh RSA không hợp lệ");
        } catch (Exception ex) { result = "ERR|" + ex.getMessage(); outcome = "Lỗi"; }
        long elapsed = (System.nanoTime() - start) / 1000000;
        requests.addRow(new Object[]{new Date(), user, command, bits, outcome, elapsed});
        ghi("RSA " + command + " client=" + user + " " + outcome + " " + elapsed + "ms", outcome.equals("Thành công"));
        return result;
    }

    private String sinhKhoa(String user, int bits) throws Exception {
        KeyPair pair = RsaUtil.taoCapKhoa(bits);
        keys.computeIfAbsent(user, k -> new ConcurrentHashMap<>()).put(bits, pair);
        String encoded = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        rsa.addRow(new Object[]{user, bits, GiaoThuc.rutGon(encoded)});
        return encoded;
    }

    private String maHoaRsa(String user, int bits, String clear) throws Exception {
        KeyPair pair = layKhoa(user, bits); return RsaUtil.maHoa(clear, pair.getPublic(), bits);
    }

    private String giaiMaRsa(String user, int bits, String encrypted) throws Exception {
        KeyPair pair = layKhoa(user, bits); return RsaUtil.giaiMa(encrypted, pair.getPrivate(), bits);
    }

    private KeyPair layKhoa(String user, int bits) throws Exception {
        Map<Integer, KeyPair> userKeys = keys.get(user);
        if (userKeys == null || !userKeys.containsKey(bits)) throw new Exception("Chưa sinh khóa");
        return userKeys.get(bits);
    }

    private boolean kiemTraKdc(String user) {
        try { return gui(Integer.parseInt(kdcPort.getText()), "CHECK-SERVICE|" + user + "|" + service.getText()).equals("CHECK-OK"); }
        catch (Exception ex) { ghi("Không hỏi được KDC: " + ex.getMessage(), false); return false; }
    }

    private String gui(int targetPort, String line) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(kdcHost.getText().trim(), targetPort), 3000); socket.setSoTimeout(3000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(line); out.newLine(); out.flush(); return in.readLine();
        }
    }

    private SecretKey khoa(String encoded) { return new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(encoded), "AES"); }
    private String lanIp() { try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception ex) { return "localhost"; } }
    private void stopServer() { running = false; try { if (server != null) server.close(); } catch (IOException ignored) { } state.setText("Đã dừng"); }
    private void ghi(String text, boolean ok) { SwingUtilities.invokeLater(() -> log.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] [" + (ok ? "OK" : "LỖI") + "] " + text + "\n")); }
    private static class Session { final String key; final long expiry; long sequence; Session(String key, long expiry) { this.key = key; this.expiry = expiry; } }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new ServiceServerForm().setVisible(true)); }
}
