# MirrorGo 🚀

**MirrorGo** 是一款专为 Windows 环境打造的轻量级、高精度文件实时镜像工具。  
它能够在检测到文件改动的瞬间，以极低延迟将数据同步至目标路径，适合用于同步构建产物、配置文件等高频变动内容。

---

## ✨ 核心特性 | Key Features

- **⚡ 实时即时同步 (Real-time Monitoring)**  
  基于高精度文件监听机制，在文件发生改动的瞬间触发同步，并使用 `System.nanoTime()` 记录执行耗时。  
  *Instantly detects file changes and synchronizes them with nanosecond-level timing.*

- **🎨 现代化 UI (Modern UI)**  
  使用 **FlatLaf** 作为界面引擎，自动跟随系统切换浅色 / 深色主题，提供原生级视觉体验。  
  *Powered by FlatLaf with system-aware Light/Dark themes.*

- **🛡️ Windows 原生优化 (Windows Optimized)**  
  配置文件自动持久化至 `%AppData%`，避免权限问题，实现真正的即开即用。  
  *Configuration is persisted in %AppData% to avoid permission issues.*

- **🌍 智能双语支持 (Bilingual Support)**  
  根据系统区域设置，在中文与英文界面之间自动切换。  
  *Automatically switches between English and Chinese based on system locale.*

---

## 🛠️ 技术栈 | Tech Stack

- **Language:** Java 17
- **UI:** [FlatLaf](https://github.com/JFormDesigner/FlatLaf)
- **JSON:** Jackson Databind
- **Build Tool:** Maven

---

## ▶️ 使用方式 | Usage

构建完成后，直接运行生成的可执行 JAR 文件：

```powershell
java -jar MirrorGo.jar
```

---

### 📝 运行日志说明 | Logs

程序运行日志会精确记录每一次操作细节，帮助你监控同步性能：

| 日志标签         | 说明                                                           |
|:-------------|:-------------------------------------------------------------|
| **[SYSTEM]** | 显示监听开启、停止及配置加载状态。                                            |
| **[SYNC]**   | 记录同步成功信息，包括 **同步序号**、**文件大小** 及 **复制耗时**（如 `Cost: 0.45 ms`）。 |
| **[ERROR]**  | 捕捉并提示路径失效或 IO 异常。                                            |

---

### ⚖️ 开源协议 | License

本项目采用 [MIT License](LICENSE) 开源。

---

**Built with ❤️ for developers who value precision.**