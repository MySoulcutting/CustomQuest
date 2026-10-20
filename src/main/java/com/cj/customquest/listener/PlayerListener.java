package com.cj.customquest.listener;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.hook.RpgCoreHook;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.navigation.NavigationPayload;
import com.cj.customquest.quest.QuestManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家数据加载/保存与任务追踪清理。
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
        // 退出时清掉导航状态与请求限流记录（客户端断线会自行清空方向显示）
        NavigationManager.getInstance().remove(event.getPlayer());
        NavigationPayload.clearRequestState(event.getPlayer());
        RpgCoreHook.forget(event.getPlayer());
        QuestManager.getInstance().getStorage().unload(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        DialogueManager.getInstance().closeDialogue(event.getPlayer());
    }
}
