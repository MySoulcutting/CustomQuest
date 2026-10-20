package com.cj.customquest.util;

import com.cj.customquest.hook.PapiHook;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文本工具：颜色代码与占位符替换。
 */
public final class TextUtil {

    private TextUtil() {
    }

    /** 将 & 颜色代码转换为颜色，并替换 %player% 与 PAPI 变量 */
    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** 对玩家应用 PAPI 占位符（自动处理 %player% -> 玩家名） */
    public static String papi(Player player, String text) {
        if (text == null) return "";
        String result = text.replace("%player%", player.getName());
        return PapiHook.setPlaceholders(player, result);
    }

    /** 颜色 + PAPI 一步到位 */
    public static String parse(Player player, String text) {
        return color(papi(player, text));
    }

    public static List<String> parse(Player player, List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(line -> parse(player, line)).collect(Collectors.toList());
    }
}
