package com.cj.customquest.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * 全局配置（config.yml）。
 */
public final class Settings {

    /** 玩家数据自动保存间隔（秒） */
    public static int autosaveSeconds = 300;

    /** 任务追踪计分板总开关 */
    public static boolean boardEnabled = true;
    /** 任务追踪计分板标题（支持 & 颜色代码） */
    public static String boardTitle = "&6&l任务追踪";
    /** 计分板中每个任务之间的空行数（0 = 无空格） */
    public static int boardGapLines = 1;

    /** NPC 对话箱子标题的默认值。 */
    public static final String DEFAULT_DIALOGUE_TITLE = "NPCdialogue";
    /** NPC 对话箱子标题；ChaUI 等按容器名匹配的插件可能不支持中文，建议改为 ASCII。 */
    public static String dialogueTitle = DEFAULT_DIALOGUE_TITLE;

    /** 玩家数据默认使用的 SQLite 文件名。 */
    public static final String DEFAULT_STORAGE_FILE = "data.db";
    /**
     * 玩家数据存储类型：{@code sqlite} 或 {@code mysql}。
     *
     * <p>无法识别的值会**回退到 sqlite 并在控制台告警** —— 绝不能静默按 SQLite 跑，
     * 那会让人以为数据在远端库里，直到换服才发现对不上。
     */
    public static String storageType = "sqlite";
    /** SQLite 数据文件名（只取文件名，不允许带目录）。 */
    public static String storageFile = DEFAULT_STORAGE_FILE;
    /** 首次启用数据库时是否把旧的 {@code data/<uuid>.yml} 导入；导入后原文件保留。 */
    public static boolean storageMigrateFromYaml = true;

    /** MySQL 连接参数（仅 type: mysql 时使用）。 */
    public static String storageHost = "127.0.0.1";
    public static int storagePort = 3306;
    public static String storageDatabase = "customquest";
    public static String storageUsername = "root";
    public static String storagePassword = "";
    /** 表名前缀：多套环境共用一个库时隔离用；只允许字母、数字、下划线。 */
    public static String storageTablePrefix = "";

    private Settings() {
    }

    public static void load(File dataFolder) {
        File file = new File(dataFolder, "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        if (!config.contains("autosave-seconds")) {
            config.set("autosave-seconds", 300);
            changed = true;
        }
        if (!config.contains("scoreboard.enabled")) {
            config.set("scoreboard.enabled", true);
            changed = true;
        }
        if (!config.contains("scoreboard.title")) {
            config.set("scoreboard.title", "&6&l任务追踪");
            changed = true;
        }
        if (!config.contains("scoreboard.gap-lines")) {
            config.set("scoreboard.gap-lines", 1);
            changed = true;
        }
        if (!config.contains("dialogue.title")) {
            config.set("dialogue.title", DEFAULT_DIALOGUE_TITLE);
            changed = true;
        }
        if (!config.contains("storage.type")) {
            config.set("storage.type", "sqlite");
            changed = true;
        }
        if (!config.contains("storage.sqlite.file")) {
            config.set("storage.sqlite.file", DEFAULT_STORAGE_FILE);
            changed = true;
        }
        if (!config.contains("storage.migrate-from-yaml")) {
            config.set("storage.migrate-from-yaml", true);
            changed = true;
        }
        if (!config.contains("storage.mysql.host")) {
            config.set("storage.mysql.host", "127.0.0.1");
            changed = true;
        }
        if (!config.contains("storage.mysql.port")) {
            config.set("storage.mysql.port", 3306);
            changed = true;
        }
        if (!config.contains("storage.mysql.database")) {
            config.set("storage.mysql.database", "customquest");
            changed = true;
        }
        if (!config.contains("storage.mysql.username")) {
            config.set("storage.mysql.username", "root");
            changed = true;
        }
        if (!config.contains("storage.mysql.password")) {
            config.set("storage.mysql.password", "");
            changed = true;
        }
        if (!config.contains("storage.mysql.table-prefix")) {
            config.set("storage.mysql.table-prefix", "");
            changed = true;
        }
        // 已移除的配置节点在升级时同步清理。
        if (config.contains("quest-book")) {
            config.set("quest-book", null);
            changed = true;
        }
        if (config.contains("dialogue.chaui")) {
            config.set("dialogue.chaui", null);
            changed = true;
        }

        autosaveSeconds = Math.max(30, config.getInt("autosave-seconds", 300));
        boardEnabled = config.getBoolean("scoreboard.enabled", true);
        boardTitle = config.getString("scoreboard.title", "&6&l任务追踪");
        boardGapLines = Math.max(0, config.getInt("scoreboard.gap-lines", 1));
        dialogueTitle = config.getString("dialogue.title", DEFAULT_DIALOGUE_TITLE);

        // 存储类型：sqlite / mysql。无法识别必须**明确告警并回退**。
        String type = config.getString("storage.type", "sqlite");
        com.cj.customquest.quest.SqlDialect dialect =
                com.cj.customquest.quest.SqlDialect.parse(type);
        if (dialect == null) {
            Bukkit.getLogger().warning("[CustomQuest] storage.type=" + type
                    + " 无法识别（可用：sqlite / mysql），已回退到 sqlite");
            dialect = com.cj.customquest.quest.SqlDialect.SQLITE;
        }
        storageType = dialect.name().toLowerCase(java.util.Locale.ROOT);
        storageFile = sanitizeFileName(config.getString("storage.sqlite.file", DEFAULT_STORAGE_FILE));
        storageMigrateFromYaml = config.getBoolean("storage.migrate-from-yaml", true);

        storageHost = trimToDefault(config.getString("storage.mysql.host"), "127.0.0.1");
        storagePort = sanitizePort(config.getInt("storage.mysql.port", 3306));
        storageDatabase = trimToDefault(config.getString("storage.mysql.database"), "customquest");
        storageUsername = trimToDefault(config.getString("storage.mysql.username"), "root");
        String password = config.getString("storage.mysql.password", "");
        storagePassword = password == null ? "" : password;
        storageTablePrefix = sanitizePrefix(config.getString("storage.mysql.table-prefix"));
        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[CustomQuest] 保存 config.yml 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 只接受文件名：把 {@code ../} 之类目录部分剥掉。
     *
     * <p>数据库路径来自配置，而配置是可以写错的（也可以被误贴）。允许带目录的话，
     * 一个笔误就能把 data.db 写到插件目录外面去 —— 备份脚本找不到它，迁移时也找不到。
     */
    private static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STORAGE_FILE;
        }
        String name = raw.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? DEFAULT_STORAGE_FILE : name;
    }

    private static String trimToDefault(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    private static int sanitizePort(int port) {
        if (port <= 0 || port > 65535) {
            Bukkit.getLogger().warning("[CustomQuest] storage.mysql.port=" + port + " 不合法，已用 3306");
            return 3306;
        }
        return port;
    }

    /** 前缀会直接拼进表名，因此只放行安全字符；不合法时忽略前缀而不是拼出坏 SQL。 */
    private static String sanitizePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String prefix = raw.trim();
        if (!prefix.matches("[A-Za-z0-9_]+")) {
            Bukkit.getLogger().warning("[CustomQuest] storage.mysql.table-prefix 只允许字母、数字与下划线，已忽略");
            return "";
        }
        return prefix;
    }

    /** 把解析结果组装成存储层要的配置。 */
    public static com.cj.customquest.quest.QuestStorage.Config storageConfig() {
        com.cj.customquest.quest.SqlDialect dialect =
                com.cj.customquest.quest.SqlDialect.parse(storageType);
        if (dialect == null) {
            dialect = com.cj.customquest.quest.SqlDialect.SQLITE;
        }
        return new com.cj.customquest.quest.QuestStorage.Config(dialect, storageFile,
                storageHost, storagePort, storageDatabase, storageUsername, storagePassword,
                storageTablePrefix, storageMigrateFromYaml);
    }
}
