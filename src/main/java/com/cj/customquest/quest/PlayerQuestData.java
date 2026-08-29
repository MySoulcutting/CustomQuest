package com.cj.customquest.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家任务数据（内存模型）。
 */
public final class PlayerQuestData {

    /** 已接取的任务：questId -> 进度 */
    private final Map<String, QuestProgress> accepted = new HashMap<>();
    /** 已完成的任务：questId -> 完成时间戳（毫秒） */
    private final Map<String, Long> completed = new HashMap<>();
    /**
     * 玩家在 NPC 上的对话数据（每个玩家独立，用于分支对话）：
     * npcId（字符串） -> (key -> value)
     */
    private final Map<String, Map<String, String>> npcData = new HashMap<>();

    public Map<String, QuestProgress> getAccepted() {
        return accepted;
    }

    public Map<String, Long> getCompleted() {
        return completed;
    }

    public Map<String, Map<String, String>> getNpcData() {
        return npcData;
    }

    /** 获取玩家在指定 NPC 上的数据表（不存在则创建空表） */
    public Map<String, String> npcDataOf(int npcId) {
        return npcData.computeIfAbsent(String.valueOf(npcId), k -> new HashMap<>());
    }

    public boolean isAccepted(String questId) {
        return accepted.containsKey(questId);
    }

    public boolean isCompleted(String questId) {
        return completed.containsKey(questId);
    }

    public long getCompletedAt(String questId) {
        return completed.getOrDefault(questId, 0L);
    }
}
