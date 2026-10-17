package org.ADuck;

import javax.swing.SwingUtilities;

public final class DemoLauncher {
    private DemoLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KDCAdminForm kdcForm = new KDCAdminForm();
            ServiceServerForm serviceForm = new ServiceServerForm();
            ClientForm clientForm = new ClientForm();

            kdcForm.setLocation(30, 30);
            serviceForm.setLocation(70, 70);
            clientForm.setLocation(110, 110);

            kdcForm.setVisible(true);
            serviceForm.setVisible(true);
            clientForm.setVisible(true);
        });
    }
}
