package org.AzusaKis.Eugene;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

public class FileSwitcher extends JFrame {

    private final JTextField srcField;
    private final JTextField dstField;
    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final JButton monitorBtn;
    private final JComboBox<String> themeBox;

    private volatile boolean monitoring = false;
    private Thread monitorThread;
    private int syncCount = 0;

    // --- 配置文件路径配置（Windows 标准路径） ---
    private static final String APP_DATA_DIR = System.getenv("APPDATA") + File.separator + "FileSwitcher";
    private static final String CONFIG_PATH = APP_DATA_DIR + File.separator + "sync_config.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isZH = Locale.getDefault().getLanguage().equals("zh");
    private final String FONT_FAMILY = "Microsoft YaHei";

    public FileSwitcher() {
        // 1. 初始化检查：确保 AppData 里的文件夹存在
        ensureConfigDirExists();

        // 2. 窗口基本设置
        setTitle("FileSwitcher");
        setSize(850, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 3. UI 布局初始化
        JPanel root = new JPanel(new BorderLayout(25, 25));
        root.setBorder(new EmptyBorder(30, 35, 30, 35));
        add(root);

        // --- 顶部栏 (标题 + 主题切换) ---
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel(getStr("文件实时同步助手", "Real-time File Sync Assistant"));
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 26));

        String[] themes = isZH
                ? new String[]{"跟随系统", "浅色模式", "深色模式"}
                : new String[]{"System Default", "Light Theme", "Dark Theme"};

        themeBox = new JComboBox<>(themes);
        themeBox.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        themeBox.setPreferredSize(new Dimension(140, 35));
        themeBox.addActionListener(e -> applyTheme(themeBox.getSelectedIndex(), true));

        JPanel themeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        themeWrapper.add(new JLabel(getStr("主题界面:", "UI Theme:")));
        themeWrapper.add(themeBox);

        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(themeWrapper, BorderLayout.EAST);
        root.add(topPanel, BorderLayout.NORTH);

        // --- 中间区域 (路径选择 + 日志) ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 25));
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);

        srcField = createStyledField();
        addRow(formPanel, getStr("源文件路径", "Source Path"), srcField, gbc, 0);
        dstField = createStyledField();
        addRow(formPanel, getStr("目标文件路径", "Target Path"), dstField, gbc, 2);
        centerPanel.add(formPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new TitledBorder(new LineBorder(new Color(150, 150, 150, 60)),
                getStr(" 运行日志 ", " Logs "), TitledBorder.LEFT, TitledBorder.TOP,
                new Font(FONT_FAMILY, Font.BOLD, 15), Color.GRAY));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        root.add(centerPanel, BorderLayout.CENTER);

        // --- 底部栏 (状态 + 控制按钮) ---
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel(getStr("● 系统就绪", "● Ready"));
        statusLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        statusLabel.setForeground(Color.GRAY);

        monitorBtn = new JButton(getStr("开启实时同步", "Start Sync"));
        monitorBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 18));
        monitorBtn.setPreferredSize(new Dimension(220, 55));
        monitorBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        monitorBtn.setBackground(new Color(0, 120, 215));
        monitorBtn.setForeground(Color.WHITE);
        monitorBtn.addActionListener(e -> toggleMonitoring());

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(monitorBtn, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        // 4. 加载持久化配置
        loadConfig();
    }

    private void ensureConfigDirExists() {
        File dir = new File(APP_DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void applyTheme(int index, boolean save) {
        FlatAnimatedLafChange.showSnapshot();
        try {
            switch (index) {
                case 1 ->
                    UIManager.setLookAndFeel(new FlatLightLaf());
                case 2 ->
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                default -> {
                    if (isWindowsDarkTheme()) {
                        UIManager.setLookAndFeel(new FlatDarkLaf());
                    } else {
                        UIManager.setLookAndFeel(new FlatLightLaf());
                    }
                }
            }
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
            SwingUtilities.updateComponentTreeUI(this);
            if (save) {
                saveConfig();
            }
        } catch (UnsupportedLookAndFeelException ex) {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (UnsupportedLookAndFeelException ignored) {
            }
        }
    }

    private boolean isWindowsDarkTheme() {
        try {
            Process process = Runtime.getRuntime().exec("reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        String[] parts = line.split("    ");
                        String value = parts[parts.length - 1].trim();
                        return "0x0".equals(value) || "0".equals(value);
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private String getStr(String zh, String en) {
        return isZH ? zh : en;
    }

    private void addRow(JPanel p, String labelText, JTextField field, GridBagConstraints gbc, int y) {
        gbc.gridy = y;
        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        p.add(lbl, gbc);

        gbc.gridy = y + 1;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        p.add(field, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(10, 15, 10, 0);
        JButton btn = new JButton(getStr("浏览", "Browse"));
        btn.setFont(new Font(FONT_FAMILY, Font.BOLD, 16));
        btn.setPreferredSize(new Dimension(120, 48));
        btn.addActionListener(e -> openFilePicker(field));
        p.add(btn, gbc);
        gbc.insets = new Insets(10, 0, 10, 0);
    }

    private JTextField createStyledField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setFont(new Font("Consolas", Font.PLAIN, 16));
        f.setPreferredSize(new Dimension(0, 48));
        return f;
    }

    private void loadConfig() {
        File f = new File(CONFIG_PATH);
        if (!f.exists()) {
            applyTheme(0, false);
            return;
        }
        try {
            JsonNode node = mapper.readTree(f);
            srcField.setText(node.path("src").asText(""));
            dstField.setText(node.path("dst").asText(""));
            int themeIdx = node.path("theme").asInt(0);
            themeBox.setSelectedIndex(themeIdx);
            applyTheme(themeIdx, false);
            appendLog("CONFIG", getStr("成功从 AppData 加载配置。", "Config loaded from AppData."));
        } catch (IOException e) {
            applyTheme(0, false);
        }
    }

    private void saveConfig() {
        try {
            ensureConfigDirExists();
            ObjectNode node = mapper.createObjectNode();
            node.put("src", srcField.getText());
            node.put("dst", dstField.getText());
            node.put("theme", themeBox.getSelectedIndex());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_PATH), node);
        } catch (IOException e) {
            appendLog("ERROR", getStr("保存配置失败: ", "Failed to save: ") + e.getMessage());
        }
    }

    private void openFilePicker(JTextField field) {
        FileDialog fd = new FileDialog(this, getStr("选择文件", "Select File"), FileDialog.LOAD);
        if (!field.getText().isEmpty()) {
            File f = new File(field.getText());
            if (f.exists()) {
                fd.setDirectory(f.getParent());
            }
        }
        fd.setVisible(true);
        if (fd.getFile() != null) {
            String path = fd.getDirectory() + fd.getFile();
            field.setText(path);
            saveConfig();
            appendLog("PATH", getStr("已更新: ", "Updated: ") + fd.getFile());
        }
    }

    private void toggleMonitoring() {
        if (monitoring) {
            stopMonitoring();
        } else {
            startSync();
        }
    }

    private void startSync() {
        String s = srcField.getText();
        String d = dstField.getText();
        if (s.isEmpty() || d.isEmpty()) {
            return;
        }
        monitoring = true;
        monitorBtn.setText(getStr("停止同步", "Stop Sync"));
        monitorBtn.setBackground(new Color(220, 53, 69));
        statusLabel.setText(getStr("● 监控中", "● Monitoring"));
        statusLabel.setForeground(new Color(40, 167, 69));

        monitorThread = new Thread(() -> {
            Path sp = Paths.get(s);
            Path dp = Paths.get(d);
            long lastMod = sp.toFile().lastModified();
            appendLog("SYSTEM", getStr("同步已开启...", "Sync active..."));

            while (monitoring && !Thread.currentThread().isInterrupted()) {
                try {
                    long currentMod = sp.toFile().lastModified();
                    if (currentMod > lastMod) {
                        Files.copy(sp, dp, StandardCopyOption.REPLACE_EXISTING);
                        lastMod = currentMod;
                        syncCount++;
                        long bytes = Files.size(dp);
                        String sizeStr = (bytes >= 1024 * 1024)
                                ? String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                                : String.format("%.2f KB", bytes / 1024.0);
                        appendLog("SYNC", String.format(getStr("同步成功 (#%d) | 大小: %s", "Success (#%d) | Size: %s"), syncCount, sizeStr));
                    }
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException ex) {
                    if (monitoring) {
                        appendLog("ERROR", ex.getMessage());
                    }
                }
            }
        });
        monitorThread.start();
    }

    private void stopMonitoring() {
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        monitorBtn.setText(getStr("开启实时同步", "Start Sync"));
        monitorBtn.setBackground(new Color(0, 120, 215));
        statusLabel.setText(getStr("● 已停止", "● Stopped"));
        statusLabel.setForeground(Color.GRAY);
        appendLog("SYSTEM", getStr("同步已停止。", "Sync stopped."));
    }

    private void appendLog(String tag, String msg) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] [%s] %s\n", now, tag, msg));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        // 设置渲染属性，防止 AWT 组件背景闪烁
        System.setProperty("sun.awt.noerasebackground", "true");

        FlatLightLaf.setup();
        UIManager.put("Button.arc", 15);
        UIManager.put("Component.arc", 15);

        SwingUtilities.invokeLater(() -> {
            FileSwitcher frame = new FileSwitcher();
            frame.setVisible(true);
            frame.appendLog("INIT", "FileSwitcher " + frame.getStr("已启动。", "Started."));
        });
    }
}
