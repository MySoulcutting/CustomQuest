package com.cj.customquest;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.command.MainCommand;
import com.cj.customquest.config.Settings;
import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.dialogue.DialoguePayload;
import com.cj.customquest.expansion.QuestExpansion;
import com.cj.customquest.hook.CitizensHook;
import com.cj.customquest.hook.MythicMobsHook;
import com.cj.customquest.hook.PapiHook;
import com.cj.customquest.kether.KetherActions;
import com.cj.customquest.listener.InventoryListener;
import com.cj.customquest.listener.MythicMobListener;
import com.cj.customquest.listener.NpcListener;
import com.cj.customquest.listener.PlayerListener;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.navigation.NavigationPayload;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.quest.QuestStorage;
import com.cj.customquest.tracking.QuestTrackingPayload;
import com.cj.customquest.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitTask;
import taboolib.common.env.RuntimeDependency;
import taboolib.common.platform.Plugin;
import taboolib.platform.BukkitPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * CustomQuest —— 基于 MythicMobs + Citizens2 + TabooLib(Kether) 的任务插件。
 */
@RuntimeDependency(
        value = "org.xerial:sqlite-jdbc:3.53.2.1",
        test = "!org.sqlite.JDBC",
        repository = "https://repo1.maven.org/maven2"
)
public class CustomQuestPlugin extends Plugin {

    private static CustomQuestPlugin instance;
    private QuestExpansion expansion;
    private BukkitTask boardTask;
    private BukkitTask autosaveTask;

    public static CustomQuestPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        prepareDefaultFiles();
    }

    @Override
    public void onEnable() {
        File dataFolder = BukkitPlugin.getInstance().getDataFolder();

        // 全局配置
        Settings.load(dataFolder);

        // 消息
        Messages.load(dataFolder);

        // SoulCore NeoForge 客户端导航通道
        NavigationPayload.register();
        // SoulCore NeoForge 客户端任务追踪通道
        QuestTrackingPayload.register();
        // SoulCore NeoForge 客户端任务对话通道
        DialoguePayload.register();

        // 挂钩
        PapiHook.init();
        MythicMobsHook.init();
        CitizensHook.init();

        // 导航系统（需在任务加载前创建，以便 reload 时注册导航目标）
        NavigationManager.create();

        // 任务与对话
        QuestManager.create(dataFolder);
        DialogueManager.create(dataFolder);

        // 任务追踪（SoulCore HUD / 计分板回退）
        QuestBoard board = QuestBoard.create();
        board.configure(Settings.boardEnabled, Settings.boardTitle);

        // Kether 自定义动作
        KetherActions.register();

        // 指令
        MainCommand.register();
        registerPermission("customquest.admin", PermissionDefault.OP);

        // 监听器
        registerListener(new PlayerListener());
        registerListener(new InventoryListener());
        if (MythicMobsHook.isEnabled()) {
            registerListener(new MythicMobListener());
        }
        if (CitizensHook.isEnabled()) {
            registerListener(new NpcListener());
        }

        // PAPI 扩展
        expansion = new QuestExpansion();
        expansion.register();

        // 定时保存玩家数据
        startAutosaveTask();

        // 任务追踪低频兜底刷新（背包变化由监听器即时刷新）
        startBoardTask();

        // 支持热重载：onEnable 时已经在线的玩家不会再次触发 PlayerJoinEvent。
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            QuestManager.getInstance().getStorage().load(player);
            board.update(player);
            QuestManager.getInstance().queueConditionCheck(player);
        }

        Bukkit.getLogger().info("[CustomQuest] 插件已启用。");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (boardTask != null) {
            boardTask.cancel();
        }
        try {
            NavigationManager.getInstance().shutdown();
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] 卸载时清理导航状态失败: " + e.getMessage());
        }
        try {
            if (QuestBoard.getInstance() != null) {
                QuestBoard.getInstance().clearAll();
            }
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] 卸载时清理任务追踪失败: " + e.getMessage());
        } finally {
            try {
                QuestTrackingPayload.unregister();
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[CustomQuest] 卸载时注销任务追踪通道失败: " + e.getMessage());
            }
        }
        try {
            if (DialogueManager.getInstance() != null) {
                DialogueManager.getInstance().shutdown();
            }
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] 卸载时清理任务对话失败: " + e.getMessage());
        } finally {
            DialoguePayload.unregister();
        }
        NavigationPayload.unregister();
        QuestStorage storage = QuestManager.getInstance() == null
                ? null : QuestManager.getInstance().getStorage();
        try {
            if (expansion != null) {
                expansion.unregister();
            }
            if (storage != null) {
                storage.saveAll();
            }
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] 卸载时保存玩家数据失败: " + e.getMessage());
        } finally {
            if (storage != null) {
                storage.close();
            }
        }
        Bukkit.getLogger().info("[CustomQuest] 插件已卸载。");
    }

    /** 重载全部配置（供 /cq reload 使用） */
    public static void reloadAll() {
        File dataFolder = BukkitPlugin.getInstance().getDataFolder();
        Settings.load(dataFolder);
        Messages.load(dataFolder);
        QuestManager.getInstance().reload();
        DialogueManager.getInstance().reload();
        QuestBoard.getInstance().configure(Settings.boardEnabled, Settings.boardTitle);
        if (Settings.boardEnabled) {
            QuestBoard.getInstance().updateAll();
        }
        // 任务条件可能因配置重载而变化；重新校准在线玩家并触发新的达成状态。
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            QuestManager.getInstance().queueConditionCheck(player);
        }
        // 重启任务追踪定时刷新（开关状态可能在本次重载中变化）
        getInstance().startBoardTask();
        // 自动保存间隔也可能在本次重载中变化
        getInstance().startAutosaveTask();
        Bukkit.getLogger().info("[CustomQuest] 配置已重载。");
    }

    private void startBoardTask() {
        if (boardTask != null) {
            boardTask.cancel();
        }
        if (!Settings.boardEnabled) {
            return;
        }
        QuestBoard board = QuestBoard.getInstance();
        boardTask = Bukkit.getScheduler().runTaskTimer(BukkitPlugin.getInstance(),
                board::refreshTracked, 20L, 20L * board.getUpdateInterval());
    }

    private void startAutosaveTask() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long period = 20L * Settings.autosaveSeconds;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(BukkitPlugin.getInstance(),
                () -> QuestManager.getInstance().getStorage().saveAll(), period, period);
    }

    private void registerListener(org.bukkit.event.Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, BukkitPlugin.getInstance());
    }

    private void registerPermission(String name, PermissionDefault def) {
        try {
            if (Bukkit.getPluginManager().getPermission(name) == null) {
                Bukkit.getPluginManager().addPermission(new Permission(name, def));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 首次运行：释放示例配置（config.yml / quests / dialogues） */
    private void prepareDefaultFiles() {
        File dataFolder = BukkitPlugin.getInstance().getDataFolder();
        dataFolder.mkdirs();
        copyResource(dataFolder, "config.yml");
        copyResource(dataFolder, "quests/example_kill.yml");
        copyResource(dataFolder, "quests/example_submit.yml");
        copyResource(dataFolder, "quests/example_describe.yml");
        copyResource(dataFolder, "dialogues/example_npc.yml");
    }

    private void copyResource(File dataFolder, String path) {
        File target = new File(dataFolder, path);
        if (target.exists()) {
            return;
        }
        try (InputStream in = BukkitPlugin.getInstance().getResource(path)) {
            if (in == null) {
                return;
            }
            target.getParentFile().mkdirs();
            Files.copy(in, target.toPath());
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] 释放示例文件失败: " + path + " (" + e.getMessage() + ")");
        }
    }
}
