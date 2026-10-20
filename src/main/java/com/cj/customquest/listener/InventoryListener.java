package com.cj.customquest.listener;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.quest.QuestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;

/**
 * 背包内容变化后校准提交物品任务条件并刷新任务追踪。
 * 所有事件由 QuestManager 合并到下一 tick，确保读取到操作完成后的背包状态。
 * 对话箱子的点击只用于提交选项，原版物品操作全部取消。
 */
public final class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDialogueClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || DialogueManager.getInstance() == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!DialogueManager.getInstance().isDialogueContainer(player, top)) {
            return;
        }
        event.setCancelled(true);
        DialogueManager.getInstance().handleDialogueContainerClick(player, top, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDialogueDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && DialogueManager.getInstance() != null
                && DialogueManager.getInstance().isDialogueContainer(
                player, event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !isDialogueClick(player, event)) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (DialogueManager.getInstance() != null
                    && DialogueManager.getInstance().handleDialogueContainerClose(player, event.getInventory())) {
                return;
            }
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(PlayerItemBreakEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        queue(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            queue(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        queue(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        queue(event.getPlayer());
    }

    /** 对话箱子上的点击不改变背包，无需重新校准任务条件。 */
    private static boolean isDialogueClick(Player player, InventoryClickEvent event) {
        return DialogueManager.getInstance() != null
                && DialogueManager.getInstance().isDialogueContainer(
                player, event.getView().getTopInventory());
    }

    private void queue(Player player) {
        QuestManager.getInstance().queueInventoryRefresh(player);
    }
}
