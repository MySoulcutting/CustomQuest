package com.cj.customquest.expansion;

import com.cj.customquest.quest.PlayerQuestData;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 扩展。
 * <p>
 * 可用变量：
 * <ul>
 *   <li>{@code %customquest_progress_<任务ID>%} —— 当前进度</li>
 *   <li>{@code %customquest_has_<任务ID>%} —— 是否已接取（true/false）</li>
 *   <li>{@code %customquest_done_<任务ID>%} —— 是否已完成（true/false）</li>
 *   <li>{@code %customquest_state_<任务ID>%} —— 状态（none/accepted/done）</li>
 *   <li>{@code %customquest_accepted_count%} —— 已接取任务数</li>
 *   <li>{@code %customquest_done_count%} —— 已完成任务数</li>
 * </ul>
 */
public class QuestExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "customquest";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CJ";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        PlayerQuestData data = QuestManager.getInstance().getStorage().getOrNull(offlinePlayer.getUniqueId());
        if (data == null) {
            return "";
        }
        return handle(data, offlinePlayer, params);
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        PlayerQuestData data = QuestManager.getInstance().getPlayerData(player);
        return handle(data, player, params);
    }

    private String handle(PlayerQuestData data, OfflinePlayer player, String params) {
        if (params.startsWith("progress_")) {
            Quest quest = QuestManager.getInstance().getQuest(params.substring("progress_".length()));
            if (quest == null) return "0";
            if (player instanceof Player online) {
                return String.valueOf(QuestManager.getInstance().getProgress(online, quest));
            }
            return "0";
        }
        if (params.startsWith("has_")) {
            Quest quest = QuestManager.getInstance().getQuest(params.substring("has_".length()));
            return quest == null ? "false" : String.valueOf(data.isAccepted(quest.getId()));
        }
        if (params.startsWith("done_")) {
            Quest quest = QuestManager.getInstance().getQuest(params.substring("done_".length()));
            return quest == null ? "false" : String.valueOf(data.isCompleted(quest.getId()));
        }
        if (params.startsWith("state_")) {
            Quest quest = QuestManager.getInstance().getQuest(params.substring("state_".length()));
            if (quest == null) return "none";
            if (data.isAccepted(quest.getId())) return "accepted";
            if (data.isCompleted(quest.getId())) return "done";
            return "none";
        }
        switch (params) {
            case "accepted_count":
                return String.valueOf(data.getAccepted().size());
            case "done_count":
                return String.valueOf(data.getCompleted().size());
            default:
                return "";
        }
    }
}
