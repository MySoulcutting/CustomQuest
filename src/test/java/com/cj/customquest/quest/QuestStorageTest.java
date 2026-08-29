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
}
