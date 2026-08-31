package com.cj.customquest.board;

import com.cj.customquest.config.Settings;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.quest.QuestObjective;
import com.cj.customquest.quest.QuestProgress;
import com.cj.customquest.quest.QuestType;
import com.cj.customquest.tracking.QuestTrackingPayload;
import com.cj.customquest.tracking.QuestTrackingSnapshot;
import com.cj.customquest.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务追踪路由：SoulCore 客户端使用 HUD，其余玩家回退到右侧计分板。
 */
public final class QuestBoard {

    /** 计分板最大行数（Minecraft 侧边栏限制） */
    private static final int MAX_LINES = 15;
    /** 稳定行 key（保持条目唯一，避免闪烁） */
    private static final String[] LINE_KEYS = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r", "§5§r", "§6§r", "§7§r",
            "§8§r", "§9§r", "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private static QuestBoard instance;

    private boolean enabled = true;

    /** 计分板标题（可在 config.yml 的 scoreboard.title 自定义） */
    private String title = "&6&l任务追踪";
    /** 低频兜底刷新间隔（秒）；常见背包变化由事件即时刷新 */
    private static final int UPDATE_INTERVAL = 5;
    /** 任务标题行默认格式（任务文件里未写 board-title 时使用） */
    private static final String QUEST_TITLE_FORMAT = "{name}";
    /** 击杀目标行默认格式（目标里未写 board-line 时使用） */
    private static final String KILL_LINE_FORMAT = "&7已击杀 &f{mob} &e{current}&7/&e{total}";
    /** 收集目标行默认格式（目标里未写 board-line 时使用） */
    private static final String ITEM_LINE_FORMAT = "&7已收集 &f{item} &e{current}&7/&e{total}";
    /** 多目标任务汇总行默认格式（空 = 不显示） */
    private static final String TOTAL_LINE_FORMAT = "";
    /** 描述任务内容行默认格式 */
    private static final String DESCRIBE_LINE_FORMAT = "{line}";

    /** 当前有任务追踪状态的玩家（SoulCore HUD 与计分板共用） */
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    /** 由本插件实际设置过计分板的玩家 */
    private final Set<UUID> scoreboardOwned = ConcurrentHashMap.newKeySet();
    /** 每个玩家独立计分板 */
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** 设置回退计分板前玩家使用的主计分板；切换 HUD/清理时恢复。 */
    private final Map<UUID, Scoreboard> previousBoards = new ConcurrentHashMap<>();
    /** 上次已投递的模式与内容；内容未变化时跳过发包/计分板写入 */
    private final Map<UUID, DeliveryState> states = new ConcurrentHashMap<>();

    public static QuestBoard getInstance() {
        return instance;
    }

    public static QuestBoard create() {
        instance = new QuestBoard();
        return instance;
    }

    public void configure(boolean enabled, String title) {
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        this.title = title;
        if (wasEnabled && !enabled) {
            clearAll(); // 关闭开关后清空所有玩家的任务计分板
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getUpdateInterval() {
        return UPDATE_INTERVAL;
    }

    /**
     * 重建某个玩家的任务追踪（无任务时清空）。
     */
    public void update(Player player) {
        update(player, false);
    }

    /** 客户端注册/注销任务通道后强制重新协商并投递当前快照。 */
    public void refreshChannel(Player player) {
        update(player, true);
    }

    private void update(Player player, boolean forceHudHeartbeat) {
        if (player == null) {
            return;
        }
        if (!enabled) {
            clear(player);
            return;
        }
        QuestTrackingSnapshot snapshot = buildSnapshot(player);
        if (snapshot.totalTaskCount() == 0) {
            clear(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        tracked.add(uuid);

        DeliveryState hudState = DeliveryState.hud(
                snapshot, QuestTrackingPayload.preferredVersion(player));
        if (QuestTrackingPayload.canSend(player)) {
            if (!forceHudHeartbeat && hudState.equals(states.get(uuid))) {
                clearScoreboard(player);
                return;
            }
            if (QuestTrackingPayload.sendSnapshot(player, snapshot)) {
                clearScoreboard(player);
                states.put(uuid, hudState);
                return;
            }
            DeliveryState previous = states.get(uuid);
            if (previous != null && previous.mode() == DeliveryMode.HUD) {
                QuestTrackingPayload.sendClear(player);
            }
        }

        List<String> lines = buildLines(player, snapshot);
        if (lines.isEmpty()) {
            clear(player);
            return;
        }
        String renderedTitle = TextUtil.color(title);
        DeliveryState nextState = DeliveryState.scoreboard(snapshot, renderedTitle, lines);
        Scoreboard currentBoard = boards.get(uuid);
        Scoreboard previousBoard = previousBoards.get(uuid);
        if (currentBoard != null && player.getScoreboard() == currentBoard
                && previousBoard != null && previousBoard.getObjective(DisplaySlot.SIDEBAR) != null) {
            // 取得回退计分板后，其他插件可能才在主计分板上新增 Sidebar；此时也应立即让出。
            clearScoreboard(player);
            states.put(uuid, DeliveryState.suppressed(snapshot));
            return;
        }
        if (nextState.equals(states.get(uuid)) && currentBoard != null && player.getScoreboard() == currentBoard) {
            return;
        }

        if (currentBoard != null && player.getScoreboard() != currentBoard) {
            // 其他插件在 CustomQuest 之后接管了计分板；立即让出，不在兜底刷新中抢回。
            scoreboardOwned.remove(uuid);
            boards.remove(uuid);
            previousBoards.remove(uuid);
            states.put(uuid, DeliveryState.suppressed(snapshot));
            return;
        }

        Scoreboard board = currentBoard;
        if (board == null) {
            Scoreboard playerBoard = player.getScoreboard();
            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
            if (playerBoard != mainBoard || playerBoard.getObjective(DisplaySlot.SIDEBAR) != null) {
                // 不覆盖其他插件的私有计分板或主计分板已有的侧边栏。
                states.put(uuid, DeliveryState.suppressed(snapshot));
                return;
            }
            previousBoards.put(uuid, playerBoard);
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(uuid, board);
            player.setScoreboard(board);
            scoreboardOwned.add(uuid);
        }

        Objective objective = board.getObjective("cq");
        if (objective == null) {
            objective = board.registerNewObjective("cq", "dummy", renderedTitle);
        } else {
            objective.setDisplayName(renderedTitle);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < MAX_LINES; i++) {
            String key = LINE_KEYS[i];
            Team team = board.getTeam("line" + i);
            if (team == null) {
                team = board.registerNewTeam("line" + i);
            }
            if (!team.hasEntry(key)) {
                team.addEntry(key);
            }
            String text = i < lines.size() ? lines.get(i) : "";
            team.setPrefix(text);
            team.setSuffix("");
            int scoreValue = MAX_LINES - i;
            if (i < lines.size()) {
                Score score = objective.getScore(key);
                score.setScore(scoreValue);
                // 隐藏原生侧边栏右侧的分数数字（15-0）
                score.numberFormat(NumberFormat.blank());
            } else {
                board.resetScores(key);
            }
        }
        states.put(uuid, nextState);
    }

    /**
     * @deprecated 背包检查已移至 QuestManager，且不再依赖任务追踪开关。
     */
    @Deprecated(forRemoval = true)
    public void queueUpdate(Player player) {
        QuestManager.getInstance().queueInventoryRefresh(player);
    }

    private QuestTrackingSnapshot buildSnapshot(Player player) {
        List<QuestTrackingSnapshot.Task> candidates = new ArrayList<>();
        for (Map.Entry<String, QuestProgress> entry
                : QuestManager.getInstance().getPlayerData(player).getAccepted().entrySet()) {
            Quest quest = QuestManager.getInstance().getQuest(entry.getKey());
            if (quest == null) {
                continue;
            }
            List<String> renderedQuestLines = buildQuestLines(player, quest);
            String trackingTitle = renderedQuestLines.isEmpty()
                    ? TextUtil.parse(player, quest.getName()) : renderedQuestLines.getFirst();
            int trackingTitleRgb = QuestTrackingSnapshot.legacyTitleRgb(
                    TextUtil.parse(player, quest.getName()));
            List<QuestTrackingSnapshot.Line> taskLines = new ArrayList<>();
            if (!quest.getBoardLines().isEmpty() || quest.getType() == QuestType.DESCRIBE) {
                for (int index = 1; index < renderedQuestLines.size()
                        && taskLines.size() < QuestTrackingSnapshot.MAX_LINES_PER_TASK; index++) {
                    taskLines.add(QuestTrackingSnapshot.Line.text(renderedQuestLines.get(index)));
                }
            } else {
                List<QuestObjective> objectives = quest.getObjectives();
                int objectiveLineOffset = Math.max(1, renderedQuestLines.size() - objectives.size());
                for (int index = 0; index < objectives.size()
                        && taskLines.size() < QuestTrackingSnapshot.MAX_LINES_PER_TASK; index++) {
                    QuestObjective objective = objectives.get(index);
                    if (objective.getBoardLine() != null && !objective.getBoardLine().isEmpty()) {
                        int renderedIndex = objectiveLineOffset + index;
                        String customLine = renderedIndex < renderedQuestLines.size()
                                ? renderedQuestLines.get(renderedIndex) : objective.getBoardLine();
                        taskLines.add(QuestTrackingSnapshot.Line.text(customLine));
                    } else {
                        int total = Math.max(1, objective.getAmount());
                        int current = Math.max(0, Math.min(
                                QuestManager.getInstance().getObjectiveProgress(player, quest, index), total));
                        String verb = quest.getType() == QuestType.KILL_MOB ? "击败 " : "收集 ";
                        taskLines.add(QuestTrackingSnapshot.Line.progress(
                                TextUtil.parse(player, verb + objective.getDisplay()),
                                current,
                                total));
                    }
                }
            }
            QuestTrackingSnapshot.TaskType type = switch (quest.getType()) {
                case KILL_MOB -> QuestTrackingSnapshot.TaskType.KILL;
                case SUBMIT_ITEM -> QuestTrackingSnapshot.TaskType.SUBMIT;
                case DESCRIBE -> QuestTrackingSnapshot.TaskType.DESCRIBE;
            };
            candidates.add(new QuestTrackingSnapshot.Task(
                    quest.getId(),
                    entry.getValue().getAcceptedAt(),
                    type,
                    NavigationManager.getInstance().isNavigating(player, quest.getId()),
                    NavigationManager.getInstance().hasTarget(quest.getId()),
                    trackingTitleRgb,
                    trackingTitle,
                    taskLines));
        }
        return QuestTrackingSnapshot.select(candidates);
    }

    private List<String> buildLines(Player player, QuestTrackingSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        int count = 0;
        int gap = Settings.boardGapLines;
        for (QuestTrackingSnapshot.Task task : snapshot.tasks()) {
            Quest quest = QuestManager.getInstance().getQuest(task.questId());
            if (quest == null) {
                continue;
            }
            // 计算该任务「完整块」所需行数（标题 + 内容行 + 任务间空行）
            List<String> questLines = buildQuestLines(player, quest);
            int needed = questLines.size() + gap;
            if (needed > MAX_LINES - 1) {
                needed = MAX_LINES - 1;
            }
            if (lines.size() + needed > MAX_LINES - 1) {
                int remaining = snapshot.totalTaskCount() - count;
                if (remaining > 0) {
                    lines.add("§7…还有 " + remaining + " 个任务");
                }
                return lines;
            }
            count++;
            // 渲染该任务块（计分板侧边栏，应用 & 颜色）
            for (String raw : questLines) {
                if (lines.size() >= MAX_LINES - 1) {
                    lines.add("§7…");
                    return lines;
                }
                lines.add(TextUtil.color(raw));
            }
            // 任务之间的空行
            for (int g = 0; g < gap && lines.size() < MAX_LINES - 1; g++) {
                lines.add("");
            }
        }
        int remaining = snapshot.totalTaskCount() - count;
        if (remaining > 0 && lines.size() < MAX_LINES) {
            lines.add("§7…还有 " + remaining + " 个任务");
        }
        return lines;
    }

    /**
     * 构建单个任务在 HUD/计分板中的显示行（变量与 PAPI 已替换，& 颜色码保留）。
     * 首行为标题行，后续为目标/描述/自定义内容行。
     */
    public List<String> buildQuestLines(Player player, Quest quest) {
        List<String> lines = new ArrayList<>();
        // 任务标题（优先任务文件 board-title，回退默认格式）
        String titleTemplate = quest.getBoardTitle() != null && !quest.getBoardTitle().isEmpty()
                ? quest.getBoardTitle() : QUEST_TITLE_FORMAT;
        lines.add(formatRaw(titleTemplate, player, Map.of(
                "name", quest.getName(), "id", quest.getId(),
                "type", quest.getType().getDisplay())));
        // 完全自定义：任务文件里写了 board-line 列表时，直接按列表逐行显示
        if (!quest.getBoardLines().isEmpty()) {
            Map<String, Object> customVars = buildCustomVars(player, quest);
            for (String line : quest.getBoardLines()) {
                lines.add(formatRaw(line, player, customVars));
            }
            return lines;
        }
        if (quest.getType() == QuestType.DESCRIBE) {
            // 描述任务：展示任务内容（description）
            for (String descLine : quest.getDescription()) {
                lines.add(formatRaw(DESCRIBE_LINE_FORMAT, player, Map.of(
                        "line", descLine, "name", quest.getName(), "id", quest.getId())));
            }
            return lines;
        }
        // 各目标进度（每个目标单独一行）
        List<QuestObjective> objectives = quest.getObjectives();
        boolean showTotal = objectives.size() > 1
                && TOTAL_LINE_FORMAT != null && !TOTAL_LINE_FORMAT.isEmpty();
        if (showTotal) {
            // 可选：任务级总数汇总行（total-line 非空时显示）
            int totalCurrent = QuestManager.getInstance().getProgress(player, quest);
            int totalNeed = quest.getTotalAmount();
            lines.add(formatRaw(TOTAL_LINE_FORMAT, player, Map.of(
                    "current", totalCurrent, "total", totalNeed,
                    "name", quest.getName(), "id", quest.getId(),
                    "type", quest.getType().getDisplay())));
        }
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            int current = QuestManager.getInstance().getObjectiveProgress(player, quest, i);
            Map<String, Object> vars = new HashMap<>();
            vars.put("current", current);
            vars.put("total", objective.getAmount());
            vars.put("amount", objective.getAmount());
            vars.put("index", i + 1);
            vars.put("target", objective.getTarget());
            vars.put("mob", objective.getDisplay());
            vars.put("item", objective.getDisplay());
            vars.put("display", objective.getDisplay());
            vars.put("name", quest.getName());
            vars.put("id", quest.getId());
            vars.put("type", quest.getType().getDisplay());
            // 目标行优先使用目标文件里的 board-line，回退默认 kill-line / item-line
            String lineTemplate = objective.getBoardLine() != null && !objective.getBoardLine().isEmpty()
                    ? objective.getBoardLine()
                    : (objective.isKill() ? KILL_LINE_FORMAT : ITEM_LINE_FORMAT);
            lines.add(formatRaw(lineTemplate, player, vars));
        }
        return lines;
    }

    /**
     * 任务级完全自定义 board-line 行可用的变量：
     * {name} {id} {type} {current} {total} —— 任务信息与总进度；
     * {targetN} {displayN} {mobN} {itemN} {currentN} {totalN} {amountN} —— 第 N 个目标（N 从 1 开始）。
     */
    private Map<String, Object> buildCustomVars(Player player, Quest quest) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", quest.getName());
        vars.put("id", quest.getId());
        vars.put("type", quest.getType().getDisplay());
        vars.put("current", QuestManager.getInstance().getProgress(player, quest));
        vars.put("total", quest.getTotalAmount());
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            int idx = i + 1;
            QuestObjective objective = objectives.get(i);
            int current = QuestManager.getInstance().getObjectiveProgress(player, quest, i);
            vars.put("target" + idx, objective.getTarget());
            vars.put("display" + idx, objective.getDisplay());
            vars.put("mob" + idx, objective.getDisplay());
            vars.put("item" + idx, objective.getDisplay());
            vars.put("current" + idx, current);
            vars.put("total" + idx, objective.getAmount());
            vars.put("amount" + idx, objective.getAmount());
        }
        return vars;
    }

    /**
     * 应用自定义格式：替换变量 + PAPI 占位符（保留 & 颜色码，供 HUD/计分板各自处理）。
     */
    private String formatRaw(String template, Player player, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return TextUtil.papi(player, result);
    }

    /** 清空某玩家的 HUD 与本插件计分板。 */
    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        states.remove(uuid);
        tracked.remove(uuid);
        QuestTrackingPayload.sendClear(player);
        clearScoreboard(player);
    }

    private void clearScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard board = boards.remove(uuid);
        Scoreboard previous = previousBoards.remove(uuid);
        if (scoreboardOwned.remove(uuid) && board != null && player.getScoreboard() == board) {
            player.setScoreboard(previous != null
                    ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** 玩家退出时清理记录（不操作计分板） */
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        tracked.remove(uuid);
        scoreboardOwned.remove(uuid);
        boards.remove(uuid);
        previousBoards.remove(uuid);
        states.remove(uuid);
    }

    /** 刷新所有在线玩家 */
    public void updateAll() {
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /** 只兜底刷新当前正在追踪任务的 HUD/计分板玩家。 */
    public void refreshTracked() {
        if (!enabled) {
            return;
        }
        for (UUID uuid : new ArrayList<>(tracked)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                tracked.remove(uuid);
                scoreboardOwned.remove(uuid);
                boards.remove(uuid);
                previousBoards.remove(uuid);
                states.remove(uuid);
                continue;
            }
            update(player, true);
        }
    }

    /** 清空所有在线玩家的任务追踪。 */
    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                clear(player);
            } catch (Throwable exception) {
                Bukkit.getLogger().warning("[CustomQuest] 清理玩家 " + player.getName()
                        + " 的任务追踪失败: " + exception.getMessage());
            }
        }
        tracked.clear();
        scoreboardOwned.clear();
        boards.clear();
        previousBoards.clear();
        states.clear();
    }

    private enum DeliveryMode {
        HUD,
        SCOREBOARD,
        SUPPRESSED
    }

    private record DeliveryState(
            DeliveryMode mode,
            QuestTrackingSnapshot snapshot,
            int protocolVersion,
            String title,
            List<String> lines
    ) {
        private DeliveryState {
            lines = List.copyOf(lines);
        }

        static DeliveryState hud(QuestTrackingSnapshot snapshot, int protocolVersion) {
            return new DeliveryState(DeliveryMode.HUD, snapshot, protocolVersion, "", List.of());
        }

        static DeliveryState scoreboard(QuestTrackingSnapshot snapshot, String title, List<String> lines) {
            return new DeliveryState(DeliveryMode.SCOREBOARD, snapshot, 0, title, lines);
        }

        static DeliveryState suppressed(QuestTrackingSnapshot snapshot) {
            return new DeliveryState(DeliveryMode.SUPPRESSED, snapshot, 0, "", List.of());
        }
    }
}
