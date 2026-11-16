package org.ADuck;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class FormSupport {
    private FormSupport() {}

    static int port(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("Cổng phải là số nguyên từ 1 đến 65535.");
    }

    static String required(String text, String name) {
        if (text == null || text.isBlank() || text.contains("|") || text.contains("\n") || text.contains("\r")) {
            throw new IllegalArgumentException(name + " không được trống hoặc chứa ký tự phân cách giao thức.");
        }
        return text;
    }

    static void appendLog(JTextArea area, String text) {
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, area);
        JScrollBar bar = pane == null ? null : pane.getVerticalScrollBar();
        boolean follow = bar == null || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
        area.append(text);
        int excess = area.getDocument().getLength() - 100_000;
        if (excess > 0) {
            try {
                String prefix = area.getDocument().getText(0, Math.min(excess + 1000, area.getDocument().getLength()));
                int newline = prefix.indexOf('\n', excess);
                area.getDocument().remove(0, newline >= 0 ? newline + 1 : excess);
            } catch (javax.swing.text.BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        }
        if (follow) area.setCaretPosition(area.getDocument().getLength());
    }

    static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public void addRow(Object[] row) {
                if (!SwingUtilities.isEventDispatchThread()) {
                    Object[] copy = row.clone();
                    SwingUtilities.invokeLater(() -> addRow(copy));
                    return;
                }
                if (getColumnCount() > 2 && getRowCount() >= 1000) removeRow(0);
                super.addRow(row);
            }
        };
    }
}
