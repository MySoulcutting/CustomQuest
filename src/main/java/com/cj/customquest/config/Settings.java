package com.cj.customquest.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * 全局配置（config.yml）。
 */
public final class Settings {

    /** 玩家数据自动保存间隔（秒） */
    public static int autosaveSeconds = 300;

    /** 任务追踪总开关（SoulCore HUD 与回退计分板） */
    public static boolean boardEnabled = true;
    /** 回退计分板标题（支持 & 颜色代码） */
    public static String boardTitle = "&6&l任务追踪";
    /** 回退计分板中每个任务之间的空行数（0 = 无空格） */
    public static int boardGapLines = 1;

    private Settings() {
    }

    public static void load(File dataFolder) {
        File file = new File(dataFolder, "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        if (!config.contains("autosave-seconds")) {
            config.set("autosave-seconds", 300);
            changed = true;
        }
        if (!config.contains("scoreboard.enabled")) {
            config.set("scoreboard.enabled", true);
            changed = true;
        }
        if (!config.contains("scoreboard.title")) {
            config.set("scoreboard.title", "&6&l任务追踪");
            changed = true;
        }
        if (!config.contains("scoreboard.gap-lines")) {
            config.set("scoreboard.gap-lines", 1);
            changed = true;
        }
        // 任务书功能已移除；升级时同步清理旧配置节点。
        if (config.contains("quest-book")) {
            config.set("quest-book", null);
            changed = true;
        }

        autosaveSeconds = Math.max(30, config.getInt("autosave-seconds", 300));
        boardEnabled = config.getBoolean("scoreboard.enabled", true);
        boardTitle = config.getString("scoreboard.title", "&6&l任务追踪");
        boardGapLines = Math.max(0, config.getInt("scoreboard.gap-lines", 1));
        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[CustomQuest] 保存 config.yml 失败: " + e.getMessage());
            }
        }
    }
}
