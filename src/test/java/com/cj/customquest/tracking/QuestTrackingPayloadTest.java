package com.cj.customquest.tracking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestTrackingPayloadTest {

    @Test
    void prefersV2ThenV1AndOtherwiseDisablesHudTransport() {
        assertEquals(3, QuestTrackingPayload.preferredVersion(
                Set.of(QuestTrackingPayload.CHANNEL, QuestTrackingPayload.CHANNEL_V2,
                        QuestTrackingPayload.CHANNEL_V3)));
        assertEquals(2, QuestTrackingPayload.preferredVersion(Set.of(QuestTrackingPayload.CHANNEL_V2)));
        assertEquals(1, QuestTrackingPayload.preferredVersion(Set.of(QuestTrackingPayload.CHANNEL)));
        assertEquals(0, QuestTrackingPayload.preferredVersion(Set.of()));
    }

    @Test
    void encodesClearPayload() {
        assertArrayEquals(new byte[]{1, 0}, QuestTrackingPayload.encodeClear());
        assertArrayEquals(new byte[]{2, 0}, QuestTrackingPayload.encodeClearV2());
        assertArrayEquals(new byte[]{3, 0}, QuestTrackingPayload.encodeClearV3());
    }

    @Test
    void encodesCrossProjectGoldenSnapshot() {
        QuestTrackingSnapshot snapshot = new QuestTrackingSnapshot(1, List.of(
                new QuestTrackingSnapshot.Task(
                        "quest-id",
                        1L,
                        QuestTrackingSnapshot.TaskType.KILL,
                        true,
                        "清剿",
                        List.of(QuestTrackingSnapshot.Line.progress("骷髅王", 1, 3)))
        ));

        assertEquals(
                "01010000000100000001000100000006e6b885e589bf000000010100000009e9aab7e9ab85e78e8b0000000100000003",
                HexFormat.of().formatHex(QuestTrackingPayload.encodeSnapshot(snapshot)));
    }

    @Test
    void encodesV2TitleColourWithoutChangingTheV1Golden() {
        QuestTrackingSnapshot snapshot = new QuestTrackingSnapshot(1, List.of(
                new QuestTrackingSnapshot.Task(
                        "quest-id",
                        1L,
                        QuestTrackingSnapshot.TaskType.KILL,
                        true,
                        0xFFFF55,
                        "清剿",
                        List.of(QuestTrackingSnapshot.Line.progress("骷髅王", 1, 3)))
        ));

        assertEquals(
                "02010000000100000001000100ffff550000000871756573742d696400000006e6b885e589bf000000010100000009e9aab7e9ab85e78e8b0000000100000003",
                HexFormat.of().formatHex(QuestTrackingPayload.encodeSnapshotV2(snapshot)));
    }

    @Test
    void v3MarksTasksWithNavigationTargetsWithoutChangingV1OrV2Flags() throws Exception {
        QuestTrackingSnapshot.Task task = new QuestTrackingSnapshot.Task(
                "quest-id", 1L, QuestTrackingSnapshot.TaskType.KILL,
                true, true, 0xFFFF55, "quest", List.of());
        QuestTrackingSnapshot snapshot = new QuestTrackingSnapshot(1, List.of(task));

        DataInputStream v1 = new DataInputStream(new ByteArrayInputStream(
                QuestTrackingPayload.encodeSnapshot(snapshot)));
        DataInputStream v2 = new DataInputStream(new ByteArrayInputStream(
                QuestTrackingPayload.encodeSnapshotV2(snapshot)));
        DataInputStream v3 = new DataInputStream(new ByteArrayInputStream(
                QuestTrackingPayload.encodeSnapshotV3(snapshot)));
        v1.skipNBytes(11);
        v2.skipNBytes(11);
        v3.skipNBytes(11);

        assertEquals(1, v1.readUnsignedByte());
        assertEquals(1, v2.readUnsignedByte());
        assertEquals(3, v3.readUnsignedByte());
    }

    @Test
    void encodesEmptyAndMaximumSnapshotsWithinPacketLimit() {
        byte[] empty = QuestTrackingPayload.encodeSnapshot(QuestTrackingSnapshot.empty());
        assertEquals("01010000000000000000", HexFormat.of().formatHex(empty));

        String title = "标".repeat(85);
        String line = "目".repeat(85);
        List<QuestTrackingSnapshot.Task> tasks = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new QuestTrackingSnapshot.Task(
                        "quest-" + index,
                        index,
                        QuestTrackingSnapshot.TaskType.SUBMIT,
                        index == 0,
                        title,
                        List.of(
                                QuestTrackingSnapshot.Line.progress(line, index, 10),
                                QuestTrackingSnapshot.Line.text(line))))
                .toList();

        byte[] payload = QuestTrackingPayload.encodeSnapshot(new QuestTrackingSnapshot(8, tasks));
        byte[] payloadV2 = QuestTrackingPayload.encodeSnapshotV2(new QuestTrackingSnapshot(8, tasks));
        assertTrue(payload.length <= QuestTrackingPayload.MAX_PAYLOAD_BYTES);
        assertTrue(payloadV2.length <= QuestTrackingPayload.MAX_PAYLOAD_BYTES);
        assertEquals(1, payload[0]);
        assertEquals(1, payload[1]);
        assertEquals(2, payloadV2[0]);
        assertEquals(1, payloadV2[1]);
    }
}
