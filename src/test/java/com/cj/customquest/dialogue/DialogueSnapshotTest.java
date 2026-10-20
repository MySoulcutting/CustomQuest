package com.cj.customquest.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueSnapshotTest {

    private static final UUID SESSION = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    @Test
    void acceptsMaximumBoundedSnapshotAndRejectsFieldOverflow() {
        List<String> lines = java.util.stream.IntStream.range(0, DialogueSnapshot.MAX_LINES)
                .mapToObj(index -> "L".repeat(DialogueSnapshot.MAX_LINE_BYTES))
                .toList();
        List<DialogueSnapshot.Option> options = java.util.stream.IntStream
                .range(0, DialogueSnapshot.MAX_OPTIONS)
                .mapToObj(index -> new DialogueSnapshot.Option(
                        "i".repeat(DialogueSnapshot.MAX_OPTION_ID_BYTES - 1) + index,
                        "T".repeat(DialogueSnapshot.MAX_OPTION_TEXT_BYTES)))
                .toList();
        DialogueSnapshot.Snapshot maximum = new DialogueSnapshot.Snapshot(
                SESSION, "Q".repeat(DialogueSnapshot.MAX_TITLE_BYTES), lines, options);
        DialogueSnapshot.validateSnapshot(maximum);

        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "Q".repeat(DialogueSnapshot.MAX_TITLE_BYTES + 1),
                        List.of(),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialogueSnapshot.Option("accept", "")))));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of("L".repeat(DialogueSnapshot.MAX_LINE_BYTES + 1)),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialogueSnapshot.Option(
                                "i".repeat(DialogueSnapshot.MAX_OPTION_ID_BYTES + 1), "text")))));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialogueSnapshot.Option(
                                "accept", "T".repeat(DialogueSnapshot.MAX_OPTION_TEXT_BYTES + 1))))));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(SESSION, "bad\ntitle", List.of(), List.of())));
    }

    @Test
    void rejectsTooManyEntriesAndDuplicateOptionIds() {
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        java.util.stream.IntStream.rangeClosed(0, DialogueSnapshot.MAX_LINES)
                                .mapToObj(index -> "line").toList(),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        java.util.stream.IntStream.rangeClosed(0, DialogueSnapshot.MAX_OPTIONS)
                                .mapToObj(index -> new DialogueSnapshot.Option(
                                        "option-" + index, "text")).toList())));
        assertThrows(IllegalArgumentException.class, () -> DialogueSnapshot.validateSnapshot(
                new DialogueSnapshot.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(
                                new DialogueSnapshot.Option("same", "one"),
                                new DialogueSnapshot.Option("same", "two")))));
    }

    @Test
    void commandOptionIdsUseUrlSafeStrictRoundTrip() {
        String optionId = "接受 任务/一";
        String encoded = DialogueSnapshot.encodeCommandOptionId(optionId);

        assertTrue(encoded.matches("[A-Za-z0-9_-]+"));
        assertEquals(optionId, DialogueSnapshot.decodeCommandOptionId(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> DialogueSnapshot.decodeCommandOptionId("%%%"));
    }

    @Test
    void sanitizesDisplayControlsWithoutRemovingLegacyFormatting() {
        assertEquals("§6任务 继续", DialogueSnapshot.sanitizeDisplayText(
                "§6任务\n继续", DialogueSnapshot.MAX_TITLE_BYTES));
        assertEquals("任", DialogueSnapshot.sanitizeDisplayText("任务", 3));
    }

    @Test
    void truncationNeverLeavesADanglingFormattingCode() {
        // § 必须与它的码字符成对：按字节截断正好落在两者之间时，宁可丢掉这个孤立的 §，
        // 否则页面会把它后面整段文字都当成码的一部分而掉色。
        assertEquals("", DialogueSnapshot.sanitizeDisplayText("§6任务", 2));
        assertEquals("§6", DialogueSnapshot.sanitizeDisplayText("§6任务", 3));
        assertEquals("§6任", DialogueSnapshot.sanitizeDisplayText("§6任务", 6));
    }
}
