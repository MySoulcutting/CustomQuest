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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 玩家数据持久化（SQLite 或 MySQL，由 {@link Config#dialect()} 决定）。
 *
 * <p>首次启用数据库时会把旧版 data/&lt;uuid&gt;.yml 数据一次性导入；
 * 导入成功后保留原文件作为备份，后续只读写数据库。
 *
 * <p>两端业务 SQL 完全一致，只有方言相关的部分走 {@link SqlDialect}（建表类型、PRAGMA、
 * 元数据 upsert）。这样加第三种库时只需再实现一个枚举常量，不必在业务代码里找 SQL。
 */
public final class QuestStorage implements AutoCloseable {

    private static final String YAML_MIGRATION_KEY = "yaml-migration-v1";
    /** 未配置时使用的数据库文件名；与插件默认配置文件一致。 */
    public static final String DEFAULT_DATABASE_FILE = "data.db";
    private static final Logger LOGGER = Logger.getLogger("CustomQuest");

    /**
     * 存储配置。
     *
     * @param dialect         SQLite 或 MySQL
     * @param sqliteFileName  SQLite 文件名（只允许文件名，相对插件目录）
     * @param tablePrefix     表名前缀（多套环境共用一个库时隔离用）
     * @param migrateFromYaml 首次启用时是否导入旧 yml
     */
    public record Config(SqlDialect dialect, String sqliteFileName, String host, int port,
            String database, String username, String password, String tablePrefix,
            boolean migrateFromYaml) {

        /** SQLite 默认配置（历史行为）。 */
        public static Config sqlite(String fileName, boolean migrateFromYaml) {
            return new Config(SqlDialect.SQLITE, fileName, "127.0.0.1", 3306, "customquest",
                    "root", "", "", migrateFromYaml);
        }
    }

    private final File yamlFolder;
    private final Map<UUID, PlayerQuestData> cache = new ConcurrentHashMap<>();
    private final Connection connection;
    /** 实际生效的方言：MySQL 连不上时会回退成 SQLite，后续 SQL 必须按回退后的来。 */
    private final SqlDialect dialect;
    private final String tablePrefix;

    public QuestStorage(File dataFolder) {
        this(dataFolder, Config.sqlite(DEFAULT_DATABASE_FILE, true));
    }

    /**
     * @param databaseFileName SQLite 文件名（只允许文件名，见 {@code Settings#sanitizeFileName}）
     * @param migrateFromYaml  首次启用时是否把 {@code data/<uuid>.yml} 导入；已导入过则不再重复
     */
    public QuestStorage(File dataFolder, String databaseFileName, boolean migrateFromYaml) {
        this(dataFolder, Config.sqlite(databaseFileName, migrateFromYaml));
    }

    public QuestStorage(File dataFolder, Config config) {
        dataFolder.mkdirs();
        this.yamlFolder = new File(dataFolder, "data");
        this.tablePrefix = config.tablePrefix() == null ? "" : config.tablePrefix();

        Opened opened = open(dataFolder, config);
        this.dialect = opened.dialect();
        Connection connection = opened.connection();
        try {
            dialect.configure(connection);
            createSchema(connection);
            if (config.migrateFromYaml()) {
                migrateYaml(connection);
            }
        } catch (SQLException | IOException | InvalidConfigurationException e) {
            closeQuietly(connection);
            throw new IllegalStateException("无法初始化玩家数据存储: " + e.getMessage(), e);
        }
        this.connection = connection;
    }

    /** 建立连接；MySQL 连不上时**回退到 SQLite 并大声告警**。 */
    private Opened open(File dataFolder, Config config) {
        if (config.dialect() == SqlDialect.MYSQL) {
            try {
                return new Opened(SqlDialect.MYSQL, connect(config, dataFolder));
            } catch (ClassNotFoundException missingDriver) {
                LOGGER.severe("[CustomQuest] 找不到 MySQL 驱动（" + SqlDialect.MYSQL.driverClass()
                        + "）。Paper 的 libraries 目录应自带它；已回退到 SQLite。");
            } catch (SQLException failure) {
                LOGGER.severe("[CustomQuest] 无法连接 MySQL（" + config.username() + "@" + config.host()
                        + ":" + config.port() + "/" + config.database() + "）：" + failure.getMessage()
                        + "；已回退到 SQLite。");
            }
            // 回退：用默认的 SQLite 文件，绝不把 MySQL 的参数套到 SQLite 上。
            Config fallback = Config.sqlite(DEFAULT_DATABASE_FILE, config.migrateFromYaml());
            try {
                return new Opened(SqlDialect.SQLITE, connect(fallback, dataFolder));
            } catch (ClassNotFoundException | SQLException failure) {
                throw new IllegalStateException("SQLite 回退也失败: " + failure.getMessage(), failure);
            }
        }
        try {
            return new Opened(config.dialect(), connect(config, dataFolder));
        } catch (ClassNotFoundException | SQLException failure) {
            throw new IllegalStateException("无法初始化玩家数据存储: " + failure.getMessage(), failure);
        }
    }

    private static Connection connect(Config config, File dataFolder)
            throws ClassNotFoundException, SQLException {
        Class.forName(config.dialect().driverClass());
        String url = config.dialect().jdbcUrl(
                new File(dataFolder, config.sqliteFileName()).getAbsolutePath(),
                config.host(), config.port(), config.database());
        // 玩家进服时会同步读一次数据，驱动层的登录超时也要有界。
        DriverManager.setLoginTimeout(3);
        return DriverManager.getConnection(url, config.username(), config.password());
    }

    private record Opened(SqlDialect dialect, Connection connection) {
    }

    /** 实际生效的方言（MySQL 连不上时会回退成 SQLITE）。供诊断与测试使用。 */
    public SqlDialect dialect() {
        return dialect;
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

    /**
     * 保留旧 API 以兼容外部调用方；该方法当前为同步保存。
     *
     * @deprecated 请改用 {@link #saveAll()}，避免误以为此调用会异步执行。
     */
    @Deprecated
    public void saveAllAsync() {
        saveAll();
    }

    PlayerQuestData load(UUID uuid) {
        PlayerQuestData data = new PlayerQuestData();
        String playerId = uuid.toString();
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, accepted_at FROM " + table("accepted_quests") + " WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        data.getAccepted().put(result.getString("quest_id"),
                                new QuestProgress(result.getLong("accepted_at")));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, counter_key, counter_value FROM " + table("quest_counters")
                            + " WHERE player_uuid = ?")) {
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
                    "SELECT quest_id, completed_at FROM " + table("completed_quests") + " WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        data.getCompleted().put(result.getString("quest_id"), result.getLong("completed_at"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT npc_id, data_key, data_value FROM " + table("npc_data") + " WHERE player_uuid = ?")) {
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

    /** 表名：逻辑名 + 配置前缀。所有 SQL 都必须经过这里，否则换前缀时会漏表。 */
    private String table(String base) {
        return tablePrefix + base;
    }

    private void createSchema(Connection connection) throws SQLException {
        String uuid = dialect.uuidType();
        String key = dialect.keyType();
        String big = dialect.bigIntType();
        String integer = dialect.intType();
        String text = dialect.textType();
        String suffix = dialect.tableSuffix();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("accepted_quests") + " ("
                    + "player_uuid " + uuid + " NOT NULL,"
                    + "quest_id " + key + " NOT NULL,"
                    + "accepted_at " + big + " NOT NULL,"
                    + "PRIMARY KEY (player_uuid, quest_id))" + suffix);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("quest_counters") + " ("
                    + "player_uuid " + uuid + " NOT NULL,"
                    + "quest_id " + key + " NOT NULL,"
                    + "counter_key " + key + " NOT NULL,"
                    + "counter_value " + integer + " NOT NULL,"
                    + "PRIMARY KEY (player_uuid, quest_id, counter_key),"
                    + "FOREIGN KEY (player_uuid, quest_id) REFERENCES " + table("accepted_quests")
                    + "(player_uuid, quest_id) ON DELETE CASCADE)" + suffix);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("completed_quests") + " ("
                    + "player_uuid " + uuid + " NOT NULL,"
                    + "quest_id " + key + " NOT NULL,"
                    + "completed_at " + big + " NOT NULL,"
                    + "PRIMARY KEY (player_uuid, quest_id))" + suffix);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("npc_data") + " ("
                    + "player_uuid " + uuid + " NOT NULL,"
                    + "npc_id " + key + " NOT NULL,"
                    + "data_key " + key + " NOT NULL,"
                    + "data_value " + text + " NOT NULL,"
                    + "PRIMARY KEY (player_uuid, npc_id, data_key))" + suffix);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("storage_meta") + " ("
                    + "meta_key " + key + " NOT NULL,"
                    + "meta_value " + key + " NOT NULL,"
                    + "PRIMARY KEY (meta_key))" + suffix);
        }
    }

    private void migrateYaml(Connection connection) throws SQLException, IOException, InvalidConfigurationException {
        if (hasMeta(connection, YAML_MIGRATION_KEY)) {
            return;
        }
        File[] files = yamlFolder.listFiles((dir, name) -> isYamlFile(name));
        int migrated = 0;
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (files != null) {
                for (File file : files) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(withoutYamlExtension(file.getName()));
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
                    + " 个 YAML 玩家数据迁移到 " + dialect + "；原文件已保留为备份。");
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

    private static boolean isYamlFile(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.ya?ml$");
    }

    private static String withoutYamlExtension(String name) {
        return name == null ? "" : name.replaceFirst("(?i)\\.ya?ml$", "");
    }

    private void writePlayer(Connection connection, UUID uuid, PlayerQuestData data) throws SQLException {
        String playerId = uuid.toString();
        deletePlayer(connection, playerId);

        try (PreparedStatement accepted = connection.prepareStatement(
                "INSERT INTO " + table("accepted_quests")
                        + "(player_uuid, quest_id, accepted_at) VALUES (?, ?, ?)");
             PreparedStatement counters = connection.prepareStatement(
                     "INSERT INTO " + table("quest_counters")
                             + "(player_uuid, quest_id, counter_key, counter_value) VALUES (?, ?, ?, ?)");
             PreparedStatement completed = connection.prepareStatement(
                     "INSERT INTO " + table("completed_quests")
                             + "(player_uuid, quest_id, completed_at) VALUES (?, ?, ?)");
             PreparedStatement npcData = connection.prepareStatement(
                     "INSERT INTO " + table("npc_data")
                             + "(player_uuid, npc_id, data_key, data_value) VALUES (?, ?, ?, ?)")) {
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

    private void deletePlayer(Connection connection, String playerId) throws SQLException {
        for (String base : new String[]{"accepted_quests", "completed_quests", "npc_data"}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table(base) + " WHERE player_uuid = ?")) {
                statement.setString(1, playerId);
                statement.executeUpdate();
            }
        }
    }

    private boolean hasMeta(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + table("storage_meta") + " WHERE meta_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void setMeta(Connection connection, String key, String value) throws SQLException {
        // upsert 语法两端不同（SQLite: INSERT OR REPLACE；MySQL: ON DUPLICATE KEY UPDATE）。
        try (PreparedStatement statement = connection.prepareStatement(
                dialect.upsertMetaSql(table("storage_meta")))) {
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
            LOGGER.warning("[CustomQuest] 恢复数据库自动提交失败: " + e.getMessage());
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
            LOGGER.warning("[CustomQuest] 关闭数据库连接失败: " + e.getMessage());
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
