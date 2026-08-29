package com.cj.customquest.listener;

import com.cj.customquest.quest.QuestManager;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * MythicMobs 击杀监听：击杀类任务进度。
 */
public class MythicMobListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player player) {
            if (event.getMobType() == null) return;
            QuestManager.getInstance().onMythicMobKill(player, event.getMobType().getInternalName());
        }
    }
}
