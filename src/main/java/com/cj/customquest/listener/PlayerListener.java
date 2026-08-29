package com.cj.customquest.listener;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.quest.QuestManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家数据加载/保存与计分板清理。
 */
public class PlayerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        QuestManager.getInstance().getStorage().load(event.getPlayer());
        QuestBoard.getInstance().update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        QuestBoard.getInstance().remove(event.getPlayer());
        NavigationManager.getInstance().remove(event.getPlayer());
        QuestManager.getInstance().getStorage().unload(event.getPlayer());
    }
}
