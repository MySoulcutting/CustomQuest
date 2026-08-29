package com.cj.customquest.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个已接取任务的进度。
 */
public final class QuestProgress {

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
}
