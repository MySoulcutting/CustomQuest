package com.cj.customquest.hook;

import org.bukkit.Bukkit;

/** EpicCraftingsPlus 挂钩状态。 */
public final class EpicCraftingsPlusHook {

    private static boolean enabled;

    private EpicCraftingsPlusHook() {
    }

    public static void init() {
        enabled = Bukkit.getPluginManager().getPlugin("EpicCraftingsPlus") != null;
        if (enabled) {
            Bukkit.getLogger().info("[CustomQuest] 已挂钩 EpicCraftingsPlus。");
        } else {
            Bukkit.getLogger().warning("[CustomQuest] 未检测到 EpicCraftingsPlus，合成类任务将不可用。");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
