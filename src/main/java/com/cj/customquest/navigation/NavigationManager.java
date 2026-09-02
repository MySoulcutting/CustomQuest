package com.cj.customquest.navigation;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.util.Messages;
import com.cj.customquest.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import taboolib.platform.BukkitPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务导航系统：服务端维护当前任务与到达判定，SoulCore Fabric 客户端负责全部视觉渲染。
 */
public final class NavigationManager {

    private static NavigationManager instance;

    /** 任务导航目标：questId -> 目标位置 */
    private final Map<String, NavTarget> targets = new ConcurrentHashMap<>();
    /** 玩家当前导航：UUID -> 任务 ID */
    private final Map<UUID, String> navigating = new ConcurrentHashMap<>();

    private BukkitTask task;

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public static NavigationManager create() {
        instance = new NavigationManager();
        instance.startTask();
        return instance;
    }

    private NavigationManager() {
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(BukkitPlugin.getInstance(), this::tick, 10L, 10L);
    }

    // ---------------- 目标管理 ----------------

    /** 注册/更新任务的导航目标（QuestManager reload 与指令配置时调用） */
    public void setTarget(String questId, Location location) {
        if (questId == null || location == null) {
            return;
        }
        targets.put(questId.toLowerCase(Locale.ROOT), new NavTarget(location.clone()));
        refreshSessionsForQuest(questId);
    }

    /** 移除任务的导航目标 */
    public void removeTarget(String questId) {
        if (questId == null) {
            return;
        }
        targets.remove(questId.toLowerCase(Locale.ROOT));
        stopSessionsForQuest(questId);
    }

    /** 清空所有目标与导航会话（reload 前调用） */
    public void clearTargets() {
        stopAll(true, false);
        targets.clear();
    }

    public NavTarget getTarget(String questId) {
        return targets.get(questId == null ? "" : questId.toLowerCase(Locale.ROOT));
    }

    public boolean hasTarget(String questId) {
        return targets.containsKey(questId == null ? "" : questId.toLowerCase(Locale.ROOT));
    }

    // ---------------- 导航 ----------------

    /** 开始导航（若已在导航其他任务，先静默替换旧导航） */
    public boolean start(Player player, Quest quest) {
        if (player == null || quest == null) {
            return false;
        }
        if (!QuestManager.getInstance().isAccepted(player, quest.getId())) {
            Messages.sendTo(player, "quest-not-accepted");
            return false;
        }
        NavTarget target = getTarget(quest.getId());
        if (target == null || target.location == null) {
            Messages.sendTo(player, "nav-no-location", Map.of("name", TextUtil.parse(player, quest.getName())));
            return false;
        }
        if (!player.getWorld().equals(target.location.getWorld())) {
            stop(player, true, true);
            Messages.sendTo(player, "nav-cross-world");
            return false;
        }
        if (!NavigationPayload.canSend(player)) {
            Messages.sendTo(player, "nav-client-required");
            return false;
        }

        stop(player, true, false);
        if (!NavigationPayload.sendStart(player, TextUtil.parse(player, quest.getName()), target.location)) {
            refreshTracking(player);
            Messages.sendTo(player, "nav-client-required");
            return false;
        }
        navigating.put(player.getUniqueId(), quest.getId());
        refreshTracking(player);
        return true;
    }

    /** 玩家手动取消当前导航 */
    public void cancel(Player player) {
        stop(player, true, true);
    }

    /** 任务完成或放弃时，静默结束对应导航。 */
    public void stopIfNavigating(Player player, String questId) {
        if (player == null || questId == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String current = navigating.get(uuid);
        if (current != null && current.equalsIgnoreCase(questId)) {
            stop(uuid, player, current, true, true);
        }
    }

    /** 玩家当前导航的任务 ID（null = 未导航） */
    public String getNavigatingQuestId(Player player) {
        return player == null ? null : navigating.get(player.getUniqueId());
    }

    /** 玩家是否正在导航指定任务 */
    public boolean isNavigating(Player player, String questId) {
        if (player == null || questId == null) {
            return false;
        }
        String current = getNavigatingQuestId(player);
        return current != null && current.equalsIgnoreCase(questId);
    }

    /** 玩家退出时清理；客户端断线事件会同步清空显示。 */
    public void remove(Player player) {
        stop(player, false, false);
    }

    /** 插件卸载时停止客户端导航并取消服务端任务。 */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        stopAll(true, false);
        targets.clear();
    }

    // ---------------- 定时到达判定 ----------------

    private void tick() {
        for (Map.Entry<UUID, String> entry : navigating.entrySet()) {
            UUID uuid = entry.getKey();
            String questId = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            Quest quest = QuestManager.getInstance().getQuest(questId);
            NavTarget target = targets.get(questId.toLowerCase(Locale.ROOT));
            if (player == null || !player.isOnline() || quest == null || target == null || target.location == null) {
                stop(uuid, player, questId, player != null && player.isOnline(), true);
                continue;
            }
            if (!QuestManager.getInstance().isAccepted(player, questId)) {
                stop(uuid, player, questId, true, true);
                continue;
            }
            if (!player.getWorld().equals(target.location.getWorld())) {
                Messages.sendTo(player, "nav-cross-world");
                stop(uuid, player, questId, true, true);
                continue;
            }
        }
    }

    private void refreshSessionsForQuest(String questId) {
        NavTarget target = getTarget(questId);
        if (target == null) {
            return;
        }
        Quest quest = QuestManager.getInstance() == null ? null : QuestManager.getInstance().getQuest(questId);
        for (Map.Entry<UUID, String> entry : navigating.entrySet()) {
            if (!entry.getValue().equalsIgnoreCase(questId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || quest == null || !player.getWorld().equals(target.location.getWorld())
                    || !NavigationPayload.sendStart(player, TextUtil.parse(player, quest.getName()), target.location)) {
                stop(entry.getKey(), player, entry.getValue(), player != null && player.isOnline(), true);
            }
        }
    }

    private void stop(Player player, boolean sendPayload, boolean refreshTracking) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String questId = navigating.get(uuid);
        if (questId != null) {
            stop(uuid, player, questId, sendPayload, refreshTracking);
        }
    }

    private void stop(UUID uuid, Player player, String questId, boolean sendPayload,
                      boolean refreshTracking) {
        if (!navigating.remove(uuid, questId)) {
            return;
        }
        if (player != null && sendPayload) {
            NavigationPayload.sendStop(player);
        }
        if (player != null && refreshTracking) {
            refreshTracking(player);
        }
    }

    private void stopSessionsForQuest(String questId) {
        for (Map.Entry<UUID, String> entry : navigating.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(questId)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                stop(entry.getKey(), player, entry.getValue(), player != null && player.isOnline(), true);
            }
        }
    }

    private void stopAll(boolean sendPayload, boolean refreshTracking) {
        for (Map.Entry<UUID, String> entry : navigating.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            stop(entry.getKey(), player, entry.getValue(),
                    sendPayload && player != null && player.isOnline(), refreshTracking);
        }
    }

    private void refreshTracking(Player player) {
        QuestBoard board = QuestBoard.getInstance();
        if (board != null && player.isOnline()) {
            board.update(player);
        }
    }

    /** 导航目标位置 */
    public static final class NavTarget {
        public final Location location;

        public NavTarget(Location location) {
            this.location = location;
        }
    }
}
