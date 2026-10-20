package com.cj.customquest.listener;

import com.cj.customquest.dialogue.DialogueManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Citizens NPC 交互监听：打开 NPC 对话。
 */
public class NpcListener implements Listener {

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        int npcId = event.getNPC().getId();
        DialogueManager.getInstance().openDialogue(player, npcId, null);
    }
}
