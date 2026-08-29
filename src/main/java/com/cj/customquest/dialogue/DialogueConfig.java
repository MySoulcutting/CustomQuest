package com.cj.customquest.dialogue;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 NPC 的对话配置文件（dialogues/*.yml）。
 * 每个文件通过 {@code npc: <id>} 或 {@code npc: [id1, id2]} 绑定 Citizens NPC。
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
        if (root.isList("npc")) {
            for (String id : root.getStringList("npc")) {
                try {
                    npcIds.add(Integer.parseInt(id.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (root.isInt("npc")) {
            npcIds.add(root.getInt("npc"));
        } else if (root.isString("npc")) {
            try {
                npcIds.add(Integer.parseInt(root.getString("npc").trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        String title = root.getString("title", "");

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
}
