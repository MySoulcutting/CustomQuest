package com.cj.customquest.quest;

/**
 * 任务类型。
 */
public enum QuestType {

    /** 击杀 MythicMobs 怪物（支持多目标） */
    KILL_MOB("kill_mob", "击杀怪物"),
    /** 提交物品（支持多种物品） */
    SUBMIT_ITEM("submit_item", "提交物品"),
    /** 描述任务（无目标，仅展示，只能通过指令强制完成） */
    DESCRIBE("describe", "描述任务");

    private final String key;
    private final String display;

    QuestType(String key, String display) {
        this.key = key;
        this.display = display;
    }

    public String getKey() {
        return key;
    }

    public String getDisplay() {
        return display;
    }

    public static QuestType parse(String text) {
        if (text == null) return null;
        for (QuestType type : values()) {
            if (type.key.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }
}
