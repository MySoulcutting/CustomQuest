package com.cj.customquest.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 当前玩家任务追踪的不可变全量快照。 */
public record QuestTrackingSnapshot(int totalTaskCount, List<Task> tasks) {

    public static final int MAX_TASKS = 5;
    public static final int MAX_LINES_PER_TASK = 2;
    public static final int NO_TITLE_RGB = -1;

    private static final Comparator<Task> DISPLAY_ORDER = Comparator
            .comparing(Task::navigating).reversed()
            .thenComparingLong(Task::acceptedAt)
            .thenComparing(Task::questId, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Task::questId);

    public QuestTrackingSnapshot {
        if (totalTaskCount < 0) {
            throw new IllegalArgumentException("Total task count cannot be negative");
        }
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        if (tasks.size() > MAX_TASKS || totalTaskCount < tasks.size()) {
            throw new IllegalArgumentException("Invalid tracked task count");
        }
    }

    public static QuestTrackingSnapshot select(List<Task> candidates) {
        List<Task> ordered = new ArrayList<>(List.copyOf(Objects.requireNonNull(candidates, "candidates")));
        ordered.sort(DISPLAY_ORDER);
        int total = ordered.size();
        if (ordered.size() > MAX_TASKS) {
            ordered = new ArrayList<>(ordered.subList(0, MAX_TASKS));
        }
        return new QuestTrackingSnapshot(total, ordered);
    }

    public static QuestTrackingSnapshot empty() {
        return new QuestTrackingSnapshot(0, List.of());
    }

    public static int legacyTitleRgb(String formattedTitle) {
        return QuestTrackingText.leadingLegacyColorRgb(formattedTitle);
    }

    public enum TaskType {
        KILL(0),
        SUBMIT(1),
        DESCRIBE(2);

        private final int protocolId;

        TaskType(int protocolId) {
            this.protocolId = protocolId;
        }

        public int protocolId() {
            return protocolId;
        }
    }

    public record Task(
            String questId,
            long acceptedAt,
            TaskType type,
            boolean navigating,
            boolean navigatable,
            int titleRgb,
            String title,
            List<Line> lines
    ) {
        public Task(String questId, long acceptedAt, TaskType type, boolean navigating,
                    String title, List<Line> lines) {
            this(questId, acceptedAt, type, navigating, false, NO_TITLE_RGB, title, lines);
        }

        public Task(String questId, long acceptedAt, TaskType type, boolean navigating,
                    int titleRgb, String title, List<Line> lines) {
            this(questId, acceptedAt, type, navigating, false, titleRgb, title, lines);
        }

        public Task {
            questId = Objects.requireNonNull(questId, "questId");
            type = Objects.requireNonNull(type, "type");
            if (titleRgb < NO_TITLE_RGB || titleRgb > 0xFFFFFF) {
                throw new IllegalArgumentException("Task title RGB must be absent or a 24-bit colour");
            }
            title = QuestTrackingText.plainSingleLine(title);
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (lines.size() > MAX_LINES_PER_TASK) {
                throw new IllegalArgumentException("A tracked task can contain at most two lines");
            }
        }
    }

    public record Line(String text, boolean hasProgress, int current, int total) {
        public Line {
            text = QuestTrackingText.legacySingleLine(text);
            if (hasProgress && (current < 0 || total <= 0)) {
                throw new IllegalArgumentException("Task progress requires a non-negative current and positive total");
            }
            if (!hasProgress) {
                current = 0;
                total = 0;
            }
        }

        public static Line progress(String text, int current, int total) {
            return new Line(text, true, current, total);
        }

        public static Line text(String text) {
            return new Line(text, false, 0, 0);
        }
    }
}
