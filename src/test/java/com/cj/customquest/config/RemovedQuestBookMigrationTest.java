package com.cj.customquest.config;

import com.cj.customquest.util.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemovedQuestBookMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void removesLegacyQuestBookConfigWithoutTouchingOtherValues() throws Exception {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
                autosave-seconds: 180
                custom-value: keep
                quest-book:
                  gap-lines: 2
                  per-page: 4
                  no-quest: old
                """);

        Settings.load(tempDir.toFile());

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file.toFile());
        assertFalse(loaded.contains("quest-book"));
        assertEquals("keep", loaded.getString("custom-value"));
        assertEquals(180, loaded.getInt("autosave-seconds"));
    }

    @Test
    void removesOnlyStandaloneQuestHelpFromLegacyMessages() throws Exception {
        Path file = tempDir.resolve("messages.yml");
        Files.writeString(file, """
                no-quests: legacy
                help:
                  - "&7/quest &f- 打开任务书"
                  - "&7/cq quest accept <玩家> <任务ID> &f- 强制接取任务"
                  - "&7/custom &f- 自定义帮助"
                """);

        Messages.load(tempDir.toFile());

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file.toFile());
        assertFalse(loaded.contains("no-quests"));
        assertFalse(loaded.getStringList("help").stream()
                .anyMatch(line -> line.contains("/quest ")));
        assertTrue(loaded.getStringList("help").stream()
                .anyMatch(line -> line.contains("/cq quest accept")));
        assertTrue(loaded.getStringList("help").stream()
                .anyMatch(line -> line.contains("/custom")));
    }
}
