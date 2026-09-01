package com.cj.customquest.dialogue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一个 NPC 的对话配置文件（dialogues/*.yml）。
 * 每个文件通过 {@code npc id: <id>} 或兼容旧版的 {@code npc: <id>} 绑定 Citizens NPC。
 */
public final class DialogueConfig {

    private final String file;
    private final List<Integer> npcIds;
    private final String title;
    /** 首次对话时初始化的玩家级 data 变量（key -> value） */
    private final Map<String, String> defaultData;
    private final List<DialogueBranch> branches;

    public DialogueConfig(String file, List<Integer> npcIds, String title,
                          Map<String, String> defaultData, List<DialogueBranch> branches) {
        this.file = file;
        this.npcIds = npcIds;
        this.title = title;
        this.defaultData = defaultData;
        this.branches = branches;
    }

    public String getFile() {
        return file;
    }

    public List<Integer> getNpcIds() {
        return npcIds;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, String> getDefaultData() {
        return defaultData;
    }

    public List<DialogueBranch> getBranches() {
        return branches;
    }

    public static DialogueConfig load(String file, ConfigurationSection root) {
        List<Integer> npcIds = new ArrayList<>();
        Object npcValue = root.contains("npc id") ? root.get("npc id") : root.get("npc");
        if (npcValue instanceof List<?> ids) {
            for (Object id : ids) {
                try {
                    npcIds.add(Integer.parseInt(String.valueOf(id).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (npcValue instanceof Number number) {
            npcIds.add(number.intValue());
        } else if (npcValue != null) {
            try {
                npcIds.add(Integer.parseInt(String.valueOf(npcValue).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        String title = root.getString("title", "");

        if (root.contains("when")) {
            return loadNodeFormat(file, root, npcIds, title);
        }

        // 首次对话初始化数据
        Map<String, String> defaultData = new LinkedHashMap<>();
        ConfigurationSection defaultSection = root.getConfigurationSection("default-data");
        if (defaultSection != null) {
            for (String key : defaultSection.getKeys(false)) {
                defaultData.put(key, defaultSection.getString(key, ""));
            }
        }

        List<DialogueBranch> branches = new ArrayList<>();
        ConfigurationSection branchesSection = root.getConfigurationSection("branches");
        if (branchesSection != null) {
            for (String key : branchesSection.getKeys(false)) {
                ConfigurationSection section = branchesSection.getConfigurationSection(key);
                if (section == null) continue;
                branches.add(DialogueBranch.load(key, section));
            }
        }
        return new DialogueConfig(file, npcIds, title, defaultData, branches);
    }

    private static DialogueConfig loadNodeFormat(String file, ConfigurationSection root,
                                                 List<Integer> npcIds, String title) {
        Map<String, ConditionSet> conditions = new LinkedHashMap<>();
        Object rawWhen = root.get("when");
        if (rawWhen instanceof List<?> entries) {
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                String open = stringValue(map.get("open"));
                if (open == null || open.isBlank()) {
                    continue;
                }
                conditions.putIfAbsent(open.trim(), parseConditionSet(stringValue(map.get("if"))));
            }
        }

        List<String> nodeIds = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            if (isNodeKey(root, key)) {
                nodeIds.add(key);
            }
        }

        List<DialogueBranch> branches = new ArrayList<>();
        Set<String> loaded = new java.util.HashSet<>();
        for (String nodeId : conditions.keySet()) {
            DialogueBranch branch = loadNode(root.getConfigurationSection(nodeId), nodeId,
                    conditions.get(nodeId));
            if (branch != null) {
                branches.add(branch);
                loaded.add(nodeId);
            }
        }
        for (String nodeId : nodeIds) {
            if (loaded.contains(nodeId)) {
                continue;
            }
            DialogueBranch branch = loadNode(root.getConfigurationSection(nodeId), nodeId,
                    new ConditionSet(List.of(), List.of()));
            if (branch != null) {
                branches.add(branch);
            }
        }

        return new DialogueConfig(file, npcIds, title, Map.of(), branches);
    }

    private static DialogueBranch loadNode(ConfigurationSection section, String nodeId,
                                           ConditionSet conditionSet) {
        if (section == null) {
            return null;
        }
        YamlConfiguration converted = new YamlConfiguration();
        converted.set("data", conditionSet.dataConditions());
        converted.set("papi", conditionSet.papiConditions());
        converted.set("lines", readTextList(section.get("npc")));

        List<Map<?, ?>> players = section.getMapList("player");
        ConfigurationSection optionsSection = converted.createSection("options");
        for (int index = 0; index < players.size(); index++) {
            Map<?, ?> source = players.get(index);
            String optionId = stringValue(source.get("id"));
            if (optionId == null || optionId.isBlank()) {
                optionId = "option_" + (index + 1);
            }
            Map<String, Object> option = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    option.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            if (option.containsKey("reply")) {
                option.put("text", option.get("reply"));
            }
            if (option.containsKey("then")) {
                option.put("kether", option.get("then"));
            }
            ConfigurationSection optionSection = optionsSection.createSection(optionId);
            for (Map.Entry<String, Object> entry : option.entrySet()) {
                optionSection.set(entry.getKey(), entry.getValue());
            }
        }
        converted.set("default", section.getBoolean("default", false));
        return DialogueBranch.load(nodeId, converted);
    }

    private static boolean isNodeKey(ConfigurationSection root, String key) {
        if (Set.of("title", "npc", "npc id", "when", "default-data", "branches").contains(key)) {
            return false;
        }
        return root.isConfigurationSection(key);
    }

    private static ConditionSet parseConditionSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ConditionSet(List.of(), List.of());
        }
        String value = raw.trim().replaceFirst("(?i)^check\\s+profile\\s+data\\s+", "");
        List<String> data = new ArrayList<>();
        List<String> papi = new ArrayList<>();
        for (String part : value.split("\\s*,\\s*")) {
            String condition = part.trim();
            if (condition.isEmpty()) {
                continue;
            }
            if (condition.contains("%")) {
                papi.add(condition);
            } else {
                data.add(condition);
            }
        }
        return new ConditionSet(data, papi);
    }

    private static List<String> readTextList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ConditionSet(List<String> dataConditions, List<String> papiConditions) {
    }
}
