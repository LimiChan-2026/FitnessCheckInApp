# 健身打卡 App (FitnessCheckInApp)

> 一款「移动端 + 服务端」的健身打卡社交系统：本地记录每一次训练，联网后与好友比拼排行榜、互相点赞关注，离线时也能正常打卡。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Language-Java-blue)](https://www.java.com/)
[![Server](https://img.shields.io/badge/Server-Javalin%206%20%2B%20SQLite-orange)](https://javalin.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-brightgreen)](https://developer.android.com/about/versions/android-7.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen)](https://developer.android.com/about/versions)

---

## 📖 项目简介

**健身打卡 App** 采用 **C/S 架构**：

- **Android 客户端**（`app/`）：账号、打卡、统计、个人中心、社区社交；
- **REST 服务端**（`server/`，Javalin 6 + SQLite）：用户认证、打卡上云、排行榜、好友动态、点赞与关注。

设计原则是「**离线可用、在线社交**」：打卡数据先写本地 SQLite 并立即可用；
网络可用时自动同步到服务端，参与好友动态与排行榜；服务器不可达时自动降级为离线模式，
不影响打卡与统计。

应用围绕「**打卡 → 统计 → 激励 → 分享**」四个环节设计：

- **打卡**：选择运动类型、计时并记录每次训练（时长、卡路里、备注）；
- **统计**：首页与数据页自动汇总今日概览、累计数据、连续打卡天数与历史记录；
- **激励**：勋章系统 + 打卡成功庆祝动画，增强坚持动力；
- **分享**：社区动态、点赞（爱心动画）、关注好友、周榜/总榜排行。

---

## ✨ 功能特性

- **🔐 账号体系**
  - 注册 / 登录 / 找回密码，**在线认证 + 离线回退**（服务器不可达时校验本地缓存账号）
  - 密码 **PBKDF2 加盐哈希**存储（客户端与服务端均不存明文）
  - 多用户数据隔离、昵称与头像修改

- **🏃 训练打卡**
  - 6 种运动类型：跑步 / 骑行 / 健身 / 瑜伽 / 跳绳 / 游泳
  - 计时器：开始 / 暂停 / 继续 / 重置 / 结束，可设目标时长
  - 自动估算卡路里，支持运动备注；保存成功播放 Lottie 庆祝动画
  - 打卡记录**本地保存后异步上云**，弱网/断网不丢失

- **📊 统计与激励**
  - 首页：今日卡路里 / 运动分钟 / 打卡次数 / 真实步数（硬件计步传感器）、连续打卡天数
  - 数据页：全部 / 今日 / 最近 7 天 / 指定日期筛选，汇总与删除
  - 勋章系统：新手 / 坚持 / 达人 / 活力

- **👥 社区社交（联网）**
  - **排行榜**：本周榜与总榜（不同时间窗聚合）
  - **好友动态流**：关注好友的打卡动态，下拉刷新 + 15 秒自动轮询
  - **点赞**：Lottie 爱心爆炸动画，幂等切换
  - **关注管理**：「发现好友」按昵称/用户名搜索并关注；取消关注后其动态即时消失
  - 登录页 Lottie 跑步动画、注册成功打勾动画

- **🔒 隐私与安全**
  - 本地数据默认不上传；仅打卡与账号信息在联网时同步
  - 参数化 SQL 防注入；密码加盐哈希

---

## 🛠 技术栈

| 分类 | 技术 |
|------|------|
| 客户端 | Java 11、AndroidX、Material Components、RecyclerView、SwipeRefreshLayout |
| 客户端网络 | `HttpURLConnection` + `org.json`（零第三方网络依赖） |
| 动画 | Lottie 6.6.2（Airbnb 开源矢量动画引擎） |
| 服务端 | Java 17、**Javalin 6.7**（HTTP/REST）、Jackson（JSON）、sqlite-jdbc |
| 数据存储 | SQLite（客户端 `fitness_check_in.db` / 服务端 `server/data/fitness_server.db`） |
| 构建工具 | Gradle 9.2.1（Kotlin DSL），AGP 9.0 |
| 协议 | HTTP + REST + JSON，统一 `{code, message}` 响应约定 |
| 版本 | `minSdk 24` / `targetSdk 36`，包名 `com.example.fitnesscheckinapp` |

---

## 📂 项目结构

```
FitnessCheckInApp/
├── app/                                    # Android 客户端
│   └── src/main/
│       ├── java/com/example/fitnesscheckinapp/
│       │   ├── LoginActivity / RegisterActivity / ForgetPasswordActivity
│       │   ├── MainActivity                # 首页概览（步数传感器、连续打卡）
│       │   ├── CheckInActivity             # 训练打卡（计时 + 上云同步）
│       │   ├── DataActivity                # 历史数据与统计
│       │   ├── MineActivity                # 个人中心 / 勋章
│       │   ├── SocialActivity              # 社区：排行榜 / 动态 / 点赞 / 关注
│       │   ├── ApiClient                   # 网络层（REST 封装）
│       │   ├── DBHelper / SportRecord / RecordAdapter / FeedEntry / FeedAdapter
│       │   ├── PasswordUtil / Bg / Nav / AnimDialog
│       │   └── assets/animations/*.json    # Lottie 动画素材
├── server/                                 # REST 服务端（详见 server/README.md）
│   ├── src/main/java/fitness/server/
│   │   ├── Main.java                       # 入口与路由
│   │   ├── FitnessService.java             # 业务逻辑
│   │   ├── Database.java                   # SQLite 数据层
│   │   └── PasswordUtil.java               # PBKDF2 哈希
│   ├── src/test/java/.../FitnessApiTest.java  # 11 个 API 集成测试
│   ├── data/fitness_server.db              # 数据库（内置演示种子数据）
│   └── start-server.bat                    # 一键启动脚本
├── README.md / LICENSE
└── build.gradle.kts / settings.gradle.kts
```

**服务端数据表**：`users`（用户与密码哈希）、`sport_records`（打卡记录）、
`likes`（点赞）、`follows`（关注关系）。

---

## 🚀 快速开始

### 环境要求

| 组件 | 要求 |
| --- | --- |
| Android Studio | 2024.2 及以上（支持 AGP 9） |
| JDK | 17（服务端 toolchain 需要） |
| Android SDK | Platform 36 |
| 网络 | 首次构建需访问 Maven Central 与 Google Maven |

### 第 1 步：启动服务端

双击 `server/start-server.bat`（或命令行执行 `gradlew :server:run`），
看到以下输出即成功，**窗口保持开启**：

```
Fitness server started: http://0.0.0.0:8080
DB file : data/fitness_server.db
Emulator URL : http://10.0.2.2:8080
```

### 第 2 步：打开并运行客户端

1. Android Studio → **Open** → 选择项目根目录，等待 Gradle Sync 完成；
2. 启动模拟器（建议 API 33+）或连接真机；
3. 点击 **Run ▶**（或 `./gradlew installDebug`）。

### 第 3 步：登录体验

数据库已内置演示数据（8 位用户、27 条打卡、关注与点赞关系）：

| 账号 | 密码 |
| --- | --- |
| `xiaoming` | `abc123` |
| `xiaohong` | `xyz789` |
| `wangyichen` / `liuxinyi` / `zhanghao` / `chenyutong` / `lijiayang` / `sunqian` | `123456` |

---

## 📡 App 连接服务端的三种方式

登录页底部有「**服务器：…**」设置入口，点击即可修改并持久化：

| 场景 | 服务器地址 | 说明 |
| --- | --- | --- |
| **模拟器** | `http://10.0.2.2:8080` | 默认值，`10.0.2.2` 即宿主机 |
| **真机（同一 WiFi）** | `http://<电脑局域网IP>:8080` | 手机与电脑连同一路由器；放行防火墙：`netsh advfirewall firewall add rule name="FitnessServer8080" dir=in action=allow protocol=TCP localport=8080` |
| **真机（USB 调试）** | `http://127.0.0.1:8080` | 执行 `adb reverse tcp:8080 tcp:8080`，无需同网段；拔线/重启后需重新执行 |

---

## 🧪 运行服务端测试

```bat
:: 中文路径下需先建立盘符映射（详见 server/README.md）
subst X: "项目绝对路径"
cd /d X:\server
X:\gradlew.bat :server:test
```

覆盖注册登录、密码哈希、打卡、排行榜、点赞幂等、关注、动态流、搜索等 11 个集成测试。

---

## ⚠️ Windows 中文路径说明

若项目位于含中文的路径（如 `D:\实习\课程设计\`）：

- 构建与运行 App 不受影响；
- 运行服务端单元测试需按上文通过 `X:` 盘符路径执行（JDK argfile 中文编码限制，JDK-8272597）。

## ❓ 常见问题

**Q：Gradle Sync 报找不到 JDK 17？**
`gradle.properties` 中的 `org.gradle.java.installations.paths` 指向原作者机器的路径。
请安装 JDK 17 并改为你的本机路径，或删除该行让 Gradle 联网自动下载。

**Q：登录提示「账号或密码错误（离线模式）」？**
说明 App 连不上服务端、回退到本地校验，而本机没有该账号缓存。请检查服务端是否启动、
服务器地址是否正确（模拟器 `10.0.2.2` / 真机见上表）。

**Q：服务端测试报 ClassNotFoundException？**
中文路径编码问题，按「Windows 中文路径说明」处理。

---

## 📄 License

本项目基于 [MIT License](./LICENSE) 开源。
