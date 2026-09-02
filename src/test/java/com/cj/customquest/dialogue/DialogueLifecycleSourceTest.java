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
        assertTrue(manager.contains("if (session == null)"));
        assertTrue(manager.contains("DialoguePayload.sendClose(player, sessionId)"));
        assertTrue(manager.contains("validInteractionNpc(player, session.npcId())"));
        assertTrue(manager.contains("selected.getSubmitQuest()"));
        assertTrue(manager.contains("selected.getSubmitItems()"));
        assertTrue(!manager.contains("getAcceptQuest()"));
        assertTrue(manager.indexOf("QuestManager.getInstance().submitQuest(")
                < manager.indexOf("if (!selected.getKether().isEmpty() || actions.targetBranch() != null)"));
        assertTrue(!manager.contains("getSubmitData()"));
    }
}
