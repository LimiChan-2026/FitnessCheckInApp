# 健身打卡 App (FitnessCheckInApp)

> 一款轻量、纯本地的 Android 运动打卡工具：记录每一次训练，自动统计时长与卡路里，帮助你坚持运动习惯。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Language-Java-blue)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-brightgreen)](https://developer.android.com/about/versions/android-7.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen)](https://developer.android.com/about/versions)

---

## 📖 项目简介

**健身打卡 App** 是一款面向个人运动记录场景的 Android 应用。它不依赖任何云端服务，所有数据（账号、训练记录、头像）均保存在**设备本地 SQLite 数据库**中，安装即用、离线可用、隐私可控。

应用围绕"**打卡 → 统计 → 激励**"三个核心环节设计：

- **打卡**：选择运动类型、计时并记录每次训练（时长、卡路里、备注）。
- **统计**：首页与数据页自动汇总今日概览、累计数据、连续打卡天数与历史记录。
- **激励**：内置勋章系统，根据训练积累解锁"新手 / 坚持 / 达人 / 活力"等成就，增强坚持动力。

---

## ✨ 功能特性

- **🔐 本地账号系统**
  - 注册 / 登录 / 找回密码
  - 多用户数据隔离（不同账号的数据互不干扰）
  - 支持修改昵称与头像

- **🏃 训练打卡**
  - 支持 6 种运动类型：跑步、骑行、健身、瑜伽、跳绳、游泳
  - 内置计时器：开始 / 暂停 / 继续 / 重置 / 结束
  - 可设置目标时长，未达标时给出提醒
  - 自动估算卡路里消耗，并支持添加运动备注

- **📊 首页概览**
  - 今日概览：卡路里、运动分钟、打卡次数、步数
  - 累计概览：累计打卡次数、累计运动分钟、连续打卡天数
  - 显示最近一次运动记录与鼓励语

- **📅 历史数据**
  - 多维度筛选：全部记录 / 今日记录 / 最近 7 天 / 指定日期
  - 自动汇总：总次数、总分钟、总消耗卡路里
  - 支持单条删除与一键清空

- **🏅 勋章系统**
  - 新手勋章、坚持勋章、达人勋章、活力勋章
  - 达标后自动解锁，未达成显示未解锁状态

- **🔒 隐私友好**
  - 数据完全本地存储，不上传任何服务器
  - 仅在修改头像时申请读取图片权限（`READ_MEDIA_IMAGES`）

---

## 🛠 技术栈

| 分类 | 技术 |
|------|------|
| 开发语言 | Java 11 |
| 构建工具 | Gradle 9.2.1（Kotlin DSL，`build.gradle.kts`） |
| UI 框架 | AndroidX AppCompat、Material Design Components、ConstraintLayout、RecyclerView |
| 数据存储 | SQLite（`SQLiteOpenHelper`，数据库 `fitness_check_in.db`，当前版本 4） |
| 最低 / 目标版本 | `minSdk 24` / `targetSdk 36`（兼容 Android 7.0 及以上） |
| 包名 | `com.example.fitnesscheckinapp` |

---

## 📂 项目结构

```
FitnessCheckInApp/
├── app/
│   └── src/main/
│       ├── java/com/example/fitnesscheckinapp/
│       │   ├── LoginActivity.java          # 登录
│       │   ├── RegisterActivity.java        # 注册
│       │   ├── ForgetPasswordActivity.java  # 找回密码
│       │   ├── MainActivity.java            # 首页概览
│       │   ├── CheckInActivity.java         # 训练打卡（计时 + 卡路里计算）
│       │   ├── DataActivity.java            # 历史数据与统计
│       │   ├── MineActivity.java            # 个人中心 / 勋章
│       │   ├── DBHelper.java                # SQLite 数据库（建表 + 版本升级）
│       │   ├── SportRecord.java             # 运动记录实体类
│       │   └── RecordAdapter.java           # 记录列表适配器
│       ├── res/                             # 布局、字符串、图标、主题等资源
│       └── AndroidManifest.xml
├── gradle/                                 # Gradle Wrapper
├── build.gradle.kts                        # 根构建脚本
└── settings.gradle.kts
```

**数据模型（SQLite）**

- `sport_record` 表：运动记录
  - `id`（主键）、`sport_type`（运动类型）、`duration`（时长，秒）、`calorie`（卡路里）
  - `create_time`（打卡时间）、`username`（所属用户）、`remark`（备注）
- `user_info` 表：用户信息
  - `id`（主键）、`username`（唯一）、`password`、`nickname`、`avatar_path`、`create_time`

---

## 🚀 安装与构建

### 方式一：使用 Android Studio（推荐，用于开发调试）

1. 克隆仓库：
   ```bash
   git clone https://github.com/LimiChan-2026/FitnessCheckInApp.git
   ```
2. 使用 **Android Studio** 打开项目根目录。
3. 等待 Gradle 同步完成（首次同步会自动下载 Gradle 9.2.1）。
4. 连接 Android 设备或启动模拟器（Android 7.0+）。
5. 点击 **Run ▶** 或执行：
   ```bash
   ./gradlew installDebug
   ```

### 方式二：直接安装 APK

> 仓库暂不提供预编译 APK。可自行在 Android Studio 中执行 `Build → Build Bundle(s) / APK(s) → Build APK(s)` 生成安装包后侧载到设备。

---

## 📱 使用说明

1. **首次启动**：进入注册页创建本地账号，随后登录。
2. **开始打卡**：底部导航进入"打卡"，选择运动类型 → 设定目标时长 → 点击"开始"计时 → 训练结束后"保存记录"。
3. **查看统计**：首页查看今日 / 累计概览；"数据"页按日期筛选查看历史记录与汇总。
4. **个人中心**："我的"页可修改昵称、头像，查看累计数据与已解锁勋章，或退出登录。

> **说明**：卡路里采用简化估算模型（每运动约 2 秒计 1 千卡），用于参考与激励，非医学精确值。

---

## 🏅 勋章解锁条件

| 勋章 | 说明 |
|------|------|
| 新手勋章 | 完成首次训练打卡 |
| 坚持勋章 | 保持连续打卡天数 |
| 达人勋章 | 累计训练达到一定量级 |
| 活力勋章 | 累计运动分钟 / 卡路里达到一定量级 |

> 具体阈值以 `MineActivity` 中的实现为准。

---

## 📸 应用截图

> 截图待补充。建议放置在 `docs/screenshots/` 目录下，并在此处引用，例如：
>
> ```markdown
> ![首页](docs/screenshots/home.png)
> ![打卡](docs/screenshots/check_in.png)
> ```

---

## 🗺 后续规划（TODO）

- [ ] 补充应用截图与演示 GIF
- [ ] 增加周 / 月趋势图表
- [ ] 支持数据导出（CSV / JSON）
- [ ] 增加训练计划与目标设定
- [ ] 提供预编译 Release APK

欢迎通过 Issue 与 Pull Request 参与贡献。

---

## 📄 许可证

本项目基于 [MIT 许可证](./LICENSE) 开源。

---

## 👤 作者

**LimiChan**

- GitHub：[@LimiChan-2026](https://github.com/LimiChan-2026)
- 仓库地址：https://github.com/LimiChan-2026/FitnessCheckInApp
