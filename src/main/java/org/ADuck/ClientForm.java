package org.ADuck;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

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

    private boolean kdcConnected;
    private boolean serverConnected;
    private boolean busy;
    private String tgtSessionKey;
    private long tgtExpiry;
    private Input input;
    private final JLabel activity = new JLabel("Sẵn sàng");
    private final java.util.Map<java.awt.Component, Boolean> enabledBefore = new java.util.IdentityHashMap<>();
    private final javax.swing.Timer expiryTimer = new javax.swing.Timer(1000, e -> expireTickets());
    private record Input(String kdcHost, int kdcPort, String serverHost, int serverPort,
                         String username, String password, int bits, String cipher) {}

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

        resultText.setEditable(false);
        bits.setSelectedItem(2048);
        bits.addActionListener(e -> {
            rsaKeyReady = false;
            capNhatNut();
        });
        DesktopTheme.finish(this, "Kerberos Client", "Xác thực hai chiều · Vé dịch vụ · Mã hóa RSA");
        expiryTimer.start();
        javax.swing.event.DocumentListener changed = new javax.swing.event.DocumentListener() {
            private void changed() {
                if (busy) return;
                xoaTicket();
                kdcConnected = false;
                serverConnected = false;
                setState(true, "Cần kiểm tra kết nối", Color.GRAY);
                setState(false, "Cần kiểm tra kết nối", Color.GRAY);
                capNhatNut();
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        };
        for (JTextField field : new JTextField[]{kdcHost, kdcPort, serverHost, serverPort, username, password}) field.getDocument().addDocumentListener(changed);
        capNhatNut();
        setSize(1180, 850);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
    }

    private JPanel buildConnectionPanel() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton pingKdc = new JButton("Kết nối KDC");
        JButton pingServer = new JButton("Kết nối Server dịch vụ");
        JButton disconnect = new JButton("Ngắt kết nối");

        pingKdc.addActionListener(e -> ping(true));
        pingServer.addActionListener(e -> ping(false));
        disconnect.addActionListener(e -> ngatKetNoi());

        row.add(new JLabel("KDC host/port"));
        row.add(kdcHost);
        row.add(kdcPort);
        row.add(pingKdc);
        row.add(kdcState);

        row.add(new JLabel("Server host/port"));
        row.add(serverHost);
        row.add(serverPort);
        row.add(pingServer);
        row.add(serverState);
        row.add(disconnect);

        JPanel endpoints = new JPanel(new GridLayout(1, 2, 16, 0));
        endpoints.add(DesktopTheme.card("Trung tâm phân phối khóa", DesktopTheme.stack(
                DesktopTheme.fields("Máy chủ", kdcHost, "Cổng", kdcPort),
                DesktopTheme.actions(pingKdc, kdcState))));
        endpoints.add(DesktopTheme.card("Máy chủ dịch vụ", DesktopTheme.stack(
                DesktopTheme.fields("Máy chủ", serverHost, "Cổng", serverPort),
                DesktopTheme.actions(pingServer, serverState))));
        return DesktopTheme.stack(endpoints, DesktopTheme.actions(disconnect, activity));
    }

    private JPanel buildKerberosPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));

        asButton.addActionListener(e -> dangNhap());
        tgsButton.addActionListener(e -> xinVe());
        apButton.addActionListener(e -> truyCap());

        JButton replay = new JButton("Gửi lại Authenticator cũ");
        replay.addActionListener(e -> guiReplay());

        JButton clear = new JButton("Xóa ticket");
        clear.addActionListener(e -> xoaTicket());

        JButton clearLog = new JButton("Xóa lịch sử");
        clearLog.addActionListener(e -> log.setText(""));

        actions.add(new JLabel("User"));
        actions.add(username);
        actions.add(new JLabel("Mật khẩu"));
        actions.add(password);
        actions.add(asButton);
        actions.add(tgsButton);
        actions.add(apButton);
        actions.add(replay);
        actions.add(clear);
        actions.add(clearLog);

        ticketInfo.setEditable(false);
        log.setEditable(false);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(ticketInfo), new JScrollPane(log));
        split.setResizeWeight(0.25);

        panel.add(DesktopTheme.card("1. Tài khoản và xác thực", DesktopTheme.stack(
                DesktopTheme.fields("Tên đăng nhập", username, "Mật khẩu", password),
                DesktopTheme.actions(asButton, tgsButton, apButton),
                DesktopTheme.actions(replay, clear, clearLog))), BorderLayout.NORTH);
        split.setTopComponent(DesktopTheme.card("Thông tin vé", new JScrollPane(ticketInfo)));
        split.setBottomComponent(DesktopTheme.card("Nhật ký hoạt động", new JScrollPane(log)));
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRsaPanel() {

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton replay = new JButton("Replay RSA");
        replay.addActionListener(e -> replayRsa());
        rsaKeyButton.addActionListener(e -> sendRsa("SINH_KHOA", ""));

        row.add(new JLabel("Kích thước"));
        row.add(bits);
        row.add(rsaKeyButton);
        row.add(replay);
        row.add(rsaGate);

        JPanel clear = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clear.add(new JLabel("Bản rõ"));
        clear.add(clearText);
        clear.add(rsaEncryptButton);
        rsaEncryptButton.addActionListener(e -> maHoaRsa());

        JPanel crypt = new JPanel(new FlowLayout(FlowLayout.LEFT));
        crypt.add(new JLabel("Bản mã Base64"));
        crypt.add(cipherText);
        crypt.add(rsaDecryptButton);
        rsaDecryptButton.addActionListener(e -> giaiMaRsa());

        JPanel result = new JPanel(new FlowLayout(FlowLayout.LEFT));
        result.add(new JLabel("Kết quả"));
        result.add(resultText);

        return DesktopTheme.stack(
                DesktopTheme.card("2. Khóa RSA", DesktopTheme.stack(
                        DesktopTheme.fields("Độ dài khóa (bit)", bits),
                        DesktopTheme.actions(rsaKeyButton, rsaGate))),
                DesktopTheme.card("3. Mã hóa và giải mã", DesktopTheme.stack(
                        DesktopTheme.fields("Bản rõ UTF-8", clearText, "Bản mã Base64", cipherText, "Kết quả", resultText),
                        DesktopTheme.actions(rsaEncryptButton, rsaDecryptButton))),
                DesktopTheme.card("Kiểm thử nâng cao", DesktopTheme.actions(replay)));

    }

    private void ping(boolean isKdc) {
        String host = isKdc ? kdcHost.getText().trim() : serverHost.getText().trim();
        String portText = isKdc ? kdcPort.getText().trim() : serverPort.getText().trim();
        if (busy) return;
        try {
            FormSupport.required(host, "Máy chủ");
            FormSupport.port(portText);
        } catch (IllegalArgumentException ex) {
            ghi(ex.getMessage(), false);
            return;
        }
        setBusy(true);
        setState(isKdc, "Đang kiểm tra", Color.GRAY);

        new SwingWorker<String, Void>() {
            private long started;

            @Override
            protected String doInBackground() throws Exception {
                started = System.nanoTime();
                int portNumber = Integer.parseInt(portText);
                return gui(host, portNumber, "PING", 3000);
            }

            @Override
            protected void done() {
                try {
                    String answer = get();
                    if (!answer.startsWith("PONG|")) {
                        throw new Exception(answer);
                    }
                    long elapsedMs = (System.nanoTime() - started) / 1000000;
                    if (isKdc) kdcConnected = true; else serverConnected = true;
                    setState(isKdc, answer + " · " + elapsedMs + " ms", new Color(0, 130, 30));
                    ghi("[KẾT NỐI] " + host + ":" + portText + " " + answer + " thành công", true);
                } catch (Exception ex) {
                    if (isKdc) kdcConnected = false; else serverConnected = false;
                    setState(isKdc, "Lỗi kết nối: " + thongBao(ex), Color.RED);
                    ghi("[KẾT NỐI] " + host + ":" + portText + " lỗi " + thongBao(ex), false);
                }
                setBusy(false);
                capNhatNut();
            }
        }.execute();
    }

    private void dangNhap() {
        if (busy) return;
        xoaTicket();
        chayNen("AS-REQ", () -> {
            String host = input.kdcHost();
            int port = input.kdcPort();
            String user = input.username();

            String answer = gui(host, port, "AS-REQ|" + user, 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer);

            if (!fields[0].equals("AS-REP")) {
                throw new Exception(fields.length > 1 ? fields[1] : answer);
            }

            SecretKey userKey = CryptoUtil.taoKhoaTuMatKhau(input.password());
            String decryptedSession = CryptoUtil.giaiMa(fields[1], userKey);
            String[] session = GiaoThuc.tachThongDiep(decryptedSession);

            sessionKey = session[0];
            ticketExpiry = Long.parseLong(session[1]);
            tgt = fields[2];
            tgtSessionKey = sessionKey;
            tgtExpiry = ticketExpiry;

            veThongTin("AS-REP nhận thành công; TGT=" + tgt + "\nKhóa phiên Client-TGS=" + sessionKey + "\nHết hạn=" + new Date(ticketExpiry));
            ghi("AS-REP thành công", true);
        });
    }

    private void xinVe() {
        chayNen("TGS-REQ", () -> {
            if (tgt == null) {
                throw new Exception("Chưa có TGT");
            }

            String user = input.username();
            String timestamp = GiaoThuc.timestamp();
            String auth = CryptoUtil.maHoa(user + "|" + timestamp, khoa(tgtSessionKey));
            lastAuthenticator = auth;

            String host = input.kdcHost();
            int port = input.kdcPort();

            String answer = gui(host, port, "TGS-REQ|" + tgt + "|" + auth + "|FileServer", 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer);

            if (!fields[0].equals("TGS-REP")) {
                throw new Exception(fields.length > 1 ? fields[1] : answer);
            }

            String decryptedSession = CryptoUtil.giaiMa(fields[1], khoa(tgtSessionKey));
            String[] session = GiaoThuc.tachThongDiep(decryptedSession);

            sessionKey = session[0];
            ticketExpiry = Long.parseLong(session[1]);
            serviceTicket = fields[2];
            apAuthenticated = false;
            rsaKeyReady = false;
            lastRsaRequest = null;
            sequence = 0;

            veThongTin("TGT=" + tgt + "\nService Ticket=" + serviceTicket + "\nClient-Service key=" + sessionKey + "\nHết hạn=" + new Date(ticketExpiry));
            ghi("TGS-REP nhận thành công", true);
        });
    }

    private void truyCap() {
        chayNen("AP-REQ", () -> {
            if (serviceTicket == null) {
                throw new Exception("Chưa có Service Ticket");
            }

            String user = input.username();
            String timestamp = GiaoThuc.timestamp();
            lastAuthenticator = CryptoUtil.maHoa(user + "|" + timestamp, khoa(sessionKey));

            String host = input.serverHost();
            int port = input.serverPort();

            String answer = gui(host, port, "AP-REQ|" + serviceTicket + "|" + lastAuthenticator, 5000);
            String[] fields = GiaoThuc.tachThongDiep(answer);

            if (!fields[0].equals("AP-REP")) {
                throw new Exception(fields.length > 1 ? fields[1] : answer);
            }

            String decryptedTimestamp = CryptoUtil.giaiMa(fields[1], khoa(sessionKey));
            long expectedTimestamp = Long.parseLong(timestamp) + 1;

            if (!decryptedTimestamp.equals(Long.toString(expectedTimestamp))) {
                throw new Exception("AP-REP không khớp");
            }

            apAuthenticated = true;
            veThongTin("AP-REP xác thực hai chiều thành công\nService Ticket=" + serviceTicket + "\nHết hạn=" + new Date(ticketExpiry));
            ghi("AP-REP thành công", true);
            capNhatNut();
        });
    }

    private void guiReplay() {
        if (serviceTicket == null || lastAuthenticator == null) {
            ghi("Không có Authenticator cũ", false);
            return;
        }

        chayNen("Replay AP", () -> {
            String host = input.serverHost();
            int port = input.serverPort();
            String answer = gui(host, port, "AP-REQ|" + serviceTicket + "|" + lastAuthenticator, 5000);
            throw new Exception("Server phản hồi: " + answer);
        });
    }

    private void maHoaRsa() {
        if (!rsaKeyReady) {
            ghi("Hãy sinh khóa RSA trước", false);
            return;
        }

        byte[] bytes = clearText.getText().getBytes(StandardCharsets.UTF_8);
        try {
            RsaUtil.kiemTraGioiHan(bytes.length, (Integer) bits.getSelectedItem());
        } catch (IllegalArgumentException ex) {
            ghi(ex.getMessage(), false);
            return;
        }
        String payload = Base64.getEncoder().encodeToString(bytes);
        chayNen("MA_HOA RSA", () -> {
            String result = sendRsaResult("MA_HOA", payload);
            SwingUtilities.invokeLater(() -> cipherText.setText(result));
        });
    }

    private void giaiMaRsa() {
        try {
            byte[] bytes = Base64.getDecoder().decode(cipherText.getText().trim());
            if (bytes.length != (Integer) bits.getSelectedItem() / 8) throw new IllegalArgumentException("Độ dài bản mã không khớp khóa RSA.");
        } catch (IllegalArgumentException ex) {
            ghi("Bản mã Base64 chưa hợp lệ: " + ex.getMessage(), false);
            return;
        }
        chayNen("GIAI_MA RSA", () -> {
            String result = sendRsaResult("GIAI_MA", input.cipher());
            byte[] decoded = Base64.getDecoder().decode(result);
            SwingUtilities.invokeLater(() -> resultText.setText(new String(decoded, StandardCharsets.UTF_8)));
        });
    }

    private void sendRsa(String command, String payload) {
        chayNen("RSA " + command, () -> {
            String result = sendRsaResult(command, payload);
            if (command.equals("SINH_KHOA")) {
                rsaKeyReady = true;
                ghi("Đã tạo khóa; public key=" + GiaoThuc.rutGon(result), true);
            }
        });
    }

    private String sendRsaResult(String command, String payload) throws Exception {
        if (!apAuthenticated) {
            throw new Exception("Cần hoàn tất bước AP");
        }

        String user = input.username();
        long seq = ++sequence;
        String timestamp = GiaoThuc.timestamp();
        Object selectedBits = input.bits();

        String body = user + "|" + seq + "|" + timestamp + "|" + command + "|" + selectedBits + "|" + payload;
        String encrypted = CryptoUtil.maHoa(body, khoa(sessionKey));

        lastRsaRequest = "RSA-SVC|" + user + "|" + encrypted;

        String host = input.serverHost();
        int port = input.serverPort();

        String answer = gui(host, port, lastRsaRequest, 10000);
        String[] fields = GiaoThuc.tachThongDiep(answer);

        if (!fields[0].equals("RSA-RES")) {
            throw new Exception(fields.length > 1 ? fields[1] : answer);
        }

        String result = CryptoUtil.giaiMa(fields[1], khoa(sessionKey));
        if (result.startsWith("ERR|")) {
            throw new Exception(result.substring(4));
        }

        return result;
    }

    private void replayRsa() {
        if (lastRsaRequest == null) {
            ghi("Chưa có yêu cầu RSA để replay", false);
            return;
        }

        chayNen("Replay RSA", () -> {
            String host = input.serverHost();
            int port = input.serverPort();
            String answer = gui(host, port, lastRsaRequest, 10000);
            throw new Exception("Server phản hồi: " + answer);
        });
    }

    private void chayNen(String label, CongViecNen congViec) {
        if (busy) return;
        try {
            input = new Input(FormSupport.required(kdcHost.getText().trim(), "Máy chủ KDC"), FormSupport.port(kdcPort.getText()),
                    FormSupport.required(serverHost.getText().trim(), "Máy chủ dịch vụ"), FormSupport.port(serverPort.getText()),
                    FormSupport.required(username.getText().trim(), "Tên đăng nhập"),
                    FormSupport.required(new String(password.getPassword()), "Mật khẩu"),
                    (Integer) bits.getSelectedItem(), cipherText.getText().trim());
        } catch (IllegalArgumentException ex) {
            ghi(ex.getMessage(), false);
            return;
        }
        setBusy(true);
        activity.setText("Đang xử lý " + label + "…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                congViec.chay();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    ghi(label + " thất bại: " + thongBao(ex), false);
                    activity.setText("Thao tác thất bại — xem nhật ký");
                }
                input = null;
                setBusy(false);
                expireTickets();
                capNhatNut();
            }
        }.execute();
    }

    private String gui(String host, int port, String line, int timeout) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), timeout);
            socket.setSoTimeout(timeout);

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            out.write(line);
            out.newLine();
            out.flush();

            String answer = in.readLine();
            if (answer == null) {
                throw new IOException("Server đóng kết nối");
            }
            return answer;
        }
    }

    private SecretKey khoa(String encoded) {
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private void setState(boolean kdc, String value, Color color) {
        JLabel label = kdc ? kdcState : serverState;
        label.setText(value);
        label.setForeground(color);
    }

    private void capNhatNut() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::capNhatNut);
            return;
        }
        if (busy) return;
        boolean kdcOk = kdcConnected;
        boolean srvOk = serverConnected;

        asButton.setEnabled(kdcOk);
        tgsButton.setEnabled(kdcOk && tgt != null);
        apButton.setEnabled(srvOk && serviceTicket != null);

        rsaKeyButton.setEnabled(apAuthenticated);
        rsaEncryptButton.setEnabled(apAuthenticated && rsaKeyReady);
        rsaDecryptButton.setEnabled(apAuthenticated && rsaKeyReady);

        rsaGate.setText(apAuthenticated ? "AP-REP thành công" : "Cần hoàn tất bước AP");
    }

    private void ngatKetNoi() {
        if (busy) return;
        int answer = JOptionPane.showConfirmDialog(this, "Giữ các ticket hiện tại?", "Ngắt kết nối", JOptionPane.YES_NO_CANCEL_OPTION);
        if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return;
        kdcConnected = false;
        serverConnected = false;
        setState(true, "Chưa kết nối", Color.GRAY);
        setState(false, "Chưa kết nối", Color.GRAY);
        apAuthenticated = false;

        if (answer != JOptionPane.YES_OPTION) {
            xoaTicket();
        }
        capNhatNut();
        ghi("Đã ngắt kết nối", true);
    }

    private void xoaTicket() {
        if (busy) return;
        tgtSessionKey = null;
        tgtExpiry = 0;
        lastRsaRequest = null;
        sequence = 0;
        cipherText.setText("");
        resultText.setText("");
        tgt = null;
        serviceTicket = null;
        sessionKey = null;
        lastAuthenticator = null;
        ticketExpiry = 0;
        apAuthenticated = false;
        rsaKeyReady = false;
        ticketInfo.setText("");
        capNhatNut();
    }

    private void setBusy(boolean value) {
        busy = value;
        if (value) {
            enabledBefore.clear();
            freeze(getContentPane());
            activity.setText("Đang xử lý…");
        } else {
            enabledBefore.forEach(java.awt.Component::setEnabled);
            enabledBefore.clear();
            if (activity.getText().startsWith("Đang")) activity.setText("Sẵn sàng");
        }
    }

    private void freeze(java.awt.Container parent) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof javax.swing.AbstractButton || child instanceof JTextField || child instanceof JComboBox<?>) {
                enabledBefore.put(child, child.isEnabled());
                child.setEnabled(false);
            }
            if (child instanceof java.awt.Container container) freeze(container);
        }
    }

    private void expireTickets() {
        if (busy) return;
        long now = System.currentTimeMillis();
        if (tgt != null && tgtExpiry <= now) {
            tgt = null;
            tgtSessionKey = null;
        }
        if (ticketExpiry > 0 && ticketExpiry <= now) {
            xoaTicket();
            ghi("Vé đã hết hạn. Vui lòng đăng nhập lại.", false);
        }
        capNhatNut();
    }

    @Override
    public void dispose() {
        expiryTimer.stop();
        super.dispose();
    }

    private void veThongTin(String text) {
        SwingUtilities.invokeLater(() -> ticketInfo.setText(text));
    }

    private String thongBao(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private void ghi(String text, boolean ok) {
        SwingUtilities.invokeLater(() -> {
            String gio = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String trangThai = ok ? "THÀNH CÔNG" : "THẤT BẠI";
            FormSupport.appendLog(log, "[" + gio + "] [" + trangThai + "] " + text + "\n");
        });
    }

    @FunctionalInterface
    private interface CongViecNen {
        void chay() throws Exception;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientForm().setVisible(true));
    }
}
