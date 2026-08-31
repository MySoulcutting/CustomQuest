package com.cj.customquest.dialogue;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

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
                // 提交任务快捷指令：成功时才会扣物、完成任务并继续 data/Kether。
                String submitQuest = normalizeQuestId(optionSection.getString("submit-quest", null));
                List<String> submitData = optionSection.isString("submit-data")
                        ? new ArrayList<>(List.of(optionSection.getString("submit-data")))
                        : optionSection.getStringList("submit-data");
                if (acceptQuest != null && submitQuest != null) {
                    throw new IllegalArgumentException("对话选项不能同时配置 accept-quest 与 submit-quest: " + key);
                }
                List<String> kether = optionSection.isString("kether")
                        ? new ArrayList<>(List.of(optionSection.getString("kether")))
                        : optionSection.getStringList("kether");
                boolean close = optionSection.getBoolean("close", true);
                options.add(new DialogueOption(key, text, hover, acceptQuest, acceptData,
                        submitQuest, submitData, kether, close));
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
}
