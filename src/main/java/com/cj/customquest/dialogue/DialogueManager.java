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
import org.bukkit.inventory.Inventory;
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
 * NPC 对话管理：加载配置、选择分支，并通过原版箱子界面执行选项。
 */
public final class DialogueManager {

    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 36.0;

    private static DialogueManager instance;

    private File dialoguesFolder;
    private final Map<Integer, List<DialogueConfig>> byNpc = new HashMap<>();
    private final DialogueSessionStore sessions = new DialogueSessionStore();
    private final DialoguePresentation presentations = new DialoguePresentation(new DialoguePresentation.Host() {
        public void select(Player player, UUID sessionId, String optionId) {
            executeOption(player, sessionId, optionId);
        }
        public void dismiss(Player player, UUID sessionId) {
            dismissSession(player, sessionId);
        }
        public void expire(Player player, UUID sessionId) {
            expireDialogue(player, sessionId);
        }
        public void discard(UUID playerId) {
            sessions.remove(playerId);
        }
        public void pushBody(Player player, String body, java.util.List<String> slotNames) {
            BodySink sink = bodySink;
            if (sink != null) {
                sink.accept(player, body, slotNames);
            }
        }
        public void displayClosing(Player player, UUID sessionId) {
            DisplayCloseSink sink = displayCloseSink;
            if (sink != null) {
                sink.accept(player, sessionId);
            }
        }
        public void warning(String message) {
            Bukkit.getLogger().warning("[CustomQuest] " + message);
        }
    });

    /**
     * 正文订阅者（由可选的外部展示层注册）；未注册时不下发。
     *
     * <p>{@code slotNames} 是箱子各槽位要显示的名称（0 号槽是 NPC 标题，选项槽是选项文字），
     * 由展示层自行决定用不用：页面展示层用它来保留颜色码。
     */
    @FunctionalInterface
    public interface BodySink {
        void accept(Player player, String body, java.util.List<String> slotNames);
    }

    /** 任务追踪订阅者（由可选的外部展示层注册）；未注册时不下发。 */
    @FunctionalInterface
    public interface BoardSink {
        void accept(Player player, String title, java.util.List<String> lines, java.util.Set<Integer> nameIndexes,
                    java.util.Map<Integer, String> nameRowQuests);
    }

    private volatile BodySink bodySink;
    private volatile BoardSink boardSink;

    /**
     * 页面收尾订阅者（由可选的外部展示层注册）：这一屏被收起、箱子还没关的时候调用。
     *
     * <p>存在的理由：本插件的界面对页面模组而言是"容器替换页"，只把容器一关，它只能靠推断
     * 认为页面结束 —— 那它就分不清"页面收尾"和"玩家跑去开了别的界面"。给它一个明确的收尾通知，
     * 它就能走正规拆除，而不是被动推断。
     */
    @FunctionalInterface
    public interface DisplayCloseSink {
        void accept(Player player, UUID sessionId);
    }

    private volatile DisplayCloseSink displayCloseSink;

    /** 注册正文订阅者；传入 null 可注销。 */
    public void setBodySink(BodySink sink) {
        this.bodySink = sink;
    }

    /** 注册页面收尾订阅者；传入 null 可注销。 */
    public void setDisplayCloseSink(DisplayCloseSink sink) {
        this.displayCloseSink = sink;
    }

    /** 注册任务追踪订阅者；传入 null 可注销。 */
    public void setBoardSink(BoardSink sink) {
        this.boardSink = sink;
    }

    /**
     * 把任务追踪的标题与行交给订阅者（例如页面展示层）。
     *
     * @param nameIndexes 哪些下标是任务名行：展示层据此放大字号。
     * @param nameRowQuests 任务名行下标 → 任务 ID：展示层据此放置任务追踪按钮。
     */
    public void pushBoard(Player player, String title, java.util.List<String> lines,
                          java.util.Set<Integer> nameIndexes, java.util.Map<Integer, String> nameRowQuests) {
        BoardSink sink = boardSink;
        if (sink != null) {
            sink.accept(player, title, lines, nameIndexes, nameRowQuests);
        }
    }

    public static DialogueManager getInstance() {
        return instance;
    }

    public static void create(File dataFolder) {
        instance = new DialogueManager();
        instance.dialoguesFolder = new File(dataFolder, "dialogues");
        instance.reload();
        instance.presentations.initialize(BukkitPlugin.getInstance());
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
        if (player == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(),
                    () -> openDialogue(player, npcId, forceBranchId));
            return;
        }
        if (!player.isOnline() || !CitizensHook.isEnabled()) {
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
                presentations.close(player, previousSession.id());
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
        String title = DialogueSnapshot.sanitizeDisplayText(
                TextUtil.parse(player, config.getTitle()), DialogueSnapshot.MAX_TITLE_BYTES);
        List<String> lines = new ArrayList<>();
        for (String line : branch.getLines()) {
            if (lines.size() >= DialogueSnapshot.MAX_LINES) {
                break;
            }
            lines.add(DialogueSnapshot.sanitizeDisplayText(
                    TextUtil.parse(player, line), DialogueSnapshot.MAX_LINE_BYTES));
        }

        List<DialogueSnapshot.Option> options = new ArrayList<>();
        for (DialogueOption option : branch.getOptions()) {
            if (options.size() >= DialogueSnapshot.MAX_OPTIONS) {
                break;
            }
            if (!DialogueSnapshot.isValidOptionId(option.getId())) {
                continue;
            }
            String text = DialogueSnapshot.sanitizeDisplayText(
                    TextUtil.parse(player, option.getText()), DialogueSnapshot.MAX_OPTION_TEXT_BYTES);
            if (text.isEmpty()) {
                continue;
            }
            options.add(new DialogueSnapshot.Option(option.getId(), text));
        }

        List<String> optionIds = options.stream().map(DialogueSnapshot.Option::id).toList();
        DialogueSessionStore.Session session = previousSession == null
                ? sessions.open(player.getUniqueId(), npcId, config.getFile(), branch.getId(), optionIds)
                : sessions.updatePending(previousSession, branch.getId(), optionIds);
        if (session == null) {
            if (previousSession != null) {
                sessions.cancelTransition(previousSession.playerId(), previousSession.id());
                presentations.close(player, previousSession.id());
            }
            return;
        }
        DialogueSnapshot.Snapshot snapshot = new DialogueSnapshot.Snapshot(
                session.id(), title, lines, options);
        if (!presentations.show(player, snapshot, previousSession != null)) {
            expireDialogue(player, session.id());
        }
    }

    /** 当前玩家是否正以本插件的箱子界面进行对话。 */
    public boolean isDialogueContainer(Player player, Inventory inventory) {
        return presentations.isDialogueContainer(player, inventory);
    }

    /** 原版箱子点击：只有选项槽位提交对应选项，其余槽位只拦截不改内容。 */
    public boolean handleDialogueContainerClick(Player player, Inventory inventory, int rawSlot) {
        return presentations.handleContainerClick(player, inventory, rawSlot);
    }

    /** 玩家按 Esc 或其他方式关闭对话箱子时结束对应业务会话。 */
    public boolean handleDialogueContainerClose(Player player, Inventory inventory) {
        return presentations.handleContainerClose(player, inventory);
    }

    /**
     * 外部请求提交选项（页面组件交互等）：按选项文字匹配当前节点，命中后执行同一个选项。
     * 返回 true 表示请求已被接受；会话与选项范围都由展示层校验。
     */
    public boolean selectOptionByRequest(Player player, String optionText) {
        return presentations.selectByRequest(player, optionText);
    }

    /** 选项只由箱子界面的点击提交，展示层已保证会话与一次性校验。 */
    private void executeOption(Player player, UUID sessionId, String optionId) {
        DialogueSessionStore.Session session = sessions.consume(
                player.getUniqueId(), sessionId, optionId);
        if (session == null) {
            // 选项是一次性的（consume 会把它取走），取不到会话说明这次提交是重复的：
            // 客户端界面可能同时经两条通道提交同一次点击（原版槽位点击一次、页面脚本请求一次），
            // 后到的那次必然落在这里。这里只忽略，绝不能关闭界面——否则刚显示出来的内容会被瞬间关掉。
            // 真正过期的会话由展示层 tick() 的 TTL 清理负责，不依赖这条分支兜底。
            return;
        }
        NPC npc = validInteractionNpc(player, session.npcId());
        if (npc == null) {
            presentations.close(player, session.id());
            return;
        }
        DialogueConfig config = findConfig(session.npcId(), session.dialogueFile());
        DialogueBranch branch = findBranch(config, session.branchId());
        if (branch == null || !matches(player, npc, branch)) {
            presentations.close(player, session.id());
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
            presentations.close(player, session.id());
            return;
        }

        OptionActions actions = parseOptionActions(selected);
        boolean transition = actions.targetBranch() != null && !actions.close();

        // 提交任务快捷按钮只有在操作实际成功后才执行 Kether，避免提交失败仍推进 NPC 状态。
        if (selected.getSubmitQuest() != null && !selected.getSubmitQuest().isEmpty()) {
            Quest quest = QuestManager.getInstance().getQuest(selected.getSubmitQuest());
            if (quest == null || !QuestManager.getInstance().submitQuest(
                    player, quest, selected.getSubmitItems())) {
                presentations.close(player, session.id());
                return;
            }
        }

        if (!transition) {
            presentations.close(player, session.id());
        }

        if (!selected.getKether().isEmpty() || actions.targetBranch() != null) {
            // 隔一 tick 再跑选项脚本：上面那句会关掉本插件的箱子 —— 在页面模组（容器替换）下，
            // 客户端要先处理完这次容器关闭、把替换界面拆干净。若在同一 tick 里紧接着让脚本再开别的
            // 界面（例如 command("market open")），新界面偶尔会被那次拆除一起带走。隔一帧玩家不可感。
            DialogueOption chosen = selected;
            int chosenIndex = optionIndex;
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    executeOptionActions(player, session, branch, chosen, chosenIndex, actions);
                }
            });
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
                    Runnable close = () -> presentations.close(player, session.id());
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

    private void dismissSession(Player player, UUID sessionId) {
        sessions.dismiss(player.getUniqueId(), sessionId);
        sessions.cancelTransition(player.getUniqueId(), sessionId);
        presentations.close(player, sessionId);
    }

    /** TTL、换世界和展示失效只清理匹配会话，不能影响替换后的新 NPC。 */
    private void expireDialogue(Player player, UUID sessionId) {
        dismissSession(player, sessionId);
    }

    /** 玩家退出时清理，不再向即将断开的客户端发包。 */
    public void remove(Player player) {
        sessions.remove(player.getUniqueId());
        presentations.forget(player);
    }

    /** 关闭当前及已消费但仍等待异步跳转的会话。 */
    public void closeDialogue(Player player) {
        if (player == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), () -> closeDialogue(player));
            return;
        }
        if (!player.isOnline()) {
            remove(player);
            return;
        }
        sessions.remove(player.getUniqueId());
        presentations.closeCurrent(player);
    }

    /**
     * 重建玩家当前会话的箱子界面，用于页面展示失效后的自愈；没有进行中的会话时返回 false。
     *
     * <p>只在主线程执行：重建要开关箱子，必须与展示层状态在同一线程上。
     */
    public boolean recoverDisplay(Player player) {
        if (player == null || !Bukkit.isPrimaryThread() || !player.isOnline()) {
            return false;
        }
        return presentations.recoverDisplay(player);
    }

    /** 插件卸载时先关闭页面，再撤销展示监听和定时任务。 */
    public void shutdown() {
        closeAll();
        presentations.shutdown();
    }

    private void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            closeDialogue(player);
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
