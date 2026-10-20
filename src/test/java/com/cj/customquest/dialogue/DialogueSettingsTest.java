package com.cj.customquest.dialogue;

import com.cj.customquest.config.Settings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSettingsTest {
    @TempDir Path directory;
    private final int autosave = Settings.autosaveSeconds;
    private final boolean boardEnabled = Settings.boardEnabled;
    private final String boardTitle = Settings.boardTitle;
    private final int boardGap = Settings.boardGapLines;
    private final String dialogueTitle = Settings.dialogueTitle;

    @AfterEach
    void restoreSettings() {
        Settings.autosaveSeconds = autosave;
        Settings.boardEnabled = boardEnabled;
        Settings.boardTitle = boardTitle;
        Settings.boardGapLines = boardGap;
        Settings.dialogueTitle = dialogueTitle;
    }

    @Test
    void upgradesOldConfigurationWithoutOverwritingAdministratorValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("autosave-seconds", 99);
        config.set("scoreboard.enabled", false);
        config.set("scoreboard.title", "管理员标题");
        config.set("scoreboard.gap-lines", 3);
        config.set("custom-node", "保留");
        config.save(directory.resolve("config.yml").toFile());

        Settings.load(directory.toFile());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());
        assertEquals(99, Settings.autosaveSeconds);
        assertFalse(Settings.boardEnabled);
        assertEquals("管理员标题", Settings.boardTitle);
        assertEquals(3, Settings.boardGapLines);
        assertEquals("保留", saved.getString("custom-node"));
    }

    @Test
    void fillsMissingKeysAndDropsRemovedSections() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dialogue.chaui.enabled", true);
        config.set("quest-book", "旧值");
        config.save(directory.resolve("config.yml").toFile());

        Settings.load(directory.toFile());

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());
        assertTrue(saved.contains("autosave-seconds"));
        assertTrue(saved.contains("scoreboard.enabled"));
        assertTrue(saved.contains("scoreboard.title"));
        assertTrue(saved.contains("scoreboard.gap-lines"));
        assertFalse(saved.contains("quest-book"));
        assertFalse(saved.contains("dialogue.chaui"));
        assertEquals(Settings.DEFAULT_DIALOGUE_TITLE, saved.getString("dialogue.title"));
    }
}
