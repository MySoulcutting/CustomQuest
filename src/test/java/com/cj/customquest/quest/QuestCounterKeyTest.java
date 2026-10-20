package com.cj.customquest.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QuestCounterKeyTest {

    @Test
    void stableKeySurvivesTargetReordering() {
        QuestObjective skeleton = QuestObjective.kill("SkeletonKing", 10, null);
        QuestObjective zombie = QuestObjective.kill("ZombieMinion", 5, null);

        String before = QuestManager.stableCounterKey(List.of(skeleton, zombie), 0);
        String after = QuestManager.stableCounterKey(List.of(zombie, skeleton), 1);

        assertEquals(before, after);
    }

    @Test
    void duplicateTargetsUseSeparateStableKeys() {
        QuestObjective first = QuestObjective.kill("SkeletonKing", 10, null);
        QuestObjective second = QuestObjective.kill("skeletonking", 20, null);
        List<QuestObjective> objectives = List.of(first, second);

        assertEquals("mob:12:skeletonking:1", QuestManager.stableCounterKey(objectives, 0));
        assertEquals("mob:12:skeletonking:2", QuestManager.stableCounterKey(objectives, 1));
    }

    @Test
    void legacyIndexCounterMigratesOnFirstRead() {
        QuestProgress progress = new QuestProgress(1L);
        progress.setCounter("obj1", 7);
        QuestObjective zombie = QuestObjective.kill("ZombieMinion", 5, null);
        QuestObjective skeleton = QuestObjective.kill("SkeletonKing", 10, null);
        List<QuestObjective> objectives = List.of(zombie, skeleton);

        String key = QuestManager.counterKey(progress, objectives, 1);

        assertEquals(7, progress.getCounter(key));
        assertFalse(progress.getCounters().containsKey("obj1"));
    }
}
