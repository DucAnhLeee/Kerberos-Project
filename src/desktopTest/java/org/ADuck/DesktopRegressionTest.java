package org.ADuck;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.*;

public final class DesktopRegressionTest {
    public static void main(String[] args) throws Exception {
        check(FormSupport.port(" 8888 ") == 8888, "Valid port");
        for (String port : new String[]{"", "abc", "0", "-1", "65536"}) {
            try {
                FormSupport.port(port);
                throw new AssertionError("Accepted invalid port: " + port);
            } catch (IllegalArgumentException expected) {}
        }
        for (String value : new String[]{"", "  ", "a|b", "a\nb"}) {
            try {
                FormSupport.required(value, "test");
                throw new AssertionError("Accepted invalid field");
            } catch (IllegalArgumentException expected) {}
        }
        var model = FormSupport.model("Name", "State");
        model.addRow(new Object[]{"an", "Ready"});
        edt(() -> {
            check(model.getRowCount() == 1, "Rows published on EDT");
            check(!model.isCellEditable(0, 0), "Read-only table");
            return null;
        });
        edt(() -> {
            JPanel actions = DesktopTheme.actions(new JButton("Khởi động"), new JButton("Dừng máy chủ"), new JLabel("Đang chạy · cổng 9999"));
            java.awt.Dimension preferred = actions.getPreferredSize();
            for (int width : new int[]{300, 410, 800, 410, 300}) {
                actions.setSize(width, 200);
                actions.doLayout();
                check(preferred.equals(actions.getPreferredSize()), "Action sizing must not depend on previous layout width");
                java.awt.Rectangle first = actions.getComponent(0).getBounds();
                java.awt.Rectangle status = actions.getComponent(2).getBounds();
                check(status.y >= first.y + first.height, "Status has a separate stable row");
            }
            return null;
        });
        lifecycle();
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("PASS validation, EDT table model, server lifecycle. SKIP Swing flow: no display.");
        } else {
            forms();
            System.out.println("PASS validation, lifecycle, Swing AS/TGS/AP/RSA, replay, expiry and restart.");
        }
    }

    private static void lifecycle() throws Exception {
        ServerLifecycle server = new ServerLifecycle();
        try (ServerSocket occupied = new ServerSocket(0)) {
            server.start(occupied.getLocalPort(), socket -> {}, text -> {});
            await(() -> server.state() == ServerLifecycle.State.ERROR, "Bind failure");
            server.close();
        }
        await(() -> server.state() == ServerLifecycle.State.STOPPED, "Bind cleanup");
        int port = freePort();
        try {
            server.start(port, socket -> {
                try {
                    socket.getOutputStream().write("PONG\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException ex) { throw new UncheckedIOException(ex); }
            }, text -> {});
            await(() -> server.state() == ServerLifecycle.State.RUNNING, "Start listener");
            server.start(port, socket -> {}, text -> {});
            check("PONG".equals(request(port, "PING")), "Repeated start preserves listener");
        } finally { server.close(); }
        await(() -> server.state() == ServerLifecycle.State.STOPPED, "Stopped state");
        server.start(port, socket -> {}, text -> {});
        await(() -> server.state() == ServerLifecycle.State.RUNNING, "Restart same port");
        server.close();
    }

    private static void forms() throws Exception {
        KDCAdminForm kdc = edt(KDCAdminForm::new);
        ServiceServerForm service = edt(ServiceServerForm::new);
        ClientForm client = edt(ClientForm::new);
        int kdcPort = freePort();
        int servicePort;
        do { servicePort = freePort(); } while (servicePort == kdcPort);
        final int targetPort = servicePort;
        try {
            edt(() -> {
                text(kdc, "port", Integer.toString(kdcPort));
                text(service, "port", Integer.toString(targetPort));
                text(service, "kdcPort", Integer.toString(kdcPort));
                text(client, "kdcPort", Integer.toString(kdcPort));
                text(client, "serverPort", Integer.toString(targetPort));
                call(kdc, "startServer");
                call(service, "startServer");
                for (JFrame frame : new JFrame[]{kdc, service, client}) {
                    frame.setSize(frame.getMinimumSize());
                    frame.setVisible(true);
                    frame.validate();
                    check(frame.getDefaultCloseOperation() == JFrame.DISPOSE_ON_CLOSE, "Independent windows");
                }
                return null;
            });
            edt(() -> {
                java.nio.file.Path directory = java.nio.file.Path.of("build", "reports", "desktop");
                java.nio.file.Files.createDirectories(directory);
                for (JFrame frame : new JFrame[]{kdc, service, client}) {
                    var image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    frame.printAll(graphics);
                    graphics.dispose();
                    javax.imageio.ImageIO.write(image, "png", directory.resolve(frame.getClass().getSimpleName() + ".png").toFile());
                }
                return null;
            });
            await(() -> request(kdcPort, "PING").startsWith("PONG|"), "KDC ready");
            await(() -> request(targetPort, "PING").startsWith("PONG|"), "Service ready");
            edt(() -> { call(client, "ping", new Class<?>[]{boolean.class}, true); return null; });
            idle(client);
            edt(() -> { call(client, "ping", new Class<?>[]{boolean.class}, false); return null; });
            idle(client);
            action(client, "dangNhap");
            check(field(client, "tgt") != null, "AS ticket");
            action(client, "xinVe");
            check(field(client, "serviceTicket") != null, "Service ticket");
            action(client, "xinVe");
            action(client, "truyCap");
            check((Boolean) field(client, "apAuthenticated"), "Mutual authentication");
            edt(() -> { call(client, "sendRsa", new Class<?>[]{String.class, String.class}, "SINH_KHOA", ""); return null; });
            idle(client);
            check((Boolean) field(client, "rsaKeyReady"), "RSA key");
            action(client, "maHoaRsa");
            check(!edt(() -> ((JTextField) field(client, "cipherText")).getText()).isEmpty(), "RSA ciphertext");
            action(client, "giaiMaRsa");
            check(edt(() -> ((JTextField) field(client, "resultText")).getText().equals(((JTextField) field(client, "clearText")).getText())), "RSA round-trip");
            action(client, "guiReplay");
            action(client, "replayRsa");
            String log = edt(() -> ((JTextArea) field(client, "log")).getText());
            check(log.contains("Replay Authenticator bị từ chối"), "AP replay rejected");
            check(log.contains("Replay RSA bị từ chối"), "RSA replay rejected");
            edt(() -> {
                Field expiry = ClientForm.class.getDeclaredField("ticketExpiry");
                expiry.setAccessible(true);
                expiry.setLong(client, 1);
                call(client, "expireTickets");
                check(field(client, "serviceTicket") == null, "Expired ticket reset");
                check(field(client, "lastRsaRequest") == null, "Replay payload reset");
                call(kdc, "stopServer");
                call(service, "stopServer");
                return null;
            });
            await(() -> ((ServerLifecycle) field(kdc, "lifecycle")).state() == ServerLifecycle.State.STOPPED, "KDC stopped");
            await(() -> ((ServerLifecycle) field(service, "lifecycle")).state() == ServerLifecycle.State.STOPPED, "Service stopped");
            edt(() -> {
                call(kdc, "startServer");
                call(service, "startServer");
                return null;
            });
            await(() -> request(kdcPort, "PING").startsWith("PONG|"), "KDC restart");
            await(() -> request(targetPort, "PING").startsWith("PONG|"), "Service restart");
            edt(() -> {
                kdc.dispose();
                check(client.isDisplayable() && service.isDisplayable(), "Closing KDC preserves other windows");
                return null;
            });
        } finally {
            edt(() -> { client.dispose(); service.dispose(); kdc.dispose(); return null; });
        }
    }

    private static void action(ClientForm client, String name) throws Exception {
        edt(() -> { call(client, name); return null; });
        idle(client);
    }

    private static void idle(ClientForm client) throws Exception {
        await(() -> edt(() -> !(Boolean) field(client, "busy")), "Client operation");
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void text(Object object, String name, String value) throws Exception {
        ((JTextField) field(object, name)).setText(value);
    }

    private static void call(Object object, String name) throws Exception {
        call(object, name, new Class<?>[0]);
    }

    private static void call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(object, args);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void await(Callable<Boolean> condition, String name) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            try { if (condition.call()) return; } catch (IOException ignored) {}
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out: " + name);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static String request(int port, String line) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 300);
            socket.setSoTimeout(1000);
            socket.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
            String reply = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            return reply == null ? "" : reply;
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
