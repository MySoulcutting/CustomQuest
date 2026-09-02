package com.cj.customquest.hook;

import com.cj.customquest.quest.QuestManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Citizens2 挂钩：NPC 查询与玩家级 NPC data 变量操作。
 * <p>
 * 重要：NPC data 变量按<b>玩家</b>存储（每个玩家在同一 NPC 上有自己独立的 data 值），
 * 因此不同玩家可以处于不同的对话分支；数据持久化在插件的 SQLite data.db 中。
 */
public final class CitizensHook {

    private static boolean enabled = false;

    private CitizensHook() {
    }

    public static void init() {
        enabled = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        if (enabled) {
            Bukkit.getLogger().info("[CustomQuest] 已挂钩 Citizens2。");
        } else {
            Bukkit.getLogger().warning("[CustomQuest] 未检测到 Citizens2，NPC 对话功能将不可用。");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** 按 id 获取 NPC */
    public static NPC getNpc(int id) {
        if (!enabled || id < 0) return null;
        try {
            return CitizensAPI.getNPCRegistry().getById(id);
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------------- 玩家级 NPC data ----------------

    /** 获取玩家在指定 NPC 上的 data 值（不存在返回 null） */
    public static String getData(Player player, NPC npc, String key) {
        if (player == null || npc == null) return null;
        Map<String, String> data = dataOf(player, npc.getId());
        return data == null ? null : data.get(key);
    }

    /** 获取玩家在指定 NPC 上的唯一变量值（变量标识使用 NPC ID）。 */
    public static String getData(Player player, NPC npc) {
        return npc == null ? null : getData(player, npc, String.valueOf(npc.getId()));
    }

    /** 获取玩家在指定 NPC 上的 data 值（带默认值） */
    public static String getData(Player player, NPC npc, String key, String def) {
        String value = getData(player, npc, key);
        return value == null ? def : value;
    }

    /** 写入玩家在指定 NPC 上的 data 值（持久化，每个玩家独立） */
    public static void setData(Player player, NPC npc, String key, String value) {
        if (player == null || npc == null) return;
        Map<String, String> data = dataOf(player, npc.getId());
        if (data != null) {
            data.put(key, value == null ? "" : value);
        }
    }

    /** 写入玩家在指定 NPC 上的唯一变量值（变量标识使用 NPC ID）。 */
    public static void setData(Player player, NPC npc, String value) {
        if (npc == null) return;
        if (value == null || value.trim().equalsIgnoreCase("null")) {
            removeData(player, npc);
            return;
        }
        setData(player, npc, String.valueOf(npc.getId()), value);
    }

    /** 删除玩家在指定 NPC 上的 data 值 */
    public static void removeData(Player player, NPC npc, String key) {
        if (player == null || npc == null) return;
        Map<String, String> data = dataOf(player, npc.getId());
        if (data != null) {
            data.remove(key);
        }
    }

    /** 删除玩家在指定 NPC 上的唯一变量值。 */
    public static void removeData(Player player, NPC npc) {
        if (npc == null) return;
        removeData(player, npc, String.valueOf(npc.getId()));
    }

    /** 玩家在指定 NPC 上是否存在该 data */
    public static boolean hasData(Player player, NPC npc, String key) {
        if (player == null || npc == null) return false;
        Map<String, String> data = dataOf(player, npc.getId());
        return data != null && data.containsKey(key);
    }

    /**
     * 批量判断玩家在指定 NPC 上的 data 条件（全部成立才为 true）。
     * 支持：{@code key}（存在）、{@code key==value}、{@code key!=value}、
     * {@code key>n}、{@code key>=n}、{@code key<n}、{@code key<=n}。
     */
    public static boolean checkDataConditions(Player player, NPC npc, List<String> conditions) {
        if (player == null || npc == null) return false;
        if (conditions == null || conditions.isEmpty()) return true;
        for (String raw : conditions) {
            if (raw == null) {
                continue;
            }
            String condition = raw.trim();
            if (condition.isEmpty()) continue;

            String op = null;
            for (String candidate : new String[]{"!=", "==", ">=", "<=", ">", "<"}) {
                if (condition.contains(candidate)) {
                    op = candidate;
                    break;
                }
            }
            if (op == null) {
                // 仅 key：存在即可
                if (!hasData(player, npc, condition)) return false;
                continue;
            }
            String[] parts = condition.split(java.util.regex.Pattern.quote(op), 2);
            String key = parts[0].trim();
            String expected = parts.length > 1 ? parts[1].trim() : "";
            String actual = getData(player, npc, key);
            if (!compare(actual, expected, op)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> dataOf(Player player, int npcId) {
        QuestManager manager = QuestManager.getInstance();
        if (manager == null || manager.getStorage() == null || player == null) {
            return null;
        }
        return manager.getPlayerData(player).npcDataOf(npcId);
    }

    private static boolean compare(String actual, String expected, String op) {
        if (actual == null) {
            return switch (op) {
                case "==" -> expected.equalsIgnoreCase("null");
                case "!=" -> !expected.equalsIgnoreCase("null");
                default -> false;
            };
        }
        if (expected.equalsIgnoreCase("null")) {
            return op.equals("!=");
        }
        switch (op) {
            case "==":
                return actual.equals(expected);
            case "!=":
                return !actual.equals(expected);
            default:
                break;
        }
        Double a = number(actual);
        Double b = number(expected);
        if (a != null && b != null) {
            switch (op) {
                case ">":
                    return a > b;
                case "<":
                    return a < b;
                case ">=":
                    return a >= b;
                case "<=":
                    return a <= b;
                default:
                    return false;
            }
        }
        switch (op) {
            case ">":
                return actual.compareTo(expected) > 0;
            case "<":
                return actual.compareTo(expected) < 0;
            case ">=":
                return actual.compareTo(expected) >= 0;
            case "<=":
                return actual.compareTo(expected) <= 0;
            default:
                return false;
        }
    }

    private static Double number(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 列出当前在线 NPC 的 id（供补全使用） */
    public static List<String> listNpcIds() {
        List<String> ids = new ArrayList<>();
        if (!enabled) return ids;
        try {
            CitizensAPI.getNPCRegistry().forEach(npc -> ids.add(String.valueOf(npc.getId())));
        } catch (Throwable ignored) {
        }
        return ids;
    }
}
