package org.AzusaKis.MirrorGo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
 * MirrorGo - 文件实时同步工具 功能：监控源文件变动并实时覆盖到目标路径
 */
public class MirrorGo extends JFrame {

    // UI 组件定义
    private final JTextField srcField;      // 源文件路径输入框
    private final JTextField dstField;      // 目标文件路径输入框
    private final JTextArea logArea;        // 日志显示区域
    private final JLabel statusLabel;       // 底部状态栏标签
    private final JButton monitorBtn;       // 启动/停止按钮
    private final JComboBox<String> themeBox; // 主题切换下拉框

    // 运行状态变量
    private volatile boolean monitoring = false; // 是否正在监控（线程安全）
    private Thread monitorThread;                // 监控工作线程
    private int syncCount = 0;                   // 同步计数器

    // 配置常量
    private static final String APP_DATA_DIR = System.getenv("APPDATA") + File.separator + "MirrorGo";
    private static final String CONFIG_PATH = APP_DATA_DIR + File.separator + "sync_config.json";

    // 工具类
    private final ObjectMapper mapper = new ObjectMapper(); // JSON 解析器
    private final boolean isZH = Locale.getDefault().getLanguage().equals("zh"); // 判断是否为中文环境
    private final String FONT_FAMILY = "Microsoft YaHei"; // 全局字体

    public MirrorGo() {
        ensureConfigDirExists(); // 初始化配置目录

        // --- 1. 设置程序图标 (影响窗口、任务栏及 Alt+Tab) ---
        try {
            // 从 Resources 读取 192x192 的 png 图标
            URL iconURL = getClass().getResource("/icon.png");
            if (iconURL != null) {
                Image icon = new ImageIcon(iconURL).getImage();
                setIconImage(icon);
            }
        } catch (Exception e) {
            System.err.println("图标加载失败: " + e.getMessage());
        }

        // 窗口基础属性
        setTitle("MirrorGo " + getStr("文件实时同步", "File Mirror"));
        setSize(850, 680);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示

        // 主容器布局
        JPanel root = new JPanel(new BorderLayout(25, 25));
        root.setBorder(new EmptyBorder(30, 35, 30, 35));
        add(root);

        // --- 顶部栏 (标题 + 主题) ---
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("MirrorGo " + getStr("文件实时同步", "File Mirror"));
        title.setFont(new Font(FONT_FAMILY, Font.BOLD, 26));

        String[] themes = isZH ? new String[]{"跟随系统", "浅色模式", "深色模式"} : new String[]{"System Default", "Light Theme", "Dark Theme"};
        themeBox = new JComboBox<>(themes);
        themeBox.setPreferredSize(new Dimension(140, 35));
        themeBox.addActionListener(e -> applyTheme(themeBox.getSelectedIndex(), true));

        JPanel themeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        themeWrapper.add(new JLabel(getStr("主题:", "Theme:")));
        themeWrapper.add(themeBox);

        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(themeWrapper, BorderLayout.EAST);
        root.add(topPanel, BorderLayout.NORTH);

        // --- 中间区域 (表单 + 日志) ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 25));

        // 路径输入区域 (GridBagLayout 实现两行布局)
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 10, 0);

        srcField = createStyledField();
        addRow(formPanel, getStr("源文件路径", "Source Path"), srcField, gbc, 0);
        dstField = createStyledField();
        addRow(formPanel, getStr("目标文件路径", "Target Path"), dstField, gbc, 2);
        centerPanel.add(formPanel, BorderLayout.NORTH);

        // 日志显示区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(FONT_FAMILY, Font.PLAIN, 14));
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(new TitledBorder(new LineBorder(new Color(150, 150, 150, 60)), getStr(" 运行日志 ", " Logs ")));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        root.add(centerPanel, BorderLayout.CENTER);

        // --- 底部栏 (状态 + 按钮) ---
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel(getStr("● 系统就绪", "● Ready"));
        statusLabel.setForeground(Color.GRAY);

        monitorBtn = new JButton(getStr("开启实时同步", "Start Sync"));
        monitorBtn.setFont(new Font(FONT_FAMILY, Font.BOLD, 18));
        monitorBtn.setPreferredSize(new Dimension(220, 55));
        monitorBtn.setBackground(new Color(0, 120, 215));
        monitorBtn.setForeground(Color.WHITE);
        monitorBtn.addActionListener(e -> toggleMonitoring());

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(monitorBtn, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        loadConfig(); // 加载上次保存的配置
    }

    /**
     * 切换监控状态
     */
    private void toggleMonitoring() {
        if (monitoring) {
            stopMonitoring();
        } else {
            startSync();
        }
    }

    /**
     * 核心同步逻辑
     */
    private void startSync() {
        String s = srcField.getText();
        String d = dstField.getText();
        if (s.isEmpty() || d.isEmpty()) {
            return;
        }

        File srcFile = new File(s);
        File dstFile = new File(d);

        // --- 三级安全校验 ---
        if (!srcFile.getName().equalsIgnoreCase(dstFile.getName())) {
            String sExt = getFileExt(srcFile.getName());
            String dExt = getFileExt(dstFile.getName());

            if (sExt.equalsIgnoreCase(dExt)) {
                // 后缀相同但文件名不同，二次确认
                int opt = JOptionPane.showConfirmDialog(this,
                        getStr("文件名不一致，确定要同步吗？", "Names differ. Proceed?"),
                        getStr("确认", "Confirm"), JOptionPane.YES_NO_OPTION);
                if (opt != JOptionPane.YES_OPTION) {
                    return;
                }
            } else {
                // 后缀不同，强制拦截（防止误选导致覆盖错误文件）
                JOptionPane.showMessageDialog(this, getStr("后缀不匹配！", "Ext mismatch!"),
                        getStr("错误", "Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        monitoring = true;
        updateUI(true);

        // 启动后台监控线程
        monitorThread = new Thread(() -> {
            Path sp = Paths.get(s);
            Path dp = Paths.get(d);
            long lastMod = sp.toFile().lastModified();
            appendLog("SYSTEM", getStr("开始监听...", "Monitoring..."));

            while (monitoring && !Thread.currentThread().isInterrupted()) {
                try {
                    long currentMod = sp.toFile().lastModified();
                    // 检测到源文件修改时间变晚
                    if (currentMod > lastMod) {

                        // 1. 记录物理同步开始时间
                        long startNano = System.nanoTime();

                        // 2. 执行文件覆盖
                        Files.copy(sp, dp, StandardCopyOption.REPLACE_EXISTING);

                        // 3. 计算总耗时 (纳秒转毫秒)
                        long endNano = System.nanoTime();
                        double durationMs = (endNano - startNano) / 1_000_000.0;

                        lastMod = currentMod;
                        syncCount++;

                        // 计算文件大小
                        long bytes = Files.size(dp);
                        String sizeStr = (bytes >= 1024 * 1024)
                                ? String.format("%.2f MB", bytes / 1024.0 / 1024.0)
                                : String.format("%.2f KB", bytes / 1024.0);

                        // 输出精准日志
                        appendLog("SYNC", String.format(
                                getStr("完成 #%d | 大小: %s | 耗时: %.2f ms", "Done #%d | Size: %s | Cost: %.2f ms"),
                                syncCount, sizeStr, durationMs));
                    }
                    TimeUnit.SECONDS.sleep(1); // 轮询间隔 1 秒
                } catch (Exception ex) {
                    if (monitoring) {
                        appendLog("ERROR", ex.getMessage());
                    }
                }
            }
        });
        monitorThread.start();
    }

    /**
     * 停止监控
     */
    private void stopMonitoring() {
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        updateUI(false);
        appendLog("SYSTEM", getStr("同步已停止。", "Sync stopped."));
    }

    /**
     * 线程安全地更新界面状态
     */
    private void updateUI(boolean running) {
        SwingUtilities.invokeLater(() -> {
            monitorBtn.setText(running ? getStr("停止同步", "Stop Sync") : getStr("开启实时同步", "Start Sync"));
            monitorBtn.setBackground(running ? new Color(220, 53, 69) : new Color(0, 120, 215));
            statusLabel.setText(running ? getStr("● 监控中", "● Monitoring") : getStr("● 已停止", "● Stopped"));
            statusLabel.setForeground(running ? new Color(40, 167, 69) : Color.GRAY);
        });
    }

    /**
     * 添加带有时间戳的日志
     */
    private void appendLog(String tag, String msg) {
        String logTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] [%s] %s\n", logTime, tag, msg));
            logArea.setCaretPosition(logArea.getDocument().getLength()); // 自动滚到底部
        });
    }

    // --- 辅助工具方法 ---
    private String getFileExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot == -1) ? "" : name.substring(dot + 1);
    }

    /**
     * 向面板添加一行带标签、输入框和浏览按钮的组件
     */
    private void addRow(JPanel p, String labelText, JTextField field, GridBagConstraints gbc, int y) {
        gbc.gridy = y;
        gbc.gridx = 0;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 15));
        p.add(lbl, gbc);

        gbc.gridy = y + 1;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        p.add(field, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(10, 15, 10, 0);
        JButton btn = new JButton(getStr("浏览", "Browse"));
        btn.setPreferredSize(new Dimension(110, 45));
        btn.addActionListener(e -> openFilePicker(field));
        p.add(btn, gbc);
        gbc.insets = new Insets(10, 0, 10, 0);
    }

    private JTextField createStyledField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setPreferredSize(new Dimension(0, 45));
        return f;
    }

    private void openFilePicker(JTextField field) {
        FileDialog fd = new FileDialog(this, getStr("选择文件", "Select File"), FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() != null) {
            field.setText(fd.getDirectory() + fd.getFile());
            saveConfig();
        }
    }

    /**
     * 应用 UI 主题 (FlatLaf)
     */
    private void applyTheme(int index, boolean save) {
        FlatAnimatedLafChange.showSnapshot();
        try {
            if (index == 1) {
                UIManager.setLookAndFeel(new FlatLightLaf()); 
            }else if (index == 2) {
                UIManager.setLookAndFeel(new FlatDarkLaf()); 
            }else {
                UIManager.setLookAndFeel(isWindowsDark() ? new FlatDarkLaf() : new FlatLightLaf());
            }

            SwingUtilities.updateComponentTreeUI(this);
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
            if (save) {
                saveConfig();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 检测 Windows 是否处于深色模式
     */
    private boolean isWindowsDark() {
        try {
            Process p = Runtime.getRuntime().exec("reg query \"HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) {
                    return line.trim().endsWith("0");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // --- 配置持久化 ---
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
            int t = node.path("theme").asInt(0);
            themeBox.setSelectedIndex(t);
            applyTheme(t, false);
        } catch (Exception e) {
            applyTheme(0, false);
        }
    }

    private void saveConfig() {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("src", srcField.getText());
            node.put("dst", dstField.getText());
            node.put("theme", themeBox.getSelectedIndex());
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_PATH), node);
        } catch (Exception ignored) {
        }
    }

    private void ensureConfigDirExists() {
        File dir = new File(APP_DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private String getStr(String zh, String en) {
        return isZH ? zh : en;
    }

    /**
     * 入口函数
     */
    public static void main(String[] args) {
        // 禁止 Swing 擦除背景（优化 FlatLaf 主题切换动画效果）
        System.setProperty("sun.awt.noerasebackground", "true");

        // 初始化外观
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 15); // 设置按钮圆角
        UIManager.put("Component.arc", 15); // 设置组件圆角

        // 启动 GUI
        SwingUtilities.invokeLater(() -> new MirrorGo().setVisible(true));
    }
}
