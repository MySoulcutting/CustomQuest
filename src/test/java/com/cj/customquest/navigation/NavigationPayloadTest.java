package com.cj.customquest.navigation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationPayloadTest {

    @Test
    void startPayloadUsesVersionedUtf8AndFiniteCoordinates() throws Exception {
        byte[] payload = NavigationPayload.encodeStart("清剿荒野", 12.5, 64.0, -3.25);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));

        assertEquals(NavigationPayload.VERSION, input.readUnsignedByte());
        assertEquals(NavigationPayload.ACTION_START, input.readUnsignedByte());
        byte[] expectedName = "清剿荒野".getBytes(StandardCharsets.UTF_8);
        int nameLength = input.readInt();
        byte[] actualName = input.readNBytes(nameLength);
        assertArrayEquals(expectedName, actualName);
        assertEquals(12.5, input.readDouble());
        assertEquals(64.0, input.readDouble());
        assertEquals(-3.25, input.readDouble());
        assertEquals(0, input.available());
    }

    @Test
    void stopPayloadContainsOnlyVersionAndAction() {
        assertArrayEquals(new byte[]{NavigationPayload.VERSION, NavigationPayload.ACTION_STOP},
                NavigationPayload.encodeStop());
    }

    @Test
    void matchesCrossProjectGoldenPayload() {
        assertEquals("01010000000551756573743ff80000000000004050000000000000c002000000000000",
                HexFormat.of().formatHex(NavigationPayload.encodeStart("Quest", 1.5, 64.0, -2.25)));
    }

    @Test
    void invalidCoordinatesAndOversizedNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.encodeStart("quest", Double.NaN, 64.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.encodeStart("quest", 30_000_001.0, 64.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.encodeStart("任".repeat(200), 0.0, 64.0, 0.0));
    }

    @Test
    void decodesStrictToggleRequests() throws Exception {
        assertEquals("example_kill", NavigationPayload.decodeToggleRequest(toggleRequest("example_kill")));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.decodeToggleRequest(new byte[]{2, 0, 0, 0, 0, 1, 'x'}));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.decodeToggleRequest(new byte[]{1, 1, 0, 0, 0, 1, 'x'}));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.decodeToggleRequest(new byte[]{1, 0, 0, 0, 0, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.decodeToggleRequest(new byte[]{1, 0, 0, 0, 0, 2, 'x'}));

        byte[] valid = toggleRequest("quest");
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> NavigationPayload.decodeToggleRequest(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> NavigationPayload.decodeToggleRequest(toggleRequest("bad\nquest")));
    }

    @Test
    void rateLimitsRequestsPerPlayer() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long start = 1_000_000_000L;

        assertEquals(true, NavigationPayload.acquireRequest(first, start));
        assertEquals(false, NavigationPayload.acquireRequest(
                first, start + NavigationPayload.REQUEST_COOLDOWN_NANOS - 1));
        assertEquals(true, NavigationPayload.acquireRequest(
                first, start + NavigationPayload.REQUEST_COOLDOWN_NANOS));
        assertEquals(true, NavigationPayload.acquireRequest(second, start));
    }

    private static byte[] toggleRequest(String questId) throws Exception {
        byte[] encoded = questId.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(NavigationPayload.REQUEST_VERSION);
            output.writeByte(NavigationPayload.REQUEST_ACTION_TOGGLE);
            output.writeInt(encoded.length);
            output.write(encoded);
        }
        return bytes.toByteArray();
    }
}
