package com.cj.customquest.dialogue;

import com.cj.customquest.quest.QuestObjective;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话分支：满足条件时显示该分支内容。
 * <p>
 * 条件：
 * <ul>
 *   <li>{@code data: key} + {@code data-value: value} —— NPC data 等于指定值</li>
 *   <li>{@code data: ["key==value", "key>=1"]} —— NPC data 条件列表</li>
 *   <li>{@code papi: ["%var% >= 5"]} —— PAPI 条件列表</li>
 *   <li>{@code default: true} —— 兜底分支（无条件时自动视为 default）</li>
 * </ul>
 */
public final class DialogueBranch {

    private final String id;
    private final boolean defaultBranch;
    private final List<String> dataConditions;
    private final List<String> papiConditions;
    private final List<String> lines;
    private final List<DialogueOption> options;

    public DialogueBranch(String id, boolean defaultBranch, List<String> dataConditions,
                          List<String> papiConditions, List<String> lines, List<DialogueOption> options) {
        this.id = id;
        this.defaultBranch = defaultBranch;
        this.dataConditions = dataConditions;
        this.papiConditions = papiConditions;
        this.lines = lines;
        this.options = options;
    }

    public String getId() {
        return id;
    }

    public boolean isDefaultBranch() {
        return defaultBranch;
    }

    public List<String> getDataConditions() {
        return dataConditions;
    }

    public List<String> getPapiConditions() {
        return papiConditions;
    }

    public List<String> getLines() {
        return lines;
    }

    public List<DialogueOption> getOptions() {
        return options;
    }

    /** 该分支是否有任何条件 */
    public boolean hasConditions() {
        return !dataConditions.isEmpty() || !papiConditions.isEmpty();
    }

    public static DialogueBranch load(String branchId, ConfigurationSection section) {
        List<String> dataConditions = new ArrayList<>();
        if (section.isString("data")) {
            String key = section.getString("data");
            String value = section.contains("data-value") ? section.getString("data-value") : null;
            if (value == null || value.isEmpty()) {
                // 仅要求 data 存在
                dataConditions.add(key);
            } else {
                dataConditions.add(key + "==" + value);
            }
        } else {
            dataConditions.addAll(section.getStringList("data"));
        }
        List<String> papiConditions = section.isString("papi")
                ? new ArrayList<>(List.of(section.getString("papi")))
                : section.getStringList("papi");

        List<String> lines = section.isString("lines")
                ? new ArrayList<>(List.of(section.getString("lines")))
                : section.getStringList("lines");

        List<DialogueOption> options = new ArrayList<>();
        ConfigurationSection optionsSection = section.getConfigurationSection("options");
        if (optionsSection != null) {
            for (String key : optionsSection.getKeys(false)) {
                ConfigurationSection optionSection = optionsSection.getConfigurationSection(key);
                if (optionSection == null) continue;
                if (!DialoguePayload.isValidOptionId(key)) {
                    throw new IllegalArgumentException("对话选项 ID 必须为 1-64 UTF-8 bytes 且不能包含控制字符: " + key);
                }
                String text = optionSection.getString("text", "");
                String hover = optionSection.getString("hover", "");
                // 接取任务快捷指令（无需写 Kether）
                String acceptQuest = normalizeQuestId(optionSection.getString("accept-quest", null));
                // 接取成功后设置的 NPC data 变量（"key=value"）
                List<String> acceptData = optionSection.isString("accept-data")
                        ? new ArrayList<>(List.of(optionSection.getString("accept-data")))
                        : optionSection.getStringList("accept-data");
                // 提交任务快捷指令：成功时才会扣物、完成任务并继续选项 Kether。
                String submitQuest = normalizeQuestId(optionSection.getString("submit-quest", null));
                List<QuestObjective> submitItems = loadSubmitItems(optionSection, key);
                if (acceptQuest != null && submitQuest != null) {
                    throw new IllegalArgumentException("对话选项不能同时配置 accept-quest 与 submit-quest: " + key);
                }
                if (!submitItems.isEmpty() && submitQuest == null) {
                    throw new IllegalArgumentException("对话选项配置 submit-items 时必须同时配置 submit-quest: " + key);
                }
                List<String> kether = optionSection.isString("kether")
                        ? new ArrayList<>(List.of(optionSection.getString("kether")))
                        : optionSection.getStringList("kether");
                boolean close = optionSection.getBoolean("close", true);
                options.add(new DialogueOption(key, text, hover, acceptQuest, acceptData,
                        submitQuest, submitItems, kether, close));
            }
        }

        boolean defaultBranch = section.getBoolean("default", false);
        return new DialogueBranch(branchId, defaultBranch, dataConditions, papiConditions, lines, options);
    }

    private static String normalizeQuestId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 严格解析 NPC 提交按钮的物品覆盖清单；配置错误时拒绝加载整个对话文件。 */
    private static List<QuestObjective> loadSubmitItems(ConfigurationSection section, String optionId) {
        if (!section.contains("submit-items")) {
            return List.of();
        }
        Object raw = section.get("submit-items");
        List<?> entries;
        if (raw instanceof List<?> list) {
            entries = list;
        } else if (raw != null) {
            entries = List.of(raw);
        } else {
            entries = List.of();
        }
        if (entries.isEmpty()) {
            throw submitItemError(optionId, 0, "列表不能为空");
        }

        List<QuestObjective> result = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            if (entry instanceof String text) {
                result.add(parseCompactSubmitItem(text, null, null, optionId, index));
            } else if (entry instanceof Map<?, ?> values) {
                String item = stringValue(values.get("item"));
                String display = blankToNull(stringValue(values.get("name")));
                String itemName = blankToNull(stringValue(values.get("item-name")));
                Object amount = values.get("amount");
                if (amount == null) {
                    result.add(parseCompactSubmitItem(item, display, itemName, optionId, index));
                } else {
                    Material material = parseMaterial(item, optionId, index);
                    result.add(QuestObjective.item(material,
                            parsePositiveAmount(amount, optionId, index), display, itemName));
                }
            } else {
                throw submitItemError(optionId, index, "必须是 MATERIAL:数量 字符串或 item 映射");
            }
        }
        return result;
    }

    private static QuestObjective parseCompactSubmitItem(String text, String display, String itemName,
                                                         String optionId, int index) {
        if (text == null) {
            throw submitItemError(optionId, index, "缺少 item");
        }
        String value = text.trim();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw submitItemError(optionId, index, "格式应为 MATERIAL:数量");
        }
        Material material = parseMaterial(value.substring(0, separator), optionId, index);
        int amount = parsePositiveAmount(value.substring(separator + 1), optionId, index);
        return QuestObjective.item(material, amount, display, itemName);
    }

    private static Material parseMaterial(String text, String optionId, int index) {
        Material material = text == null ? null : Material.matchMaterial(text.trim());
        if (material == null || material == Material.AIR
                || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            throw submitItemError(optionId, index, "未知材料 '" + text + "'");
        }
        return material;
    }

    private static int parsePositiveAmount(Object value, String optionId, int index) {
        String text = String.valueOf(value).trim();
        if (!text.matches("[1-9]\\d*")) {
            throw submitItemError(optionId, index, "数量必须是正整数");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw submitItemError(optionId, index, "数量超出整数范围");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static IllegalArgumentException submitItemError(String optionId, int index, String reason) {
        return new IllegalArgumentException("对话选项 " + optionId + " 的 submit-items[" + (index + 1) + "]: " + reason);
    }
}
