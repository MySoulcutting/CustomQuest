package com.cj.customquest.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 方言的差异点。
 *
 * <p>这些是**不需要数据库**就能钉住的部分，而且恰好是最容易出错的部分：
 * 主键列在 MySQL 上不能是裸 {@code TEXT}（会直接建表失败）、SQLite 的 {@code PRAGMA}
 * 在 MySQL 上会报语法错、元数据 upsert 两端语法不同。
 *
 * <p>真正的跨库读写要有 MySQL 实例才能验证（见 README 的手工验证清单）；
 * 这里保证"生成出来的 SQL 本身是对的"，把最容易写错的那层先锁住。
 */
class SqlDialectTest {

    @Test
    void parsesTypeNames() {
        assertEquals(SqlDialect.SQLITE, SqlDialect.parse(null));
        assertEquals(SqlDialect.SQLITE, SqlDialect.parse("  "));
        assertEquals(SqlDialect.SQLITE, SqlDialect.parse("sqlite"));
        assertEquals(SqlDialect.SQLITE, SqlDialect.parse("SQLite3"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.parse("mysql"));
        assertEquals(SqlDialect.MYSQL, SqlDialect.parse("MariaDB"));
        assertNull(SqlDialect.parse("postgres"), "无法识别的类型必须返回 null，由调用方回退");
    }

    /**
     * MySQL 的主键列必须定长。
     *
     * <p>这条钉的是最硬的一个坑：MySQL 不允许对 {@code TEXT} 建主键（除非指定前缀长度，
     * 而前缀长度又会和 utf8mb4 的字节数纠缠）。用 VARCHAR 是唯一稳妥的写法。
     */
    @Test
    void mysqlUsesFixedLengthKeyColumns() {
        assertEquals("VARCHAR(36)", SqlDialect.MYSQL.uuidType());
        assertEquals("VARCHAR(128)", SqlDialect.MYSQL.keyType());
        assertEquals("BIGINT", SqlDialect.MYSQL.bigIntType());
        assertEquals("INT", SqlDialect.MYSQL.intType());
        assertTrue(SqlDialect.MYSQL.tableSuffix().contains("InnoDB"),
                "外键与级联删除依赖 InnoDB");
        assertTrue(SqlDialect.MYSQL.tableSuffix().contains("utf8mb4"));
    }

    @Test
    void sqliteKeepsTextAndIntegerTypes() {
        assertEquals("TEXT", SqlDialect.SQLITE.uuidType());
        assertEquals("TEXT", SqlDialect.SQLITE.keyType());
        assertEquals("INTEGER", SqlDialect.SQLITE.bigIntType());
        assertEquals("INTEGER", SqlDialect.SQLITE.intType());
        assertEquals("", SqlDialect.SQLITE.tableSuffix(), "SQLite 不需要引擎声明");
    }

    /** 元数据写入的 upsert：两端语法不同，写混了只会在另一个库上炸。 */
    @Test
    void metaUpsertDiffersPerDialect() {
        String sqlite = SqlDialect.SQLITE.upsertMetaSql("storage_meta");
        String mysql = SqlDialect.MYSQL.upsertMetaSql("storage_meta");

        assertTrue(sqlite.startsWith("INSERT OR REPLACE"), "SQLite 用 INSERT OR REPLACE");
        assertFalse(sqlite.contains("ON DUPLICATE KEY"), "SQLite 不认 MySQL 的写法");

        assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE"), "MySQL 用 ON DUPLICATE KEY UPDATE");
        assertFalse(mysql.startsWith("INSERT OR REPLACE"), "MySQL 不认 SQLite 的写法");
    }

    /** 驱动类名与连接串必须各自正确（探测失败会走回退，见 QuestStorage 的 open）。 */
    @Test
    void driversAndUrlsMatchDialect() {
        assertEquals("org.sqlite.JDBC", SqlDialect.SQLITE.driverClass());
        assertEquals("com.mysql.cj.jdbc.Driver", SqlDialect.MYSQL.driverClass());

        assertEquals("jdbc:sqlite:/tmp/data.db",
                SqlDialect.SQLITE.jdbcUrl("/tmp/data.db", "ignored", 1, "ignored"));

        String mysql = SqlDialect.MYSQL.jdbcUrl("ignored", "db.example.com", 3307, "quest");
        assertTrue(mysql.startsWith("jdbc:mysql://db.example.com:3307/quest"), mysql);
        assertTrue(mysql.contains("connectTimeout="), "主线程会同步读数据，连接必须有超时");
    }
}
