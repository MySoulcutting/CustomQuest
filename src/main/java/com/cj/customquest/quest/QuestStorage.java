package com.cj.customquest.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 玩家数据持久化（SQLite：data.db）。
 * <p>
 * 首次启用 SQLite 时会把旧版 data/&lt;uuid&gt;.yml 数据一次性导入；
 * 导入成功后保留原文件作为备份，后续只读写数据库。
 */
public final class QuestStorage implements AutoCloseable {

    private static final String YAML_MIGRATION_KEY = "yaml-migration-v1";
    private static final Logger LOGGER = Logger.getLogger("CustomQuest");

    private final File yamlFolder;
    private final Map<UUID, PlayerQuestData> cache = new ConcurrentHashMap<>();
    private final Connection connection;

    public QuestStorage(File dataFolder) {
        dataFolder.mkdirs();
        yamlFolder = new File(dataFolder, "data");
        File databaseFile = new File(dataFolder, "data.db");

        Connection opened = null;
        try {
            Class.forName("org.sqlite.JDBC");
            opened = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            configure(opened);
            createSchema(opened);
            migrateYaml(opened);
        } catch (ClassNotFoundException | SQLException | IOException | InvalidConfigurationException e) {
            closeQuietly(opened);
            throw new IllegalStateException("无法初始化 SQLite 玩家数据存储: " + e.getMessage(), e);
        }
        connection = opened;
    }

    public synchronized PlayerQuestData get(Player player) {
        UUID uuid = player.getUniqueId();
        return cache.computeIfAbsent(uuid, this::load);
    }

    public PlayerQuestData getOrNull(UUID uuid) {
        return cache.get(uuid);
    }

    public synchronized void load(Player player) {
        cache.put(player.getUniqueId(), load(player.getUniqueId()));
    }

    public synchronized void save(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerQuestData data = cache.get(uuid);
        if (data != null) {
            save(uuid, data);
        }
    }

    public synchronized void saveAll() {
        if (cache.isEmpty()) {
            return;
        }
        try {
            beginTransaction();
            for (Map.Entry<UUID, PlayerQuestData> entry : cache.entrySet()) {
                writePlayer(connection, entry.getKey(), entry.getValue());
            }
            commitTransaction();
        } catch (SQLException e) {
            rollbackTransaction();
            throw databaseFailure("保存全部玩家数据", e);
        } finally {
            restoreAutoCommit();
        }
    }

    public synchronized void unload(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerQuestData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        save(uuid, data);
        cache.remove(uuid, data);
    }

    /** 保留旧 API；当前调用方仍在主线程定时保存。 */
    public void saveAllAsync() {
        saveAll();
    }

    PlayerQuestData load(UUID uuid) {
        PlayerQuestData data = new PlayerQuestData();
        String playerId = uuid.toString();
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, accepted_at FROM accepted_quests WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        data.getAccepted().put(result.getString("quest_id"),
                                new QuestProgress(result.getLong("accepted_at")));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, counter_key, counter_value FROM quest_counters WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        QuestProgress progress = data.getAccepted().get(result.getString("quest_id"));
                        if (progress != null) {
                            progress.setCounter(result.getString("counter_key"), result.getInt("counter_value"));
                        }
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, completed_at FROM completed_quests WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        data.getCompleted().put(result.getString("quest_id"), result.getLong("completed_at"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT npc_id, data_key, data_value FROM npc_data WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        data.getNpcData()
                                .computeIfAbsent(result.getString("npc_id"), key -> new HashMap<>())
                                .put(result.getString("data_key"), result.getString("data_value"));
                    }
                }
            }
            return data;
        } catch (SQLException e) {
            throw databaseFailure("读取玩家 " + playerId + " 的数据", e);
        }
    }

    synchronized void save(UUID uuid, PlayerQuestData data) {
        try {
            beginTransaction();
            writePlayer(connection, uuid, data);
            commitTransaction();
        } catch (SQLException e) {
            rollbackTransaction();
            throw databaseFailure("保存玩家 " + uuid + " 的数据", e);
        } finally {
            restoreAutoCommit();
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS accepted_quests (
                        player_uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        accepted_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, quest_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quest_counters (
                        player_uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        counter_key TEXT NOT NULL,
                        counter_value INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, quest_id, counter_key),
                        FOREIGN KEY (player_uuid, quest_id)
                            REFERENCES accepted_quests(player_uuid, quest_id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS completed_quests (
                        player_uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        completed_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, quest_id)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS npc_data (
                        player_uuid TEXT NOT NULL,
                        npc_id TEXT NOT NULL,
                        data_key TEXT NOT NULL,
                        data_value TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, npc_id, data_key)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS storage_meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    )
                    """);
        }
    }

    private void migrateYaml(Connection connection) throws SQLException, IOException, InvalidConfigurationException {
        if (hasMeta(connection, YAML_MIGRATION_KEY)) {
            return;
        }
        File[] files = yamlFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        int migrated = 0;
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (files != null) {
                for (File file : files) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning("[CustomQuest] 跳过无法识别的旧玩家数据文件: " + file.getName());
                        continue;
                    }
                    writePlayer(connection, uuid, loadYaml(file));
                    migrated++;
                }
            }
            setMeta(connection, YAML_MIGRATION_KEY, String.valueOf(System.currentTimeMillis()));
            connection.commit();
        } catch (SQLException | IOException | InvalidConfigurationException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
        if (migrated > 0) {
            LOGGER.info("[CustomQuest] 已将 " + migrated
                    + " 个 YAML 玩家数据迁移到 SQLite；原文件已保留为备份。");
        }
    }

    private static PlayerQuestData loadYaml(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        PlayerQuestData data = new PlayerQuestData();

        ConfigurationSection accepted = config.getConfigurationSection("accepted");
        if (accepted != null) {
            for (String questId : accepted.getKeys(false)) {
                ConfigurationSection section = accepted.getConfigurationSection(questId);
                long acceptedAt = section == null ? 0L : section.getLong("accepted-at", 0L);
                QuestProgress progress = new QuestProgress(acceptedAt);
                if (section != null) {
                    ConfigurationSection counters = section.getConfigurationSection("counters");
                    if (counters != null) {
                        for (String key : counters.getKeys(false)) {
                            progress.setCounter(key, counters.getInt(key, 0));
                        }
                    }
                }
                data.getAccepted().put(questId, progress);
            }
        }

        ConfigurationSection completed = config.getConfigurationSection("completed");
        if (completed != null) {
            for (String questId : completed.getKeys(false)) {
                data.getCompleted().put(questId, completed.getLong(questId, 0L));
            }
        }

        ConfigurationSection npcData = config.getConfigurationSection("npc-data");
        if (npcData != null) {
            for (String npcId : npcData.getKeys(false)) {
                ConfigurationSection section = npcData.getConfigurationSection(npcId);
                if (section == null) {
                    continue;
                }
                Map<String, String> values = data.getNpcData().computeIfAbsent(npcId, key -> new HashMap<>());
                for (String key : section.getKeys(false)) {
                    values.put(key, section.getString(key, ""));
                }
            }
        }
        return data;
    }

    private static void writePlayer(Connection connection, UUID uuid, PlayerQuestData data) throws SQLException {
        String playerId = uuid.toString();
        deletePlayer(connection, playerId);

        try (PreparedStatement accepted = connection.prepareStatement(
                "INSERT INTO accepted_quests(player_uuid, quest_id, accepted_at) VALUES (?, ?, ?)");
             PreparedStatement counters = connection.prepareStatement(
                     "INSERT INTO quest_counters(player_uuid, quest_id, counter_key, counter_value) VALUES (?, ?, ?, ?)");
             PreparedStatement completed = connection.prepareStatement(
                     "INSERT INTO completed_quests(player_uuid, quest_id, completed_at) VALUES (?, ?, ?)");
             PreparedStatement npcData = connection.prepareStatement(
                     "INSERT INTO npc_data(player_uuid, npc_id, data_key, data_value) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<String, QuestProgress> entry : data.getAccepted().entrySet()) {
                accepted.setString(1, playerId);
                accepted.setString(2, entry.getKey());
                accepted.setLong(3, entry.getValue().getAcceptedAt());
                accepted.addBatch();
                for (Map.Entry<String, Integer> counter : entry.getValue().getCounters().entrySet()) {
                    counters.setString(1, playerId);
                    counters.setString(2, entry.getKey());
                    counters.setString(3, counter.getKey());
                    counters.setInt(4, counter.getValue());
                    counters.addBatch();
                }
            }
            accepted.executeBatch();
            counters.executeBatch();

            for (Map.Entry<String, Long> entry : data.getCompleted().entrySet()) {
                completed.setString(1, playerId);
                completed.setString(2, entry.getKey());
                completed.setLong(3, entry.getValue());
                completed.addBatch();
            }
            completed.executeBatch();

            for (Map.Entry<String, Map<String, String>> npcEntry : data.getNpcData().entrySet()) {
                for (Map.Entry<String, String> entry : npcEntry.getValue().entrySet()) {
                    npcData.setString(1, playerId);
                    npcData.setString(2, npcEntry.getKey());
                    npcData.setString(3, entry.getKey());
                    npcData.setString(4, entry.getValue());
                    npcData.addBatch();
                }
            }
            npcData.executeBatch();
        }
    }

    private static void deletePlayer(Connection connection, String playerId) throws SQLException {
        for (String table : new String[]{"accepted_quests", "completed_quests", "npc_data"}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                statement.executeUpdate();
            }
        }
    }

    private static boolean hasMeta(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM storage_meta WHERE meta_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void setMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO storage_meta(meta_key, meta_value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    private void commitTransaction() throws SQLException {
        connection.commit();
    }

    private void rollbackTransaction() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOGGER.warning("[CustomQuest] 恢复 SQLite 自动提交失败: " + e.getMessage());
        }
    }

    private static IllegalStateException databaseFailure(String action, SQLException e) {
        LOGGER.severe("[CustomQuest] " + action + "失败: " + e.getMessage());
        return new IllegalStateException(action + "失败", e);
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.warning("[CustomQuest] 关闭 SQLite 连接失败: " + e.getMessage());
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
