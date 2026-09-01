import io.izzel.taboolib.gradle.Bukkit
import io.izzel.taboolib.gradle.BukkitUtil
import io.izzel.taboolib.gradle.Kether
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    java
    kotlin("jvm") version "2.0.21"
    id("io.izzel.taboolib") version "2.0.38"
}

group = "com.cj.customquest"
version = "1.6.8"

// TabooLib 版本（6.2.4 维护线，修复了 6.2.2 的类加载/Gson 序列化冲突）
val TABOOLIB_VERSION = "6.2.4-c90a237"
val SQLITE_JDBC_VERSION = "3.53.2.1"
val JUNIT_VERSION = "6.0.3"

repositories {
    mavenCentral()
    // Paper 1.21.1 API（服务器请使用 Paper 1.21.1+，如需其他 1.21.x 版本请修改下面版本号）
    maven("https://repo.papermc.io/repository/maven-public/")
    // MythicMobs
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.13.0")
    // Citizens 官方仓库的旧快照 JAR 已返回 403，使用仓库内 API JAR 保证 CI 可复现构建。
    compileOnly(files("libs/CitizensAPI-2.0.36-SNAPSHOT.jar"))
    // PlaceholderAPI 已下载到 libs/ 目录（若不需要 PAPI 支持可删除此行）
    compileOnly(files("libs/PlaceholderAPI-2.12.3.jar"))

    testImplementation("org.junit.jupiter:junit-jupiter:$JUNIT_VERSION")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$JUNIT_VERSION")
    testImplementation("org.xerial:sqlite-jdbc:$SQLITE_JDBC_VERSION")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

taboolib {
    env {
        // Bukkit 平台 + Bukkit 工具 + Kether 脚本引擎
        // （BukkitUtil 提供 bukit-util / bukit-xseries，NMS 记分板等运行时依赖它）
        install(Bukkit, BukkitUtil, Kether)
    }
    version {
        taboolib = TABOOLIB_VERSION
    }
    description {
        name = "CustomQuest"
        desc("MythicMobs & Citizens quest plugin with Kether scripting.")
        bukkitApi("1.21")
        contributors {
            name("CJ")
        }
        dependencies {
            name("PlaceholderAPI")
            name("MythicMobs").optional(true)
            name("Citizens").optional(true)
            name("NeigeItems").optional(true)
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<ProcessResources> {
    // 不打包 plugin.yml（由 TabooLib 构建任务自动生成）
    exclude("plugin.yml")
}

tasks.test {
    useJUnitPlatform()
}

// ------------------------------------------------------------------
// 收集运行时依赖到 offline-libs（maven 布局），用于制作离线完整包：
// 服务器首次启动时 TabooLib 需要从 repo.tabooproject.org 下载依赖，
// 若服务器无法访问该仓库，可将 offline-libs 目录内的文件放到
// 服务器根目录的 libraries/ 文件夹，插件将跳过下载直接加载。
// 用法：gradle collectLibs
// ------------------------------------------------------------------
val collectLibs by tasks.registering {
    group = "customquest"
    description = "收集 TabooLib 运行时依赖到 offline-libs（maven 布局）"
    val conf = configurations.detachedConfiguration()
    fun add(coords: String) = conf.dependencies.add(dependencies.create(coords))
    // 原始加载器依赖（PrimitiveLoader，版本与新内置 common 硬编码一致）
    add("me.lucko:jar-relocator:1.7")
    add("org.ow2.asm:asm:9.8")
    add("org.ow2.asm:asm-util:9.8")
    add("org.ow2.asm:asm-commons:9.8")
    add("org.tabooproject.reflex:reflex:1.2.3")
    add("org.tabooproject.reflex:analyser:1.2.3")
    add("io.izzel.taboolib:common-env:$TABOOLIB_VERSION")
    // 原始加载器 loadAll 硬编码加载的 common 模块
    add("io.izzel.taboolib:common-util:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:common-legacy-api:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:common-platform-api:$TABOOLIB_VERSION")
    // Kotlin 环境
    add("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    add("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // TabooLib 运行时模块（与 env.properties 的 module 列表一致）
    add("io.izzel.taboolib:platform-bukkit:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:platform-bukkit-impl:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:bukkit-util:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:bukkit-xseries:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:minecraft-kether:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:minecraft-chat:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:minecraft-i18n:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:basic-configuration:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:bukkit-nms:$TABOOLIB_VERSION")
    add("io.izzel.taboolib:bukkit-nms-stable:$TABOOLIB_VERSION")
    // Kether 运行时注解依赖（@RuntimeDependencies）
    add("org.apache.commons:commons-jexl3:3.2.1")
    add("com.mojang:datafixerupper:4.0.26")
    // 玩家数据 SQLite 驱动（与 @RuntimeDependency 版本一致）
    add("org.xerial:sqlite-jdbc:$SQLITE_JDBC_VERSION")

    doLast {
        val root = layout.projectDirectory.dir("offline-libs").asFile
        var count = 0
        conf.resolvedConfiguration.resolvedArtifacts.forEach { art ->
            val id = art.id.componentIdentifier
            if (id is ModuleComponentIdentifier) {
                val dest = File(File(File(root, id.group.replace('.', '/') + "/${id.module}"), id.version), art.file.name)
                dest.parentFile.mkdirs()
                art.file.copyTo(dest, overwrite = true)
                count++
            }
        }
        println("[collectLibs] 已收集 $count 个文件到 offline-libs")
    }
}
