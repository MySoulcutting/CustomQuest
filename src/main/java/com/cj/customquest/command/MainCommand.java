package com.cj.customquest.command;

import com.cj.customquest.CustomQuestPlugin;
import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.dialogue.DialogueSnapshot;
import com.cj.customquest.hook.CitizensHook;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.util.Messages;
import kotlin.Unit;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import taboolib.common.platform.PlatformFactory;
import taboolib.common.platform.ProxyCommandSender;
import taboolib.common.platform.command.CommandStructure;
import taboolib.common.platform.command.PermissionDefault;
import taboolib.common.platform.service.PlatformCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 主指令 /cq（别名 /customquest）。
 */
public final class MainCommand {

    private static final String ADMIN_PERMISSION = "customquest.admin";
    private static final List<String> SUB_COMMANDS = List.of(
            "help", "reload", "list", "quest", "data");

    private MainCommand() {
    }

    public static void register() {
        PlatformCommand platformCommand = PlatformFactory.INSTANCE.getService(
                "taboolib.common.platform.service.PlatformCommand");

        CommandStructure structure = new CommandStructure(
                "cq",
                List.of("customquest"),
                "CustomQuest 任务插件主指令",
                "/cq help",
                "customquest.use",
                "",
                PermissionDefault.TRUE,
                Map.of(),
                false
        );

        platformCommand.registerCommand(structure,
                (sender, command, label, args) -> onCommand(sender, args),
                (sender, command, label, args) -> onTabComplete(sender, args),
                base -> Unit.INSTANCE);

    }

    private static boolean onCommand(ProxyCommandSender proxy, String[] args) {
        CommandSender sender = (CommandSender) proxy.getOrigin();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "reload" -> {
                if (!requireAdmin(sender)) return true;
                CustomQuestPlugin.reloadAll();
                Messages.send(sender, "reloaded");
            }
            case "list" -> {
                if (!requireAdmin(sender)) return true;
                listQuests(sender);
            }
            case "quest" -> handleQuest(sender, args);
            case "data" -> handleData(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ---------------- quest 子指令 ----------------

    private static void handleQuest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Messages.send(sender, "usage-quest");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "accept" -> {
                if (!requireAdmin(sender)) return;
                if (args.length < 4) {
                    Messages.send(sender, "usage-quest-accept");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                Quest quest = QuestManager.getInstance().getQuest(args[3]);
                if (target == null) {
                    Messages.send(sender, "player-not-found");
                    return;
                }
                if (quest == null) {
                    Messages.send(sender, "quest-not-found", Map.of("id", args[3]));
                    return;
                }
                QuestManager.getInstance().acceptQuest(target, quest, true);
            }
            case "abandon" -> {
                if (!requireAdmin(sender)) return;
                if (args.length < 4) {
                    Messages.send(sender, "usage-quest-abandon");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                Quest quest = QuestManager.getInstance().getQuest(args[3]);
                if (target == null) {
                    Messages.send(sender, "player-not-found");
                    return;
                }
                if (quest == null) {
                    Messages.send(sender, "quest-not-found", Map.of("id", args[3]));
                    return;
                }
                QuestManager.getInstance().abandonQuest(target, quest);
            }
            case "complete" -> {
                if (!requireAdmin(sender)) return;
                if (args.length < 4) {
                    Messages.send(sender, "usage-quest-complete");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                Quest quest = QuestManager.getInstance().getQuest(args[3]);
                if (target == null) {
                    Messages.send(sender, "player-not-found");
                    return;
                }
                if (quest == null) {
                    Messages.send(sender, "quest-not-found", Map.of("id", args[3]));
                    return;
                }
                QuestManager.getInstance().completeQuest(target, quest, true);
            }
            case "nav" -> handleQuestNav(sender, args);
            default -> Messages.send(sender, "usage-quest");
        }
    }

    // ---------------- data 子指令（玩家级 NPC data 变量） ----------------

    private static void handleData(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 4) {
            Messages.send(sender, "usage-data");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            Messages.send(sender, "player-not-found");
            return;
        }
        int npcId = parseInt(args[3], -1);
        NPC npc = CitizensHook.getNpc(npcId);
        if (npc == null) {
            Messages.send(sender, "npc-not-found", Map.of("id", npcId));
            return;
        }
        switch (action) {
            case "set" -> {
                if (args.length < 5) {
                    Messages.send(sender, "usage-data-set");
                    return;
                }
                String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                CitizensHook.setData(target, npc, value);
                Messages.send(sender, "npc-data-set", Map.of(
                        "player", target.getName(), "id", npcId, "value", value));
            }
            case "get" -> {
                String value = CitizensHook.getData(target, npc);
                if (value == null) {
                    Messages.send(sender, "npc-data-missing", Map.of("player", target.getName(), "id", npcId));
                } else {
                    Messages.send(sender, "npc-data-get", Map.of(
                            "player", target.getName(), "id", npcId, "value", value));
                }
            }
            case "remove" -> {
                CitizensHook.removeData(target, npc);
                Messages.send(sender, "npc-data-removed", Map.of("player", target.getName(), "id", npcId));
            }
            default -> Messages.send(sender, "usage-data");
        }
    }

    // ---------------- click 子指令（内部：对话选项点击回调） ----------------


    // ---------------- 其他 ----------------

    private static void listQuests(CommandSender sender) {
        var quests = QuestManager.getInstance().getQuests();
        if (quests.isEmpty()) {
            Messages.send(sender, "list-empty");
            return;
        }
        Messages.send(sender, "list-header", Map.of("count", quests.size()));
        for (Quest quest : quests.values()) {
            sender.sendMessage(Messages.get("list-entry", Map.of(
                    "id", quest.getId(), "type", quest.getType().getDisplay(), "name", quest.getName())));
        }
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help-header"));
        for (String line : Messages.getList("help")) {
            sender.sendMessage(line);
        }
    }

    private static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        Messages.send(sender, "no-permission");
        return false;
    }

    private static int parseInt(String text, int def) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ---------------- quest nav 子指令（设置/移除任务导航位置） ----------------

    /**
     * /cq quest nav set &lt;任务ID&gt; —— 以执行者当前位置作为该任务的导航点并写回任务 yml；
     * /cq quest nav remove &lt;任务ID&gt; —— 移除导航点（同样写回任务 yml）。
     */
    private static void handleQuestNav(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            Messages.send(sender, "usage-quest-nav");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        Quest quest = QuestManager.getInstance().getQuest(args[3]);
        if (quest == null) {
            Messages.send(sender, "quest-not-found", Map.of("id", args[3]));
            return;
        }
        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    Messages.send(sender, "player-only");
                    return;
                }
                Location location = player.getLocation();
                boolean saved = QuestManager.getInstance().setNavigate(quest.getId(),
                        location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
                if (!saved) {
                    Messages.send(sender, "nav-save-failed");
                    return;
                }
                NavigationManager.getInstance().setTarget(quest.getId(), location);
                Messages.send(sender, "nav-set", Map.of("name", quest.getName()));
                // 追踪按钮的显隐与预留列要立刻跟着变
                QuestBoard.getInstance().refreshTracked();
            }
            case "remove" -> {
                if (!QuestManager.getInstance().removeNavigate(quest.getId())) {
                    Messages.send(sender, "nav-save-failed");
                    return;
                }
                NavigationManager.getInstance().removeTarget(quest.getId());
                Messages.send(sender, "nav-removed", Map.of("name", quest.getName()));
                QuestBoard.getInstance().refreshTracked();
            }
            default -> Messages.send(sender, "usage-quest-nav");
        }
    }

    // ---------------- 补全 ----------------

    private static List<String> onTabComplete(ProxyCommandSender proxy, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "quest" -> {
                if (args.length == 2) {
                    return filter(List.of("accept", "abandon", "complete", "nav"), args[1]);
                }
                if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("nav")) {
                        return filter(List.of("set", "remove"), args[2]);
                    }
                    if (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("abandon")
                            || args[1].equalsIgnoreCase("complete")) {
                        return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
                    }
                }
                if (args.length == 4) {
                    // 放弃/完成只能针对已接取的任务，补全也按该玩家的实际接取列表来
                    boolean onlyAccepted = args[1].equalsIgnoreCase("abandon")
                            || args[1].equalsIgnoreCase("complete");
                    return filter(onlyAccepted ? acceptedQuestIds(args[2]) : questIds(), args[3]);
                }
            }
            case "data" -> {
                if (args.length == 2) {
                    return filter(List.of("set", "get", "remove"), args[1]);
                }
                if (args.length == 3) {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
                }
                if (args.length == 4) {
                    return filter(CitizensHook.listNpcIds(), args[3]);
                }
            }
            default -> {
            }
        }
        return result;
    }

    private static List<String> questIds() {
        return new ArrayList<>(QuestManager.getInstance().getQuests().keySet());
    }

    /** 指定玩家当前已接取的任务 ID；玩家不在线或不存在时返回空列表。 */
    private static List<String> acceptedQuestIds(String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        QuestManager manager = QuestManager.getInstance();
        if (target == null || manager == null) {
            return List.of();
        }
        return new ArrayList<>(manager.getPlayerData(target).getAccepted().keySet());
    }

    private static List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
