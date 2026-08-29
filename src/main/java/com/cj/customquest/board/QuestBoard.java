package com.cj.customquest.board;

import com.cj.customquest.config.Settings;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.quest.QuestObjective;
import com.cj.customquest.quest.QuestProgress;
import com.cj.customquest.quest.QuestType;
import com.cj.customquest.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;
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
 * 任务计分板（全息视图）：玩家接取任务后，在右侧侧边栏显示任务标题与各项进度。
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
    /** 最多同时显示的任务数 */
    private static final int MAX_QUESTS = 3;
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

    /** 由本插件设置过计分板的玩家 */
    private final Set<UUID> owned = ConcurrentHashMap.newKeySet();
    /** 每个玩家独立计分板 */
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** 上次已应用的渲染结果；内容未变化时跳过计分板写入 */
    private final Map<UUID, BoardState> states = new ConcurrentHashMap<>();
    /** 等待下一 tick 合并刷新的玩家，避免一次背包操作触发多次更新 */
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();

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
     * 重建某个玩家的任务计分板（无任务时清空）。
     */
    public void update(Player player) {
        if (!enabled) {
            return;
        }
        List<String> lines = buildLines(player);
        if (lines.isEmpty()) {
            clear(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        String renderedTitle = TextUtil.color(title);
        BoardState nextState = new BoardState(renderedTitle, List.copyOf(lines));
        Scoreboard currentBoard = boards.get(uuid);
        if (nextState.equals(states.get(uuid)) && currentBoard != null && player.getScoreboard() == currentBoard) {
            return;
        }

        Scoreboard board = boards.computeIfAbsent(uuid,
                ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
        player.setScoreboard(board);
        owned.add(uuid);

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

    /** 背包事件调用：合并为下一 tick 的单次刷新。 */
    public void queueUpdate(Player player) {
        UUID uuid = player.getUniqueId();
        if (!enabled || !owned.contains(uuid) || !tracksInventoryProgress(player) || !queued.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), () -> {
            queued.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                update(online);
            }
        });
    }

    private boolean tracksInventoryProgress(Player player) {
        for (String questId : QuestManager.getInstance().getPlayerData(player).getAccepted().keySet()) {
            Quest quest = QuestManager.getInstance().getQuest(questId);
            if (quest != null && quest.getType() == QuestType.SUBMIT_ITEM) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildLines(Player player) {
        Map<String, QuestProgress> accepted = QuestManager.getInstance().getPlayerData(player).getAccepted();
        if (accepted.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int count = 0;
        int gap = Settings.boardGapLines;
        for (Map.Entry<String, QuestProgress> entry : accepted.entrySet()) {
            Quest quest = QuestManager.getInstance().getQuest(entry.getKey());
            if (quest == null) {
                continue;
            }
            if (count >= MAX_QUESTS) {
                int remaining = accepted.size() - count;
                if (remaining > 0) {
                    lines.add("§7…还有 " + remaining + " 个任务");
                }
                break;
            }
            // 计算该任务「完整块」所需行数（标题 + 内容行 + 任务间空行）
            List<String> questLines = buildQuestLines(player, quest);
            int needed = questLines.size() + gap;
            if (needed > MAX_LINES - 1) {
                needed = MAX_LINES - 1;
            }
            if (lines.size() + needed > MAX_LINES - 1) {
                int remaining = accepted.size() - count;
                if (remaining > 0) {
                    lines.add("§7…还有 " + remaining + " 个任务");
                }
                break;
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
        return lines;
    }

    /**
     * 构建单个任务在计分板/任务书中的显示行（变量与 PAPI 已替换，& 颜色码保留）。
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
     * 应用自定义格式：替换变量 + PAPI 占位符（保留 & 颜色码，供计分板/任务书各自上色）。
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

    /** 清空某玩家的任务计分板（仅当计分板由本插件设置时） */
    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        states.remove(uuid);
        queued.remove(uuid);
        if (owned.remove(uuid)) {
            boards.remove(uuid);
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }

    /** 玩家退出时清理记录（不操作计分板） */
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        owned.remove(uuid);
        boards.remove(uuid);
        states.remove(uuid);
        queued.remove(uuid);
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

    /** 只兜底刷新当前正在显示任务面板的玩家。 */
    public void refreshTracked() {
        if (!enabled) {
            return;
        }
        for (UUID uuid : new ArrayList<>(owned)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                owned.remove(uuid);
                boards.remove(uuid);
                states.remove(uuid);
                queued.remove(uuid);
                continue;
            }
            update(player);
        }
    }

    /** 清空所有在线玩家的任务计分板 */
    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    private record BoardState(String title, List<String> lines) {
    }
}
