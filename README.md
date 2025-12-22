# MirrorGo 🚀

**MirrorGo** 是一款专为 Windows 环境打造的轻量级、高精度文件实时镜像工具。它能够在检测到文件改动的瞬间，以极低的响应延迟将数据同步至目标路径，是开发者同步构建产物或配置文件的理想伴侣。

---

### ✨ 核心特性 | Key Features

* **⚡ 实时即时同步 (Real-time Monitoring)**
    利用高精度监听技术，在文件发生改动的瞬间即刻捕捉，并以微秒级精度（$nanoTime$ 级）追踪并执行无缝同步。
    *Instantly detects file changes and synchronizes them to the target path with microsecond-level latency tracking.*

* **🎨 自适应现代 UI (Modern UI)**
    由 **FlatLaf** 引擎驱动的精美界面，支持随系统自动切换深/浅色主题，提供原生级别的视觉体验。
    *Integrated with FlatLaf to support system-aware Light/Dark themes for a sleek, native look.*

* **🛡️ Windows 原生优化 (Windows Optimized)**
    配置自动持久化于 `%AppData%` 目录，完美规避路径权限困扰，确保在各种系统环境下都能“即开即用”。
    *Automatic configuration persistence in %AppData% to bypass permission issues and ensure a zero-config experience.*

* **🌍 智能双语支持 (Bilingual Support)**
    内置智能本地化引擎，根据系统区域设置自动在中文与英文间无感切换。
    *Automatically toggles between English and Chinese based on system locale.*

---

### 🛠️ 技术栈 | Tech Stack

* **Core:** Java 17
* **UI Framework:** [FlatLaf](https://github.com/JFormDesigner/FlatLaf) (Modern Look and Feel)
* **JSON Processor:** Jackson Databind
* **Build Tool:** Maven

---

### 🚀 快速开始 | Quick Start

#### 环境要求
* JRE / JDK 17 或更高版本。
* Maven (仅用于自行编译)。

#### 编译与运行
1.  **克隆仓库**：
    ```powershell
    git clone [https://github.com/your-username/MirrorGo.git](https://github.com/your-username/MirrorGo.git)
    cd MirrorGo
    ```
2.  **构建项目**：
    ```powershell
    mvn clean package
    ```
3.  **运行程序**：
    直接运行 `target` 目录下的可执行 JAR：
    ```powershell
    java -jar target/MirrorGo.jar
    ```

---

### 📝 运行日志说明 | Logs

程序运行日志会精确记录每一次操作细节，帮助你监控同步性能：

| 日志标签 | 说明 |
| :--- | :--- |
| **[SYSTEM]** | 显示监听开启、停止及配置加载状态。 |
| **[SYNC]** | 记录同步成功信息，包括 **同步序号**、**文件大小** 及 **复制耗时**（如 `Cost: 0.45 ms`）。 |
| **[ERROR]** | 捕捉并提示路径失效或 IO 异常。 |



---

### ⚖️ 开源协议 | License

本项目采用 [MIT License](LICENSE) 开源。

---

**Built with ❤️ for developers who value precision.**