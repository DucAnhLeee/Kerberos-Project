package org.ADuck;

import javax.swing.SwingUtilities;

public final class DemoLauncher {
    private DemoLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopTheme.install();
            javax.swing.JFrame[] windows = {new KDCAdminForm(), new ServiceServerForm(), new ClientForm()};
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            for (int i = 0; i < windows.length; i++) {
                javax.swing.JFrame window = windows[i];
                int offset = i * 24;
                window.setSize(Math.min(window.getWidth(), screen.width - 48), Math.min(window.getHeight(), screen.height - 48));
                window.setLocation(screen.x + Math.min(offset, screen.width - window.getWidth()),
                        screen.y + Math.min(offset, screen.height - window.getHeight()));
                window.setVisible(true);
            }
        });
    }
}
