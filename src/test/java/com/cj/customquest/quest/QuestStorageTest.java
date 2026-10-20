package com.cj.customquest.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsAllPlayerData() {
        UUID uuid = UUID.randomUUID();
        PlayerQuestData source = new PlayerQuestData();
        QuestProgress progress = new QuestProgress(123456789L);
        progress.setCounter("obj0", 7);
        progress.updateConditionMet(true);
        source.getAccepted().put("quest.alpha", progress);
        source.getCompleted().put("finished.quest", 987654321L);
        source.npcDataOf(5).put("story.stage", "doing");

        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            storage.save(uuid, source);
        }

        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            PlayerQuestData loaded = storage.load(uuid);
            assertEquals(123456789L, loaded.getAccepted().get("quest.alpha").getAcceptedAt());
            assertEquals(7, loaded.getAccepted().get("quest.alpha").getCounter("obj0"));
            assertTrue(loaded.getAccepted().get("quest.alpha").isConditionMet());
            assertEquals(987654321L, loaded.getCompletedAt("finished.quest"));
            assertEquals("doing", loaded.getNpcData().get("5").get("story.stage"));
        }
    }

    @Test
    void migratesYamlOnceAndKeepsTheBackup() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path yamlFolder = Files.createDirectories(tempDir.resolve("data"));
        Path yamlFile = yamlFolder.resolve(uuid + ".yml");
        Files.writeString(yamlFile, """
                accepted:
                  example_kill:
                    accepted-at: 100
                    counters:
                      obj0: 3
                completed:
                  example_submit: 200
                npc-data:
                  '5':
                    stage: doing
                """);

        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            PlayerQuestData migrated = storage.load(uuid);
            assertEquals(3, migrated.getAccepted().get("example_kill").getCounter("obj0"));
            assertEquals(200L, migrated.getCompletedAt("example_submit"));
            assertEquals("doing", migrated.getNpcData().get("5").get("stage"));
        }
        assertTrue(Files.exists(yamlFile));

        Files.writeString(yamlFile, "completed:\n  example_submit: 999\n");
        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            assertEquals(200L, storage.load(uuid).getCompletedAt("example_submit"));
        }
    }

    @Test
    void savingEmptyDataRemovesPreviousRows() {
        UUID uuid = UUID.randomUUID();
        PlayerQuestData source = new PlayerQuestData();
        source.getAccepted().put("active", new QuestProgress(1L));
        source.getCompleted().put("done", 2L);
        source.npcDataOf(5).put("stage", "done");

        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            storage.save(uuid, source);
            storage.save(uuid, new PlayerQuestData());
        }

        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            PlayerQuestData loaded = storage.load(uuid);
            assertTrue(loaded.getAccepted().isEmpty());
            assertTrue(loaded.getCompleted().isEmpty());
            assertTrue(loaded.getNpcData().isEmpty());
        }
    }

    @Test
    void failedYamlMigrationCanBeRetried() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path yamlFolder = Files.createDirectories(tempDir.resolve("data"));
        Path yamlFile = yamlFolder.resolve(uuid + ".yml");
        Files.writeString(yamlFile, "accepted: [");

        assertThrows(IllegalStateException.class, () -> new QuestStorage(tempDir.toFile()));

        Files.writeString(yamlFile, "completed:\n  recovered: 321\n");
        try (QuestStorage storage = new QuestStorage(tempDir.toFile())) {
            PlayerQuestData loaded = storage.load(uuid);
            assertEquals(321L, loaded.getCompletedAt("recovered"));
            assertFalse(loaded.getCompleted().isEmpty());
        }
    }

    /**
     * 配置里的文件名要真的被用上。
     *
     * <p>这条钉住的是"配置能生效"本身：文件名接错时插件仍会正常工作（写进 data.db），
     * 只有备份脚本、迁移脚本或换库时才会发现读的不是同一个文件。
     */
    @Test
    void usesConfiguredDatabaseFileName() {
        try (QuestStorage storage = new QuestStorage(tempDir.toFile(), "custom-name.db", true)) {
            storage.save(UUID.randomUUID(), new PlayerQuestData());
        }
        assertTrue(Files.exists(tempDir.resolve("custom-name.db")), "应当使用配置指定的库文件名");
        assertFalse(Files.exists(tempDir.resolve("data.db")), "不该再额外生成默认文件名的库");
    }

    /** {@code migrate-from-yaml: false} 时必须跳过导入，但库本身照常可用。 */
    @Test
    void migrationCanBeDisabledByConfig() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path yamlFolder = Files.createDirectories(tempDir.resolve("data"));
        Files.writeString(yamlFolder.resolve(uuid + ".yml"), "completed:\n  old_quest: 111\n");

        try (QuestStorage storage = new QuestStorage(tempDir.toFile(), "data.db", false)) {
            assertTrue(storage.load(uuid).getCompleted().isEmpty(), "关掉迁移后不应导入旧 yml");
            PlayerQuestData fresh = new PlayerQuestData();
            fresh.getCompleted().put("new_quest", 222L);
            storage.save(uuid, fresh);
        }

        try (QuestStorage storage = new QuestStorage(tempDir.toFile(), "data.db", false)) {
            assertEquals(222L, storage.load(uuid).getCompletedAt("new_quest"));
        }
    }

    /**
     * MySQL 连不上时**回退到 SQLite**：插件必须能起来，且回退后读写照常。
     *
     * <p>用端口 1 制造"立刻连不上"（不需要外网，也不会真的等满超时）。
     * 这里同时钉住两件事：回退后方言确实是 SQLITE（否则后续 SQL 会带 MySQL 语法），
     * 以及数据落在默认的 data.db 里。
     */
    @Test
    void fallsBackToSqliteWhenMysqlIsUnreachable() {
        UUID uuid = UUID.randomUUID();
        QuestStorage.Config mysql = new QuestStorage.Config(SqlDialect.MYSQL, "data.db",
                "127.0.0.1", 1, "no_such_db", "nobody", "", "", true);

        PlayerQuestData data = new PlayerQuestData();
        data.getCompleted().put("quest.one", 42L);

        try (QuestStorage storage = new QuestStorage(tempDir.toFile(), mysql)) {
            assertEquals(SqlDialect.SQLITE, storage.dialect(), "连不上 MySQL 就该回退到 SQLite");
            storage.save(uuid, data);
            assertEquals(42L, storage.load(uuid).getCompletedAt("quest.one"));
        }
        assertTrue(Files.exists(tempDir.resolve("data.db")), "回退后数据应落在默认 SQLite 文件");
    }
}
