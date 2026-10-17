package org.ADuck;

import javax.crypto.SecretKey;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

public class ClientForm extends JFrame {
    private final JTextField kdcHost = new JTextField("localhost", 10);
    private final JTextField kdcPort = new JTextField("8888", 5);
    private final JTextField serverHost = new JTextField("localhost", 10);
    private final JTextField serverPort = new JTextField("9999", 5);
    private final JLabel kdcState = new JLabel("Chưa kết nối");
    private final JLabel serverState = new JLabel("Chưa kết nối");
    private final JLabel rsaGate = new JLabel("Cần hoàn tất bước AP");
    private final JTextField username = new JTextField("an", 10);
    private final JPasswordField password = new JPasswordField("123456", 10);
    private final JButton asButton = new JButton("Đăng nhập (AS)");
    private final JButton tgsButton = new JButton("Xin vé dịch vụ (TGS)");
    private final JButton apButton = new JButton("Truy cập dịch vụ (AP)");
    private final JButton rsaKeyButton = new JButton("Sinh khóa RSA");
    private final JButton rsaEncryptButton = new JButton("Mã hóa");
    private final JButton rsaDecryptButton = new JButton("Giải mã");
    private final JTextArea ticketInfo = new JTextArea(5, 75);
    private final JTextArea log = new JTextArea(12, 75);
    private final JTextField clearText = new JTextField("Xin chao Kerberos", 25);
    private final JTextField cipherText = new JTextField(35);
    private final JTextField resultText = new JTextField(25);
    private final JComboBox<Integer> bits = new JComboBox<>(new Integer[]{1024, 2048});
    private String tgt;
    private String serviceTicket;
    private String sessionKey;
    private long ticketExpiry;
    private String lastAuthenticator;
    private boolean apAuthenticated;
    private boolean rsaKeyReady;
    private long sequence;
    private String lastRsaRequest;

    public ClientForm() {
        super("Kerberos Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        add(buildConnectionPanel(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Kerberos", buildKerberosPanel());
        tabs.add("Dịch vụ RSA", buildRsaPanel());
        add(tabs, BorderLayout.CENTER);
        capNhatNut();
        setSize(1100, 760);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
    }

    private JPanel buildConnectionPanel() {
        JPanel p = new JPanel(new GridLayout(2, 1));
        JPanel k = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton pingKdc = new JButton("Kết nối KDC");
        pingKdc.addActionListener(e -> ping(true));
        JButton pingServer = new JButton("Kết nối Server dịch vụ");
        pingServer.addActionListener(e -> ping(false));
        JButton disconnect = new JButton("Ngắt kết nối");
        disconnect.addActionListener(e -> ngatKetNoi());
        k.add(new JLabel("KDC host/port")); k.add(kdcHost); k.add(kdcPort); k.add(pingKdc); k.add(kdcState);
        k.add(new JLabel("Server host/port")); k.add(serverHost); k.add(serverPort); k.add(pingServer); k.add(serverState); k.add(disconnect);
        p.add(k);
        return p;
    }

    private JPanel buildKerberosPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        asButton.addActionListener(e -> dangNhap()); tgsButton.addActionListener(e -> xinVe()); apButton.addActionListener(e -> truyCap());
        JButton replay = new JButton("Gửi lại Authenticator cũ"); replay.addActionListener(e -> guiReplay());
        JButton clear = new JButton("Xóa ticket"); clear.addActionListener(e -> xoaTicket());
        JButton clearLog = new JButton("Xóa lịch sử"); clearLog.addActionListener(e -> log.setText(""));
        actions.add(new JLabel("User")); actions.add(username); actions.add(new JLabel("Mật khẩu")); actions.add(password);
        actions.add(asButton); actions.add(tgsButton); actions.add(apButton); actions.add(replay); actions.add(clear); actions.add(clearLog);
        ticketInfo.setEditable(false); log.setEditable(false);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(ticketInfo), new JScrollPane(log)); split.setResizeWeight(.25);
        p.add(actions, BorderLayout.NORTH); p.add(split, BorderLayout.CENTER); return p;
    }

    private JPanel buildRsaPanel() {
        JPanel p = new JPanel(new GridLayout(6, 1));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton replay = new JButton("Replay RSA"); replay.addActionListener(e -> replayRsa());
        rsaKeyButton.addActionListener(e -> sendRsa("SINH_KHOA", ""));
        row.add(new JLabel("Kích thước")); row.add(bits); row.add(rsaKeyButton); row.add(replay); row.add(rsaGate);
        JPanel clear = new JPanel(new FlowLayout(FlowLayout.LEFT)); clear.add(new JLabel("Bản rõ")); clear.add(clearText); clear.add(rsaEncryptButton);
        rsaEncryptButton.addActionListener(e -> maHoaRsa());
        JPanel crypt = new JPanel(new FlowLayout(FlowLayout.LEFT)); crypt.add(new JLabel("Bản mã Base64")); crypt.add(cipherText); crypt.add(rsaDecryptButton);
        rsaDecryptButton.addActionListener(e -> giaiMaRsa());
        JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT)); result.add(new JLabel("Kết quả")); result.add(resultText);
        p.add(row); p.add(clear); p.add(crypt); p.add(result); p.add(new JScrollPane(new JTextArea("RSA dùng khóa do Service Server tạo; private key không gửi về Client.", 3, 60)));
        return p;
    }

    private void ping(boolean isKdc) {
        String host = isKdc ? kdcHost.getText().trim() : serverHost.getText().trim();
        String portText = isKdc ? kdcPort.getText().trim() : serverPort.getText().trim();
        setState(isKdc, "Đang kiểm tra", Color.GRAY);
        new SwingWorker<String, Void>() {
            long started;
            protected String doInBackground() throws Exception { started = System.nanoTime(); return gui(host, Integer.parseInt(portText), "PING", 3000); }
            protected void done() {
                try { String answer = get(); if (!answer.startsWith("PONG|")) throw new Exception(answer); setState(isKdc, answer + " · " + ((System.nanoTime() - started) / 1000000) + " ms", new Color(0, 130, 30)); ghi("[KẾT NỐI] " + host + ":" + portText + " " + answer + " thành công", true); }
                catch (Exception ex) { setState(isKdc, "Lỗi kết nối: " + thongBao(ex), Color.RED); ghi("[KẾT NỐI] " + host + ":" + portText + " lỗi " + thongBao(ex), false); }
                capNhatNut();
            }
        }.execute();
    }

    private void dangNhap() {
        chayNen("AS-REQ", () -> {
            String answer = gui(kdcHost.getText(), Integer.parseInt(kdcPort.getText()), "AS-REQ|" + username.getText().trim(), 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer); if (!fields[0].equals("AS-REP")) throw new Exception(fields.length > 1 ? fields[1] : answer);
            SecretKey key = CryptoUtil.taoKhoaTuMatKhau(new String(password.getPassword()));
            String[] session = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(fields[1], key));
            sessionKey = session[0]; ticketExpiry = Long.parseLong(session[1]); tgt = fields[2];
            veThongTin("AS-REP nhận thành công; TGT=" + tgt + "\nKhóa phiên Client-TGS=" + sessionKey + "\nHết hạn=" + new Date(ticketExpiry)); ghi("AS-REP thành công", true);
        });
    }

    private void xinVe() {
        chayNen("TGS-REQ", () -> {
            if (tgt == null) throw new Exception("Chưa có TGT");
            String auth = CryptoUtil.maHoa(username.getText().trim() + "|" + GiaoThuc.timestamp(), khoa(sessionKey)); lastAuthenticator = auth;
            String answer = gui(kdcHost.getText(), Integer.parseInt(kdcPort.getText()), "TGS-REQ|" + tgt + "|" + auth + "|FileServer", 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer); if (!fields[0].equals("TGS-REP")) throw new Exception(fields.length > 1 ? fields[1] : answer);
            String[] session = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(fields[1], khoa(sessionKey)));
            sessionKey = session[0]; ticketExpiry = Long.parseLong(session[1]); serviceTicket = fields[2];
            veThongTin("TGT=" + tgt + "\nService Ticket=" + serviceTicket + "\nClient-Service key=" + sessionKey + "\nHết hạn=" + new Date(ticketExpiry)); ghi("TGS-REP nhận thành công", true);
        });
    }

    private void truyCap() {
        chayNen("AP-REQ", () -> {
            if (serviceTicket == null) throw new Exception("Chưa có Service Ticket");
            String timestamp = GiaoThuc.timestamp(); lastAuthenticator = CryptoUtil.maHoa(username.getText().trim() + "|" + timestamp, khoa(sessionKey));
            String answer = gui(serverHost.getText(), Integer.parseInt(serverPort.getText()), "AP-REQ|" + serviceTicket + "|" + lastAuthenticator, 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer); if (!fields[0].equals("AP-REP")) throw new Exception(fields.length > 1 ? fields[1] : answer);
            if (!CryptoUtil.giaiMa(fields[1], khoa(sessionKey)).equals(Long.toString(Long.parseLong(timestamp) + 1))) throw new Exception("AP-REP không khớp");
            apAuthenticated = true; veThongTin("AP-REP xác thực hai chiều thành công\nService Ticket=" + serviceTicket + "\nHết hạn=" + new Date(ticketExpiry)); ghi("AP-REP thành công", true); capNhatNut();
        });
    }

    private void guiReplay() { if (serviceTicket == null || lastAuthenticator == null) { ghi("Không có Authenticator cũ", false); return; } chayNen("Replay AP", () -> { String answer = gui(serverHost.getText(), Integer.parseInt(serverPort.getText()), "AP-REQ|" + serviceTicket + "|" + lastAuthenticator, 5000); throw new Exception("Server phản hồi: " + answer); }); }

    private void maHoaRsa() { if (!rsaKeyReady) { ghi("Hãy sinh khóa RSA trước", false); return; } String payload = Base64.getEncoder().encodeToString(clearText.getText().getBytes(StandardCharsets.UTF_8)); chayNen("MA_HOA RSA", () -> { String result = sendRsaResult("MA_HOA", payload); cipherText.setText(result); }); }
    private void giaiMaRsa() { chayNen("GIAI_MA RSA", () -> { String result = sendRsaResult("GIAI_MA", cipherText.getText().trim()); resultText.setText(new String(Base64.getDecoder().decode(result), StandardCharsets.UTF_8)); }); }

    private void sendRsa(String command, String payload) { chayNen("RSA " + command, () -> { String result = sendRsaResult(command, payload); if (command.equals("SINH_KHOA")) { rsaKeyReady = true; ghi("Đã tạo khóa; public key=" + GiaoThuc.rutGon(result), true); } }); }

    private String sendRsaResult(String command, String payload) throws Exception {
        if (!apAuthenticated) throw new Exception("Cần hoàn tất bước AP");
        String encodedPayload = payload;
        String body = username.getText().trim() + "|" + (++sequence) + "|" + GiaoThuc.timestamp() + "|" + command + "|" + bits.getSelectedItem() + "|" + encodedPayload;
        String encrypted = CryptoUtil.maHoa(body, khoa(sessionKey));
        lastRsaRequest = "RSA-SVC|" + username.getText().trim() + "|" + encrypted;
        String answer = gui(serverHost.getText(), Integer.parseInt(serverPort.getText()), lastRsaRequest, 10000);
        String[] fields = GiaoThuc.tachThongDiep(answer); if (!fields[0].equals("RSA-RES")) throw new Exception(fields.length > 1 ? fields[1] : answer);
        String result = CryptoUtil.giaiMa(fields[1], khoa(sessionKey)); if (result.startsWith("ERR|")) throw new Exception(result.substring(4));
        return result;
    }

    private void replayRsa() {
        if (lastRsaRequest == null) { ghi("Chưa có yêu cầu RSA để replay", false); return; }
        chayNen("Replay RSA", () -> { String answer = gui(serverHost.getText(), Integer.parseInt(serverPort.getText()), lastRsaRequest, 10000); throw new Exception("Server phản hồi: " + answer); });
    }

    private void chayNen(String label, Tarea tarea) {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { tarea.run(); return null; }
            protected void done() { try { get(); } catch (Exception ex) { ghi(label + " thất bại: " + thongBao(ex), false); if (label.contains("AP") || label.contains("RSA")) serverState.setForeground(Color.RED); } capNhatNut(); }
        }.execute();
    }

    private String gui(String host, int port, String line, int timeout) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), timeout); socket.setSoTimeout(timeout);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out.write(line); out.newLine(); out.flush(); String answer = in.readLine(); if (answer == null) throw new IOException("Server đóng kết nối"); return answer;
        }
    }

    private SecretKey khoa(String encoded) { return new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(encoded), "AES"); }
    private void setState(boolean kdc, String value, Color color) { JLabel label = kdc ? kdcState : serverState; label.setText(value); label.setForeground(color); }
    private void capNhatNut() { boolean kdcOk = kdcState.getForeground().equals(new Color(0, 130, 30)); boolean srvOk = serverState.getForeground().equals(new Color(0, 130, 30)); asButton.setEnabled(kdcOk); tgsButton.setEnabled(kdcOk && tgt != null); apButton.setEnabled(srvOk && serviceTicket != null); rsaKeyButton.setEnabled(apAuthenticated); rsaEncryptButton.setEnabled(apAuthenticated && rsaKeyReady); rsaDecryptButton.setEnabled(apAuthenticated && rsaKeyReady); rsaGate.setText(apAuthenticated ? "AP-REP thành công" : "Cần hoàn tất bước AP"); }
    private void ngatKetNoi() { int answer = JOptionPane.showConfirmDialog(this, "Giữ các ticket hiện tại?", "Ngắt kết nối", JOptionPane.YES_NO_OPTION); setState(true, "Chưa kết nối", Color.GRAY); setState(false, "Chưa kết nối", Color.GRAY); apAuthenticated = false; if (answer != JOptionPane.YES_OPTION) xoaTicket(); capNhatNut(); ghi("Đã ngắt kết nối", true); }
    private void xoaTicket() { tgt = null; serviceTicket = null; sessionKey = null; lastAuthenticator = null; ticketExpiry = 0; apAuthenticated = false; rsaKeyReady = false; ticketInfo.setText(""); capNhatNut(); }
    private void veThongTin(String text) { SwingUtilities.invokeLater(() -> ticketInfo.setText(text)); }
    private String thongBao(Exception ex) { Throwable root = ex; while (root.getCause() != null) root = root.getCause(); return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(); }
    private void ghi(String text, boolean ok) { SwingUtilities.invokeLater(() -> log.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] [" + (ok ? "THÀNH CÔNG" : "THẤT BẠI") + "] " + text + "\n")); }
    private interface Tarea { void run() throws Exception; }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new ClientForm().setVisible(true)); }
}
