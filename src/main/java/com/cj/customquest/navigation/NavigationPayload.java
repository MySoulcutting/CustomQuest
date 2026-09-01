package com.cj.customquest.navigation;

import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** SoulCore NeoForge 客户端导航通道。 */
public final class NavigationPayload {

    public static final String CHANNEL = "soulcore:quest_navigation";
    public static final String REQUEST_CHANNEL = "soulcore:quest_navigation_request";
    static final int VERSION = 1;
    static final int ACTION_STOP = 0;
    static final int ACTION_START = 1;
    static final int MAX_NAME_BYTES = 512;
    static final double MAX_COORDINATE = 30_000_000.0;
    static final int REQUEST_VERSION = 1;
    static final int REQUEST_ACTION_TOGGLE = 0;
    static final int MAX_QUEST_ID_BYTES = 256;
    static final long REQUEST_COOLDOWN_NANOS = 300_000_000L;
    private static final Map<UUID, Long> REQUEST_TIMES = new ConcurrentHashMap<>();

    private NavigationPayload() {
    }

    public static void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(
                BukkitPlugin.getInstance(), REQUEST_CHANNEL, NavigationPayload::handleRequest);
    }

    public static void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(BukkitPlugin.getInstance(), REQUEST_CHANNEL);
        REQUEST_TIMES.clear();
    }

    public static boolean canSend(Player player) {
        return player != null && player.getListeningPluginChannels().contains(CHANNEL);
    }

    public static boolean sendStart(Player player, String questName, Location target) {
        if (!canSend(player) || target == null || target.getWorld() == null) {
            return false;
        }
        try {
            player.sendPluginMessage(BukkitPlugin.getInstance(), CHANNEL,
                    encodeStart(plainName(questName), target.getX(), target.getY(), target.getZ()));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static void sendStop(Player player) {
        if (!canSend(player)) {
            return;
        }
        try {
            player.sendPluginMessage(BukkitPlugin.getInstance(), CHANNEL, encodeStop());
        } catch (IllegalArgumentException ignored) {
        }
    }

    static byte[] encodeStart(String questName, double x, double y, double z) {
        if (!validCoordinate(x) || !validCoordinate(y) || !validCoordinate(z)) {
            throw new IllegalArgumentException("Navigation coordinates are outside the supported range");
        }
        byte[] name = (questName == null ? "" : questName).getBytes(StandardCharsets.UTF_8);
        if (name.length > MAX_NAME_BYTES) {
            throw new IllegalArgumentException("Navigation quest name is too long");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(2 + 4 + name.length + 24);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(VERSION);
            output.writeByte(ACTION_START);
            output.writeInt(name.length);
            output.write(name);
            output.writeDouble(x);
            output.writeDouble(y);
            output.writeDouble(z);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode navigation payload", exception);
        }
    }

    static byte[] encodeStop() {
        return new byte[]{VERSION, ACTION_STOP};
    }

    static String decodeToggleRequest(byte[] payload) {
        if (payload == null || payload.length < 7 || payload.length > 6 + MAX_QUEST_ID_BYTES) {
            throw new IllegalArgumentException("Invalid navigation request length");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readUnsignedByte() != REQUEST_VERSION) {
                throw new IllegalArgumentException("Unsupported navigation request version");
            }
            if (input.readUnsignedByte() != REQUEST_ACTION_TOGGLE) {
                throw new IllegalArgumentException("Unsupported navigation request action");
            }
            int length = input.readInt();
            if (length <= 0 || length > MAX_QUEST_ID_BYTES) {
                throw new IllegalArgumentException("Invalid navigation quest ID length");
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new EOFException("Truncated navigation quest ID");
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing navigation request bytes");
            }
            String questId = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (questId.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.LINE_SEPARATOR
                    || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR)) {
                throw new IllegalArgumentException("Navigation quest ID contains control characters");
            }
            return questId;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Navigation quest ID is not valid UTF-8", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decode navigation request", exception);
        }
    }

    public static void clearRequestState(Player player) {
        if (player != null) {
            REQUEST_TIMES.remove(player.getUniqueId());
        }
    }

    static boolean acquireRequest(UUID playerId, long now) {
        synchronized (REQUEST_TIMES) {
            Long previous = REQUEST_TIMES.get(playerId);
            if (previous != null && now - previous < REQUEST_COOLDOWN_NANOS) {
                return false;
            }
            REQUEST_TIMES.put(playerId, now);
            return true;
        }
    }

    private static void handleRequest(String channel, Player player, byte[] payload) {
        if (!REQUEST_CHANNEL.equals(channel) || player == null) {
            return;
        }
        final String questId;
        try {
            questId = decodeToggleRequest(payload);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        long now = System.nanoTime();
        if (!acquireRequest(player.getUniqueId(), now)) {
            return;
        }
        Runnable toggle = () -> {
            if (!player.isOnline() || QuestManager.getInstance() == null) {
                return;
            }
            Quest quest = QuestManager.getInstance().getQuest(questId);
            if (quest == null) {
                return;
            }
            String navigating = NavigationManager.getInstance().getNavigatingQuestId(player);
            if (navigating != null && navigating.equalsIgnoreCase(questId)) {
                NavigationManager.getInstance().cancel(player);
            } else {
                NavigationManager.getInstance().start(player, quest);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            toggle.run();
        } else {
            Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), toggle);
        }
    }

    private static String plainName(String value) {
        String stripped = ChatColor.stripColor(value == null ? "" : value);
        if (stripped == null) {
            return "";
        }
        String sanitized = stripped.replace('\n', ' ').replace('\r', ' ').replace('\0', ' ').trim();
        StringBuilder limited = new StringBuilder(sanitized.length());
        int bytes = 0;
        for (int offset = 0; offset < sanitized.length(); ) {
            int codePoint = sanitized.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int encodedLength = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + encodedLength > MAX_NAME_BYTES) {
                break;
            }
            limited.append(character);
            bytes += encodedLength;
            offset += Character.charCount(codePoint);
        }
        return limited.toString();
    }

    private static boolean validCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
    }
}
