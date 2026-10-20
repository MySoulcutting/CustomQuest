package com.cj.customquest.board;

import com.cj.customquest.config.Settings;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.quest.QuestObjective;
import com.cj.customquest.quest.QuestProgress;
import com.cj.customquest.quest.QuestType;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务追踪：把玩家已接取的任务渲染到右侧计分板。
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

    /** 当前有任务追踪状态的玩家 */
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    /** 由本插件实际设置过计分板的玩家 */
    private final Set<UUID> scoreboardOwned = ConcurrentHashMap.newKeySet();
    /** 为避免与其他插件争夺计分板而主动让出的玩家 */
    private final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();
    /** 每个玩家独立计分板 */
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** 设置计分板前玩家使用的主计分板；清理时恢复。 */
    private final Map<UUID, Scoreboard> previousBoards = new ConcurrentHashMap<>();
    /** 上次已写入的计分板内容；内容未变化时跳过重复写入 */
    private final Map<UUID, List<String>> states = new ConcurrentHashMap<>();

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
     * 重建某个玩家的任务追踪计分板（无任务时清空）。
     */
    public void update(Player player) {
        if (player == null) {
            return;
        }
        if (!enabled) {
            // 侧边栏关闭时页面仍在工作：清掉计分板，把任务数据推给 HUD。
            // 只把"有内容"的玩家留在兜底刷新名单里，否则每个进服玩家每 5 秒都会被推一屏空行。
            RowMeta meta = new RowMeta();
            List<String> pageLines = withBullets(
                    buildLines(player, PAGE_LINE_LIMIT, meta, PAGE_GAP_LINES), meta.nameIndexes);
            clear(player);
            if (pageLines.isEmpty()) {
                tracked.remove(player.getUniqueId());
                pushBoard(player, List.of());
            } else {
                tracked.add(player.getUniqueId());
                pushBoard(player, pageLines, meta);
            }
            return;
        }
        UUID uuid = player.getUniqueId();
        List<String> lines = buildLines(player);
        if (lines.isEmpty()) {
            clear(player);
            pushBoard(player, List.of());
            return;
        }
        tracked.add(uuid);
        String renderedTitle = TextUtil.color(title);
        Scoreboard currentBoard = boards.get(uuid);
        Scoreboard previousBoard = previousBoards.get(uuid);
        if (currentBoard != null && player.getScoreboard() == currentBoard
                && previousBoard != null && previousBoard.getObjective(DisplaySlot.SIDEBAR) != null) {
            // 取得回退计分板后，其他插件可能才在主计分板上新增 Sidebar；此时也应立即让出。
            clearScoreboard(player);
            states.remove(uuid);
            suppressed.add(uuid);
            // 让出的是侧边栏，不是 HUD：页面照常显示任务。
            publishTo(player);
            return;
        }
        if (currentBoard != null && player.getScoreboard() != currentBoard) {
            // 其他插件在 CustomQuest 之后接管了计分板；立即让出，不在兜底刷新中抢回。
            scoreboardOwned.remove(uuid);
            boards.remove(uuid);
            previousBoards.remove(uuid);
            states.remove(uuid);
            suppressed.add(uuid);
            // 同上：其他插件接管的是侧边栏，HUD 不受影响。
            publishTo(player);
            return;
        }
        List<String> nextState = List.copyOf(lines);
        if (nextState.equals(states.get(uuid)) && currentBoard != null && player.getScoreboard() == currentBoard) {
            return;
        }

        Scoreboard board = currentBoard;
        if (board == null) {
            Scoreboard playerBoard = player.getScoreboard();
            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
            if (playerBoard != mainBoard || playerBoard.getObjective(DisplaySlot.SIDEBAR) != null) {
                // 不覆盖其他插件的私有计分板或主计分板已有的侧边栏。
                states.remove(uuid);
                suppressed.add(uuid);
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
        suppressed.remove(uuid);
        states.put(uuid, nextState);
        // 同一份内容同步给页面展示层（未安装 ChaUI 时为空操作）。
        // 注意推的是**全量**行：侧边栏那份已在 15 行处折叠，页面需要完整数据才能滚动查看。
        publishTo(player);
    }

    /**
     * @deprecated 背包检查已移至 QuestManager，且不再依赖任务追踪开关。
     */
    @Deprecated(forRemoval = true)
    public void queueUpdate(Player player) {
        QuestManager.getInstance().queueInventoryRefresh(player);
    }

    /**
     * 推给页面展示层的行数上限：侧边栏受 15 行限制，页面要拿到全量数据才能滚动查看。
     */
    private static final int PAGE_LINE_LIMIT = Integer.MAX_VALUE;

    /** 页面里任务之间不留空行（用户要求：board-line 下方不要出现空格）。 */
    private static final int PAGE_GAP_LINES = 0;

    /** 页面里内容行的前缀符号：纯白 ●，紧跟文字、中间不留空格（用户要求），任务名行不加。 */
    private static final String PAGE_BULLET = "§f●";

    /**
     * 给页面内容行加前缀符号。
     *
     * <p>任务名行不加；空行（内容不足一屏时的补位）也不加，否则会凭空多出一个孤立的 ●。
     */
    private static List<String> withBullets(List<String> rows, Set<Integer> nameIndexes) {
        List<String> result = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            String row = rows.get(index);
            result.add(row.isEmpty() || nameIndexes.contains(index) ? row : bulleted(row));
        }
        return result;
    }

    /**
     * 把白色 ● 顶到行首：行内容自己的颜色码紧跟在后面生效，
     * 所以 ● 保持白色，而正文颜色不会被这句 §f 带走。
     */
    private static String bulleted(String row) {
        return PAGE_BULLET + row;
    }

    /** 页面展示层需要的行元信息：任务名行的下标，以及这些行分别属于哪个任务。 */
    public static final class RowMeta {
        /** 任务名行的下标（决定该行用 1.2 倍字号） */
        public final Set<Integer> nameIndexes = new HashSet<>();
        /** 任务名行下标 → 任务 ID（HUD 的追踪按钮点击时靠它定位任务） */
        public final Map<Integer, String> nameRowQuests = new HashMap<>();
    }

    /**
     * 把 config 里的计分板标题与行数据推给页面；未安装 ChaUI 时整条路径为空操作。
     *
     * @param lines 空列表表示当前没有可显示的内容（无任务或计分板关闭），页面据此清空。
     */
    private void pushBoard(Player player, List<String> lines) {
        pushBoard(player, lines, new RowMeta());
    }

    private void pushBoard(Player player, List<String> lines, RowMeta meta) {
        DialogueManager manager = DialogueManager.getInstance();
        if (player == null || manager == null) {
            return;
        }
        manager.pushBoard(player, TextUtil.color(title), List.copyOf(lines), meta.nameIndexes, meta.nameRowQuests);
    }

    /**
     * 页面索要数据（页面加载时请求）：即时按当前状态回一份**完整**数据。
     * 计分板内容未变化时 update 不会推送，因此这条路径是页面首次显示的保证。
     * 与计分板开关无关：侧边栏关闭时 HUD 才是唯一显示，更要照常推送。
     */
    public void publishTo(Player player) {
        if (player == null) {
            return;
        }
        RowMeta meta = new RowMeta();
        pushBoard(player, withBullets(buildLines(player, PAGE_LINE_LIMIT, meta, PAGE_GAP_LINES), meta.nameIndexes),
                meta);
    }

    /** 侧边栏用：最多 15 行，超出部分折叠成"还有 N 个任务"。 */
    private List<String> buildLines(Player player) {
        return buildLines(player, MAX_LINES, null, Settings.boardGapLines);
    }

    /**
     * 按「接取时间 + 任务 ID」稳定排序后渲染行。
     *
     * @param limit 行数上限：侧边栏用 {@link #MAX_LINES}，页面用 {@link #PAGE_LINE_LIMIT} 取全量。
     * @param meta 非空时，把每个任务块首行（任务名行）的下标与所属任务记进去，供展示层放大字号与放追踪按钮。
     * @param gap 任务之间插入的空行数：侧边栏用 config 的 gap-lines，页面用 {@link #PAGE_GAP_LINES}。
     */
    private List<String> buildLines(Player player, int limit, RowMeta meta, int gap) {
        QuestManager manager = QuestManager.getInstance();
        List<Map.Entry<String, QuestProgress>> accepted = new ArrayList<>();
        for (Map.Entry<String, QuestProgress> entry : manager.getPlayerData(player).getAccepted().entrySet()) {
            if (manager.getQuest(entry.getKey()) != null) {
                accepted.add(entry);
            }
        }
        accepted.sort(Comparator
                .comparingLong((Map.Entry<String, QuestProgress> entry) -> entry.getValue().getAcceptedAt())
                .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

        List<String> lines = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, QuestProgress> entry : accepted) {
            Quest quest = manager.getQuest(entry.getKey());
            if (quest == null) {
                continue;
            }
            // 计算该任务「完整块」所需行数（标题 + 内容行 + 任务间空行）
            List<String> questLines = buildQuestLines(player, quest);
            int needed = Math.min(questLines.size() + gap, limit - 1);
            if (lines.size() + needed > limit - 1) {
                int remaining = accepted.size() - count;
                if (remaining > 0) {
                    lines.add("§7…还有 " + remaining + " 个任务");
                }
                return lines;
            }
            count++;
            // 任务块首行是任务名行：页面用它决定该行字号
            if (meta != null) {
                meta.nameIndexes.add(lines.size());
                // 同一处顺手记下这一行属于哪个任务，避免以后按行号反推导致错位
                meta.nameRowQuests.put(lines.size(), quest.getId());
            }
            // 渲染该任务块（计分板侧边栏，应用 & 颜色）
            for (String raw : questLines) {
                if (lines.size() >= limit - 1) {
                    lines.add("§7…");
                    return lines;
                }
                lines.add(TextUtil.color(raw));
            }
            // 任务之间的空行
            for (int g = 0; g < gap && lines.size() < limit - 1; g++) {
                lines.add("");
            }
        }
        int remaining = accepted.size() - count;
        if (remaining > 0 && lines.size() < limit) {
            lines.add("§7…还有 " + remaining + " 个任务");
        }
        return lines;
    }

    /**
     * 构建单个任务在计分板中的显示行（变量与 PAPI 已替换，& 颜色码保留）。
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
     * 应用自定义格式：替换变量 + PAPI 占位符（保留 & 颜色码，交给计分板渲染）。
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

    /** 清空某玩家的任务追踪计分板。 */
    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        states.remove(uuid);
        tracked.remove(uuid);
        suppressed.remove(uuid);
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
        suppressed.remove(uuid);
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

    /** 只兜底刷新当前正在追踪任务的玩家。 */
    public void refreshTracked() {
        if (!enabled) {
            // 侧边栏关闭时没有计分板要维护，但 HUD 仍要兜底补发：
            // 客户端晚于首次推送才加载页面时，靠这里的重发自愈。
            for (UUID uuid : new ArrayList<>(tracked)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    tracked.remove(uuid);
                    continue;
                }
                publishTo(player);
            }
            return;
        }
        for (UUID uuid : new ArrayList<>(tracked)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                tracked.remove(uuid);
                scoreboardOwned.remove(uuid);
                suppressed.remove(uuid);
                boards.remove(uuid);
                previousBoards.remove(uuid);
                states.remove(uuid);
                continue;
            }
            update(player);
            // 兜底补发页面数据：内容未变化时 update 不推送，而客户端可能刚加载页面、错过了首次推送。
            // 同内容的补发不会重置滚动位置（由页面钩子按内容是否变化决定）。
            publishTo(player);
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
        suppressed.clear();
        boards.clear();
        previousBoards.clear();
        states.clear();
    }
}
