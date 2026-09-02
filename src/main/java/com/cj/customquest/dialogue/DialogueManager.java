package com.cj.customquest.dialogue;

import com.cj.customquest.condition.ConditionParser;
import com.cj.customquest.hook.CitizensHook;
import com.cj.customquest.kether.KetherRunner;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.util.TextUtil;
import net.citizensnpcs.api.npc.NPC;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * NPC 对话管理：加载配置、选择分支，并通过 SoulCore UI 或安全聊天回退执行选项。
 */
public final class DialogueManager {

    /** 点击聊天回退选项时执行的内部指令。 */
    public static final String CLICK_COMMAND = "cq";
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 36.0;

    private static DialogueManager instance;

    private File dialoguesFolder;
    private final Map<Integer, List<DialogueConfig>> byNpc = new HashMap<>();
    private final DialogueSessionStore sessions = new DialogueSessionStore();

    public static DialogueManager getInstance() {
        return instance;
    }

    public static void create(File dataFolder) {
        instance = new DialogueManager();
        instance.dialoguesFolder = new File(dataFolder, "dialogues");
        instance.reload();
    }

    public void reload() {
        closeAll();
        byNpc.clear();
        dialoguesFolder.mkdirs();
        File[] files = dialoguesFolder.listFiles((dir, name) -> isYamlFile(name));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                DialogueConfig dialogue = DialogueConfig.load(file.getName(), config);
                for (String warning : dialogue.getWarnings()) {
                    Bukkit.getLogger().warning("[CustomQuest] " + warning);
                }
                if (dialogue.getNpcIds().isEmpty()) {
                    Bukkit.getLogger().warning("[CustomQuest] 对话文件 " + file.getName() + " 未配置 npc，已忽略。");
                    continue;
                }
                for (int npcId : dialogue.getNpcIds()) {
                    byNpc.computeIfAbsent(npcId, ignored -> new ArrayList<>()).add(dialogue);
                }
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[CustomQuest] 对话文件 " + file.getName() + " 加载失败: " + e.getMessage());
            }
        }
    }

    private static boolean isYamlFile(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.ya?ml$");
    }

    public File getDialoguesFolder() {
        return dialoguesFolder;
    }

    /**
     * 玩家点击 NPC 时打开对话。
     *
     * @param forceBranchId 强制显示的分支 id（Kether 再次打开对话时使用），可为 null
     */
    public void openDialogue(Player player, int npcId, String forceBranchId) {
        if (!CitizensHook.isEnabled()) {
            return;
        }
        List<DialogueConfig> configs = byNpc.get(npcId);
        if (configs == null || configs.isEmpty()) {
            return;
        }
        NPC npc = CitizensHook.getNpc(npcId);
        if (npc == null) {
            return;
        }

        // 首次对话时初始化玩家级 data 变量（default-data）。
        for (DialogueConfig config : configs) {
            for (Map.Entry<String, String> entry : config.getDefaultData().entrySet()) {
                if (!CitizensHook.hasData(player, npc, entry.getKey())) {
                    CitizensHook.setData(player, npc, entry.getKey(), entry.getValue());
                }
            }
        }

        // 打开提交 NPC 时再校准一次背包/击杀条件，覆盖其他插件直接改背包而未触发事件的情况。
        QuestManager.getInstance().checkConditionStates(player);

        MatchedDialogue matched = findBranch(player, npc, configs, forceBranchId);
        if (matched == null) {
            return;
        }
        display(player, matched.config(), matched.branch(), npcId, null);
    }

    /** 在已有会话中跳转到指定分支，避免重新创建会话和重置客户端界面。 */
    public void gotoDialogue(Player player, DialogueSessionStore.Session previousSession, String targetBranchId) {
        if (player == null || previousSession == null || targetBranchId == null || targetBranchId.isBlank()) {
            return;
        }
        Runnable navigate = () -> {
            if (!player.isOnline()) {
                sessions.cancelTransition(previousSession.playerId(), previousSession.id());
                return;
            }
            DialogueConfig config = findConfig(previousSession.npcId(), previousSession.dialogueFile());
            DialogueBranch target = findBranch(config, targetBranchId);
            NPC npc = validInteractionNpc(player, previousSession.npcId());
            if (config == null || target == null || npc == null) {
                sessions.cancelTransition(previousSession.playerId(), previousSession.id());
                DialoguePayload.sendClose(player, previousSession.id());
                return;
            }
            display(player, config, target, previousSession.npcId(), previousSession);
        };
        if (Bukkit.isPrimaryThread()) {
            navigate.run();
        } else {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), navigate);
        }
    }

    private MatchedDialogue findBranch(Player player, NPC npc, List<DialogueConfig> configs,
                                       String forceBranchId) {
        if (forceBranchId != null) {
            for (DialogueConfig config : configs) {
                DialogueBranch target = findBranch(config, forceBranchId);
                if (target != null) {
                    return new MatchedDialogue(config, target);
                }
            }
            return null;
        }
        for (DialogueConfig config : configs) {
            for (DialogueBranch candidate : config.getBranches()) {
                if (config.isNodeFormat() && !config.isEntryBranch(candidate.getId())) {
                    continue;
                }
                if (!config.isNodeFormat() && !candidate.hasConditions()) {
                    continue;
                }
                if (matches(player, npc, candidate)) {
                    return new MatchedDialogue(config, candidate);
                }
            }
        }
        return null;
    }

    private boolean matches(Player player, NPC npc, DialogueBranch branch) {
        if (!CitizensHook.checkDataConditions(player, npc, branch.getDataConditions())) {
            return false;
        }
        return ConditionParser.checkAll(player, branch.getPapiConditions());
    }

    private void display(Player player, DialogueConfig config, DialogueBranch branch, int npcId,
                         DialogueSessionStore.Session previousSession) {
        String title = DialoguePayload.sanitizeDisplayText(
                TextUtil.parse(player, config.getTitle()), DialoguePayload.MAX_TITLE_BYTES);
        List<String> lines = new ArrayList<>();
        for (String line : branch.getLines()) {
            if (lines.size() >= DialoguePayload.MAX_LINES) {
                break;
            }
            lines.add(DialoguePayload.sanitizeDisplayText(
                    TextUtil.parse(player, line), DialoguePayload.MAX_LINE_BYTES));
        }

        List<DialoguePayload.Option> options = new ArrayList<>();
        for (DialogueOption option : branch.getOptions()) {
            if (options.size() >= DialoguePayload.MAX_OPTIONS) {
                break;
            }
            if (!DialoguePayload.isValidOptionId(option.getId())) {
                continue;
            }
            String text = DialoguePayload.sanitizeDisplayText(
                    TextUtil.parse(player, option.getText()), DialoguePayload.MAX_OPTION_TEXT_BYTES);
            if (text.isEmpty()) {
                continue;
            }
            options.add(new DialoguePayload.Option(option.getId(), text));
        }

        List<String> optionIds = options.stream().map(DialoguePayload.Option::id).toList();
        DialogueSessionStore.Session session = previousSession == null
                ? sessions.open(player.getUniqueId(), npcId, config.getFile(), branch.getId(), optionIds)
                : sessions.updatePending(previousSession, branch.getId(), optionIds);
        if (session == null) {
            if (previousSession != null) {
                sessions.cancelTransition(previousSession.playerId(), previousSession.id());
                DialoguePayload.sendClose(player, previousSession.id());
            }
            return;
        }
        DialoguePayload.Snapshot snapshot = new DialoguePayload.Snapshot(
                session.id(), title, lines, options);
        if (!DialoguePayload.sendOpen(player, snapshot)) {
            displayChatFallback(player, snapshot);
        }
    }

    private void displayChatFallback(Player player, DialoguePayload.Snapshot snapshot) {
        if (!snapshot.title().isEmpty()) {
            player.sendMessage(snapshot.title());
        }
        for (String line : snapshot.lines()) {
            player.sendMessage(line);
        }
        for (DialoguePayload.Option option : snapshot.options()) {
            TextComponent component = new TextComponent(TextComponent.fromLegacyText(option.text()));
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/" + CLICK_COMMAND + " click " + snapshot.sessionId() + " "
                            + DialoguePayload.encodeCommandOptionId(option.id())));
            player.spigot().sendMessage(component);
        }
    }

    /** 处理 Mod 或聊天回退提交的安全选项。 */
    public void onOptionClick(Player player, UUID sessionId, String optionId) {
        DialogueSessionStore.Session session = sessions.consume(
                player.getUniqueId(), sessionId, optionId);
        if (session == null) {
            DialoguePayload.sendClose(player, sessionId);
            return;
        }
        NPC npc = validInteractionNpc(player, session.npcId());
        if (npc == null) {
            DialoguePayload.sendClose(player, session.id());
            return;
        }
        DialogueConfig config = findConfig(session.npcId(), session.dialogueFile());
        DialogueBranch branch = findBranch(config, session.branchId());
        if (branch == null || !matches(player, npc, branch)) {
            DialoguePayload.sendClose(player, session.id());
            return;
        }

        int optionIndex = -1;
        DialogueOption selected = null;
        for (int index = 0; index < branch.getOptions().size(); index++) {
            DialogueOption candidate = branch.getOptions().get(index);
            if (candidate.getId().equals(optionId)) {
                optionIndex = index;
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            DialoguePayload.sendClose(player, session.id());
            return;
        }

        OptionActions actions = parseOptionActions(selected);
        boolean transition = actions.targetBranch() != null && !actions.close();

        // 提交任务快捷按钮只有在操作实际成功后才执行 Kether，避免提交失败仍推进 NPC 状态。
        if (selected.getSubmitQuest() != null && !selected.getSubmitQuest().isEmpty()) {
            Quest quest = QuestManager.getInstance().getQuest(selected.getSubmitQuest());
            if (quest == null || !QuestManager.getInstance().submitQuest(
                    player, quest, selected.getSubmitItems())) {
                DialoguePayload.sendClose(player, session.id());
                return;
            }
        }

        if (!transition) {
            DialoguePayload.sendClose(player, session.id());
        }

        if (!selected.getKether().isEmpty() || actions.targetBranch() != null) {
            executeOptionActions(player, session, branch, selected, optionIndex, actions);
        }
    }

    private void executeOptionActions(Player player, DialogueSessionStore.Session session,
                                      DialogueBranch branch, DialogueOption option,
                                      int optionIndex, OptionActions actions) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("@NpcId", session.npcId());
        vars.put("@BranchId", branch.getId());
        vars.put("@Option", optionIndex);
        vars.put("@OptionId", option.getId());
        vars.put("@Player", player);
        vars.put("@DialogueSession", session);

        String nextBranch = actions.targetBranch();
        boolean transition = nextBranch != null && !actions.close();
        if (transition) {
            sessions.beginTransition(session);
        }
        CompletableFuture<Object> execution = KetherRunner.run(player, actions.kether(), vars);
        if (transition) {
            execution.whenComplete((ignored, error) -> {
                if (error != null) {
                    sessions.cancelTransition(session.playerId(), session.id());
                    Runnable close = () -> DialoguePayload.sendClose(player, session.id());
                    if (Bukkit.isPrimaryThread()) {
                        close.run();
                    } else {
                        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), close);
                    }
                    return;
                }
                gotoDialogue(player, session, nextBranch);
            });
        }
    }

    private OptionActions parseOptionActions(DialogueOption option) {
        List<String> kether = new ArrayList<>();
        String targetBranch = null;
        boolean close = option.isClose();
        for (String raw : option.getKether()) {
            String action = raw == null ? "" : actionLine(raw);
            if (action.isEmpty()) {
                continue;
            }
            if (action.equalsIgnoreCase("close")) {
                close = true;
                continue;
            }
            if (action.regionMatches(true, 0, "goto", 0, 4)
                    && (action.length() == 4 || Character.isWhitespace(action.charAt(4)))) {
                String target = action.substring(4).trim();
                if (target.length() >= 2 && target.startsWith("\"") && target.endsWith("\"")) {
                    target = target.substring(1, target.length() - 1).trim();
                }
                if (!target.isEmpty()) {
                    targetBranch = target;
                }
                continue;
            }
            kether.add(action);
        }
        return new OptionActions(kether, targetBranch, close);
    }

    private String actionLine(String raw) {
        return raw.replace('\ufeff', ' ').trim();
    }

    /** 客户端主动关闭对话；只接受当前匹配会话。 */
    public void onDismiss(Player player, UUID sessionId) {
        DialogueSessionStore.Session dismissed = sessions.dismiss(player.getUniqueId(), sessionId);
        if (dismissed != null) {
            DialoguePayload.sendClose(player, dismissed.id());
        }
    }

    /** 玩家退出时清理，不再向即将断开的客户端发包。 */
    public void remove(Player player) {
        sessions.remove(player.getUniqueId());
    }

    /** 关闭玩家当前对话；供 Kether 的 close 动作使用。 */
    public void closeDialogue(Player player) {
        DialogueSessionStore.Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            DialoguePayload.sendClose(player, session.id());
        }
    }

    /** 插件卸载时关闭所有在线对话并清空会话。 */
    public void shutdown() {
        closeAll();
    }

    private void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            DialogueSessionStore.Session session = sessions.remove(player.getUniqueId());
            if (session != null) {
                DialoguePayload.sendClose(player, session.id());
            }
        }
        sessions.clear();
    }

    private DialogueConfig findConfig(int npcId, String file) {
        List<DialogueConfig> configs = byNpc.get(npcId);
        if (configs == null) {
            return null;
        }
        for (DialogueConfig config : configs) {
            if (config.getFile().equals(file)) {
                return config;
            }
        }
        return null;
    }

    private DialogueBranch findBranch(DialogueConfig config, String branchId) {
        if (config == null) {
            return null;
        }
        for (DialogueBranch branch : config.getBranches()) {
            if (branch.getId().equals(branchId)) {
                return branch;
            }
        }
        return null;
    }

    private NPC validInteractionNpc(Player player, int npcId) {
        NPC npc = CitizensHook.getNpc(npcId);
        if (npc == null || !npc.isSpawned()) {
            return null;
        }
        Entity entity = npc.getEntity();
        if (entity == null || !entity.isValid() || !player.getWorld().equals(entity.getWorld())) {
            return null;
        }
        return player.getLocation().distanceSquared(entity.getLocation()) <= MAX_INTERACTION_DISTANCE_SQUARED
                ? npc : null;
    }

    private record MatchedDialogue(DialogueConfig config, DialogueBranch branch) {
    }

    private record OptionActions(List<String> kether, String targetBranch, boolean close) {
    }
}
