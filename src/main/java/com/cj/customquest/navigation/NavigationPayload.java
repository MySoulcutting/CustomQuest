package com.cj.customquest.navigation;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import taboolib.platform.BukkitPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** SoulCore Fabric 客户端导航通道。 */
public final class NavigationPayload {

    public static final String CHANNEL = "soulcore:quest_navigation";
    static final int VERSION = 1;
    static final int ACTION_STOP = 0;
    static final int ACTION_START = 1;
    static final int MAX_NAME_BYTES = 512;
    static final double MAX_COORDINATE = 30_000_000.0;

    private NavigationPayload() {
    }

    public static void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
    }

    public static void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(BukkitPlugin.getInstance(), CHANNEL);
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
