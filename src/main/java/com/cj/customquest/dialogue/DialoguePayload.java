package com.cj.customquest.dialogue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SoulCore Fabric 客户端任务对话通道。 */
public final class DialoguePayload {

    public static final String CHANNEL = "soulcore:quest_dialogue";
    public static final String REQUEST_CHANNEL = "soulcore:quest_dialogue_request";

    static final int VERSION = 1;
    static final int ACTION_CLOSE = 0;
    static final int ACTION_OPEN = 1;
    static final int REQUEST_ACTION_DISMISS = 0;
    static final int REQUEST_ACTION_SELECT = 1;

    static final int MAX_PAYLOAD_BYTES = 8192;
    static final int MAX_REQUEST_BYTES = 86;
    static final int MAX_TITLE_BYTES = 256;
    static final int MAX_LINES = 8;
    static final int MAX_LINE_BYTES = 512;
    static final int MAX_OPTIONS = 6;
    static final int MAX_OPTION_ID_BYTES = 64;
    static final int MAX_OPTION_TEXT_BYTES = 256;
    /** 保留旧版封包中的空悬浮字段，避免旧客户端因字段错位无法解析。 */
    static final int MAX_LEGACY_TOOLTIP_BYTES = 256;
    private static final int HEADER_BYTES = 18;

    private DialoguePayload() {
    }

    public static void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(
                BukkitPlugin.getInstance(), REQUEST_CHANNEL, DialoguePayload::handleRequest);
    }

    public static void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(BukkitPlugin.getInstance(), REQUEST_CHANNEL);
    }

    public static boolean canSend(Player player) {
        return player != null && player.getListeningPluginChannels().contains(CHANNEL);
    }

    public static boolean sendOpen(Player player, Snapshot snapshot) {
        if (!canSend(player) || snapshot == null) {
            return false;
        }
        try {
            player.sendPluginMessage(BukkitPlugin.getInstance(), CHANNEL, encodeOpen(snapshot));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static void sendClose(Player player, UUID sessionId) {
        if (!canSend(player) || sessionId == null) {
            return;
        }
        try {
            player.sendPluginMessage(BukkitPlugin.getInstance(), CHANNEL, encodeClose(sessionId));
        } catch (IllegalArgumentException ignored) {
        }
    }

    static byte[] encodeClose(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES);
            DataOutputStream output = new DataOutputStream(bytes);
            writeHeader(output, ACTION_CLOSE, sessionId);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode dialogue close payload", exception);
        }
    }

    static byte[] encodeOpen(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateSnapshot(snapshot);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeHeader(output, ACTION_OPEN, snapshot.sessionId());
            writeText(output, snapshot.title(), MAX_TITLE_BYTES, true);
            output.writeByte(snapshot.lines().size());
            for (String line : snapshot.lines()) {
                writeText(output, line, MAX_LINE_BYTES, true);
            }
            output.writeByte(snapshot.options().size());
            for (Option option : snapshot.options()) {
                writeText(output, option.id(), MAX_OPTION_ID_BYTES, false);
                writeText(output, option.text(), MAX_OPTION_TEXT_BYTES, false);
                writeText(output, "", MAX_LEGACY_TOOLTIP_BYTES, true);
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Dialogue payload is too large");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode dialogue payload", exception);
        }
    }

    static Request decodeRequest(byte[] payload) {
        if (payload == null || payload.length < HEADER_BYTES || payload.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Invalid dialogue request length");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != VERSION) {
                throw new IllegalArgumentException("Unsupported dialogue request version");
            }
            int action = input.readUnsignedByte();
            if (action != REQUEST_ACTION_DISMISS && action != REQUEST_ACTION_SELECT) {
                throw new IllegalArgumentException("Unsupported dialogue request action");
            }
            UUID sessionId = new UUID(input.readLong(), input.readLong());
            String optionId = null;
            if (action == REQUEST_ACTION_SELECT) {
                optionId = readText(input, MAX_OPTION_ID_BYTES, false);
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing dialogue request bytes");
            }
            return new Request(action, sessionId, optionId);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decode dialogue request", exception);
        }
    }

    static String encodeCommandOptionId(String optionId) {
        byte[] encoded = strictUtf8(optionId);
        validateText(encoded, optionId, MAX_OPTION_ID_BYTES, false);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    }

    public static String decodeCommandOptionId(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_OPTION_ID_BYTES) {
                throw new IllegalArgumentException("Invalid dialogue option ID length");
            }
            String optionId = decodeUtf8Strict(bytes);
            validateText(bytes, optionId, MAX_OPTION_ID_BYTES, false);
            return optionId;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid encoded dialogue option ID", exception);
        }
    }

    static boolean isValidOptionId(String optionId) {
        try {
            byte[] bytes = strictUtf8(optionId);
            validateText(bytes, optionId, MAX_OPTION_ID_BYTES, false);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 保留合法样式码，把显示文本中的换行和控制字符替换为空格，并按 UTF-8 截断。 */
    static String sanitizeDisplayText(String value, int maxBytes) {
        String source = value == null ? "" : value;
        StringBuilder sanitized = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length(); ) {
            int codePoint = source.codePointAt(offset);
            boolean separator = Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
            sanitized.appendCodePoint(Character.isISOControl(codePoint) || separator ? ' ' : codePoint);
            offset += Character.charCount(codePoint);
        }
        String normalized = sanitized.toString();
        byte[] encoded = strictUtf8(normalized);
        if (encoded.length <= maxBytes) {
            return normalized;
        }
        StringBuilder limited = new StringBuilder(normalized.length());
        int bytes = 0;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int length = strictUtf8(character).length;
            if (bytes + length > maxBytes) {
                break;
            }
            limited.append(character);
            bytes += length;
            offset += Character.charCount(codePoint);
        }
        return limited.toString();
    }

    private static void handleRequest(String channel, Player player, byte[] payload) {
        if (!REQUEST_CHANNEL.equals(channel) || player == null) {
            return;
        }
        final Request request;
        try {
            request = decodeRequest(payload);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        Runnable action = () -> {
            DialogueManager manager = DialogueManager.getInstance();
            if (!player.isOnline() || manager == null) {
                return;
            }
            if (request.action() == REQUEST_ACTION_DISMISS) {
                manager.onDismiss(player, request.sessionId());
            } else {
                manager.onOptionClick(player, request.sessionId(), request.optionId());
            }
        };
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), action);
        }
    }

    private static void validateSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot.sessionId(), "sessionId");
        if (snapshot.lines().size() > MAX_LINES || snapshot.options().size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Dialogue snapshot contains too many entries");
        }
        validateText(strictUtf8(snapshot.title()), snapshot.title(), MAX_TITLE_BYTES, true);
        for (String line : snapshot.lines()) {
            validateText(strictUtf8(line), line, MAX_LINE_BYTES, true);
        }
        HashSet<String> optionIds = new HashSet<>();
        for (Option option : snapshot.options()) {
            validateText(strictUtf8(option.id()), option.id(), MAX_OPTION_ID_BYTES, false);
            if (!optionIds.add(option.id())) {
                throw new IllegalArgumentException("Dialogue snapshot contains duplicate option IDs");
            }
            validateText(strictUtf8(option.text()), option.text(), MAX_OPTION_TEXT_BYTES, false);
        }
    }

    private static void writeHeader(DataOutputStream output, int action, UUID sessionId) throws IOException {
        output.writeByte(VERSION);
        output.writeByte(action);
        output.writeLong(sessionId.getMostSignificantBits());
        output.writeLong(sessionId.getLeastSignificantBits());
    }

    private static void writeText(DataOutputStream output, String value, int maxBytes,
                                  boolean allowEmpty) throws IOException {
        byte[] encoded = strictUtf8(value);
        validateText(encoded, value, maxBytes, allowEmpty);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readText(DataInputStream input, int maxBytes, boolean allowEmpty) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maxBytes || (!allowEmpty && length == 0)) {
            throw new IllegalArgumentException("Invalid dialogue text length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated dialogue text");
        }
        String value = decodeUtf8Strict(bytes);
        validateText(bytes, value, maxBytes, allowEmpty);
        return value;
    }

    private static void validateText(byte[] encoded, String value, int maxBytes, boolean allowEmpty) {
        if (encoded.length > maxBytes || (!allowEmpty && encoded.length == 0)) {
            throw new IllegalArgumentException("Invalid dialogue text length");
        }
        if (value.codePoints().anyMatch(DialoguePayload::isForbiddenCodePoint)) {
            throw new IllegalArgumentException("Dialogue text contains control characters");
        }
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value == null ? "" : value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Dialogue text is not valid Unicode", exception);
        }
    }

    private static String decodeUtf8Strict(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Dialogue text is not valid UTF-8", exception);
        }
    }

    public record Snapshot(UUID sessionId, String title, List<String> lines, List<Option> options) {
        public Snapshot {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            options = List.copyOf(Objects.requireNonNull(options, "options"));
        }
    }

    public record Option(String id, String text) {
    }

    record Request(int action, UUID sessionId, String optionId) {
    }
}
