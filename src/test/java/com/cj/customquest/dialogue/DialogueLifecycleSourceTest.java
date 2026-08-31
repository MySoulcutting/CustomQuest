package com.cj.customquest.dialogue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueLifecycleSourceTest {

    @Test
    void registersTransportAndClearsSessionsAcrossLifecycleBoundaries() throws IOException {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/CustomQuestPlugin.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/listener/PlayerListener.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/dialogue/DialogueManager.java"));

        assertTrue(plugin.contains("DialoguePayload.register()"));
        assertTrue(plugin.contains("DialogueManager.getInstance().shutdown()"));
        assertTrue(plugin.indexOf("DialogueManager.getInstance().shutdown()")
                < plugin.indexOf("DialoguePayload.unregister()"));
        assertTrue(listener.contains("DialogueManager.getInstance().remove(event.getPlayer())"));
        assertTrue(manager.contains("public void reload()"));
        assertTrue(manager.indexOf("closeAll();") < manager.indexOf("byNpc.clear();"));
    }

    @Test
    void routesBothTransportsThroughOneTimeSessionValidation() throws IOException {
        String command = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/command/MainCommand.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/dialogue/DialogueManager.java"));

        assertTrue(command.contains("UUID.fromString(args[1])"));
        assertTrue(command.contains("DialoguePayload.decodeCommandOptionId(args[2])"));
        assertTrue(manager.contains("sessions.consume("));
        assertTrue(manager.contains("if (session == null) {\n            DialoguePayload.sendClose(player, sessionId);"));
        assertTrue(manager.contains("validInteractionNpc(player, session.npcId())"));
        assertTrue(manager.contains("quest == null || !QuestManager.getInstance().acceptQuest(player, quest)"));
        assertTrue(manager.contains("quest == null || !QuestManager.getInstance().submitQuest(player, quest)"));
        assertTrue(manager.indexOf("QuestManager.getInstance().submitQuest(player, quest)")
                < manager.indexOf("applyData(player, npc, selected.getSubmitData())"));
    }
}
