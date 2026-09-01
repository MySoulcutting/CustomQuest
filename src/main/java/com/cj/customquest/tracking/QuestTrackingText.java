package com.cj.customquest.tracking;

import org.bukkit.ChatColor;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** 任务追踪协议的纯文本规范化。 */
final class QuestTrackingText {

    private QuestTrackingText() {
    }

    static String plainSingleLine(String value) {
        String source = value == null ? "" : value;
        encodeUtf8Strict(source);
        String colored = ChatColor.translateAlternateColorCodes('&', source);
        String stripped = ChatColor.stripColor(colored);
        return sanitizeSingleLine(stripped);
    }

    static String legacySingleLine(String value) {
        String source = value == null ? "" : value;
        encodeUtf8Strict(source);
        String colored = ChatColor.translateAlternateColorCodes('&', source);
        String limited = sanitizeSingleLine(colored);
        return limited.endsWith(String.valueOf(ChatColor.COLOR_CHAR))
                ? limited.substring(0, limited.length() - 1) : limited;
    }

    private static String sanitizeSingleLine(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder singleLine = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            boolean lineSeparator = Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
            singleLine.appendCodePoint(Character.isISOControl(codePoint) || lineSeparator ? ' ' : codePoint);
            offset += Character.charCount(codePoint);
        }
        return truncateUtf8(singleLine.toString().trim(), QuestTrackingPayload.MAX_TEXT_BYTES);
    }

    static int leadingLegacyColorRgb(String value) {
        String source = value == null ? "" : value;
        int rgb = QuestTrackingSnapshot.NO_TITLE_RGB;
        for (int offset = 0; offset < source.length(); ) {
            char current = source.charAt(offset);
            if ((current == '&' || current == '§') && offset + 1 < source.length()) {
                char code = Character.toLowerCase(source.charAt(offset + 1));
                int colour = legacyColorRgb(code);
                if (colour >= 0) {
                    rgb = colour;
                    offset += 2;
                    continue;
                }
                if (code == 'r') {
                    rgb = 0xFFFFFF;
                    offset += 2;
                    continue;
                }
                if (code >= 'k' && code <= 'o') {
                    offset += 2;
                    continue;
                }
            }
            if (Character.isWhitespace(current)) {
                offset++;
                continue;
            }
            break;
        }
        return rgb;
    }

    private static int legacyColorRgb(char code) {
        return switch (code) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> -1;
        };
    }

    static String truncateUtf8(String value, int maxBytes) {
        if (value == null || value.isEmpty() || maxBytes <= 0) {
            return "";
        }
        if (encodeUtf8Strict(value).length <= maxBytes) {
            return value;
        }
        StringBuilder limited = new StringBuilder(value.length());
        int bytes = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int encodedLength = encodeUtf8Strict(character).length;
            if (bytes + encodedLength > maxBytes) {
                break;
            }
            limited.append(character);
            bytes += encodedLength;
            offset += Character.charCount(codePoint);
        }
        return limited.toString();
    }

    static byte[] encodeUtf8Strict(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value == null ? "" : value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Task tracking text is not valid Unicode", exception);
        }
    }
}
