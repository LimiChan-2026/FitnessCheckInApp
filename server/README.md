# 健身打卡系统 —— 服务端

基于 **Javalin 6 + SQLite** 的轻量级 REST API 服务端，为 Android 客户端提供用户认证、
打卡数据上云、运动排行榜、好友动态、点赞与关注等社交功能。

## 一、快速启动

### 方式一：脚本启动（推荐，双击运行）

```
server/start-server.bat
```

脚本会自动：建立 `X:` 盘符映射（绕过中文路径在 JDK 工具链下的编码问题）→ 编译 → 启动服务端。

### 方式二：命令行启动

```bash
# Windows（项目根目录下）
subst X: "项目绝对路径"        # 中文路径必须先映射，Gradle 测试/运行 JVM 依赖纯 ASCII 路径
gradlew :server:run

# 首次编译产物已就绪时，也可以直接用 java 启动
gradlew :server:jar
java -cp server/build/libs/server.jar;<依赖jar> fitness.server.Main
```

- 默认端口：**8080**（可传参覆盖：`gradlew :server:run --args="9090"`）
- 数据库文件：`server/data/fitness_server.db`（首次启动自动建表）

## 二、App 客户端如何连接

| 场景 | 服务器地址 |
| --- | --- |
| Android 模拟器 | `http://10.0.2.2:8080`（默认值，10.0.2.2 即宿主机） |
| 真机（同一 WiFi） | `http://电脑局域网IP:8080`（如 `http://192.168.1.100:8080`） |

> 提示：真机连不上时检查 Windows 防火墙是否放行 8080 端口。

## 三、REST API 一览

统一约定：`code == 0` 表示成功；错误时返回对应 HTTP 状态码 + `{code, message}`。

### 用户
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/register` | 注册，body: `{username, password, nickname}` |
| POST | `/api/login` | 登录，body: `{username, password}`，成功返回用户信息 |
| POST | `/api/change-password` | 修改密码，body: `{username, newPassword}` |

密码以 **PBKDF2 加盐哈希** 存储（`salt$hash` 格式），数据库无明文。

### 打卡记录
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/records?username=xx` | 打卡上云，body: `{sportType, duration, calorie, createTime, remark}` |
| DELETE | `/api/records/{id}?username=xx` | 删除单条云端记录 |
| DELETE | `/api/records?username=xx` | 清空该用户云端记录 |

### 社交
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/leaderboard?days=7&limit=20` | 排行榜（days=0 为总榜），按时长降序 |
| GET | `/api/feed?username=xx` | 好友动态流（自己 + 关注的人） |
| POST | `/api/records/{id}/like?username=xx` | 点赞/取消（幂等切换） |
| POST | `/api/follows?username=xx` | 关注/取关，body: `{followee}` |
| GET | `/api/users/search?keyword=x&username=xx` | 用户搜索（排除自己） |
| GET | `/api/follows/my?username=xx` | 我的关注/粉丝列表 |
| GET | `/api/ping` | 连通性测试 |

## 四、测试

```bash
# 中文项目路径下，必须先建立 X: 盘符映射，再通过 X: 路径运行（全程 ASCII 路径）：
subst X: "项目绝对路径"
cd /d X:\server
X:\gradlew.bat :server:test
```

`FitnessApiTest` 包含 11 个集成测试用例，通过真实 HTTP 请求覆盖
「注册 → 登录 → 打卡上云 → 排行榜 → 点赞 → 关注 → 动态流 → 删除/清空」全链路。

> 原理：Windows 中文系统（GBK 代码页）下，JDK 测试进程读取 Gradle 写入的
> UTF-8 @argfile classpath 时中文路径会乱码（JDK-8272597），导致
> ClassNotFoundException。通过 X: 盘符路径访问项目即可全程避开中文路径。
> 注意：直接在 D:\ 原路径下跑 `:server:test` 会报 ClassNotFoundException，属正常现象。

## 五、技术选型

| 组件 | 选择 | 理由 |
| --- | --- | --- |
| Web 框架 | Javalin 6.7 | 轻量（无 Spring 全家桶），API 直观，适合课程演示与讲解 |
| 数据库 | SQLite（sqlite-jdbc） | 零安装、单文件，与客户端本地存储技术同源 |
| JSON | Jackson（Javalin 内置） | 工业标准序列化 |
| 密码 | PBKDF2WithHmacSHA256 | JDK 自带，无需额外依赖，安全强度足够 |
