package com.cj.customquest.quest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务定义（从 quests/*.yml 读取）。
 * <p>
 * 支持多项目标：
 * <ul>
 *   <li>kill_mob：单目标简写 {@code mob: SkeletonKing} + {@code amount: 10}；
 *       或多目标 {@code objectives: [{mob: ..., amount: ...}, ...]}</li>
 *   <li>submit_item：{@code items: ["DIAMOND:5", "IRON_INGOT:10"]}（天然支持多种物品）</li>
 * </ul>
 * <p>
 * 注意：接取任务<b>不校验前置条件</b>，任务门控请使用 NPC 对话分支条件（data / PAPI 条件）。
 */
public final class Quest {

    /** 任务 ID（唯一） */
    private final String id;
    /** 任务名称 */
    private final String name;
    /** 任务描述 */
    private final List<String> description;
    /** 任务类型 */
    private final QuestType type;
    /** 目标列表（至少 1 个） */
    private final List<QuestObjective> objectives;
    /** 达成全部任务条件时执行一次的控制台指令（不完成任务） */
    private final List<String> conditionCommands;
    /** 是否可重复 */
    private final boolean repeatable;
    /** 可重复任务的冷却时间（秒） */
    private final long cooldown;
    /** 该任务在全息视图（计分板）上的自定义标题行格式（null = 使用全局默认格式） */
    private final String boardTitle;
    /** 该任务在全息视图（计分板）上的完全自定义显示行列表（空 = 使用自动生成的目标行） */
    private final List<String> boardLines;
    /** 导航目标位置（null = 未配置导航） */
    private final Location navigateLocation;

    private Quest(String id, String name, List<String> description, QuestType type,
                  List<QuestObjective> objectives,
                  List<String> conditionCommands,
                  boolean repeatable, long cooldown, String boardTitle,
                  List<String> boardLines, Location navigateLocation) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.objectives = objectives;
        this.conditionCommands = conditionCommands;
        this.repeatable = repeatable;
        this.cooldown = cooldown;
        this.boardTitle = boardTitle;
        this.boardLines = boardLines;
        this.navigateLocation = navigateLocation;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescription() {
        return description;
    }

    public QuestType getType() {
        return type;
    }

    public List<QuestObjective> getObjectives() {
        return objectives;
    }

    public List<String> getConditionCommands() {
        return conditionCommands;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public long getCooldown() {
        return cooldown;
    }

    /** 该任务在全息视图（计分板）上的自定义标题行格式（null = 使用全局默认格式） */
    public String getBoardTitle() {
        return boardTitle;
    }

    /** 该任务在全息视图（计分板）上的完全自定义显示行列表（空 = 使用自动生成的目标行） */
    public List<String> getBoardLines() {
        return boardLines;
    }

    /** 导航目标位置（null = 未配置导航） */
    public Location getNavigateLocation() {
        return navigateLocation;
    }

    /** 总需求数量（所有目标需求之和） */
    public int getTotalAmount() {
        int total = 0;
        for (QuestObjective objective : objectives) {
            total += objective.getAmount();
        }
        return total;
    }

    private static List<String> stringList(ConfigurationSection section, String key) {
        if (section.isString(key)) {
            return new ArrayList<>(List.of(section.getString(key)));
        }
        return section.getStringList(key);
    }

    private static int intOf(ConfigurationSection section, String key, int def) {
        return section.contains(key) ? section.getInt(key) : def;
    }

    private static boolean boolOf(ConfigurationSection section, String key, boolean def) {
        return section.contains(key) ? section.getBoolean(key) : def;
    }

    /** 解析单个目标（objectives 列表项或映射项），失败返回 null 并把原因写入 errors。 */
    private static QuestObjective parseObjective(QuestType type, String questId, String display,
                                                Map<?, ?> values, List<String> errors) {
        String boardLine = blankToNull(strOf(values.get("board-line")));
        if (type == QuestType.KILL_MOB) {
            String mob = strOf(values.get("mob"));
            int amount = intOfValue(values.get("amount"), 1);
            String displayName = strOf(values.get("name"));
            if (mob == null || mob.isEmpty()) {
                errors.add("任务 " + questId + ": " + display + " 缺少 mob（MythicMobs 怪物内部名）");
                return null;
            }
            return QuestObjective.kill(mob, amount, displayName, boardLine);
        }
        String itemText = strOf(values.get("item"));
        String displayName = strOf(values.get("name"));
        String itemName = strOf(values.get("item-name"));
        QuestObjective objective = QuestObjective.parseItem(itemText, displayName, itemName, boardLine);
        if (objective == null) {
            errors.add("任务 " + questId + ": " + display + " 无法解析物品 '" + itemText + "'（格式: 材料:数量）");
            return null;
        }
        return objective;
    }

    /** 空白字符串视为未配置（返回 null） */
    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String strOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intOfValue(Object value, int def) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    /** 解析导航位置 "世界名,x,y,z"，失败返回 null */
    private static Location parseNavigate(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000.0 || Math.abs(y) > 30_000_000.0
                    || Math.abs(z) > 30_000_000.0) {
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从配置节点加载任务，加载失败返回 null（原因放入 errors）。
     */
    public static Quest load(String fileId, ConfigurationSection section, List<String> errors) {
        String id = section.getString("quest-id", fileId);
        QuestType type = QuestType.parse(section.getString("type", ""));
        if (type == null) {
            errors.add("任务 " + id + ": 未知的任务类型 '" + section.getString("type") + "'（可选: kill_mob / submit_item / describe）");
            return null;
        }
        String name = section.getString("name", id);
        List<String> description = stringList(section, "description");
        // 该任务在全息视图（计分板）上的自定义标题行格式（可选，留空 = 使用全局默认格式）
        String boardTitle = blankToNull(section.getString("board-title", null));
        // 该任务在全息视图（计分板）上的完全自定义显示行（可选，留空 = 自动生成目标行）
        List<String> boardLines = stringList(section, "board-line");
        // 导航目标位置（可选）：navigate: "世界名,x,y,z"
        Location navigateLocation = parseNavigate(section.getString("navigate"));

        // ---------------- 目标加载（支持多项目标；描述任务无目标） ----------------
        List<QuestObjective> objectives = new ArrayList<>();
        if (type != QuestType.DESCRIBE) {
            // 写法一：objectives 列表（推荐，文档与示例格式）
            List<Map<?, ?>> objectiveMaps = section.getMapList("objectives");
            if (!objectiveMaps.isEmpty()) {
                for (int i = 0; i < objectiveMaps.size(); i++) {
                    String display = "目标[" + (i + 1) + "]";
                    QuestObjective objective = parseObjective(type, id, display, objectiveMaps.get(i), errors);
                    if (objective != null) {
                        objectives.add(objective);
                    }
                }
            } else {
                // 兼容映射写法：objectives: { key: { mob/item: ... } }
                ConfigurationSection objectivesSection = section.getConfigurationSection("objectives");
                if (objectivesSection != null) {
                    for (String key : objectivesSection.getKeys(false)) {
                        ConfigurationSection objectiveSection = objectivesSection.getConfigurationSection(key);
                        if (objectiveSection == null) continue;
                        String display = "目标[" + key + "]";
                        QuestObjective objective = parseObjective(type, id, display, objectiveSection.getValues(false), errors);
                        if (objective != null) {
                            objectives.add(objective);
                        }
                    }
                }
            }
            if (objectives.isEmpty()) {
                // 单目标简写（兼容旧配置）
                if (type == QuestType.KILL_MOB) {
                    String mob = section.getString("mob", "");
                    int amount = intOf(section, "amount", 1);
                    if (mob.isEmpty()) {
                        errors.add("任务 " + id + ": kill_mob 类型必须配置 mob 或 objectives（MythicMobs 怪物内部名）");
                        return null;
                    }
                    objectives.add(QuestObjective.kill(mob, amount, section.getString("name", null)));
                } else {
                    for (String line : stringList(section, "items")) {
                        QuestObjective objective = QuestObjective.parseItem(line);
                        if (objective == null) {
                            errors.add("任务 " + id + ": 无法解析物品 '" + line + "'（格式: 材料:数量，如 DIAMOND:5）");
                            continue;
                        }
                        objectives.add(objective);
                    }
                    if (objectives.isEmpty()) {
                        errors.add("任务 " + id + ": submit_item 类型必须配置 items 或 objectives 列表");
                        return null;
                    }
                }
            }
        }

        List<String> conditionCommands = stringList(section, "condition-commands");
        List<String> removedFields = new ArrayList<>();
        for (String key : List.of("auto-complete", "commands", "kether")) {
            if (section.contains(key)) {
                removedFields.add(key);
            }
        }
        if (!removedFields.isEmpty()) {
            errors.add("任务 " + id + ": 已删除并忽略配置项 " + String.join(", ", removedFields));
        }
        boolean repeatable = boolOf(section, "repeatable", false);
        long cooldown = section.contains("cooldown") ? section.getLong("cooldown") : 0L;

        return new Quest(id, name, description, type, objectives,
                conditionCommands, repeatable, cooldown, boardTitle, boardLines,
                navigateLocation);
    }
}
