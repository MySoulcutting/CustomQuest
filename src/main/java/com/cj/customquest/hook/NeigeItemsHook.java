package com.cj.customquest.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** NeigeItems 可选软挂钩，不在编译期或启动加载时强制依赖 NI。 */
public final class NeigeItemsHook {
    private static boolean enabled;
    private static Method getItemId;

    private NeigeItemsHook() {
    }

    public static void init() {
        enabled = false;
        getItemId = null;
        if (Bukkit.getPluginManager().getPlugin("NeigeItems") == null) {
            return;
        }
        try {
            Class<?> itemUtils = Class.forName("pers.neige.neigeitems.utils.ItemUtils");
            getItemId = itemUtils.getMethod("getItemId", ItemStack.class);
            enabled = true;
            Bukkit.getLogger().info("[CustomQuest] 已启用 NeigeItems 物品提交支持。");
        } catch (Throwable exception) {
            Bukkit.getLogger().warning("[CustomQuest] 检测到 NeigeItems，但无法加载其 API：" + exception.getMessage());
        }
    }

    public static String getItemId(ItemStack itemStack) {
        if (!enabled || itemStack == null) return null;
        try {
            Object value = getItemId.invoke(null, itemStack);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }
}