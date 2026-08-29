package com.cj.customquest.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestTrackingSnapshotTest {

    @Test
    void prioritizesNavigationThenAcceptanceTimeAndIdAndKeepsFive() {
        QuestTrackingSnapshot snapshot = QuestTrackingSnapshot.select(List.of(
                task("later", 20L, false),
                task("beta", 10L, false),
                task("alpha", 10L, false),
                task("third", 30L, false),
                task("fourth", 40L, false),
                task("fifth", 50L, false),
                task("navigating", 100L, true)
        ));

        assertEquals(7, snapshot.totalTaskCount());
        assertEquals(List.of("navigating", "alpha", "beta", "later", "third"),
                snapshot.tasks().stream().map(QuestTrackingSnapshot.Task::questId).toList());
    }

    @Test
    void normalizesProtocolTextAndKeepsUtf8Boundaries() {
        String longChinese = "任务".repeat(100);
        QuestTrackingSnapshot.Task task = new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.KILL, false,
                "&a标题\n下一行\u2028末尾", List.of(QuestTrackingSnapshot.Line.progress(
                        "§b" + longChinese + "\r目标\u2028末尾", 3, 10)));

        assertEquals("标题 下一行 末尾", task.title());
        assertTrue(task.lines().getFirst().text().getBytes(StandardCharsets.UTF_8).length
                <= QuestTrackingPayload.MAX_TEXT_BYTES);
        assertTrue(task.lines().getFirst().text().indexOf('§') < 0);
        assertTrue(task.lines().getFirst().text().codePoints().allMatch(codePoint -> !Character.isISOControl(codePoint)));
        assertTrue(task.lines().getFirst().text().indexOf('\u2028') < 0);
        assertTrue(task.title().chars().noneMatch(value -> Character.isSurrogate((char) value)));
    }

    @Test
    void defensivelyCopiesTasksAndLines() {
        List<QuestTrackingSnapshot.Line> lines = new ArrayList<>();
        lines.add(QuestTrackingSnapshot.Line.text("line"));
        QuestTrackingSnapshot.Task task = new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.DESCRIBE, false, "title", lines);
        List<QuestTrackingSnapshot.Task> tasks = new ArrayList<>();
        tasks.add(task);
        QuestTrackingSnapshot snapshot = new QuestTrackingSnapshot(1, tasks);

        lines.clear();
        tasks.clear();

        assertEquals(1, snapshot.tasks().size());
        assertEquals(1, snapshot.tasks().getFirst().lines().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.tasks().add(task("other", 2L, false)));
    }

    @Test
    void rejectsInvalidCountsAndProgress() {
        List<QuestTrackingSnapshot.Line> threeLines = List.of(
                QuestTrackingSnapshot.Line.text("1"),
                QuestTrackingSnapshot.Line.text("2"),
                QuestTrackingSnapshot.Line.text("3"));
        assertThrows(IllegalArgumentException.class, () -> new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.KILL, false, "title", threeLines));
        assertThrows(IllegalArgumentException.class,
                () -> QuestTrackingSnapshot.Line.progress("line", -1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> QuestTrackingSnapshot.Line.progress("line", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new QuestTrackingSnapshot(0, List.of(task("quest", 1L, false))));
        List<QuestTrackingSnapshot.Task> sixTasks = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> task("quest-" + index, index, false))
                .toList();
        assertThrows(IllegalArgumentException.class, () -> new QuestTrackingSnapshot(6, sixTasks));
    }

    @Test
    void rejectsUnpairedSurrogatesInsteadOfReplacingThem() {
        assertThrows(IllegalArgumentException.class, () -> new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.DESCRIBE, false,
                "broken-\uD800", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> QuestTrackingSnapshot.Line.text("broken-\uDC00"));
        assertThrows(IllegalArgumentException.class,
                () -> QuestTrackingText.encodeUtf8Strict("broken-\uD800"));
    }

    @Test
    void exposesFrozenTaskTypeIds() {
        assertEquals(0, QuestTrackingSnapshot.TaskType.KILL.protocolId());
        assertEquals(1, QuestTrackingSnapshot.TaskType.SUBMIT.protocolId());
        assertEquals(2, QuestTrackingSnapshot.TaskType.DESCRIBE.protocolId());
    }

    @Test
    void extractsTheEffectiveLeadingLegacyTitleColour() {
        assertEquals(0xFFFF55, QuestTrackingSnapshot.legacyTitleRgb("&6&l&e清剿荒野"));
        assertEquals(0x55FFFF, QuestTrackingSnapshot.legacyTitleRgb("  §b收集钻石"));
        assertEquals(0xFFFFFF,
                QuestTrackingSnapshot.legacyTitleRgb("&e&r普通任务"));
        assertEquals(QuestTrackingSnapshot.NO_TITLE_RGB,
                QuestTrackingSnapshot.legacyTitleRgb("普通任务"));
    }

    @Test
    void rejectsInvalidTaskTitleColours() {
        assertThrows(IllegalArgumentException.class, () -> new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.KILL, false,
                -2, "title", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuestTrackingSnapshot.Task(
                "quest", 1L, QuestTrackingSnapshot.TaskType.KILL, false,
                0x1000000, "title", List.of()));
    }

    private static QuestTrackingSnapshot.Task task(String id, long acceptedAt, boolean navigating) {
        return new QuestTrackingSnapshot.Task(
                id,
                acceptedAt,
                QuestTrackingSnapshot.TaskType.KILL,
                navigating,
                id,
                List.of(QuestTrackingSnapshot.Line.progress("target", 1, 10)));
    }
}
