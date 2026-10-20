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
    private final Set<String> entryBranchIds;
    private final boolean nodeFormat;
    private final List<String> warnings;

    public DialogueConfig(String file, List<Integer> npcIds, String title,
                          Map<String, String> defaultData, List<DialogueBranch> branches) {
        this(file, npcIds, title, defaultData, branches, Set.of(), false, List.of());
    }

    private DialogueConfig(String file, List<Integer> npcIds, String title,
                           Map<String, String> defaultData, List<DialogueBranch> branches,
                           Set<String> entryBranchIds, boolean nodeFormat, List<String> warnings) {
        this.file = file;
        this.npcIds = List.copyOf(npcIds);
        this.title = title;
        this.defaultData = Map.copyOf(defaultData);
        this.branches = List.copyOf(branches);
        this.entryBranchIds = Set.copyOf(entryBranchIds);
        this.nodeFormat = nodeFormat;
        this.warnings = List.copyOf(warnings);
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

    public boolean isNodeFormat() {
        return nodeFormat;
    }

    public boolean isEntryBranch(String branchId) {
        return entryBranchIds.contains(branchId);
    }

    public List<String> getWarnings() {
        return warnings;
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

        if (hasNodeEntries(root.get("when"))) {
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
        List<String> warnings = new ArrayList<>();
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
                String branchId = open.trim();
                if (conditions.containsKey(branchId)) {
                    warnings.add("对话文件 " + file + " 的 when.open 重复：" + branchId + "，已保留第一次配置");
                    continue;
                }
                conditions.put(branchId, parseConditionSet(stringValue(map.get("if"))));
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
            ConfigurationSection section = root.getConfigurationSection(nodeId);
            if (section == null) {
                warnings.add("对话文件 " + file + " 的入口分支不存在：" + nodeId);
                continue;
            }
            DialogueBranch branch = loadNode(section, nodeId,
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

        validateGotoTargets(file, branches, warnings);
        return new DialogueConfig(file, npcIds, title, Map.of(), branches,
                conditions.keySet(), true, warnings);
    }

    private static boolean hasNodeEntries(Object value) {
        if (value instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map) {
                    Object open = map.get("open");
                    if (open != null && !String.valueOf(open).isBlank()) {
                        return true;
                    }
                }
            }
        }
        return false;
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
                if (!option.containsKey("close")) {
                    option.put("close", false);
                }
            }
            ConfigurationSection optionSection = optionsSection.createSection(optionId);
            for (Map.Entry<String, Object> entry : option.entrySet()) {
                optionSection.set(entry.getKey(), entry.getValue());
            }
        }
        return DialogueBranch.load(nodeId, converted);
    }

    private static boolean isNodeKey(ConfigurationSection root, String key) {
        if (Set.of("title", "npc", "npc id", "when", "default-data", "branches").contains(key)) {
            return false;
        }
        ConfigurationSection section = root.getConfigurationSection(key);
        return section != null
                && (section.contains("npc")
                || section.contains("player")
                || section.contains("format"));
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

    private static void validateGotoTargets(String file, List<DialogueBranch> branches,
                                            List<String> warnings) {
        Set<String> branchIds = branches.stream().map(DialogueBranch::getId).collect(java.util.stream.Collectors.toSet());
        for (DialogueBranch branch : branches) {
            for (DialogueOption option : branch.getOptions()) {
                for (String action : option.getKether()) {
                    String target = gotoTarget(action);
                    if (target != null && !branchIds.contains(target)) {
                        warnings.add("对话文件 " + file + " 的分支 " + branch.getId()
                                + " 选项 " + option.getId() + " 跳转目标不存在：" + target);
                    }
                }
            }
        }
    }

    private static String gotoTarget(String action) {
        if (action == null) {
            return null;
        }
        String value = action.replace('\ufeff', ' ').trim();
        if (!value.regionMatches(true, 0, "goto", 0, 4)
                || (value.length() > 4 && !Character.isWhitespace(value.charAt(4)))) {
            return null;
        }
        String target = value.substring(4).trim();
        if (target.length() >= 2 && target.startsWith("\"") && target.endsWith("\"")) {
            target = target.substring(1, target.length() - 1).trim();
        }
        return target.isEmpty() ? null : target;
    }

    private record ConditionSet(List<String> dataConditions, List<String> papiConditions) {
    }
}
