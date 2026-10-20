package com.cj.customquest.dialogue;

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

/** NPC 对话展示层共用的可信快照与文本边界。 */
public final class DialogueSnapshot {

    static final int MAX_TITLE_BYTES = 256;
    static final int MAX_LINES = 8;
    static final int MAX_LINE_BYTES = 512;
    static final int MAX_OPTIONS = 6;
    static final int MAX_OPTION_ID_BYTES = 64;
    static final int MAX_OPTION_TEXT_BYTES = 256;

    private DialogueSnapshot() {
    }

    static void validateSnapshot(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
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

    static boolean isValidOptionId(String optionId) {
        try {
            byte[] bytes = strictUtf8(optionId);
            validateText(bytes, optionId, MAX_OPTION_ID_BYTES, false);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 聊天命令只携带 URL-safe Base64，避免选项 ID 破坏命令分词。 */
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
        // 截断点可能正好落在 "§" 与它的码字符之间：留下落单的 "§" 会让它后面整段掉色，这里去掉
        if (limited.length() > 0 && limited.charAt(limited.length() - 1) == '§') {
            limited.setLength(limited.length() - 1);
        }
        return limited.toString();
    }

    private static void validateText(byte[] encoded, String value, int maxBytes, boolean allowEmpty) {
        if (encoded.length > maxBytes || (!allowEmpty && encoded.length == 0)) {
            throw new IllegalArgumentException("Invalid dialogue text length");
        }
        if (value.codePoints().anyMatch(DialogueSnapshot::isForbiddenCodePoint)) {
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
}
