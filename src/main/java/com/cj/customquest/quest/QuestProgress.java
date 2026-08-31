package com.cj.customquest.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个已接取任务的进度。
 */
public final class QuestProgress {

    private static final String CONDITION_MET_KEY = "state:condition-met";

    private final long acceptedAt;
    /** 计数器：击杀任务使用稳定目标键；旧版 objN 会在读取时自动迁移。 */
    private final Map<String, Integer> counters = new HashMap<>();

    public QuestProgress(long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public long getAcceptedAt() {
        return acceptedAt;
    }

    public int getCounter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public void increment(String key, int amount) {
        counters.put(key, getCounter(key) + amount);
    }

    public void setCounter(String key, int value) {
        counters.put(key, value);
    }

    public Map<String, Integer> getCounters() {
        return counters;
    }

    /** 本次接取期间是否曾经满足全部任务条件。 */
    public boolean isConditionMet() {
        return getCounter(CONDITION_MET_KEY) > 0;
    }

    /**
     * 首次记录条件达成。达成标记在本次接取期间保持不变，避免重复执行条件指令。
     *
     * @return 仅在本次从“未达成”变为“已达成”时返回 true
     */
    public boolean updateConditionMet(boolean conditionMet) {
        if (!conditionMet || isConditionMet()) {
            return false;
        }
        counters.put(CONDITION_MET_KEY, 1);
        return true;
    }
}
