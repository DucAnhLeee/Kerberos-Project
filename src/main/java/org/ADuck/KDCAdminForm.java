package org.ADuck;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KDCAdminForm extends JFrame {
    private final JTextField port = new JTextField("8888", 6);
    private final JTextField username = new JTextField(10);
    private final JPasswordField password = new JPasswordField(10);
    private final JTextField serviceName = new JTextField("FileServer", 10);
    private final JPasswordField servicePassword = new JPasswordField("FileServer-demo-key", 14);
    private final JSpinner tgtMinutes = new JSpinner(new SpinnerNumberModel(10, 1, 1440, 1));
    private final JSpinner ticketMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 1440, 1));
    private final JLabel state = new JLabel("Đã dừng");
    private final JTextArea log = new JTextArea(12, 65);
    private final DefaultTableModel usersModel = new DefaultTableModel(new String[]{"Người dùng", "Trạng thái"}, 0);
    private final DefaultTableModel ticketsModel = new DefaultTableModel(new String[]{"Loại", "Người dùng", "Dịch vụ", "Hết hạn"}, 0);
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Set<String> locked = ConcurrentHashMap.newKeySet();
    private final Map<String, String> services = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile ServerSocket server;
    private int connections;

    public KDCAdminForm() {
        super("Kerberos KDC - Quản trị");
        users.put("an", "123456");
        users.put("binh", "abc123");
        services.put("FileServer", "FileServer-demo-key");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildControls(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Người dùng", new JScrollPane(new JTable(usersModel)));
        tabs.add("Vé đã cấp", new JScrollPane(new JTable(ticketsModel)));
        tabs.add("Lịch sử", new JScrollPane(log));
        add(tabs, BorderLayout.CENTER);
        capNhatUsers();
        pack();
        setLocationByPlatform(true);
    }

    private JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton start = new JButton("Bật KDC");
        JButton stop = new JButton("Tắt KDC");
        JButton addUser = new JButton("Thêm/đổi mật khẩu");
        JButton lockUser = new JButton("Khóa/mở user");
        JButton addService = new JButton("Thêm dịch vụ");
        JButton remove = new JButton("Xóa user");
        start.addActionListener(e -> startServer());
        stop.addActionListener(e -> stopServer());
        addUser.addActionListener(e -> themUser());
        lockUser.addActionListener(e -> khoaUser());
        addService.addActionListener(e -> themDichVu());
        remove.addActionListener(e -> xoaUser());
        p.add(new JLabel("Cổng")); p.add(port); p.add(start); p.add(stop); p.add(state);
        p.add(new JLabel("User")); p.add(username); p.add(new JLabel("Mật khẩu")); p.add(password);
        p.add(addUser); p.add(lockUser); p.add(remove);
        p.add(new JLabel("Dịch vụ")); p.add(serviceName); p.add(new JLabel("Khóa")); p.add(servicePassword); p.add(addService);
        p.add(new JLabel("TGT phút")); p.add(tgtMinutes); p.add(new JLabel("Service Ticket phút")); p.add(ticketMinutes);
        return p;
    }

    private void startServer() {
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                server = new ServerSocket(Integer.parseInt(port.getText().trim()));
                running = true;
                SwingUtilities.invokeLater(() -> state.setText("Đang chạy - " + diaChiLan() + ":" + port.getText()));
                while (running) {
                    Socket socket = server.accept();
                    connections++;
                    new Thread(() -> phucVu(socket), "kdc-client").start();
                }
                return null;
            }
            protected void done() {
                try { get(); } catch (Exception ex) { if (running) ghi("Lỗi cổng: " + ex.getMessage(), false); state.setText("Lỗi/Đã dừng"); }
            }
        }.execute();
    }

    private void phucVu(Socket socket) {
        try (socket; BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String request = in.readLine();
            if (request == null) return;
            String[] f = GiaoThuc.tachThongDiep(request);
            ghi("Kết nối " + socket.getInetAddress().getHostAddress() + " lệnh " + f[0] + " (" + connections + ")", true);
            String response = xuLy(f);
            out.write(response); out.newLine(); out.flush();
        } catch (Exception ex) { ghi("Lỗi kết nối: " + ex.getMessage(), false); }
    }

    private String xuLy(String[] f) {
        try {
            if (f[0].equals("PING")) return "PONG|KDC|" + GiaoThuc.VERSION;
            if (f[0].equals("AS-REQ")) return xuLyAS(f);
            if (f[0].equals("TGS-REQ")) return xuLyTGS(f);
            if (f[0].equals("CHECK-SERVICE")) return kiemTraDichVu(f);
            return "ERR|Lệnh không được hỗ trợ";
        } catch (Exception ex) { ghi(f[0] + " thất bại: " + ex.getMessage(), false); return "ERR|" + ex.getMessage(); }
    }

    private String xuLyAS(String[] f) throws Exception {
        String user = f[1];
        if (!users.containsKey(user) || locked.contains(user)) throw new Exception("User không tồn tại hoặc bị khóa");
        SecretKey clientKey = CryptoUtil.taoKhoaTuMatKhau(users.get(user));
        String session = Base64.getEncoder().encodeToString(CryptoUtil.taoKhoaPhien().getEncoded());
        long expiry = System.currentTimeMillis() + (Integer) tgtMinutes.getValue() * 60000L;
        String tgt = CryptoUtil.maHoa(user + "|" + session + "|" + expiry, CryptoUtil.taoKhoaTuMatKhau(GiaoThuc.TGS_KEY_PASSWORD));
        String encryptedSession = CryptoUtil.maHoa(session + "|" + expiry, clientKey);
        ticketsModel.addRow(new Object[]{"TGT", user, "TGS", new Date(expiry)});
        ghi("AS-REQ/AS-REP thành công cho " + user, true);
        return "AS-REP|" + encryptedSession + "|" + tgt;
    }

    private String xuLyTGS(String[] f) throws Exception {
        String[] tgt = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(f[1], CryptoUtil.taoKhoaTuMatKhau(GiaoThuc.TGS_KEY_PASSWORD)));
        String user = tgt[0];
        if (Long.parseLong(tgt[2]) < System.currentTimeMillis()) throw new Exception("TGT hết hạn");
        String[] auth = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(f[2], khoa(tgt[1])));
        if (!auth[0].equals(user) || !GiaoThuc.timestampHopLe(Long.parseLong(auth[1]))) throw new Exception("Authenticator không hợp lệ");
        if (!services.containsKey(f[3])) throw new Exception("Dịch vụ không tồn tại");
        long expiry = System.currentTimeMillis() + (Integer) ticketMinutes.getValue() * 60000L;
        String session = Base64.getEncoder().encodeToString(CryptoUtil.taoKhoaPhien().getEncoded());
        String ticket = CryptoUtil.maHoa(user + "|" + session + "|" + expiry + "|" + f[3], CryptoUtil.taoKhoaTuMatKhau(services.get(f[3])));
        String response = CryptoUtil.maHoa(session + "|" + expiry, khoa(tgt[1]));
        ticketsModel.addRow(new Object[]{"Service Ticket", user, f[3], new Date(expiry)});
        ghi("TGS-REQ/TGS-REP thành công: " + user + " → " + f[3], true);
        return "TGS-REP|" + response + "|" + ticket;
    }

    private String kiemTraDichVu(String[] f) {
        boolean valid = users.containsKey(f[1]) && !locked.contains(f[1]) && services.containsKey(f[2]);
        return valid ? "CHECK-OK" : "CHECK-DENY";
    }

    private SecretKey khoa(String encoded) { return new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(encoded), "AES"); }
    private void themUser() { users.put(username.getText().trim(), new String(password.getPassword())); capNhatUsers(); ghi("Đã thêm/cập nhật user", true); }
    private void khoaUser() { String user = username.getText().trim(); if (!locked.remove(user)) locked.add(user); capNhatUsers(); }
    private void xoaUser() { users.remove(username.getText().trim()); locked.remove(username.getText().trim()); capNhatUsers(); }
    private void themDichVu() { services.put(serviceName.getText().trim(), new String(servicePassword.getPassword())); ghi("Đã lưu dịch vụ " + serviceName.getText(), true); }
    private void capNhatUsers() { usersModel.setRowCount(0); users.forEach((u, p) -> usersModel.addRow(new Object[]{u, locked.contains(u) ? "Đã khóa" : "Hoạt động"})); }

    private void stopServer() { running = false; try { if (server != null) server.close(); } catch (IOException ignored) { } state.setText("Đã dừng"); }
    private String diaChiLan() { try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception ex) { return "localhost"; } }
    private void ghi(String s, boolean ok) { SwingUtilities.invokeLater(() -> log.append("[" + new java.text.SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + (ok ? "[THÀNH CÔNG] " : "[THẤT BẠI] ") + s + "\n")); }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new KDCAdminForm().setVisible(true)); }
}
