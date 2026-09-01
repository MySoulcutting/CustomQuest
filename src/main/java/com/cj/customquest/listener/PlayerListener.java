package com.cj.customquest.listener;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.navigation.NavigationPayload;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.tracking.QuestTrackingPayload;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import taboolib.platform.BukkitPlugin;

import java.util.UUID;

/**
 * 玩家数据加载/保存、任务追踪清理与 SoulCore 通道切换。
 */
public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        QuestManager.getInstance().getStorage().load(event.getPlayer());
        QuestBoard.getInstance().update(event.getPlayer());
        QuestManager.getInstance().queueConditionCheck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        DialogueManager.getInstance().remove(event.getPlayer());
        QuestBoard.getInstance().remove(event.getPlayer());
        NavigationPayload.clearRequestState(event.getPlayer());
        NavigationManager.getInstance().remove(event.getPlayer());
        QuestManager.getInstance().getStorage().unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        queueChannelRefresh(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelUnregister(PlayerUnregisterChannelEvent event) {
        queueChannelRefresh(event.getPlayer(), event.getChannel());
    }

    private void queueChannelRefresh(Player player, String channel) {
        if (!QuestTrackingPayload.CHANNEL.equals(channel)
                && !QuestTrackingPayload.CHANNEL_V2.equals(channel)
                && !QuestTrackingPayload.CHANNEL_V3.equals(channel)
                && !QuestTrackingPayload.CHANNEL_V4.equals(channel)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline() && QuestBoard.getInstance() != null) {
                QuestBoard.getInstance().refreshChannel(online);
            }
        });
    }
}
