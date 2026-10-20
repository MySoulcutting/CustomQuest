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
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

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
        DEFAULTS.put("dialogue-submit-items-invalid", "&c该对话的 submit-items 只能用于提交物品任务，请联系管理员。");
        DEFAULTS.put("dialogue-display-recover-failed", "&c对话界面未能加载，请关闭后重新与 NPC 交互。");
        DEFAULTS.put("npc-not-found", "&cCitizens NPC &e{id} &c不存在。");
        DEFAULTS.put("npc-data-set", "&a已为玩家 &f{player} &a设置 NPC &f{id} &a的变量值为 &f{value}&a。");
        DEFAULTS.put("npc-data-get", "&7玩家 &f{player} &7在 NPC &f{id} &7的变量值为 &f{value}");
        DEFAULTS.put("npc-data-removed", "&a已删除玩家 &f{player} &a在 NPC &f{id} &a上的变量。");
        DEFAULTS.put("npc-data-missing", "&7玩家 &f{player} &7在 NPC &f{id} &7尚未设置变量。");
        // 任务导航（视觉由 RPGCore 客户端模组绘制）
        DEFAULTS.put("nav-start", "&a已开始导航：&f{name}");
        DEFAULTS.put("nav-cancelled", "&7已取消导航：&f{name}");
        DEFAULTS.put("nav-no-location", "&c任务 &f{name} &c没有可用的导航点（请检查任务文件的 navigate）。");
        DEFAULTS.put("nav-cross-world", "&c目标不在当前世界，无法导航。");
        DEFAULTS.put("nav-client-required", "&c需要安装 RPGCore 客户端才能使用任务导航。");
        // 物品栏上方（动作栏）的导航开关提示；动作栏不带聊天前缀
        DEFAULTS.put("nav-actionbar-start", "&a你已开启 &f{name} &a任务导航");
        DEFAULTS.put("nav-actionbar-stop", "&7你已关闭 &f{name} &7任务导航");
        DEFAULTS.put("nav-set", "&a已设置任务 &f{name} &a的导航位置为你的当前位置。");
        DEFAULTS.put("nav-removed", "&a已移除任务 &f{name} &a的导航位置。");
        DEFAULTS.put("nav-save-failed", "&c写回任务文件失败，请检查控制台日志。");
        DEFAULTS.put("usage-quest-nav", "&7/cq quest nav <set|remove> <任务ID>   &f（set 取你当前位置）");
        // 接取任务时显示的目标信息
        DEFAULTS.put("quest-target-kill", "&7目标[{index}]：击败 &e{amount} &7只 &f{display}");
        DEFAULTS.put("quest-target-item", "&7目标[{index}]：提交 &e{amount} &7个 &f{display}");
        // 指令用法提示
        DEFAULTS.put("usage-quest", "&7/cq quest <accept|abandon|complete> ...");
        DEFAULTS.put("usage-quest-accept", "&7/cq quest accept <玩家> <任务ID>");
        DEFAULTS.put("usage-quest-abandon", "&7/cq quest abandon <玩家> <任务ID>");
        DEFAULTS.put("usage-quest-complete", "&7/cq quest complete <玩家> <任务ID>");
        DEFAULTS.put("usage-data", "&7/cq data <set|get|remove> <玩家> <npcId> [value]");
        DEFAULTS.put("usage-data-set", "&7/cq data set <玩家> <npcId> <value>");
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
                "&7/cq quest nav set|remove <任务ID> &f- 设置或移除任务导航位置",
                "&7/cq data set <玩家> <npcId> <value> &f- 设置玩家级 NPC 变量",
                "&7/cq data get|remove <玩家> <npcId> &f- 查看或删除玩家级 NPC 变量"
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
                Bukkit.getLogger().log(Level.WARNING,
                        "[CustomQuest] 创建消息配置文件失败: " + file.getAbsolutePath(), e);
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
        for (String key : List.of("npc-data-set", "npc-data-get", "npc-data-removed", "npc-data-missing")) {
            String value = config.getString(key);
            if (value != null && value.contains("{key}")) {
                config.set(key, DEFAULTS.get(key));
                changed = true;
            }
        }
        // 导航指令与导航消息都已恢复使用，所以这里不再清理 /cq quest nav 的帮助行。
        // 只清理旧版任务书帮助行，同时保留 /cq quest ... 管理子命令。
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
            Bukkit.getLogger().log(Level.WARNING,
                    "[CustomQuest] 保存消息配置文件失败: " + file.getAbsolutePath(), e);
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

    /**
     * 解析一条面向玩家的短提示文案（颜色与占位符已处理，**不带聊天前缀**）。
     *
     * <p>页面提示与动作栏兜底共用它，保证两种显示方式的文字完全一致。
     */
    public static String tipText(Player player, String key, Map<String, Object> replace) {
        return TextUtil.parse(player, get(key, replace));
    }

    /** 在物品栏上方（动作栏）提示玩家。与聊天消息共用同一套文案键，但**不带聊天前缀**。 */
    public static void sendActionBar(Player player, String key, Map<String, Object> replace) {
        sendActionBar(player, tipText(player, key, replace));
    }

    /** 直接发一段已经解析好的文本到动作栏。 */
    public static void sendActionBar(Player player, String text) {
        if (player == null || text == null) {
            return;
        }
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(text));
    }

    public static void broadcast(String key) {
        String message = prefix() + get(key);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    static boolean isQuestBookHelpLine(String value) {
        if (value == null) {
            return false;
        }
        String plain = value.replaceAll("(?i)&[0-9A-FK-ORX]", "").trim().toLowerCase(Locale.ROOT);
        return plain.equals("/quest") || plain.startsWith("/quest ");
    }

}
