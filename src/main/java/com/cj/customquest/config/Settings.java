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

    /** 全息视图（右侧任务计分板）开关 */
    public static boolean boardEnabled = true;
    /** 计分板标题（支持 & 颜色代码） */
    public static String boardTitle = "&6&l任务追踪";
    /** 全息显示中每个任务之间的空行数（0 = 无空格） */
    public static int boardGapLines = 1;

    /** 任务书中每个任务之间的空行数（0 = 无空格） */
    public static int bookGapLines = 1;
    /** 任务书每页显示的任务数 */
    public static int bookPerPage = 3;
    /** 任务书无任务时显示的文本（支持 & 颜色） */
    public static String bookNoQuestText = "&c您当前没有接取任务...";

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
        if (!config.contains("quest-book.gap-lines")) {
            config.set("quest-book.gap-lines", 1);
            changed = true;
        }
        if (!config.contains("quest-book.per-page")) {
            config.set("quest-book.per-page", 3);
            changed = true;
        }
        if (!config.contains("quest-book.no-quest")) {
            config.set("quest-book.no-quest", "&c您当前没有接取任务...");
            changed = true;
        }

        autosaveSeconds = Math.max(30, config.getInt("autosave-seconds", 300));
        boardEnabled = config.getBoolean("scoreboard.enabled", true);
        boardTitle = config.getString("scoreboard.title", "&6&l任务追踪");
        boardGapLines = Math.max(0, config.getInt("scoreboard.gap-lines", 1));
        bookGapLines = Math.max(0, config.getInt("quest-book.gap-lines", 1));
        bookPerPage = Math.max(1, config.getInt("quest-book.per-page", 3));
        bookNoQuestText = config.getString("quest-book.no-quest", "&c您当前没有接取任务...");

        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[CustomQuest] 保存 config.yml 失败: " + e.getMessage());
            }
        }
    }
}
