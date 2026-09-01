package com.cj.customquest.tracking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestTrackingLifecycleSourceTest {

    @Test
    void routesHudHeartbeatsWithoutStealingAnotherPluginsScoreboard() throws IOException {
        String board = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/board/QuestBoard.java"));

        assertTrue(board.contains("previousBoards"));
        assertTrue(board.contains("playerBoard != mainBoard"));
        assertTrue(board.contains("playerBoard.getObjective(DisplaySlot.SIDEBAR) != null"));
        assertTrue(board.contains("previousBoard.getObjective(DisplaySlot.SIDEBAR) != null"));
        assertTrue(board.contains("player.getScoreboard() != currentBoard"));
        assertTrue(board.contains("DeliveryMode.SUPPRESSED"));
        assertTrue(board.contains("update(player, true)"));
    }

    @Test
    void handlesChannelChangesHotEnableAndDisableOrdering() throws IOException {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/CustomQuestPlugin.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/listener/PlayerListener.java"));

        assertTrue(plugin.contains("QuestTrackingPayload.register()"));
        assertTrue(plugin.contains("QuestBoard.getInstance().clearAll()"));
        assertTrue(plugin.indexOf("QuestBoard.getInstance().clearAll()")
                < plugin.indexOf("QuestTrackingPayload.unregister()"));
        assertTrue(plugin.contains("for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers())"));
        assertTrue(plugin.contains("getStorage().load(player)"));
        assertTrue(listener.contains("PlayerRegisterChannelEvent"));
        assertTrue(listener.contains("PlayerUnregisterChannelEvent"));
        assertTrue(listener.contains("QuestTrackingPayload.CHANNEL.equals(channel)"));
        assertTrue(listener.contains("QuestTrackingPayload.CHANNEL_V2.equals(channel)"));
        assertTrue(listener.contains("QuestTrackingPayload.CHANNEL_V3.equals(channel)"));
        assertTrue(listener.contains("QuestTrackingPayload.CHANNEL_V4.equals(channel)"));
        assertTrue(listener.contains("refreshChannel(online)"));
    }
}
