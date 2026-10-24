# 健身打卡系统（FitnessCheckInApp）

移动互联课程设计项目：Android 客户端 + Javalin REST 服务端，实现运动打卡、数据统计、
好友动态、排行榜、点赞与关注等社交功能。

## 项目结构

```
FitnessCheckInApp/
├── app/       Android 客户端（Java）
├── server/    REST API 服务端（Javalin 6 + SQLite）
└── server/README.md   API 文档与部署说明
```

## 环境要求

| 组件 | 要求 |
| --- | --- |
| Android Studio | 2024.2 及以上（含 AGP 9 支持） |
| JDK | 17（服务端 toolchain 需要；Android 端 JDK 11+） |
| Android SDK | Platform 36（客户端 compileSdk） |
| 网络 | 首次构建需访问 Maven Central 与 Google Maven |

## 快速开始

1. **启动服务端**：双击 `server/start-server.bat`（会自动建档、初始化数据库并监听 8080 端口）；
   看到 `Fitness server started: http://0.0.0.0:8080` 即成功，窗口保持开启。
2. **打开项目**：Android Studio → Open → 选择本目录，等待 Gradle Sync 完成。
3. **运行 App**：启动模拟器（建议 API 33+），选中 app 配置点 ▶。

## 测试账号（数据库已内置演示数据）

| 账号 | 密码 |
| --- | --- |
| `xiaoming` | `abc123` |
| `xiaohong` | `xyz789` |
| `wangyichen` / `liuxinyi` / `zhanghao` / `chenyutong` / `lijiayang` / `sunqian` | `123456` |

## App 连接服务端的地址

App 登录页底部有「服务器」设置入口，点击可修改并持久化：

- **模拟器**：`http://10.0.2.2:8080`（默认值，10.0.2.2 即宿主机）
- **真机（同一 WiFi）**：`http://<电脑局域网IP>:8080`，并确保 Windows 防火墙放行 8080：
  ```bat
  netsh advfirewall firewall add rule name="FitnessServer8080" dir=in action=allow protocol=TCP localport=8080
  ```
- **真机（USB，无需同网段）**：
  ```bat
  adb reverse tcp:8080 tcp:8080
  ```
  然后把 App 服务器地址改为 `http://127.0.0.1:8080`（拔线/重启后需重新执行该命令）。

## 中文路径注意事项（Windows）

若项目位于含中文的路径下（如 `D:\实习\课程设计\`）：

- 构建与运行 App 不受影响；
- **运行服务端单元测试**需要先建立盘符映射再通过映射路径执行（详见 `server/README.md` 第四节）：
  ```bat
  subst X: "项目绝对路径"
  cd /d X:\server
  X:\gradlew.bat :server:test
  ```

## 更多文档

- 服务端 API 一览、部署细节、测试说明：见 [`server/README.md`](server/README.md)
