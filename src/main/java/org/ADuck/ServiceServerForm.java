package org.ADuck;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceServerForm extends JFrame {
    private final JTextField service = new JTextField("FileServer", 9);
    private final JPasswordField secret = new JPasswordField("FileServer-demo-key", 12);
    private final JTextField port = new JTextField("9999", 5);
    private final JTextField kdcHost = new JTextField("localhost", 9);
    private final JTextField kdcPort = new JTextField("8888", 5);
    private final JLabel state = new JLabel("Đã dừng");
    private final JButton start = new JButton("Khởi động");
    private final JButton stop = new JButton("Dừng máy chủ");
    private final JTextArea log = new JTextArea(14, 65);
    private final DefaultTableModel tickets = FormSupport.model("Client", "Trạng thái", "Hết hạn");
    private final DefaultTableModel rsa = FormSupport.model("Client", "Bit", "Public key");
    private final DefaultTableModel requests = FormSupport.model("Thời gian", "Client", "Lệnh", "Bit", "Kết quả", "ms");

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, KeyPair>> keys = new ConcurrentHashMap<>();
    private final Set<String> authenticators = ConcurrentHashMap.newKeySet();

    private final java.util.concurrent.atomic.AtomicInteger connections = new java.util.concurrent.atomic.AtomicInteger();
    private volatile String activeService;
    private volatile String activeSecret;
    private volatile String activeKdcHost;
    private volatile int activeKdcPort;

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

        DesktopTheme.finish(this, "Máy chủ dịch vụ", "Xác thực vé · Quản lý phiên · Dịch vụ RSA");
        setSize(1180, 800);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private JPanel buildControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        stop.setEnabled(false);

        start.addActionListener(e -> startServer());
        stop.addActionListener(e -> stopServer());

        panel.add(new JLabel("Dịch vụ"));
        panel.add(service);
        panel.add(new JLabel("Khóa bí mật"));
        panel.add(secret);
        panel.add(new JLabel("Cổng"));
        panel.add(port);
        panel.add(start);
        panel.add(stop);
        panel.add(state);
        panel.add(new JLabel("KDC host/port"));
        panel.add(kdcHost);
        panel.add(kdcPort);

        return DesktopTheme.stack(
                DesktopTheme.card("Cấu hình dịch vụ", DesktopTheme.fields(
                        "Tên dịch vụ", service, "Khóa bí mật", secret, "Cổng lắng nghe", port)),
                DesktopTheme.card("Trung tâm phân phối khóa", DesktopTheme.fields(
                        "Máy chủ KDC", kdcHost, "Cổng KDC", kdcPort)),
                DesktopTheme.actions(start, stop, state));
    }

    private final ServerLifecycle lifecycle = new ServerLifecycle();

    private void startServer() {
        try {
            int number = FormSupport.port(port.getText());
            FormSupport.port(kdcPort.getText());
            FormSupport.required(kdcHost.getText().trim(), "Máy chủ KDC");
            FormSupport.required(service.getText().trim(), "Tên dịch vụ");
            FormSupport.required(new String(secret.getPassword()), "Khóa dịch vụ");
            if (lifecycle.state() == ServerLifecycle.State.RUNNING || lifecycle.state() == ServerLifecycle.State.STARTING || lifecycle.state() == ServerLifecycle.State.STOPPING) return;
            activeService = service.getText().trim();
            activeSecret = new String(secret.getPassword());
            activeKdcHost = kdcHost.getText().trim();
            activeKdcPort = FormSupport.port(kdcPort.getText());
            configurationEnabled(false);
            lifecycle.start(number, this::phucVu, text -> {
                state.setText(text);
                start.setEnabled(lifecycle.state() == ServerLifecycle.State.ERROR);
                stop.setEnabled(lifecycle.state() != ServerLifecycle.State.ERROR);
                if (lifecycle.state() == ServerLifecycle.State.ERROR) configurationEnabled(true);
            });
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cấu hình chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void stopServer() {
        state.setText("Đang dừng…");
        start.setEnabled(false);
        stop.setEnabled(false);
        lifecycle.stop(() -> {
            sessions.clear();
            keys.clear();
            authenticators.clear();
            configurationEnabled(true);
            start.setEnabled(true);
            state.setText("Đã dừng");
        });
    }

    private void configurationEnabled(boolean enabled) {
        for (JComponent component : new JComponent[]{service, secret, port, kdcHost, kdcPort}) component.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        lifecycle.close();
        super.dispose();
    }

    private void phucVu(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String request = in.readLine();
            if (request == null) {
                return;
            }

            String[] cacTruong = GiaoThuc.tachThongDiep(request);
            ghi("Client " + socket.getInetAddress().getHostAddress() + " gửi " + cacTruong[0] + " #" + connections.incrementAndGet(), true);

            String response;
            if (cacTruong[0].equals("PING")) {
                response = "PONG|" + activeService + "|" + GiaoThuc.VERSION;
            } else {
                try {
                    response = xuLy(cacTruong);
                } catch (Exception ex) {
                    response = "ERR|" + ex.getMessage();
                    ghi(ex.getMessage(), false);
                }
            }

            out.write(response);
            out.newLine();
            out.flush();
        } catch (Exception ex) {
            ghi("Lỗi client: " + ex.getMessage(), false);
        }
    }

    private String xuLy(String[] cacTruong) throws Exception {
        String loaiThongDiep = cacTruong[0];
        if (loaiThongDiep.equals("AP-REQ")) {
            return xuLyAP(cacTruong);
        }
        if (loaiThongDiep.equals("RSA-SVC")) {
            return xuLyYeuCauDichVu(cacTruong);
        }
        return "ERR|Lệnh không hỗ trợ";
    }

    private String xuLyAP(String[] cacTruong) throws Exception {
        String servicePassword = activeSecret;
        SecretKey serviceKey = CryptoUtil.taoKhoaTuMatKhau(servicePassword);

        String[] ticket = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(cacTruong[1], serviceKey));
        String user = ticket[0];
        String sessionKeyClientService = ticket[1];
        long ticketExpiry = Long.parseLong(ticket[2]);
        String ticketService = ticket[3];

        if (!ticketService.equals(activeService)) {
            throw new Exception("Service Ticket dành cho dịch vụ khác");
        }

        String[] auth = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(cacTruong[2], khoa(sessionKeyClientService)));
        String authUser = auth[0];
        long authTimestamp = Long.parseLong(auth[1]);

        if (!authUser.equals(user) || ticketExpiry < System.currentTimeMillis() || !GiaoThuc.timestampHopLe(authTimestamp)) {
            throw new Exception("Ticket/Authenticator không hợp lệ hoặc hết hạn");
        }

        String replayKey = user + "|" + auth[1];
        if (!authenticators.add(replayKey)) {
            throw new Exception("Replay Authenticator bị từ chối");
        }

        if (!kiemTraKdc(user)) {
            throw new Exception("Tài khoản hoặc dịch vụ không còn hợp lệ");
        }

        sessions.put(user, new Session(sessionKeyClientService, ticketExpiry));
        tickets.addRow(new Object[]{user, "Hợp lệ", new Date(ticketExpiry)});
        ghi("AP-REQ hợp lệ: " + user, true);

        long responseTimestamp = authTimestamp + 1;
        String encryptedResponse = CryptoUtil.maHoa(Long.toString(responseTimestamp), khoa(sessionKeyClientService));
        return "AP-REP|" + encryptedResponse;
    }

    private String xuLyYeuCauDichVu(String[] cacTruong) throws Exception {
        String user = cacTruong[1];
        Session session = sessions.get(user);

        if (session == null || session.expiry < System.currentTimeMillis()) {
            return "RSA-ERR|Chưa xác thực hoặc ticket hết hạn";
        }

        String body = CryptoUtil.giaiMa(cacTruong[2], khoa(session.key));
        String[] request = GiaoThuc.tachThongDiep(body);

        String requestUser = request[0];
        long requestSequence = Long.parseLong(request[1]);
        long requestTimestamp = Long.parseLong(request[2]);

        if (!requestUser.equals(user) || !GiaoThuc.timestampHopLe(requestTimestamp)) {
            return "RSA-ERR|Timestamp hoặc user không hợp lệ";
        }

        if (requestSequence <= session.sequence) {
            return "RSA-ERR|Replay RSA bị từ chối";
        }

        if (!kiemTraKdc(user)) {
            return "RSA-ERR|User/dịch vụ bị khóa hoặc xóa";
        }

        session.sequence = requestSequence;

        String command = request[3];
        int bits = Integer.parseInt(request[4]);
        String payload = request.length > 5 ? request[5] : "";

        String result = xuLyLenhRsa(user, command, bits, payload);
        String encryptedResult = CryptoUtil.maHoa(result, khoa(session.key));

        return "RSA-RES|" + encryptedResult;
    }

    private String xuLyLenhRsa(String user, String command, int bits, String payload) {
        long start = System.nanoTime();
        String result;
        String outcome = "Thành công";

        try {
            if (command.equals("SINH_KHOA")) {
                result = sinhKhoa(user, bits);
            } else if (command.equals("MA_HOA")) {
                byte[] decodedPayload = Base64.getDecoder().decode(payload);
                String clearText = new String(decodedPayload, StandardCharsets.UTF_8);
                result = maHoaRsa(user, bits, clearText);
            } else if (command.equals("GIAI_MA")) {
                String decrypted = giaiMaRsa(user, bits, payload);
                result = Base64.getEncoder().encodeToString(decrypted.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new Exception("Lệnh RSA không hợp lệ");
            }
        } catch (Exception ex) {
            result = "ERR|" + ex.getMessage();
            outcome = "Lỗi";
        }

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
        KeyPair pair = layKhoa(user, bits);
        return RsaUtil.maHoa(clear, pair.getPublic(), bits);
    }

    private String giaiMaRsa(String user, int bits, String encrypted) throws Exception {
        KeyPair pair = layKhoa(user, bits);
        return RsaUtil.giaiMa(encrypted, pair.getPrivate(), bits);
    }

    private KeyPair layKhoa(String user, int bits) throws Exception {
        Map<Integer, KeyPair> userKeys = keys.get(user);
        if (userKeys == null || !userKeys.containsKey(bits)) {
            throw new Exception("Chưa sinh khóa");
        }
        return userKeys.get(bits);
    }

    private boolean kiemTraKdc(String user) {
        try {
            int portNumber = activeKdcPort;
            String request = "CHECK-SERVICE|" + user + "|" + activeService;
            String response = gui(portNumber, request);
            return "CHECK-OK".equals(response);
        } catch (Exception ex) {
            ghi("Không hỏi được KDC: " + ex.getMessage(), false);
            return false;
        }
    }

    private String gui(int targetPort, String line) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(activeKdcHost, targetPort), 3000);
            socket.setSoTimeout(3000);

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            out.write(line);
            out.newLine();
            out.flush();

            return in.readLine();
        }
    }

    private SecretKey khoa(String encoded) {
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private void ghi(String text, boolean ok) {
        SwingUtilities.invokeLater(() -> {
            String gio = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String trangThai = ok ? "OK" : "LỖI";
            FormSupport.appendLog(log, "[" + gio + "] [" + trangThai + "] " + text + "\n");
        });
    }

    private static class Session {
        final String key;
        final long expiry;
        long sequence;

        Session(String key, long expiry) {
            this.key = key;
            this.expiry = expiry;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServiceServerForm().setVisible(true));
    }
}
