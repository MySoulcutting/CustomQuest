package com.cj.customquest.dialogue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialoguePayloadTest {

    private static final UUID SESSION = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    @Test
    void encodesCrossProjectGoldenOpenPayload() {
        DialoguePayload.Snapshot snapshot = new DialoguePayload.Snapshot(
                SESSION,
                "老杰克",
                List.of("你好"),
                List.of(new DialoguePayload.Option("accept", "接受"))
        );

        assertEquals(
                "010100112233445566778899aabbccddeeff"
                        + "00000009e88081e69db0e5858b"
                        + "0100000006e4bda0e5a5bd"
                        + "0100000006616363657074"
                        + "00000006e68ea5e58f97"
                        + "00000000",
                HexFormat.of().formatHex(DialoguePayload.encodeOpen(snapshot))
        );
    }

    @Test
    void encodesCloseWithVersionActionAndUuidOnly() {
        assertEquals(
                "010000112233445566778899aabbccddeeff",
                HexFormat.of().formatHex(DialoguePayload.encodeClose(SESSION))
        );
    }

    @Test
    void decodesStrictDismissAndSelectRequests() throws Exception {
        byte[] dismissPayload = request(DialoguePayload.REQUEST_ACTION_DISMISS, null);
        assertEquals("010000112233445566778899aabbccddeeff",
                HexFormat.of().formatHex(dismissPayload));
        DialoguePayload.Request dismiss = DialoguePayload.decodeRequest(dismissPayload);
        assertEquals(DialoguePayload.REQUEST_ACTION_DISMISS, dismiss.action());
        assertEquals(SESSION, dismiss.sessionId());
        assertEquals(null, dismiss.optionId());

        byte[] selectPayload = request(DialoguePayload.REQUEST_ACTION_SELECT, "accept");
        assertEquals("010100112233445566778899aabbccddeeff00000006616363657074",
                HexFormat.of().formatHex(selectPayload));
        DialoguePayload.Request select = DialoguePayload.decodeRequest(selectPayload);
        assertEquals(DialoguePayload.REQUEST_ACTION_SELECT, select.action());
        assertEquals(SESSION, select.sessionId());
        assertEquals("accept", select.optionId());
    }

    @Test
    void rejectsMalformedRequestsAndTrailingBytes() throws Exception {
        byte[] select = request(DialoguePayload.REQUEST_ACTION_SELECT, "accept");
        byte[] trailing = Arrays.copyOf(select, select.length + 1);
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.decodeRequest(trailing));
        byte[] dismissTrailing = Arrays.copyOf(
                request(DialoguePayload.REQUEST_ACTION_DISMISS, null), 19);
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.decodeRequest(dismissTrailing));
        assertThrows(IllegalArgumentException.class,
                () -> DialoguePayload.decodeRequest(Arrays.copyOf(select, select.length - 1)));

        byte[] wrongVersion = select.clone();
        wrongVersion[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.decodeRequest(wrongVersion));

        byte[] wrongAction = select.clone();
        wrongAction[1] = 2;
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.decodeRequest(wrongAction));

        assertThrows(IllegalArgumentException.class,
                () -> DialoguePayload.decodeRequest(request(DialoguePayload.REQUEST_ACTION_SELECT, "")));
        assertThrows(IllegalArgumentException.class,
                () -> DialoguePayload.decodeRequest(request(DialoguePayload.REQUEST_ACTION_SELECT, "bad\noption")));
        assertThrows(IllegalArgumentException.class,
                () -> DialoguePayload.decodeRequest(request(DialoguePayload.REQUEST_ACTION_SELECT, "x".repeat(65))));

        byte[] malformedUtf8 = request(DialoguePayload.REQUEST_ACTION_SELECT, "x");
        malformedUtf8[malformedUtf8.length - 1] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.decodeRequest(malformedUtf8));
    }

    @Test
    void acceptsMaximumBoundedSnapshotAndRejectsFieldOverflow() {
        List<String> lines = java.util.stream.IntStream.range(0, DialoguePayload.MAX_LINES)
                .mapToObj(index -> "L".repeat(DialoguePayload.MAX_LINE_BYTES))
                .toList();
        List<DialoguePayload.Option> options = java.util.stream.IntStream
                .range(0, DialoguePayload.MAX_OPTIONS)
                .mapToObj(index -> new DialoguePayload.Option(
                        "i".repeat(DialoguePayload.MAX_OPTION_ID_BYTES - 1) + index,
                        "T".repeat(DialoguePayload.MAX_OPTION_TEXT_BYTES)))
                .toList();
        DialoguePayload.Snapshot maximum = new DialoguePayload.Snapshot(
                SESSION, "Q".repeat(DialoguePayload.MAX_TITLE_BYTES), lines, options);

        byte[] encoded = DialoguePayload.encodeOpen(maximum);
        assertTrue(encoded.length <= DialoguePayload.MAX_PAYLOAD_BYTES);

        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "Q".repeat(DialoguePayload.MAX_TITLE_BYTES + 1),
                        List.of(),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialoguePayload.Option("accept", "")))));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of("L".repeat(DialoguePayload.MAX_LINE_BYTES + 1)),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialoguePayload.Option(
                                "i".repeat(DialoguePayload.MAX_OPTION_ID_BYTES + 1), "text")))));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(new DialoguePayload.Option(
                                "accept", "T".repeat(DialoguePayload.MAX_OPTION_TEXT_BYTES + 1))))));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "bad\ntitle",
                        List.of(),
                        List.of())));
    }

    @Test
    void rejectsTooManyEntriesAndDuplicateOptionIds() {
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        java.util.stream.IntStream.rangeClosed(0, DialoguePayload.MAX_LINES)
                                .mapToObj(index -> "line").toList(),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        java.util.stream.IntStream.rangeClosed(0, DialoguePayload.MAX_OPTIONS)
                                .mapToObj(index -> new DialoguePayload.Option(
                                        "option-" + index, "text")).toList())));
        assertThrows(IllegalArgumentException.class, () -> DialoguePayload.encodeOpen(
                new DialoguePayload.Snapshot(
                        SESSION,
                        "title",
                        List.of(),
                        List.of(
                                new DialoguePayload.Option("same", "one"),
                                new DialoguePayload.Option("same", "two")))));
    }

    @Test
    void commandOptionIdsUseUrlSafeStrictRoundTrip() {
        String optionId = "接受 任务/一";
        String encoded = DialoguePayload.encodeCommandOptionId(optionId);

        assertTrue(encoded.matches("[A-Za-z0-9_-]+"));
        assertEquals(optionId, DialoguePayload.decodeCommandOptionId(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> DialoguePayload.decodeCommandOptionId("%%%"));
    }

    @Test
    void sanitizesDisplayControlsWithoutRemovingLegacyFormatting() {
        assertEquals("§6任务 继续", DialoguePayload.sanitizeDisplayText(
                "§6任务\n继续", DialoguePayload.MAX_TITLE_BYTES));
        assertEquals("任", DialoguePayload.sanitizeDisplayText("任务", 3));
    }

    private static byte[] request(int action, String optionId) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(DialoguePayload.VERSION);
            output.writeByte(action);
            output.writeLong(SESSION.getMostSignificantBits());
            output.writeLong(SESSION.getLeastSignificantBits());
            if (action == DialoguePayload.REQUEST_ACTION_SELECT) {
                byte[] encoded = optionId.getBytes(StandardCharsets.UTF_8);
                output.writeInt(encoded.length);
                output.write(encoded);
            }
        }
        return bytes.toByteArray();
    }
}
