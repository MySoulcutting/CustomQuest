package com.cj.customquest.quest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 玩家数据库的**方言**：把 SQLite 与 MySQL 的差异收在一处。
 *
 * <p>为什么需要它：两边的差异不是"连接串不同"这么简单 ——
 * 建表类型（{@code TEXT} 主键 vs {@code VARCHAR(n)} 主键）、连上后的调优语句
 * （SQLite 的 {@code PRAGMA} 在 MySQL 上直接报错）、以及元数据写入的 upsert 语法
 * （{@code INSERT OR REPLACE} vs {@code ON DUPLICATE KEY UPDATE}）全都不同。
 * 把这些散在业务代码里，就会出现"改了一边忘了另一边"，而症状是"某个库上玩家数据读不出来"。
 *
 * <p>业务 SQL（增删改查玩家数据）两端是**同一套**，不需要方言参与。
 */
public enum SqlDialect {
    SQLITE {
        @Override
        public String driverClass() {
            return "org.sqlite.JDBC";
        }

        @Override
        public String jdbcUrl(String sqliteFile, String host, int port, String database) {
            return "jdbc:sqlite:" + sqliteFile;
        }

        @Override
        public void configure(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
        }

        @Override
        public String upsertMetaSql(String table) {
            return "INSERT OR REPLACE INTO " + table + "(meta_key, meta_value) VALUES (?, ?)";
        }

        @Override
        public String uuidType() {
            return "TEXT";
        }

        @Override
        public String keyType() {
            return "TEXT";
        }

        @Override
        public String bigIntType() {
            return "INTEGER";
        }

        @Override
        public String intType() {
            return "INTEGER";
        }

        @Override
        public String textType() {
            return "TEXT";
        }

        @Override
        public String tableSuffix() {
            return "";
        }
    },

    MYSQL {
        @Override
        public String driverClass() {
            return "com.mysql.cj.jdbc.Driver";
        }

        @Override
        public String jdbcUrl(String sqliteFile, String host, int port, String database) {
            // connectTimeout/socketTimeout：玩家进服会同步读一次数据，不能让它无限等网络。
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    + "&connectTimeout=3000&socketTimeout=8000";
        }

        @Override
        public void configure(Connection connection) {
            // MySQL 不需要 SQLite 那套 PRAGMA；连接参数已经在 URL 里。
        }

        @Override
        public String upsertMetaSql(String table) {
            return "INSERT INTO " + table + "(meta_key, meta_value) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";
        }

        /** 主键列必须定长：MySQL 不允许对 {@code TEXT} 直接建主键（除非指定前缀长度）。 */
        @Override
        public String uuidType() {
            return "VARCHAR(36)";
        }

        @Override
        public String keyType() {
            return "VARCHAR(128)";
        }

        @Override
        public String bigIntType() {
            return "BIGINT";
        }

        @Override
        public String intType() {
            return "INT";
        }

        @Override
        public String textType() {
            return "TEXT";
        }

        /** 外键与级联删除依赖 InnoDB；字符集显式声明，避免跟随库默认值出现排序规则不一致。 */
        @Override
        public String tableSuffix() {
            return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
    };

    /** JDBC 驱动类名（启动时用 {@code Class.forName} 探测，缺失则回退）。 */
    public abstract String driverClass();

    /** 连接串；SQLite 只用 {@code sqliteFile}，MySQL 只用后三个参数。 */
    public abstract String jdbcUrl(String sqliteFile, String host, int port, String database);

    /** 连接建立后的调优语句（SQLite 的 PRAGMA；MySQL 无）。 */
    public abstract void configure(Connection connection) throws SQLException;

    /** 元数据表的 upsert 语句：两端语法不同。 */
    public abstract String upsertMetaSql(String table);

    public abstract String uuidType();

    /** 业务键（任务 id / NPC id / 计数键）：必须是定长类型，因为它进主键。 */
    public abstract String keyType();

    public abstract String bigIntType();

    public abstract String intType();

    /** 普通文本值（不参与主键），可以是无长度限制的类型。 */
    public abstract String textType();

    /** 建表语句末尾的引擎/字符集声明。 */
    public abstract String tableSuffix();

    /** 解析配置里写的类型名；无法识别返回 {@code null}，由调用方决定回退还是报错。 */
    public static SqlDialect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SQLITE;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "sqlite", "sqlite3" -> SQLITE;
            case "mysql", "mariadb" -> MYSQL;
            default -> null;
        };
    }
}
