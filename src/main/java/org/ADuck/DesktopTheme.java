package org.ADuck;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class DesktopTheme {
    static final Color BACKGROUND = new Color(244, 247, 251);
    static final Color INK = new Color(25, 42, 65);
    static final Color ACCENT = new Color(33, 87, 190);

    private DesktopTheme() {}

    static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Table.selectionBackground", new Color(220, 233, 255));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TextComponent.selectionBackground", new Color(220, 233, 255));
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "PasswordField.font", "ComboBox.font", "TabbedPane.font", "Table.font", "TableHeader.font", "Spinner.font"}) {
            UIManager.put(key, font);
        }
    }

    static JPanel stack(Component... components) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component component : components) {
            if (component instanceof JComponent item) item.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(component);
            panel.add(Box.createVerticalStrut(12));
        }
        return panel;
    }

    static JPanel card(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 227, 237)), new EmptyBorder(16, 18, 16, 18)));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    static JPanel fields(Object... items) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        for (int i = 0; i < items.length; i += 2) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = i / 2;
            c.insets = new Insets(5, 0, 5, 14);
            c.anchor = GridBagConstraints.LINE_START;
            c.gridx = 0;
            JLabel label = new JLabel(items[i].toString());
            label.setLabelFor((Component) items[i + 1]);
            panel.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add((Component) items[i + 1], c);
        }
        return panel;
    }

    static JPanel actions(Component... components) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        int row = 0;
        int column = 0;
        for (Component component : components) {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 0, 6, 8);
            c.anchor = GridBagConstraints.LINE_START;
            if (component instanceof JLabel) {
                if (column != 0) row++;
                c.gridx = 0;
                c.gridy = row++;
                c.gridwidth = 2;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                column = 0;
            } else {
                c.gridx = column;
                c.gridy = row;
                c.weightx = 0.5;
                column++;
                if (column == 2) {
                    column = 0;
                    row++;
                }
            }
            panel.add(component, c);
        }
        return panel;
    }

    static void finish(JFrame frame, String title, String subtitle) {
        install();
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Container old = frame.getContentPane();
        if (!(frame instanceof ClientForm) && old.getLayout() instanceof BorderLayout layout) {
            Component controls = layout.getLayoutComponent(BorderLayout.NORTH);
            Component tabs = layout.getLayoutComponent(BorderLayout.CENTER);
            old.removeAll();
            JScrollPane settings = new JScrollPane(controls);
            settings.setBorder(BorderFactory.createEmptyBorder());
            settings.setPreferredSize(new Dimension(410, 580));
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, settings, tabs);
            split.setResizeWeight(0.35);
            split.setBorder(BorderFactory.createEmptyBorder());
            old.add(split, BorderLayout.CENTER);
        }
        SwingUtilities.updateComponentTreeUI(frame);
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(20, 24, 20, 24));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        JLabel description = new JLabel(subtitle);
        description.setForeground(new Color(91, 108, 130));
        root.add(stack(heading, description), BorderLayout.NORTH);
        if (frame instanceof ClientForm) {
            JScrollPane scroll = new JScrollPane(old);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(20);
            root.add(scroll, BorderLayout.CENTER);
        } else {
            root.add(old, BorderLayout.CENTER);
        }
        frame.setContentPane(root);
        decorate(old);
    }

    private static void decorate(Component component) {
        if (component instanceof JButton button) {
            button.setFocusPainted(true);
            button.setMargin(new Insets(8, 14, 8, 14));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String text = button.getText();
            if (text != null && (text.startsWith("Đăng nhập") || text.startsWith("Khởi động") || text.startsWith("Sinh khóa"))) {
                button.setBackground(ACCENT);
                button.setForeground(Color.WHITE);
                button.setOpaque(true);
            }
        }
        if (component instanceof JTable table) {
            table.setRowHeight(32);
            table.setShowVerticalLines(false);
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setDefaultEditor(Object.class, null);
            table.getTableHeader().setReorderingAllowed(false);
        }
        if (component instanceof JTextArea area) {
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            area.setMargin(new Insets(12, 12, 12, 12));
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            ((javax.swing.text.DefaultCaret) area.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) decorate(child);
        }
    }
}
