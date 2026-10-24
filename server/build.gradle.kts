plugins {
    java
    application
}


// 服务端子模块：基于 Javalin 的轻量级 REST API（移动互联课程设计）
// 独立于 Android 客户端，可单独用 gradlew :server:run 启动
//
// 注意：不要把 buildDirectory 重定向到项目外（会让 Android Studio 的索引错乱）。
// 中文路径下运行 :server:test 的正确方式：先 subst X: 项目根，再通过 X:\server
// 路径调用 gradlew（全程 ASCII 路径，绕开 JDK argfile 编码问题），详见 README。

java {
    toolchain {
        // 本机 JDK 17 路径已在根 gradle.properties 中声明，Gradle 自动探测
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    // Javalin 6 需要 Jackson 做 JSON 序列化
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    // 日志实现（消除 SLF4J no-provider 警告，并输出启动日志）
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.javalin:javalin-testtools:6.7.0")
    testImplementation("org.json:json:20250107") // 测试侧独立解析 JSON 响应
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "fitness.server.Main"
}

tasks.test {
    useJUnitPlatform()
    // 测试使用独立的临时数据库文件，避免污染开发数据
    systemProperty("server.db.path", layout.buildDirectory.dir("test-db/fitness_server_test.db").get().asFile.absolutePath)
    // Windows 中文系统（代码页936/GBK）：Gradle 以 UTF-8 写 @argfile 里的 classpath，
    // 测试 JVM 按系统默认编码读取会乱码，导致 ClassNotFoundException。
    // 显式让测试 JVM 按 UTF-8 解码 argfile，路径即可正确解析。
    jvmArgs("-Dfile.encoding=UTF-8")
}

// 服务端运行在 JDK 17 + 中文 Windows 上，JVM 默认字符集是 GBK。
// Javalin 把 JSON 响应字符串转字节时用默认编码，会把 UTF-8 的中文按 GBK
// 重新编码，客户端解码后变成乱码。这里强制运行 JVM 使用 UTF-8。
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "fitness.server.Main"
    }
}
