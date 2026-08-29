package com.cj.customquest.tracking;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

/** SoulCore Fabric 客户端任务追踪通道。 */
public final class QuestTrackingPayload {

    public static final String CHANNEL = "soulcore:quest_tracking";
    public static final String CHANNEL_V2 = "soulcore:quest_tracking_v2";
    public static final String CHANNEL_V3 = "soulcore:quest_tracking_v3";
    public static final int VERSION_V1 = 1;
    public static final int VERSION_V2 = 2;
    public static final int VERSION_V3 = 3;
    public static final int VERSION = VERSION_V1;
    public static final int ACTION_CLEAR = 0;
    public static final int ACTION_SNAPSHOT = 1;
    public static final int MAX_TEXT_BYTES = 256;
    public static final int MAX_PAYLOAD_BYTES = 8192;

    private QuestTrackingPayload() {
    }

    public static void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL_V2);
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL_V3);
    }

    public static void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL_V2);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL_V3);
    }

    public static boolean canSend(Player player) {
        return preferredVersion(player) != 0;
    }

    public static int preferredVersion(Player player) {
        return player == null ? 0 : preferredVersion(player.getListeningPluginChannels());
    }

    static int preferredVersion(Set<String> channels) {
        if (channels.contains(CHANNEL_V3)) {
            return VERSION_V3;
        }
        if (channels.contains(CHANNEL_V2)) {
            return VERSION_V2;
        }
        return channels.contains(CHANNEL) ? VERSION_V1 : 0;
    }

    public static boolean sendSnapshot(Player player, QuestTrackingSnapshot snapshot) {
        if (!canSend(player) || snapshot == null) {
            return false;
        }
        try {
            int version = preferredVersion(player);
            String channel = version == VERSION_V3 ? CHANNEL_V3
                    : version == VERSION_V2 ? CHANNEL_V2 : CHANNEL;
            byte[] payload = version == VERSION_V3 ? encodeSnapshotV3(snapshot)
                    : version == VERSION_V2 ? encodeSnapshotV2(snapshot) : encodeSnapshot(snapshot);
            player.sendPluginMessage(BukkitPlugin.getInstance(), channel, payload);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean sendClear(Player player) {
        if (!canSend(player)) {
            return false;
        }
        try {
            int version = preferredVersion(player);
            String channel = version == VERSION_V3 ? CHANNEL_V3
                    : version == VERSION_V2 ? CHANNEL_V2 : CHANNEL;
            byte[] payload = version == VERSION_V3 ? encodeClearV3()
                    : version == VERSION_V2 ? encodeClearV2() : encodeClear();
            player.sendPluginMessage(BukkitPlugin.getInstance(), channel, payload);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static byte[] encodeClear() {
        return new byte[]{VERSION_V1, ACTION_CLEAR};
    }

    static byte[] encodeClearV2() {
        return new byte[]{VERSION_V2, ACTION_CLEAR};
    }

    static byte[] encodeClearV3() {
        return new byte[]{VERSION_V3, ACTION_CLEAR};
    }

    static byte[] encodeSnapshot(QuestTrackingSnapshot snapshot) {
        return encodeSnapshot(snapshot, VERSION_V1);
    }

    static byte[] encodeSnapshotV2(QuestTrackingSnapshot snapshot) {
        return encodeSnapshot(snapshot, VERSION_V2);
    }

    static byte[] encodeSnapshotV3(QuestTrackingSnapshot snapshot) {
        return encodeSnapshot(snapshot, VERSION_V3);
    }

    private static byte[] encodeSnapshot(QuestTrackingSnapshot snapshot, int version) {
        if (snapshot == null || snapshot.tasks().size() > QuestTrackingSnapshot.MAX_TASKS) {
            throw new IllegalArgumentException("Invalid task tracking snapshot");
        }
        if (version != VERSION_V1 && version != VERSION_V2 && version != VERSION_V3) {
            throw new IllegalArgumentException("Unsupported task tracking protocol version");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(version);
            output.writeByte(ACTION_SNAPSHOT);
            output.writeInt(snapshot.totalTaskCount());
            output.writeInt(snapshot.tasks().size());
            for (QuestTrackingSnapshot.Task task : snapshot.tasks()) {
                output.writeByte(task.type().protocolId());
                int taskFlags = task.navigating() ? 1 : 0;
                if (version == VERSION_V3 && task.navigatable()) {
                    taskFlags |= 2;
                }
                output.writeByte(taskFlags);
                if (version == VERSION_V2 || version == VERSION_V3) {
                    output.writeInt(task.titleRgb());
                    writeText(output, task.questId());
                }
                writeText(output, task.title());
                output.writeInt(task.lines().size());
                for (QuestTrackingSnapshot.Line line : task.lines()) {
                    output.writeByte(line.hasProgress() ? 1 : 0);
                    writeText(output, line.text());
                    if (line.hasProgress()) {
                        output.writeInt(line.current());
                        output.writeInt(line.total());
                    }
                }
            }
            output.flush();
            byte[] payload = bytes.toByteArray();
            if (payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Task tracking payload is too large");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode task tracking payload", exception);
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] encoded = QuestTrackingText.encodeUtf8Strict(value);
        if (encoded.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Task tracking text is too long");
        }
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
