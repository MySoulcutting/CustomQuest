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

        assertTrue(!plugin.contains("DialoguePayload"));
        assertTrue(!plugin.toLowerCase(java.util.Locale.ROOT).contains("soulcore"));
        assertTrue(plugin.contains("DialogueManager.getInstance().shutdown()"));
        assertTrue(listener.contains("DialogueManager.getInstance().remove(event.getPlayer())"));
        assertTrue(manager.contains("public void reload()"));
        assertTrue(manager.indexOf("closeAll();") < manager.indexOf("byNpc.clear();"));
        assertTrue(listener.contains("PlayerChangedWorldEvent"));
        assertTrue(listener.contains("DialogueManager.getInstance().closeDialogue(event.getPlayer())"));
        assertTrue(manager.contains("presentations.forget(player)"));
        assertTrue(manager.contains("presentations.shutdown()"));
        assertTrue(manager.contains("sessions.cancelTransition(player.getUniqueId(), sessionId)"));
        int call = manager.indexOf("executeOptionActions(player, session, branch, chosen, chosenIndex, actions)");
        int task = manager.lastIndexOf("runTask(BukkitPlugin.getInstance()", call);
        assertTrue(task > 0 && call - task < 160,
                "选项脚本必须由 runTask 隔一 tick 调度：关箱子的同一 tick 里紧接着开新界面，"
                        + "新界面会被客户端那次拆除替换界面带走");
        assertTrue(manager.contains("() -> openDialogue(player, npcId, forceBranchId)"));
        assertTrue(manager.contains("() -> closeDialogue(player)"));
    }

    @Test
    void routesChatCommandsThroughOneTimeSessionValidation() throws IOException {
        String command = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/command/MainCommand.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/dialogue/DialogueManager.java"));

        assertTrue(!command.contains("handleClick"));
        assertTrue(manager.contains("handleDialogueContainerClick"));
        assertTrue(!manager.contains("onDismiss("));
        assertTrue(manager.contains("sessions.consume("));
        assertTrue(manager.contains("if (session == null)"));
        assertTrue(manager.contains("presentations.close(player, sessionId)"));
        assertTrue(manager.contains("handleDialogueContainerClick"));
        assertTrue(manager.contains("presentations.show(player, snapshot, previousSession != null)"));
        assertTrue(manager.contains("validInteractionNpc(player, session.npcId())"));
        assertTrue(manager.contains("selected.getSubmitQuest()"));
        assertTrue(manager.contains("selected.getSubmitItems()"));
        assertTrue(!manager.contains("getAcceptQuest()"));
        assertTrue(manager.indexOf("QuestManager.getInstance().submitQuest(")
                < manager.indexOf("if (!selected.getKether().isEmpty() || actions.targetBranch() != null)"));
        assertTrue(!manager.contains("getSubmitData()"));
    }
}
