package com.cj.customquest.dialogue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话界面完全由原版箱子承载，不再对接任何客户端模组；
 * 这些断言守住「没有 ChaUI 依赖 + 点击槽位即提交」的约定。
 */
class DialoguePresentationTest {

    @Test
    void hasNoClientModIntegrationLeft() throws IOException {
        String presentation = source("src/main/java/com/cj/customquest/dialogue/DialoguePresentation.java");
        String manager = source("src/main/java/com/cj/customquest/dialogue/DialogueManager.java");
        String container = source("src/main/java/com/cj/customquest/dialogue/DialogueContainer.java");

        for (String text : new String[] {presentation, manager, container}) {
            String lower = text.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("chaui"));
            assertFalse(lower.contains("packet"));
            assertFalse(lower.contains("pluginmessage"));
        }
        assertFalse(Files.exists(Path.of("src/main/java/com/cj/customquest/dialogue/ChaUiDialogueBridge.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/cj/customquest/dialogue/ChaUiDialogueView.java")));
    }

    @Test
    void routesVanillaClicksToTheDialogueOption() throws IOException {
        String presentation = source("src/main/java/com/cj/customquest/dialogue/DialoguePresentation.java");
        String manager = source("src/main/java/com/cj/customquest/dialogue/DialogueManager.java");
        String listener = source("src/main/java/com/cj/customquest/listener/InventoryListener.java");

        assertTrue(presentation.contains("boolean handleContainerClick(Player player, Inventory inventory, int rawSlot)"));
        assertTrue(presentation.contains(
                "DialogueContainer.optionIndex(rawSlot, current.snapshot.options().size())"));
        assertTrue(presentation.contains("host.select(player, current.snapshot.sessionId()"));
        assertTrue(manager.contains("handleDialogueContainerClick(Player player, Inventory inventory, int rawSlot)"));
        assertTrue(listener.contains("DialogueManager.getInstance().handleDialogueContainerClick(player, top, event.getRawSlot())"));
        assertTrue(listener.contains("event.setCancelled(true)"));
    }

    @Test
    void keepsTheBookInSlotOneAndOptionsFromSlotThree() throws IOException {
        String container = source("src/main/java/com/cj/customquest/dialogue/DialogueContainer.java");

        assertTrue(container.contains("int SPEAKER_SLOT = 0"));
        assertTrue(container.contains("int FIRST_OPTION_SLOT = 2"));
        assertTrue(container.contains("inventory.setItem(SPEAKER_SLOT, item("));
        assertTrue(container.contains("int slot = FIRST_OPTION_SLOT + (optionCount - 1 - index);"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
