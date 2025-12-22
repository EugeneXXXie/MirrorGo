package org.AzusaKis.Eugene;

// --- 导入 UI、IO、JSON 以及线程相关库 ---
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

/**
 * FileSwitcher - 实时文件同步助手 基于 Java Swing + FlatLaf 构建，支持多语言、深色模式及 AppData 持久化配置
 */
public class FileSwitcher extends JFrame {

    // --- UI 核心组件 ---
    private final JTextField srcField;     // 显示源路径
    private final JTextField dstField;     // 显示目标路径
    private final JTextArea logArea;       // 日志打印区域
    private final JLabel statusLabel;      // 底部状态文字（带圆点）
    private final JButton monitorBtn;      // 启动/停止按钮
    private final JComboBox<String> themeBox; // 主题选择下拉框

    // --- 同步逻辑变量 ---
    private volatile boolean monitoring = false; // 线程开关标识
    private Thread monitorThread;                // 后台监听线程
    private int syncCount = 0;                   // 同步次数统计

    /**
     * * 配置文件路径配置（Windows 标准路径） 存储在 %AppData%/FileSwitcher 目录下，避免在 C 盘根目录或
     * Program Files 下产生权限报错
     */
    private static final String APP_DATA_DIR = System.getenv("APPDATA") + File.separator + "FileSwitcher";
    private static final String CONFIG_PATH = APP_DATA_DIR + File.separator + "sync_config.json";

    private final ObjectMapper mapper = new ObjectMapper(); // JSON 解析引擎
    private final boolean isZH = Locale.getDefault().getLanguage().equals("zh"); // 系统语言判断
    private final String FONT_FAMILY = "Microsoft YaHei"; // 预设中文字体

    public FileSwitcher() {
        // 1. 初始化检查：确保配置文件夹存在
        ensureConfigDirExists();

        // 2. 窗口基础属性
        setTitle("FileSwitcher");
        setSize(850, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 窗口居中

        // 3. UI 布局初始化
        // 使用 BorderLayout 配合 EmptyBorder 产生四周留白效果
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

        // --- 中间区域 (路径选择表单 + 日志容器) ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 25));

        // 路径选择表单 (使用 GridBagLayout 保证拉伸效果)
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);

        srcField = createStyledField();
        addRow(formPanel, getStr("源文件路径", "Source Path"), srcField, gbc, 0);
        dstField = createStyledField();
        addRow(formPanel, getStr("目标文件路径", "Target Path"), dstField, gbc, 2);
        centerPanel.add(formPanel, BorderLayout.NORTH);

        // 日志显示区域 (带 TitledBorder 边框)
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

        // --- 底部栏 (状态文字 + 巨型启动按钮) ---
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel(getStr("● 系统就绪", "● Ready"));
        statusLabel.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        statusLabel.setForeground(Color.GRAY);

        monitorBtn = new JButton(getStr("开启实时同步", "Start Sync"));
        monitorBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 18));
        monitorBtn.setPreferredSize(new Dimension(220, 55));
        monitorBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        monitorBtn.setBackground(new Color(0, 120, 215)); // 初始蓝色
        monitorBtn.setForeground(Color.WHITE);
        monitorBtn.addActionListener(e -> toggleMonitoring());

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(monitorBtn, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        // 4. 加载持久化配置 (路径、主题)
        loadConfig();
    }

    /**
     * 确保 AppData 文件夹存在，防止首次运行保存失败
     */
    private void ensureConfigDirExists() {
        File dir = new File(APP_DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 应用 FlatLaf 主题并开启平滑过渡动画
     */
    private void applyTheme(int index, boolean save) {
        FlatAnimatedLafChange.showSnapshot(); // 记录当前界面快照用于渐变
        try {
            switch (index) {
                case 1 ->
                    UIManager.setLookAndFeel(new FlatLightLaf());
                case 2 ->
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                default -> { // 跟随系统：检测 Windows 注册表
                    if (isWindowsDarkTheme()) {
                        UIManager.setLookAndFeel(new FlatDarkLaf());
                    } else {
                        UIManager.setLookAndFeel(new FlatLightLaf());
                    }
                }
            }
            FlatAnimatedLafChange.hideSnapshotWithAnimation(); // 执行动画
            SwingUtilities.updateComponentTreeUI(this); // 刷新全局组件
            if (save) {
                saveConfig(); // 持久化主题选择
            }
        } catch (UnsupportedLookAndFeelException ex) {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (UnsupportedLookAndFeelException ignored) {
            }
        }
    }

    /**
     * 通过命令行查询注册表，判断 Windows 10/11 是否处于深色模式
     */
    private boolean isWindowsDarkTheme() {
        try {
            Process process = Runtime.getRuntime().exec("reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        String[] parts = line.split("    ");
                        String value = parts[parts.length - 1].trim();
                        return "0x0".equals(value) || "0".equals(value); // 0 代表深色
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * 简单的多语言工具函数
     */
    private String getStr(String zh, String en) {
        return isZH ? zh : en;
    }

    /**
     * 表单行构建辅助方法
     */
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

    /**
     * 创建统一样式的路径文本框（不可手动输入）
     */
    private JTextField createStyledField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setFont(new Font("Consolas", Font.PLAIN, 16));
        f.setPreferredSize(new Dimension(0, 48));
        return f;
    }

    /**
     * 从 AppData 读取并应用配置
     */
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

    /**
     * 将当前路径和主题保存到 AppData
     */
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

    /**
     * 打开 AWT 原生文件选择器（比 Swing 默认选择器更符合 Windows 系统外观）
     */
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

    /**
     * 开启或停止监控的切换逻辑
     */
    private void toggleMonitoring() {
        if (monitoring) {
            stopMonitoring();
        } else {
            startSync();
        }
    }

    /**
     * 启动监控线程：核心逻辑
     */
    private void startSync() {
        String s = srcField.getText();
        String d = dstField.getText();
        if (s.isEmpty() || d.isEmpty()) {
            return;
        }

        monitoring = true;
        monitorBtn.setText(getStr("停止同步", "Stop Sync"));
        monitorBtn.setBackground(new Color(220, 53, 69)); // 停止状态设为红色
        statusLabel.setText(getStr("● 监控中", "● Monitoring"));
        statusLabel.setForeground(new Color(40, 167, 69)); // 活跃状态设为绿色

        // 启动后台线程执行文件轮询，避免阻塞 UI 界面
        monitorThread = new Thread(() -> {
            Path sp = Paths.get(s);
            Path dp = Paths.get(d);
            long lastMod = sp.toFile().lastModified(); // 记录初始修改时间
            appendLog("SYSTEM", getStr("同步已开启...", "Sync active..."));

            while (monitoring && !Thread.currentThread().isInterrupted()) {
                try {
                    long currentMod = sp.toFile().lastModified();
                    // 如果源文件时间戳变大，执行覆盖操作
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
                    TimeUnit.SECONDS.sleep(1); // 每秒检查一次
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

    /**
     * 停止线程并重置 UI 样式
     */
    private void stopMonitoring() {
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        monitorBtn.setText(getStr("开启实时同步", "Start Sync"));
        monitorBtn.setBackground(new Color(0, 120, 215)); // 恢复蓝色
        statusLabel.setText(getStr("● 已停止", "● Stopped"));
        statusLabel.setForeground(Color.GRAY);
        appendLog("SYSTEM", getStr("同步已停止。", "Sync stopped."));
    }

    /**
     * 安全地向日志区追加文字 使用 SwingUtilities.invokeLater 确保在 UI 线程执行更新，防止多线程崩溃
     */
    private void appendLog(String tag, String msg) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] [%s] %s\n", now, tag, msg));
            logArea.setCaretPosition(logArea.getDocument().getLength()); // 自动滚屏到最下方
        });
    }

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        // 设置系统属性：优化 AWT/Swing 在高分屏下的表现
        System.setProperty("sun.awt.noerasebackground", "true");

        // 初始化 FlatLaf 皮肤
        FlatLightLaf.setup();
        // 设置全局组件圆角弧度（更现代的视觉效果）
        UIManager.put("Button.arc", 15);
        UIManager.put("Component.arc", 15);

        // 在 EDT 线程启动窗口
        SwingUtilities.invokeLater(() -> {
            FileSwitcher frame = new FileSwitcher();
            frame.setVisible(true);
            frame.appendLog("INIT", "FileSwitcher " + frame.getStr("已启动。", "Started."));
        });
    }
}
