package org.ADuck;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

final class ServerLifecycle implements AutoCloseable {
    enum State { STOPPED, STARTING, RUNNING, STOPPING, ERROR }
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile State state = State.STOPPED;
    private ServerSocket listener;
    private ExecutorService workers;
    private long generation;
    private Thread acceptor;

    State state() { return state; }

    synchronized void start(int port, Consumer<Socket> handler, Consumer<String> status) {
        if (state == State.STARTING || state == State.RUNNING || state == State.STOPPING) return;
        state = State.STARTING;
        long current = ++generation;
        status.accept("Đang khởi động…");
        workers = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), r -> {
                    Thread thread = new Thread(r, "kerberos-request");
                    thread.setDaemon(true);
                    return thread;
                });
        ExecutorService pool = workers;
        acceptor = new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(port)) {
                synchronized (this) {
                    if (current != generation) return;
                    listener = socket;
                    state = State.RUNNING;
                }
                notifyStatus(current, "Đang chạy · cổng " + port, status);
                while (!socket.isClosed()) {
                    Socket client = socket.accept();
                    client.setSoTimeout(15000);
                    clients.add(client);
                    try {
                        pool.execute(() -> {
                            try (client) { handler.accept(client); }
                            catch (IOException ignored) {}
                            finally { clients.remove(client); }
                        });
                    } catch (RejectedExecutionException ex) {
                        clients.remove(client);
                        client.close();
                    }
                }
            } catch (IOException ex) {
                synchronized (this) {
                    if (current != generation) return;
                    state = State.ERROR;
                }
                notifyStatus(current, "Lỗi: " + ex.getMessage(), status);
            } finally {
                pool.shutdownNow();
            }
        }, "kerberos-listener");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void notifyStatus(long current, String text, Consumer<String> status) {
        SwingUtilities.invokeLater(() -> {
            synchronized (this) {
                if (current == generation) status.accept(text);
            }
        });
    }

    synchronized void stop(Runnable stopped) {
        state = State.STOPPING;
        long current = ++generation;
        try { if (listener != null) listener.close(); } catch (IOException ignored) {}
        for (Socket socket : clients) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        if (workers != null) workers.shutdownNow();
        ExecutorService pool = workers;
        Thread listenerThread = acceptor;
        listener = null;
        Thread cleanup = new Thread(() -> {
            try {
                if (listenerThread != null) listenerThread.join();
                if (pool != null) {
                    while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                        for (Socket socket : clients) {
                            try { socket.close(); } catch (IOException ignored) {}
                        }
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    synchronized (this) {
                        if (current != generation) return;
                        clients.clear();
                        state = State.STOPPED;
                        stopped.run();
                    }
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "kerberos-shutdown");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    @Override
    public void close() {
        stop(() -> {});
    }
}
