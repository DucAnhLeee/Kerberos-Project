package org.ADuck;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
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
    private final JButton start = new JButton("Khởi động");
    private final JButton stop = new JButton("Dừng máy chủ");
    private final JTextArea log = new JTextArea(12, 65);
    private final DefaultTableModel usersModel = FormSupport.model("Người dùng", "Trạng thái");
    private final DefaultTableModel ticketsModel = FormSupport.model("Loại", "Người dùng", "Dịch vụ", "Hết hạn");

    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Set<String> locked = ConcurrentHashMap.newKeySet();
    private final Map<String, String> services = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicInteger connections = new java.util.concurrent.atomic.AtomicInteger();

    public KDCAdminForm() {
        super("Kerberos KDC - Quản trị");
        users.put("an", "123456");
        users.put("binh", "abc123");
        services.put("FileServer", "FileServer-demo-key");

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        add(buildControls(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        JTable userTable = new JTable(usersModel);
        userTable.getSelectionModel().addListSelectionListener(e -> {
            int row = userTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0) {
                username.setText(usersModel.getValueAt(userTable.convertRowIndexToModel(row), 0).toString());
                password.setText("");
            }
        });
        tabs.add("Người dùng", new JScrollPane(userTable));
        tabs.add("Vé đã cấp", new JScrollPane(new JTable(ticketsModel)));
        tabs.add("Lịch sử", new JScrollPane(log));
        add(tabs, BorderLayout.CENTER);

        capNhatUsers();
        DesktopTheme.finish(this, "Trung tâm phân phối khóa", "Quản trị tài khoản · Dịch vụ · Cấp phát vé Kerberos");
        setSize(1180, 900);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private JPanel buildControls() {

        JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel userRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel serviceRow = new JPanel(new FlowLayout(FlowLayout.LEFT));

        stop.setEnabled(false);
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

        serverRow.add(new JLabel("Cổng"));
        serverRow.add(port);
        serverRow.add(start);
        serverRow.add(stop);
        serverRow.add(state);

        userRow.add(new JLabel("User"));
        userRow.add(username);
        userRow.add(new JLabel("Mật khẩu"));
        userRow.add(password);
        userRow.add(addUser);
        userRow.add(lockUser);
        userRow.add(remove);

        serviceRow.add(new JLabel("Dịch vụ"));
        serviceRow.add(serviceName);
        serviceRow.add(new JLabel("Khóa"));
        serviceRow.add(servicePassword);
        serviceRow.add(addService);
        serviceRow.add(new JLabel("TGT phút"));
        serviceRow.add(tgtMinutes);
        serviceRow.add(new JLabel("Service Ticket phút"));
        serviceRow.add(ticketMinutes);

        return DesktopTheme.stack(
                DesktopTheme.card("Điều khiển KDC", DesktopTheme.stack(
                        DesktopTheme.fields("Cổng lắng nghe", port), DesktopTheme.actions(start, stop, state))),
                DesktopTheme.card("Quản lý tài khoản", DesktopTheme.stack(
                        DesktopTheme.fields("Tên đăng nhập", username, "Mật khẩu", password),
                        DesktopTheme.actions(addUser, lockUser, remove))),
                DesktopTheme.card("Dịch vụ và thời hạn vé", DesktopTheme.stack(
                        DesktopTheme.fields("Tên dịch vụ", serviceName, "Khóa dịch vụ", servicePassword,
                                "TGT (phút)", tgtMinutes, "Vé dịch vụ (phút)", ticketMinutes),
                        DesktopTheme.actions(addService))));
    }

    private final ServerLifecycle lifecycle = new ServerLifecycle();
    private volatile int tgtLifetime = 10;
    private volatile int serviceLifetime = 5;

    private void startServer() {
        try {
            if (lifecycle.state() == ServerLifecycle.State.RUNNING || lifecycle.state() == ServerLifecycle.State.STARTING || lifecycle.state() == ServerLifecycle.State.STOPPING) return;
            int number = FormSupport.port(port.getText());
            tgtLifetime = (Integer) tgtMinutes.getValue();
            serviceLifetime = (Integer) ticketMinutes.getValue();
            lifecycle.start(number, this::phucVu, text -> {
                state.setText(text);
                start.setEnabled(lifecycle.state() == ServerLifecycle.State.ERROR);
                stop.setEnabled(lifecycle.state() != ServerLifecycle.State.ERROR);
                boolean editable = lifecycle.state() == ServerLifecycle.State.ERROR;
                port.setEnabled(editable);
                tgtMinutes.setEnabled(editable);
                ticketMinutes.setEnabled(editable);
            });
            port.setEnabled(false);
            tgtMinutes.setEnabled(false);
            ticketMinutes.setEnabled(false);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cấu hình chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void stopServer() {
        state.setText("Đang dừng…");
        start.setEnabled(false);
        stop.setEnabled(false);
        lifecycle.stop(() -> {
            port.setEnabled(true);
            tgtMinutes.setEnabled(true);
            ticketMinutes.setEnabled(true);
            start.setEnabled(true);
            state.setText("Đã dừng");
        });
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
            ghi("Kết nối " + socket.getInetAddress().getHostAddress() + " lệnh " + cacTruong[0] + " (" + connections.incrementAndGet() + ")", true);

            String response = xuLy(cacTruong);
            out.write(response);
            out.newLine();
            out.flush();
        } catch (Exception ex) {
            ghi("Lỗi kết nối: " + ex.getMessage(), false);
        }
    }

    private String xuLy(String[] cacTruong) {
        try {
            String loaiThongDiep = cacTruong[0];
            if (loaiThongDiep.equals("PING")) {
                return "PONG|KDC|" + GiaoThuc.VERSION;
            }
            if (loaiThongDiep.equals("AS-REQ")) {
                return xuLyAS(cacTruong);
            }
            if (loaiThongDiep.equals("TGS-REQ")) {
                return xuLyTGS(cacTruong);
            }
            if (loaiThongDiep.equals("CHECK-SERVICE")) {
                return kiemTraDichVu(cacTruong);
            }
            return "ERR|Lệnh không được hỗ trợ";
        } catch (Exception ex) {
            ghi(cacTruong[0] + " thất bại: " + ex.getMessage(), false);
            return "ERR|" + ex.getMessage();
        }
    }

    private String xuLyAS(String[] cacTruong) throws Exception {
        String user = cacTruong[1];
        if (!users.containsKey(user) || locked.contains(user)) {
            throw new Exception("User không tồn tại hoặc bị khóa");
        }

        SecretKey clientKey = CryptoUtil.taoKhoaTuMatKhau(users.get(user));
        SecretKey tgsKey = CryptoUtil.taoKhoaTuMatKhau(GiaoThuc.TGS_KEY_PASSWORD);

        String sessionKey = Base64.getEncoder().encodeToString(CryptoUtil.taoKhoaPhien().getEncoded());
        long expiry = System.currentTimeMillis() + tgtLifetime * 60000L;

        String tgt = CryptoUtil.maHoa(user + "|" + sessionKey + "|" + expiry, tgsKey);
        String encryptedSession = CryptoUtil.maHoa(sessionKey + "|" + expiry, clientKey);

        ticketsModel.addRow(new Object[]{"TGT", user, "TGS", new Date(expiry)});
        ghi("AS-REQ/AS-REP thành công cho " + user, true);

        return "AS-REP|" + encryptedSession + "|" + tgt;
    }

    private String xuLyTGS(String[] cacTruong) throws Exception {
        SecretKey tgsKey = CryptoUtil.taoKhoaTuMatKhau(GiaoThuc.TGS_KEY_PASSWORD);
        String[] tgt = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(cacTruong[1], tgsKey));
        String user = tgt[0];
        String sessionKeyClientTgs = tgt[1];
        long tgtExpiry = Long.parseLong(tgt[2]);

        if (tgtExpiry < System.currentTimeMillis()) {
            throw new Exception("TGT hết hạn");
        }

        String[] auth = GiaoThuc.tachThongDiep(CryptoUtil.giaiMa(cacTruong[2], khoa(sessionKeyClientTgs)));
        String authUser = auth[0];
        long authTimestamp = Long.parseLong(auth[1]);

        if (!authUser.equals(user) || !GiaoThuc.timestampHopLe(authTimestamp)) {
            throw new Exception("Authenticator không hợp lệ");
        }

        String targetService = cacTruong[3];
        if (!services.containsKey(targetService)) {
            throw new Exception("Dịch vụ không tồn tại");
        }

        long serviceTicketExpiry = System.currentTimeMillis() + serviceLifetime * 60000L;
        String sessionKeyClientService = Base64.getEncoder().encodeToString(CryptoUtil.taoKhoaPhien().getEncoded());
        SecretKey serviceKey = CryptoUtil.taoKhoaTuMatKhau(services.get(targetService));

        String serviceTicket = CryptoUtil.maHoa(user + "|" + sessionKeyClientService + "|" + serviceTicketExpiry + "|" + targetService, serviceKey);
        String response = CryptoUtil.maHoa(sessionKeyClientService + "|" + serviceTicketExpiry, khoa(sessionKeyClientTgs));

        ticketsModel.addRow(new Object[]{"Service Ticket", user, targetService, new Date(serviceTicketExpiry)});
        ghi("TGS-REQ/TGS-REP thành công: " + user + " → " + targetService, true);

        return "TGS-REP|" + response + "|" + serviceTicket;
    }

    private String kiemTraDichVu(String[] cacTruong) {
        String user = cacTruong[1];
        String targetService = cacTruong[2];
        boolean valid = users.containsKey(user) && !locked.contains(user) && services.containsKey(targetService);
        return valid ? "CHECK-OK" : "CHECK-DENY";
    }

    private void themUser() {
        String user = username.getText().trim();
        String pass = new String(password.getPassword());
        if (!validEntry(user, pass)) return;
        users.put(user, pass);
        password.setText("");
        capNhatUsers();
        ghi("Đã thêm/cập nhật user", true);
    }

    private void khoaUser() {
        String user = username.getText().trim();
        if (!users.containsKey(user)) {
            ghi("Tài khoản không tồn tại", false);
            return;
        }
        if (!locked.remove(user)) {
            locked.add(user);
        }
        capNhatUsers();
    }

    private void xoaUser() {
        String user = username.getText().trim();
        if (!users.containsKey(user)) {
            ghi("Tài khoản không tồn tại", false);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Xóa tài khoản " + user + "?", "Xác nhận xóa", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        users.remove(user);
        locked.remove(user);
        capNhatUsers();
    }

    private void themDichVu() {
        String name = serviceName.getText().trim();
        String secret = new String(servicePassword.getPassword());
        if (!validEntry(name, secret)) return;
        services.put(name, secret);
        ghi("Đã lưu dịch vụ " + name, true);
    }

    private boolean validEntry(String name, String secret) {
        try {
            FormSupport.required(name, "Tên");
            FormSupport.required(secret, "Mật khẩu/khóa");
            return true;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dữ liệu chưa hợp lệ", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    private void capNhatUsers() {
        usersModel.setRowCount(0);
        users.forEach((u, p) -> {
            String trangThai = locked.contains(u) ? "Đã khóa" : "Hoạt động";
            usersModel.addRow(new Object[]{u, trangThai});
        });
    }

    private SecretKey khoa(String encoded) {
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        return new SecretKeySpec(keyBytes, "AES");
    }

    private void ghi(String thongDiep, boolean ok) {
        SwingUtilities.invokeLater(() -> {
            String gio = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String trangThai = ok ? "[THÀNH CÔNG] " : "[THẤT BẠI] ";
            FormSupport.appendLog(log, "[" + gio + "] " + trangThai + thongDiep + "\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KDCAdminForm().setVisible(true));
    }
}
