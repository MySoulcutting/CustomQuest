package com.cj.customquest.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语言/消息管理（messages.yml）。
 */
public final class Messages {

    private static File file;
    private static FileConfiguration config;
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    private static final Map<String, List<String>> DEFAULT_LISTS = new HashMap<>();

    static {
        DEFAULTS.put("prefix", "&8[&6任务&8] &f");
        DEFAULTS.put("reloaded", "&a配置已重载。");
        DEFAULTS.put("no-permission", "&c你没有权限执行此命令。");
        DEFAULTS.put("player-only", "&c该命令只能由玩家执行。");
        DEFAULTS.put("player-not-found", "&c玩家不存在或不在线。");
        DEFAULTS.put("quest-not-found", "&c任务 &e{id} &c不存在。");
        DEFAULTS.put("quest-accepted", "&a已接取任务：&f{name}");
        DEFAULTS.put("quest-condition-met", "&a你已达成任务条件：&f{name}");
        DEFAULTS.put("quest-already-accepted", "&c你已经接取过该任务了。");
        DEFAULTS.put("quest-completed", "&a任务完成：&f{name}");
        DEFAULTS.put("quest-not-accepted", "&c你还没有接取该任务。");
        DEFAULTS.put("quest-abandoned", "&7已放弃任务：&f{name}");
        DEFAULTS.put("quest-command-only", "&c该任务仅可通过指令强制完成。");
        DEFAULTS.put("quest-progress-not-enough", "&c任务进度尚未完成（{current}/{total}）。");
        DEFAULTS.put("quest-requirements-not-met", "&c没有达到任务要求。");
        DEFAULTS.put("quest-repeatable", "&7该任务可重复完成。");
        DEFAULTS.put("quest-cooldown", "&c该任务冷却中，剩余 &e{time} &c秒。");
        DEFAULTS.put("quest-not-repeatable", "&c该任务已经完成过，无法重复接取。");
        DEFAULTS.put("items-not-enough", "&c缺少 &f{item} &e{missing} &c个（需要 &e{need}&c，已有 &e{have}&c）。");
        DEFAULTS.put("items-submitted", "&7已提交 &e{amount} &7个 &f{item} &7。");
        DEFAULTS.put("dialogue-no-config", "&c该 NPC 没有配置对话。");
        DEFAULTS.put("dialogue-submit-items-invalid", "&c该对话的 submit-items 只能用于提交物品任务，请联系管理员。");
        DEFAULTS.put("npc-not-found", "&cCitizens NPC &e{id} &c不存在。");
        DEFAULTS.put("npc-data-set", "&a已为玩家 &f{player} &a设置 NPC &f{id} &a的数据 &f{key} &a= &f{value} &a。");
        DEFAULTS.put("npc-data-get", "&7玩家 &f{player} &7在 NPC &f{id} &7的数据 &f{key} &7= &f{value}");
        DEFAULTS.put("npc-data-removed", "&a已删除玩家 &f{player} &a在 NPC &f{id} &a的数据 &f{key} &a。");
        DEFAULTS.put("npc-data-missing", "&7玩家 &f{player} &7在 NPC &f{id} &7没有数据 &f{key} &7。");
        DEFAULTS.put("nav-no-location", "&c任务 &e{name} &c未配置导航位置。");
        DEFAULTS.put("nav-client-required", "&c任务导航需要安装并启用 SoulCore NeoForge 客户端 Mod。");
        DEFAULTS.put("nav-start", "&a已开始导航：&f{name}");
        DEFAULTS.put("nav-cancelled", "&7已取消导航。");
        DEFAULTS.put("nav-arrived", "&a已到达导航目标：&f{name}");
        DEFAULTS.put("nav-cross-world", "&c导航目标与当前不在同一世界，已取消导航。");
        DEFAULTS.put("nav-set", "&a已设置任务 &f{name} &a的导航位置为你的当前位置。");
        DEFAULTS.put("nav-removed", "&a已移除任务 &f{name} &a的导航位置。");
        // 接取任务时显示的目标信息
        DEFAULTS.put("quest-target-kill", "&7目标[{index}]：击败 &e{amount} &7只 &f{display}");
        DEFAULTS.put("quest-target-item", "&7目标[{index}]：提交 &e{amount} &7个 &f{display}");
        // 指令用法提示
        DEFAULTS.put("usage-quest", "&7/cq quest <accept|abandon|complete|nav> ...");
        DEFAULTS.put("usage-quest-accept", "&7/cq quest accept <玩家> <任务ID>");
        DEFAULTS.put("usage-quest-abandon", "&7/cq quest abandon <玩家> <任务ID>");
        DEFAULTS.put("usage-quest-complete", "&7/cq quest complete <玩家> <任务ID>");
        DEFAULTS.put("usage-quest-nav", "&7/cq quest nav <set|remove> <任务ID>");
        DEFAULTS.put("usage-data", "&7/cq data <set|get|remove> <玩家> <npcId> <key> [value]");
        DEFAULTS.put("usage-data-set", "&7/cq data set <玩家> <npcId> <key> <value>");
        // 任务列表
        DEFAULTS.put("list-empty", "&7没有已加载的任务。");
        DEFAULTS.put("list-header", "&7任务列表（共 &e{count} &7个）：");
        DEFAULTS.put("list-entry", "&7- &f{id} &7[{type}] &e{name}");
        // 帮助标题
        DEFAULTS.put("help-header", "&6===== &fCustomQuest 指令帮助 &6=====");

        // /cq help 的每一行（可自行增删改）
        DEFAULT_LISTS.put("help", List.of(
                "&7/cq help &f- 查看帮助",
                "&7/cq list &f- 列出所有任务",
                "&7/cq reload &f- 重载配置",
                "&7/cq quest accept <玩家> <任务ID> &f- 强制接取任务",
                "&7/cq quest abandon <玩家> <任务ID> &f- 放弃任务",
                "&7/cq quest complete <玩家> <任务ID> &f- 强制完成任务",
                "&7/cq quest nav set <任务ID> &f- 设置任务导航位置为你的当前位置",
                "&7/cq quest nav remove <任务ID> &f- 移除任务导航位置",
                "&7/cq data set|get|remove <玩家> <npcId> <key> [value] &f- 管理玩家级 NPC data 变量"
        ));
    }

    private Messages() {
    }

    public static void load(File dataFolder) {
        file = new File(dataFolder, "messages.yml");
        if (!file.exists()) {
            try {
                dataFolder.mkdirs();
                file.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        for (Map.Entry<String, List<String>> entry : DEFAULT_LISTS.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        // 清理旧版任务书帮助与专用消息，同时保留 /cq quest ... 管理子命令。
        if (config.isList("help")) {
            List<String> help = new ArrayList<>(config.getStringList("help"));
            if (help.removeIf(Messages::isQuestBookHelpLine)) {
                config.set("help", help);
                changed = true;
            }
        }
        if (config.contains("no-quests")) {
            config.set("no-quests", null);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public static void save() {
        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        String value = config.getString(key, DEFAULTS.getOrDefault(key, key));
        return TextUtil.color(value);
    }

    public static String get(String key, Map<String, Object> replace) {
        String value = config.getString(key, DEFAULTS.getOrDefault(key, key));
        for (Map.Entry<String, Object> entry : replace.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return TextUtil.color(value);
    }

    /** 读取列表型消息（如 help），逐行上色 */
    public static List<String> getList(String key) {
        List<String> values = config.getStringList(key);
        if (values.isEmpty()) {
            values = DEFAULT_LISTS.getOrDefault(key, List.of());
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(TextUtil.color(value));
        }
        return result;
    }

    public static String prefix() {
        return get("prefix");
    }

    public static void send(CommandSender sender, String key) {
        sender.sendMessage(prefix() + get(key));
    }

    public static void send(CommandSender sender, String key, Map<String, Object> replace) {
        sender.sendMessage(prefix() + get(key, replace));
    }

    public static void sendRaw(CommandSender sender, String text) {
        sender.sendMessage(TextUtil.color(text));
    }

    public static void sendTo(Player player, String key) {
        send((CommandSender) player, key);
    }

    public static void sendTo(Player player, String key, Map<String, Object> replace) {
        send((CommandSender) player, key, replace);
    }

    public static void broadcast(String key) {
        String message = prefix() + get(key);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    static boolean isQuestBookHelpLine(String value) {
        if (value == null) {
            return false;
        }
        String plain = value.replaceAll("(?i)&[0-9A-FK-ORX]", "").trim().toLowerCase(java.util.Locale.ROOT);
        return plain.equals("/quest") || plain.startsWith("/quest ");
    }
}
