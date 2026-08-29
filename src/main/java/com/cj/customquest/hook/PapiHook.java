package com.cj.customquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 挂钩。
 */
public final class PapiHook {

    private static boolean enabled = false;

    private PapiHook() {
    }

    public static void init() {
        enabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (enabled) {
            Bukkit.getLogger().info("[CustomQuest] 已挂钩 PlaceholderAPI。");
        } else {
            Bukkit.getLogger().warning("[CustomQuest] 未检测到 PlaceholderAPI，PAPI 条件将无法使用。");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String setPlaceholders(Player player, String text) {
        if (!enabled || player == null || text == null) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable e) {
            return text;
        }
    }
}
