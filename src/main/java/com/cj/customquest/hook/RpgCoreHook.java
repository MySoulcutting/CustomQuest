package com.cj.customquest.hook;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * RPGCore GUI 页面接入（可选）。
 *
 * <p>任务面板与 NPC 对话都由 RPGCore 的**通用 page**承载：页面文件（{@code quest_board.yml} /
 * {@code dialogue.yml}）声明元素与变量绑定，本类只负责把内容推给页面、并处理页面回包。
 * 与旧 ChaUI 版本的差异只有两处：驱动接口换成 RPGCore 的 {@code RpgCoreGui}，以及**没有**页面脚本
 * 可调（旧版的 runScript/版本握手/刷新回执在新协议里没有对应物，已整体去掉）。
 *
 * <p>接入方式与旧版一致：事件类与服务接口都**按名字反射解析**，因此本插件对 RPGCore 没有编译期依赖，
 * 未安装 RPGCore 时整条链路跳过，原版箱子点击路径完全不受影响。
 */
public final class RpgCoreHook {

    /** RPGCore 抛出的页面回包事件（点击/滚动）。 */
    private static final String PACKET_EVENT_CLASS_NAME = "com.rpgcore.gui.RpgCoreGuiPacketEvent";

    /** RPGCore 抛出的悬停事件：文本元素没有悬停贴图，只能靠它驱动服务端侧高亮。 */
    private static final String HOVER_EVENT_CLASS_NAME = "com.rpgcore.gui.RpgCoreGuiHoverEvent";

    /** 页面 id：与 gui/quest_board.yml、gui/dialogue.yml 的 id 字段一一对应，改名要两边一起改。 */
    private static final String BOARD_PAGE_ID = "quest_board";
    private static final String DIALOGUE_PAGE_ID = "npc_dialogue";

    /** 回包 id：与页面元素上的 click-packet / scroll-*-packet 一一对应。 */
    private static final String BOARD_NAV_ID = "customquest:board_nav";
    private static final String BOARD_SCROLL_ID = "customquest:board_scroll";
    private static final String DIALOGUE_SELECT_ID = "customquest:dialogue_select";
    private static final String DIALOGUE_SKIP_ID = "customquest:dialogue_skip";

    /** 任务面板：每屏行数、首行 y、行距、任务块之间的额外间距（像素，用户要求）。 */
    private static final int BOARD_LINE_COUNT = 11;
    private static final int BOARD_LINE_START_Y = 66;
    private static final int BOARD_LINE_STEP = 11;
    private static final int BOARD_BLOCK_GAP_PIXELS = 3;

    /** 每行的字号变量取值：任务名 1.2、其余 1（用户要求）。 */
    private static final String BOARD_NAME_SIZE = "1.2";
    private static final String BOARD_BODY_SIZE = "1";

    /**
     * 面板在页面文件里的**静态原点 x**（{@code quest_board.yml} 的 600）。
     *
     * <p>只用来算导航列的 x：它是页面坐标，等于"静态原点 + 面板宽 − 导航列宽"（见 {@link #BOARD_NAV_COLUMN}）。
     * 面板的屏幕位置与整页缩放**不在这里算** —— 它们由页面文件里的公式按客户端实时窗口求值
     * （{@code x-variable}/{@code y-variable}/{@code scale-variable}，见 quest_board.yml 抬头），
     * 本插件只负责推内容与 {@code boardWidth} 这类变量。
     */
    private static final double BOARD_STATIC_ORIGIN_X = 600.0;

    /**
     * 面板宽度：随文字长短自适应。
     *
     * <p>宽度只有客户端能精确量（字体在客户端），这里按"字号 × 字符宽度比例"**估算**：页面正文用
     * 方正系中文字体，CJK 是方块字（宽 ≈ 字号），ASCII 约半宽。够用来让面板随文字伸缩，
     * 代价是宽/窄字符混排时会有一两像素误差。估算值推给页面的 {@code boardWidth} 变量。
     */
    private static final int BOARD_LEFT_PAD = 6;
    private static final int BOARD_NAV_COLUMN = 31;
    private static final int BOARD_RIGHT_PAD = 4;
    private static final int BOARD_MIN_WIDTH = 110;
    /** 字体基准字号：页面里 TTF 按 9px 基准绘制（见客户端 {@code TtfTextRenderer}）。 */
    private static final double BOARD_FONT_BASE = 9.0;
    private static final double BOARD_WIDE_RATIO = 1.0;
    private static final double BOARD_NARROW_RATIO = 0.5;
    private static final double BOARD_TITLE_SCALE = 1.0;
    private static final double BOARD_NAME_SCALE = Double.parseDouble(BOARD_NAME_SIZE);
    private static final double BOARD_BODY_SCALE = Double.parseDouble(BOARD_BODY_SIZE);

    /** 滚轮一格向下/向上滚动的行数（用户选择）。 */
    private static final int BOARD_SCROLL_STEP = 3;

    /** 导航提示停留时长（tick）：到点后把提示藏回去。 */
    private static final long NAV_TIP_TICKS = 50L;

    /** 正文逐字间隔与收尾余量（毫秒），与 dialogue.yml 的 reveal-interval-ms 保持一致。 */
    private static final long REVEAL_INTERVAL_MS = 80L;
    private static final long REVEAL_TAIL_MS = 150L;

    /** 对话框的选项个数：元素 id 为 option_1..option_4，悬停变量为 option1Hover..option4Hover。 */
    private static final int OPTION_COUNT = 4;

    /** 每位玩家最近一次下发的正文：跳过打字时要拿它推算"已经打完"。 */
    private static final Map<UUID, String> LAST_BODY = new ConcurrentHashMap<>();

    /** 每位玩家最近一次的任务追踪行：翻页时用它重算窗口。 */
    private static final Map<UUID, List<String>> LAST_BOARD = new ConcurrentHashMap<>();

    /** 上面那份行数据里哪些下标是任务名行：决定该行用 1.2 倍字号。 */
    private static final Map<UUID, Set<Integer>> LAST_BOARD_NAMES = new ConcurrentHashMap<>();

    /** 任务名行下标 → 任务 ID：点击追踪按钮时用它定位任务（与行数据一起缓存）。 */
    private static final Map<UUID, Map<Integer, String>> LAST_BOARD_QUESTS = new ConcurrentHashMap<>();

    /** 任务追踪的滚动偏移（每屏行数 11）。 */
    private static final Map<UUID, Integer> BOARD_OFFSET = new ConcurrentHashMap<>();

    /** 每位玩家正在计时的"收起导航提示"任务：连续开关导航时取消上一个。 */
    private static final Map<UUID, BukkitTask> NAV_TIP_TASKS = new ConcurrentHashMap<>();

    /** 每位玩家正在计时的"正文播完"任务：收到跳过回包时取消并立即收尾。 */
    private static final Map<UUID, BukkitTask> REVEAL_TASKS = new ConcurrentHashMap<>();

    /** 注册时保存，供事件回调与定时任务使用。 */
    private static Plugin owner;

    /**
     * RPGCore 的开发者 API；{@code null} 表示 RPGCore 未安装或版本不含 API，整条链路跳过。
     *
     * <p>早先这里靠"按类名反射解析 {@code RpgCoreGui} 再反射调用 setVariable/refreshPage"，
     * 因为当时没有对外契约。现在 RPGCore 提供了 {@code com.rpgcore.api}，改用编译期类型 +
     * {@code ServicesManager} 取用：方法名写错会在编译期报错，而不是静默地"点了没反应"。
     */
    private static volatile com.rpgcore.api.RpgCoreApi api;

    private RpgCoreHook() {
    }

    /** 注册事件监听与内容下发；RPGCore 不存在时静默跳过。 */
    public static void register(Plugin plugin) {
        com.rpgcore.api.RpgCoreApi resolved = resolveApi();
        // 事件类仍按名字解析：本插件对 RPGCore 是**可选**依赖（没装时走原版箱子界面），
        // 直接写类型会让"没装 RPGCore"的服务器在加载本类时就 NoClassDefFoundError。
        Class<?> packetEvent = resolveClass(PACKET_EVENT_CLASS_NAME);
        Class<?> hoverEvent = resolveClass(HOVER_EVENT_CLASS_NAME);
        if (resolved == null || packetEvent == null || hoverEvent == null
                || !Event.class.isAssignableFrom(packetEvent)
                || !Event.class.isAssignableFrom(hoverEvent)) {
            return;
        }
        api = resolved;
        owner = plugin;
        warnIfTitleCannotBeMatched();
        DialogueManager manager = DialogueManager.getInstance();
        if (manager != null) {
            manager.setBodySink((player, body, slotNames) -> pushBody(plugin, player, body));
            manager.setBoardSink((player, title, lines, names, rowQuests) ->
                    pushBoard(plugin, player, title, lines, names, rowQuests));
            // 收尾时明确告诉页面"这一屏收了"（在关箱子之前），它才能区分"页面收尾"与"玩家开了别的界面"。
            manager.setDisplayCloseSink((player, sessionId) -> closePage(player, DIALOGUE_PAGE_ID));
        }
        registerEvent(plugin, packetEvent, RpgCoreHook::handlePacket);
        registerEvent(plugin, hoverEvent, RpgCoreHook::handleHover);
    }

    /**
     * 从 ServicesManager 取 RPGCore API。
     *
     * <p>用 try/catch(Throwable)：没装 RPGCore 时，连"引用 {@code RpgCoreApi.class}"这一步
     * 都会抛 {@code NoClassDefFoundError}（类在常量池里，首次使用时解析）。
     */
    private static com.rpgcore.api.RpgCoreApi resolveApi() {
        try {
            com.rpgcore.api.RpgCoreApi loaded =
                    Bukkit.getServicesManager().load(com.rpgcore.api.RpgCoreApi.class);
            if (loaded == null || loaded.apiVersion() != com.rpgcore.api.RpgCoreApi.API_VERSION) {
                return null;
            }
            return loaded;
        } catch (Throwable missing) {
            return null;
        }
    }

    /** 该玩家的页面缓存清理（退出时调用），避免长期运行下按 UUID 的表一直累积。 */
    public static void forget(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        LAST_BODY.remove(uuid);
        LAST_BOARD.remove(uuid);
        LAST_BOARD_NAMES.remove(uuid);
        LAST_BOARD_QUESTS.remove(uuid);
        BOARD_OFFSET.remove(uuid);
        cancel(NAV_TIP_TASKS.remove(uuid));
        cancel(REVEAL_TASKS.remove(uuid));
    }

    /**
     * 把任务追踪的标题与行推给面板：页面只负责显示，行内容（颜色、文案）由任务文件决定。
     * 每次推送都把行缓存下来，翻页时据此重算窗口。
     */
    private static void pushBoard(Plugin plugin, Player player, String title, List<String> lines,
                                  Set<Integer> nameIndexes, Map<Integer, String> nameRowQuests) {
        if (player == null || !player.isOnline()) {
            return;
        }
        List<String> copy = lines == null ? List.of() : List.copyOf(lines);
        Set<Integer> names = nameIndexes == null ? Set.of() : Set.copyOf(nameIndexes);
        Map<Integer, String> rowQuests = nameRowQuests == null ? Map.of() : Map.copyOf(nameRowQuests);
        List<String> previous = LAST_BOARD.put(player.getUniqueId(), copy);
        LAST_BOARD_NAMES.put(player.getUniqueId(), names);
        LAST_BOARD_QUESTS.put(player.getUniqueId(), rowQuests);
        // 只有内容变化才回到顶部，否则兜底补发会把玩家滚下去的面板弹回第一行。
        if (!copy.equals(previous)) {
            BOARD_OFFSET.put(player.getUniqueId(), 0);
        }
        // 宽度先算出来：导航列的 x 由它决定（页面坐标）。
        int boardWidth = boardWidthFor(title, copy, names, anyNavigable(rowQuests));
        invoke(BOARD_PAGE_ID, player, "setVariable", "boardWidth", Integer.toString(boardWidth));
        invoke(BOARD_PAGE_ID, player, "setVariable", "navColumnX",
                Integer.toString((int) Math.round(BOARD_STATIC_ORIGIN_X + boardWidth - BOARD_NAV_COLUMN)));
        invoke(BOARD_PAGE_ID, player, "setVariable", "boardTitle", title == null ? "" : title);
        invoke(BOARD_PAGE_ID, player, "setVariable", "boardVisible", copy.isEmpty() ? "0" : "1");
        writeBoardWindow(player, copy, names, rowQuests, BOARD_OFFSET.getOrDefault(player.getUniqueId(), 0));
        invoke(BOARD_PAGE_ID, player, "refreshPage");
    }

    /** 面板宽度估算：内容最宽的一行（标题或正文，正文按该行字号缩放）加左右留白与导航列。 */
    private static int boardWidthFor(String title, List<String> lines, Set<Integer> names, boolean anyNav) {
        double content = estimateTextWidth(title, BOARD_TITLE_SCALE);
        for (int index = 0; index < lines.size(); index++) {
            double scale = names.contains(index) ? BOARD_NAME_SCALE : BOARD_BODY_SCALE;
            content = Math.max(content, estimateTextWidth(lines.get(index), scale));
        }
        double width = BOARD_LEFT_PAD + content + (anyNav ? BOARD_NAV_COLUMN : BOARD_RIGHT_PAD);
        return (int) Math.max(BOARD_MIN_WIDTH, Math.round(width));
    }

    /**
     * 估算一段文本的像素宽度（页面按 TTF 绘制，服务端只能近似）。
     *
     * <p>旧式颜色/格式码不占字形宽度，算进去面板会偏宽，所以先跳过它们：{@code &} 与 {@code §}
     * 都要认（任务文案经 {@code TextUtil.color} 转成 {@code §}，而 navTip 这类配置文本还是 {@code &}），
     * 十六进制的 {@code &#RRGGBB} / {@code §x§R§R§G§G§B§B} 也按整体跳过。客户端渲染端（TTF 那条
     * 路径）用的正是同一套码，两边对齐面板宽度才不会与实际画出来的文字错开。
     *
     * <p>全角字符按一个字号宽、其余按半宽。中文任务名/描述是方块字，用这个比例相当接近；
     * 宽窄混排会有一两像素误差 —— 宽度只是个近似值，客户端还会按屏幕宽度夹一次。
     */
    private static double estimateTextWidth(String text, double scale) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        double units = 0.0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '§' || character == '&') {
                index += codeExtraLength(text, index);
                continue;
            }
            units += character < 0x80 ? BOARD_NARROW_RATIO : BOARD_WIDE_RATIO;
        }
        return units * BOARD_FONT_BASE * scale;
    }

    /**
     * 颜色码要在标记字符之后再吃掉几个字符：{@code &#RRGGBB} 是 7 个、{@code §x§R§R§G§G§B§B} 是 12 个、
     * 普通的 {@code &a} 是 1 个；认不出的就返回 0（标记本身也不占宽，与原版渲染管线丢掉它的方向一致）。
     */
    private static int codeExtraLength(String text, int index) {
        int remaining = text.length() - index - 1;
        if (remaining <= 0) {
            return 0;
        }
        char next = text.charAt(index + 1);
        if (next == '#') {
            return Math.min(remaining, 7);
        }
        if (next == 'x' || next == 'X') {
            return Math.min(remaining, 12);
        }
        char lower = Character.toLowerCase(next);
        boolean codeLetter = (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')
                || (lower >= 'k' && lower <= 'o') || lower == 'r';
        return codeLetter ? 1 : 0;
    }

    /** 全量行里是否有任何任务可导航：决定要不要给导航列留宽度（按全量算，滚动时不跳宽）。 */
    private static boolean anyNavigable(Map<Integer, String> rowQuests) {
        NavigationManager navigation = NavigationManager.getInstance();
        if (navigation == null) {
            return false;
        }
        for (String questId : rowQuests.values()) {
            if (questId != null && !questId.isEmpty() && navigation.hasTarget(questId)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 写当前一屏：文字、字号、纵向位置、导航按钮显隐与纵向位置。
     *
     * <p>行与导航按钮的 y **必须一起推**：页面只有行绑了 y-variable 时，任务块下移会让文字动而按钮不动，
     * 于是按钮指着别的任务。两者都推，布局才自洽。
     */
    private static void writeBoardWindow(Player player, List<String> lines, Set<Integer> names,
                                         Map<Integer, String> rowQuests, int offset) {
        NavigationManager navigation = NavigationManager.getInstance();
        int shift = 0;
        for (int index = 0; index < BOARD_LINE_COUNT; index++) {
            int source = offset + index;
            boolean name = names.contains(source);
            // 任务块首行（本屏第一行除外）起累计下移：任务与任务之间留出额外间距。
            if (name && index > 0) {
                shift += BOARD_BLOCK_GAP_PIXELS;
            }
            int y = BOARD_LINE_START_Y + index * BOARD_LINE_STEP + shift;
            int row = index + 1;
            invoke(BOARD_PAGE_ID, player, "setVariable", "line" + row,
                    source < lines.size() ? lines.get(source) : "");
            invoke(BOARD_PAGE_ID, player, "setVariable", "line" + row + "Size",
                    name ? BOARD_NAME_SIZE : BOARD_BODY_SIZE);
            invoke(BOARD_PAGE_ID, player, "setVariable", "line" + row + "Y", Integer.toString(y));
            invoke(BOARD_PAGE_ID, player, "setVariable", "nav" + row + "Y", Integer.toString(y));
            String questId = rowQuests.get(source);
            boolean hasTarget = questId != null && !questId.isEmpty()
                    && navigation != null && navigation.hasTarget(questId);
            invoke(BOARD_PAGE_ID, player, "setVariable", "line" + row + "NavShow", hasTarget ? "1" : "0");
        }
    }

    /** 翻页：只在缓存范围内移动窗口，越界时忽略。 */
    private static void scrollBoard(Player player, List<?> data) {
        if (player == null || data == null || data.isEmpty()) {
            return;
        }
        List<String> lines = LAST_BOARD.get(player.getUniqueId());
        if (lines == null) {
            return;
        }
        Set<Integer> names = LAST_BOARD_NAMES.getOrDefault(player.getUniqueId(), Set.of());
        Map<Integer, String> rowQuests = LAST_BOARD_QUESTS.getOrDefault(player.getUniqueId(), Map.of());
        int maxOffset = Math.max(0, lines.size() - BOARD_LINE_COUNT);
        int offset = BOARD_OFFSET.getOrDefault(player.getUniqueId(), 0);
        String direction = asText(data.get(0));
        int next = "up".equalsIgnoreCase(direction)
                ? offset - BOARD_SCROLL_STEP : offset + BOARD_SCROLL_STEP;
        // 夹紧而不吸附到步长整数倍：列表末尾才不会被跳过，最后一屏一定露得出尾部内容。
        next = Math.min(Math.max(0, next), maxOffset);
        if (next == offset) {
            return;
        }
        BOARD_OFFSET.put(player.getUniqueId(), next);
        writeBoardWindow(player, lines, names, rowQuests, next);
        invoke(BOARD_PAGE_ID, player, "refreshPage");
    }

    /** 点击第 N 行的追踪按钮：切换该任务的导航（NavigationManager 内部保证同时只追踪一个）。 */
    private static void toggleNavigation(Player player, List<?> data) {
        if (player == null || data == null || data.isEmpty()) {
            return;
        }
        int row;
        try {
            row = Integer.parseInt(asText(data.get(0)));
        } catch (NumberFormatException failure) {
            return;
        }
        // 行号是**当前窗口**里的第几行，必须加上窗口起点才是它在全量清单里的下标 ——
        // 少了这个 offset，翻页后所有导航都会指着清单最前面的任务（越界的行则为空、点了没反应）。
        String questId = questForRow(LAST_BOARD_QUESTS.get(player.getUniqueId()),
                BOARD_OFFSET.getOrDefault(player.getUniqueId(), 0), row);
        if (questId == null || questId.isEmpty()) {
            return;
        }
        Quest quest = QuestManager.getInstance() == null ? null : QuestManager.getInstance().getQuest(questId);
        if (quest == null) {
            return;
        }
        NavigationManager navigation = NavigationManager.getInstance();
        if (navigation == null) {
            return;
        }
        if (navigation.isNavigating(player, questId)) {
            navigation.cancel(player);
        } else {
            navigation.start(player, quest);
        }
    }

    /**
     * 面板上的行号（**当前窗口**里的第 1..N 行）→ 该行对应任务的 id；该行不是任务名行时返回 null。
     *
     * <p>抽出来是为了能单测：这里的 off-by-offset 只在翻页后才显形（不翻页时窗口起点恒为 0，
     * 行号恰好等于全量下标，怎么看都是对的）。
     *
     * @param rowQuests 全量清单里"任务名行下标 → 任务 id"（见 {@code writeBoardWindow} 的同一份表）
     * @param offset    当前窗口起点
     */
    static String questForRow(Map<Integer, String> rowQuests, int offset, int row) {
        return rowQuests == null ? null : rowQuests.get(offset + row - 1);
    }

    /**
     * 把正文推给对话页面并**由服务端推进逐字状态**。
     *
     * <p>旧版把逐字交给页面脚本、再用页面回执推算"打完了"；新协议没有页面脚本，也没有页面→服务端的
     * 回执，所以这里改成：写入正文 → {@code typing=1}（显示逐字 + 跳过层）→ 按字符数×逐字间隔排一个
     * 收尾任务 → 到点置 {@code typing=0 / bodyReady=1 / textReady=1}。玩家点"跳过"则立即收尾。
     *
     * <p>选项文字与 NPC 名字不再由本类下发：它们是**容器槽物品名**，由页面按槽位绑定直接读。
     */
    private static void pushBody(Plugin plugin, Player player, String body) {
        if (player == null || body == null || !player.isOnline()) {
            return;
        }
        LAST_BODY.put(player.getUniqueId(), body);
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "bodyText", body);
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "typing", "1");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "bodyReady", "0");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "textReady", "0");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "hintVisible", "1");
        invoke(DIALOGUE_PAGE_ID, player, "refreshPage");
        scheduleRevealEnd(plugin, player);
    }

    /** 排一个"正文播完"的任务；重复下发时取消上一个，免得新正文被上一次的收尾提前结束。 */
    private static void scheduleRevealEnd(Plugin plugin, Player player) {
        UUID uuid = player.getUniqueId();
        cancel(REVEAL_TASKS.remove(uuid));
        String body = LAST_BODY.get(uuid);
        if (plugin == null || body == null) {
            return;
        }
        long delayTicks = Math.max(1L, (revealMillis(body) + REVEAL_TAIL_MS + 49L) / 50L);
        REVEAL_TASKS.put(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            REVEAL_TASKS.remove(uuid);
            finishReveal(player);
        }, delayTicks));
    }

    /** 玩家点任意处跳过打字：立刻收尾（幂等，重复点击不会出问题）。 */
    private static void skipReveal(Player player) {
        if (player == null) {
            return;
        }
        cancel(REVEAL_TASKS.remove(player.getUniqueId()));
        finishReveal(player);
    }

    /** 收尾：正文由逐字态转为整段可见，并收起跳过提示。 */
    private static void finishReveal(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "typing", "0");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "bodyReady", "1");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "textReady", "1");
        invoke(DIALOGUE_PAGE_ID, player, "setVariable", "hintVisible", "0");
        invoke(DIALOGUE_PAGE_ID, player, "refreshPage");
    }

    /** 揭示耗时：只数可见字符，颜色码与换行不计入。 */
    private static long revealMillis(String body) {
        long characters = 0L;
        for (int index = 0; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '§' && index + 1 < body.length()) {
                index++;
                continue;
            }
            if (current == '\n' || current == '\r') {
                continue;
            }
            characters++;
        }
        return characters * REVEAL_INTERVAL_MS;
    }

    /**
     * 把导航开关的提示交给面板显示（{@code navTip} 文案 + {@code navTipShow} 显隐）。
     *
     * <p>返回 true 表示已经交给页面，调用方不必再发原版动作栏；未安装 RPGCore 或接口缺方法时返回 false，
     * 由调用方走动作栏兜底。
     */
    public static boolean showNavTip(Player player, String text) {
        if (player == null || text == null || !player.isOnline()) {
            return false;
        }
        if (!invoke(BOARD_PAGE_ID, player, "setVariable", "navTip", text)
                || !invoke(BOARD_PAGE_ID, player, "setVariable", "navTipShow", "1")) {
            return false;
        }
        scheduleHideNavTip(player);
        return true;
    }

    /** 到点收起提示；连续开关导航时取消上一次的收起任务，不会被提前藏掉。 */
    private static void scheduleHideNavTip(Player player) {
        Plugin plugin = owner;
        if (plugin == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancel(NAV_TIP_TASKS.remove(uuid));
        NAV_TIP_TASKS.put(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NAV_TIP_TASKS.remove(uuid);
            if (player.isOnline()) {
                // 只翻显隐变量，文案留着，免得下次弹出时先闪一下空文字。
                invoke(BOARD_PAGE_ID, player, "setVariable", "navTipShow", "0");
            }
        }, NAV_TIP_TICKS));
    }

    /** 处理页面回包（点击 / 滚动）。 */
    private static void handlePacket(Object event) {
        try {
            Object playerValue = read(event, "getPlayer");
            if (!(playerValue instanceof Player player)) {
                return;
            }
            String pageId = asText(read(event, "getPageId"));
            String packetId = asText(read(event, "getPacketId"));
            List<?> data = asList(read(event, "getData"));
            if (BOARD_PAGE_ID.equals(pageId)) {
                if (BOARD_SCROLL_ID.equals(packetId)) {
                    scrollBoard(player, data);
                } else if (BOARD_NAV_ID.equals(packetId)) {
                    toggleNavigation(player, data);
                }
                return;
            }
            if (!DIALOGUE_PAGE_ID.equals(pageId)) {
                return;
            }
            if (DIALOGUE_SKIP_ID.equals(packetId)) {
                skipReveal(player);
                return;
            }
            if (!DIALOGUE_SELECT_ID.equals(packetId)) {
                return;
            }
            if (data == null || data.isEmpty()) {
                return;
            }
            // 选项回包带的是**槽下标**（"2".."5"）：插件按槽取该格物品名，语义与"把选项文本作为参数"一致。
            DialogueManager manager = DialogueManager.getInstance();
            if (manager != null && manager.selectOptionByRequest(player, optionTextFor(player, asText(data.get(0))))) {
                playClickSound(player);
            }
        } catch (Throwable failure) {
            Bukkit.getLogger().warning("[CustomQuest] 处理页面回包失败: " + failure.getMessage());
        }
    }

    /** 把选项槽下标翻成该格的物品名（= 选项文字）；取不到时用原始下标兜底。 */
    private static String optionTextFor(Player player, String slotText) {
        try {
            int slot = Integer.parseInt(slotText);
            if (player.getOpenInventory() == null) {
                return slotText;
            }
            org.bukkit.inventory.ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                return slotText;
            }
            return item.getItemMeta().getDisplayName();
        } catch (Throwable failure) {
            return slotText;
        }
    }

    /** 处理悬停上报：把对话选项的悬停态推给页面（文本元素没有悬停贴图，只能服务端改显隐）。 */
    private static void handleHover(Object event) {
        try {
            Object playerValue = read(event, "getPlayer");
            if (!(playerValue instanceof Player player)) {
                return;
            }
            if (!DIALOGUE_PAGE_ID.equals(asText(read(event, "getPageId")))) {
                return;
            }
            String elementId = asText(read(event, "getElementId"));
            for (int option = 1; option <= OPTION_COUNT; option++) {
                boolean hovered = elementId.equals("option_" + option);
                invoke(DIALOGUE_PAGE_ID, player, "setVariable", "option" + option + "Hover", hovered ? "1" : "0");
            }
        } catch (Throwable failure) {
            Bukkit.getLogger().warning("[CustomQuest] 处理页面悬停失败: " + failure.getMessage());
        }
    }

    /** 播放原版按钮点击音效（只放给该玩家，无需客户端素材）。 */
    private static void playClickSound(Player player) {
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } catch (Throwable failure) {
            Bukkit.getLogger().warning("[CustomQuest] 播放选项点击音效失败: " + failure.getMessage());
        }
    }

    /**
     * 对话箱子标题必须能命中页面：页面按 {@code match-title: NPCdialogue} 认领容器，
     * 标题对不上时对话只会退回原版箱子（页面不激活）。装了 RPGCore 时这里给出提示。
     */
    private static void warnIfTitleCannotBeMatched() {
        String title = com.cj.customquest.config.Settings.dialogueTitle;
        if (title == null || title.contains("NPCdialogue")) {
            return;
        }
        Bukkit.getLogger().warning("[CustomQuest] 对话箱子标题 \"" + title
                + "\" 不含页面认领串 NPCdialogue，对话页面不会激活（会退回原版箱子）。"
                + "请在 config.yml 把 dialogue.title 改成含 NPCdialogue 的值。");
    }

    // ---- 反射与事件接线 ----

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerEvent(Plugin plugin, Class<?> eventClass, Consumer<Object> handler) {
        Class<? extends Event> typed = (Class<? extends Event>) eventClass;
        Bukkit.getPluginManager().registerEvent(typed, new Listener() {
        }, EventPriority.NORMAL, (listener, event) -> handler.accept(event), plugin);
    }

    /** 从 ServicesManager 取 RPGCore 的页面服务实例；未安装时返回 null。 */
    /** RPGCore 的 GUI 能力；未接入时为 {@code null}。 */
    private static com.rpgcore.api.GuiApi gui() {
        com.rpgcore.api.RpgCoreApi current = api;
        return current == null ? null : current.gui();
    }

    /** 关闭某个页面（映射到 {@link com.rpgcore.api.GuiApi#closePage}）。 */
    private static boolean closePage(Player player, String pageId) {
        return invoke(pageId, player, "closePage");
    }

    /**
     * 下发页面操作。
     *
     * <p>保留这个"按操作名转发"的形状，是为了让二十多个调用点保持简洁（每次都写三个参数的
     * 三段式反而更长）。与旧实现的区别是**不再反射**：方法名对不上会在编译期就被发现，
     * 而不会变成静默的"点了没反应"。
     */
    private static boolean invoke(String pageId, Player player, String method, Object... arguments) {
        com.rpgcore.api.GuiApi target = gui();
        if (target == null || player == null) {
            return false;
        }
        try {
            switch (method) {
                case "setVariable" -> target.setVariable(player, pageId,
                        String.valueOf(arguments[0]), String.valueOf(arguments[1]));
                case "refreshPage" -> target.refreshPage(player, pageId);
                case "closePage" -> target.closePage(player, pageId);
                default -> {
                    Bukkit.getLogger().warning("[CustomQuest] 未知的页面操作: " + method);
                    return false;
                }
            }
            return true;
        } catch (Throwable failure) {
            Bukkit.getLogger().warning("[CustomQuest] 调用页面接口 " + method + " 失败: " + failure.getMessage());
            return false;
        }
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private static Object read(Object event, String getter) throws ReflectiveOperationException {
        Method method = event.getClass().getMethod(getter);
        return method.invoke(event);
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
