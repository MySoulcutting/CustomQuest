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

class DefaultExamplesTest {

    @TempDir
    Path tempDir;

    @Test
    void installsExamplesIntoMissingDirectory() {
        Path dataFolder = tempDir.resolve("missing");

        assertTrue(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void installsExamplesIntoEmptyDirectory() throws Exception {
        Path dataFolder = Files.createDirectory(tempDir.resolve("empty"));

        assertTrue(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void treatsExistingContentAsLegacyInstallation() throws Exception {
        Path dataFolder = Files.createDirectory(tempDir.resolve("legacy"));
        Files.writeString(dataFolder.resolve("config.yml"), "autosave-seconds: 300\n");

        assertFalse(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void markerPreventsExamplesFromBeingInstalledAgain() throws Exception {
        Path dataFolder = tempDir.resolve("initialized");
        DefaultExamples.markInitialized(dataFolder.toFile());

        assertTrue(DefaultExamples.isInitialized(dataFolder.toFile()));
        assertFalse(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void recognizesMarkerCreatedByPreviousImplementation() throws Exception {
        Path dataFolder = Files.createDirectory(tempDir.resolve("legacy-marker"));
        Files.createFile(dataFolder.resolve(".defaults-initialized"));

        assertTrue(DefaultExamples.isInitialized(dataFolder.toFile()));
        assertFalse(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void deletingExamplesAfterInitializationDoesNotRestartInstallation() throws Exception {
        Path dataFolder = Files.createDirectory(tempDir.resolve("deleted-examples"));
        Path example = dataFolder.resolve("quests/example_kill.yml");
        Files.createDirectories(example.getParent());
        Files.writeString(example, "quest-id: example_kill\n");
        DefaultExamples.markInitialized(dataFolder.toFile());
        Files.delete(example);

        assertFalse(DefaultExamples.shouldInstall(dataFolder.toFile()));
    }

    @Test
    void missingConfigKeepsOriginalRecreationBehavior() {
        Path dataFolder = tempDir.resolve("missing-config");
        Path configFile = dataFolder.resolve("config.yml");

        Settings.load(dataFolder.toFile());

        assertTrue(Files.isRegularFile(configFile));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile.toFile());
        assertEquals(300, config.getInt("autosave-seconds"));
        assertTrue(config.getBoolean("scoreboard.enabled"));
    }

    @Test
    void missingMessagesKeepsOriginalRecreationBehavior() {
        Path dataFolder = tempDir.resolve("missing-messages");
        Path messagesFile = dataFolder.resolve("messages.yml");

        Messages.load(dataFolder.toFile());

        assertTrue(Files.isRegularFile(messagesFile));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(messagesFile.toFile());
        assertTrue(config.contains("prefix"));
        assertTrue(config.isList("help"));
    }
}
