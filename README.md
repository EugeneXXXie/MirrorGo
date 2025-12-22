# FileSwitcher 🚀

[**English**](#english) | [**中文说明**](#中文说明)

---

## English

A lightweight, real-time file synchronization assistant for Windows. It monitors file changes and syncs them instantly with a modern UI.

### ✨ Features
* **Real-time Sync**: Monitors file timestamps and synchronizes changes every second.
* **Modern UI**: Powered by FlatLaf with support for Light/Dark modes and system theme auto-detection.
* **Native Experience**: Uses Windows native file dialogs for better performance and familiarity.
* **Safe Persistence**: Saves configurations in `%AppData%\FileSwitcher` to avoid permission issues.
* **Multilingual**: Automatically switches between English and Chinese based on system locale.

### 🚀 Quick Start
1. Download `FileSwitcher.exe` from the [Releases](https://github.com/YourUsername/FileSwitcher/releases) page.
2. Select your **Source File** and **Target File**.
3. Click **Start Sync** to begin monitoring.

---

## 中文说明

一个为 Windows 设计的轻量级实时文件同步助手。它可以监控文件变动并即刻同步，拥有现代化的用户界面。

### ✨ 功能亮点
* **实时同步**：基于时间戳监控文件，秒级触发自动同步。
* **现代化 UI**：采用 FlatLaf 引擎，支持深色/浅色模式及系统主题自动跟随。
* **原生体验**：调用 Windows 原生文件选择对话框，运行流畅不卡顿。
* **配置持久化**：配置文件存放在 `%AppData%\FileSwitcher`，规避系统盘写入权限限制。
* **多语言支持**：根据系统语言自动切换中英文界面。

### 🚀 快速开始
1. 从 [Releases](https://github.com/YourUsername/FileSwitcher/releases) 页面下载 `FileSwitcher.exe`。
2. 分别选择你的 **源文件** 和 **目标文件** 路径。
3. 点击 **开启实时同步** 即可开始工作。

---

## 🛠️ Build & Development | 编译与开发

```bash
# Clone the repository | 克隆仓库
git clone [https://github.com/YourUsername/FileSwitcher.git](https://github.com/YourUsername/FileSwitcher.git)

# Build with Maven | 使用 Maven 编译
mvn clean package

# Run the JAR | 运行 JAR
java -jar target/FileSwitcher.jar