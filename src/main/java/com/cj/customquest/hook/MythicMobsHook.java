package com.cj.customquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * MythicMobs 挂钩：怪物内部名查询。
 */
public final class MythicMobsHook {

    private static boolean enabled = false;

    private MythicMobsHook() {
    }

    public static void init() {
        enabled = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        if (enabled) {
            Bukkit.getLogger().info("[CustomQuest] 已挂钩 MythicMobs。");
        } else {
            Bukkit.getLogger().warning("[CustomQuest] 未检测到 MythicMobs，击杀类任务将不可用。");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 判断某个实体是否为指定内部名的 MythicMobs 怪物。
     */
    public static boolean isMythicMob(String internalName, io.lumine.mythic.api.mobs.MythicMob mobType) {
        return enabled && mobType != null && mobType.getInternalName().equalsIgnoreCase(internalName);
    }

    /**
     * 使用 MythicMobs API 生成一个 MythicMob（供奖励等使用）。
     */
    public static boolean spawnMob(Player player, String internalName, int amount) {
        if (!enabled) return false;
        try {
            for (int i = 0; i < amount; i++) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mm mobs spawn " + internalName + " 1 " + player.getLocation().getWorld().getName()
                                + "," + player.getLocation().getX() + "," + player.getLocation().getY() + "," + player.getLocation().getZ());
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
