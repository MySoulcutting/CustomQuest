package com.cj.customquest.quest;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.kether.KetherRunner;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.util.Messages;
import com.cj.customquest.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务管理器：任务加载、接取/放弃/提交/完成、多目标进度统计与奖励发放。
 */
public final class QuestManager {

    private static QuestManager instance;

    private File questsFolder;
    private QuestStorage storage;
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public static QuestManager getInstance() {
        return instance;
    }

    public static void create(File dataFolder) {
        instance = new QuestManager();
        instance.questsFolder = new File(dataFolder, "quests");
        instance.storage = new QuestStorage(dataFolder);
        instance.reload();
    }

    public QuestStorage getStorage() {
        return storage;
    }

    public Map<String, Quest> getQuests() {
        return quests;
    }

    public Quest getQuest(String id) {
        if (id == null) return null;
        return quests.get(id.toLowerCase());
    }

    /** 找到任务对应的 yml 文件（按 quest-id 匹配，用于指令写回配置） */
    public File getQuestFile(String questId) {
        if (questId == null) return null;
        File[] files = questsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return null;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                if (questId.equalsIgnoreCase(config.getString("quest-id"))) {
                    return file;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 设置任务导航位置并写回任务 yml（指令配置用；重写会丢失原注释） */
    public boolean setNavigate(String questId, String world, double x, double y, double z) {
        File file = getQuestFile(questId);
        if (file == null) return false;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("navigate", world + "," + x + "," + y + "," + z);
            config.set("navigate-color", null);
            config.save(file);
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[CustomQuest] 保存导航位置失败: " + e.getMessage());
            return false;
        }
    }

    /** 移除任务导航位置并写回任务 yml */
    public boolean removeNavigate(String questId) {
        File file = getQuestFile(questId);
        if (file == null) return false;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("navigate", null);
            config.set("navigate-color", null);
            config.save(file);
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[CustomQuest] 移除导航位置失败: " + e.getMessage());
            return false;
        }
    }

    public void reload() {
        quests.clear();
        NavigationManager.getInstance().clearTargets();
        questsFolder.mkdirs();
        File[] files = questsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                List<String> errors = new ArrayList<>();
                Quest quest = Quest.load(file.getName().replace(".yml", ""), config, errors);
                if (quest == null) {
                    for (String error : errors) {
                        Bukkit.getLogger().warning("[CustomQuest] " + error + "（文件: " + file.getName() + "）");
                    }
                    continue;
                }
                quests.put(quest.getId().toLowerCase(), quest);
                // 注册导航目标（navigate 未配置则忽略）
                if (quest.getNavigateLocation() != null) {
                    NavigationManager.getInstance().setTarget(quest.getId(), quest.getNavigateLocation());
                }
                for (String error : errors) {
                    Bukkit.getLogger().warning("[CustomQuest] " + error);
                }
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[CustomQuest] 任务文件 " + file.getName() + " 加载失败: " + e.getMessage());
            }
        }
        Bukkit.getLogger().info("[CustomQuest] 已加载 " + quests.size() + " 个任务。");
    }

    // ---------------- 查询 ----------------

    public boolean isAccepted(Player player, String questId) {
        Quest quest = getQuest(questId);
        if (quest == null) return false;
        return storage.get(player).isAccepted(quest.getId());
    }

    public boolean isCompleted(Player player, String questId) {
        Quest quest = getQuest(questId);
        if (quest == null) return false;
        return storage.get(player).isCompleted(quest.getId());
    }

    public PlayerQuestData getPlayerData(Player player) {
        return storage.get(player);
    }

    /** 指定目标索引的当前进度（击杀类读计数器，提交类读背包） */
    public int getObjectiveProgress(Player player, Quest quest, int index) {
        if (index < 0 || index >= quest.getObjectives().size()) return 0;
        QuestObjective objective = quest.getObjectives().get(index);
        return Math.min(objectiveProgressRaw(player, quest, index, objective), objective.getAmount());
    }

    private int objectiveProgressRaw(Player player, Quest quest, int index, QuestObjective objective) {
        if (objective.isKill()) {
            QuestProgress progress = storage.get(player).getAccepted().get(quest.getId());
            return progress == null ? 0 : progress.getCounter(counterKey(progress, quest.getObjectives(), index));
        }
        return countItems(player, objective);
    }

    /** 全部目标合计进度（每个目标封顶其需求数） */
    public int getProgress(Player player, Quest quest) {
        int total = 0;
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            total += getObjectiveProgress(player, quest, i);
        }
        return total;
    }

    public int getProgress(Player player, String questId) {
        Quest quest = getQuest(questId);
        if (quest == null) return 0;
        return getProgress(player, quest);
    }

    /** 任务是否已满足完成进度（所有目标达成；描述任务只能指令完成，恒为 false） */
    public boolean isProgressComplete(Player player, Quest quest) {
        if (quest.getType() == QuestType.DESCRIBE) {
            return false;
        }
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (objectiveProgressRaw(player, quest, i, objective) < objective.getAmount()) {
                return false;
            }
        }
        return true;
    }

    // ---------------- 接取 / 放弃 ----------------

    /**
     * 接取任务（不校验前置条件；任务门控请使用 NPC 对话分支的 data / PAPI 条件）。
     */
    public boolean acceptQuest(Player player, Quest quest) {
        PlayerQuestData data = storage.get(player);
        if (data.isAccepted(quest.getId())) {
            Messages.sendTo(player, "quest-already-accepted");
            return false;
        }
        if (data.isCompleted(quest.getId())) {
            if (!quest.isRepeatable()) {
                Messages.sendTo(player, "quest-not-repeatable");
                return false;
            }
            long last = data.getCompletedAt(quest.getId());
            long passed = (System.currentTimeMillis() - last) / 1000;
            if (quest.getCooldown() > 0 && passed < quest.getCooldown()) {
                Messages.sendTo(player, "quest-cooldown", Map.of("time", quest.getCooldown() - passed));
                return false;
            }
        }

        storage.get(player).getAccepted().put(quest.getId(), new QuestProgress(System.currentTimeMillis()));
        Messages.sendTo(player, "quest-accepted", Map.of("name", TextUtil.parse(player, quest.getName())));
        sendQuestInfo(player, quest);
        QuestBoard.getInstance().update(player);
        return true;
    }

    /** 放弃任务 */
    public boolean abandonQuest(Player player, Quest quest) {
        PlayerQuestData data = storage.get(player);
        if (!data.isAccepted(quest.getId())) {
            Messages.sendTo(player, "quest-not-accepted");
            return false;
        }
        data.getAccepted().remove(quest.getId());
        NavigationManager.getInstance().stopIfNavigating(player, quest.getId());
        Messages.sendTo(player, "quest-abandoned", Map.of("name", TextUtil.parse(player, quest.getName())));
        QuestBoard.getInstance().update(player);
        return true;
    }

    /** 提交任务（进度足够则完成；描述任务只能通过指令完成） */
    public boolean submitQuest(Player player, Quest quest) {
        if (!storage.get(player).isAccepted(quest.getId())) {
            Messages.sendTo(player, "quest-not-accepted");
            return false;
        }
        if (quest.getType() == QuestType.DESCRIBE) {
            Messages.sendTo(player, "quest-command-only");
            return false;
        }
        if (!isProgressComplete(player, quest)) {
            Messages.sendTo(player, "quest-progress-not-enough",
                    Map.of("current", getProgress(player, quest), "total", quest.getTotalAmount()));
            return false;
        }
        return completeQuest(player, quest, false);
    }

    // ---------------- 完成 ----------------

    /**
     * 完成任务并发放奖励。
     *
     * @param force 强制完成（跳过进度校验，仍会扣除提交物品）
     */
    public boolean completeQuest(Player player, Quest quest, boolean force) {
        PlayerQuestData data = storage.get(player);
        if (!force) {
            if (quest.getType() == QuestType.DESCRIBE) {
                Messages.sendTo(player, "quest-command-only");
                return false;
            }
            if (!data.isAccepted(quest.getId())) {
                Messages.sendTo(player, "quest-not-accepted");
                return false;
            }
            if (!isProgressComplete(player, quest)) {
                Messages.sendTo(player, "quest-progress-not-enough",
                        Map.of("current", getProgress(player, quest), "total", quest.getTotalAmount()));
                return false;
            }
        }
        // 扣除提交物品
        if (quest.getType() == QuestType.SUBMIT_ITEM && !takeItems(player, quest)) {
            QuestObjective first = quest.getObjectives().get(0);
            Messages.sendTo(player, "items-not-enough",
                    Map.of("need", first.getAmount(), "have", countItems(player, first)));
            return false;
        }
        data.getAccepted().remove(quest.getId());
        data.getCompleted().put(quest.getId(), System.currentTimeMillis());
        NavigationManager.getInstance().stopIfNavigating(player, quest.getId());
        QuestBoard.getInstance().update(player);

        Messages.sendTo(player, "quest-completed", Map.of("name", TextUtil.parse(player, quest.getName())));
        if (quest.isRepeatable()) {
            Messages.sendTo(player, "quest-repeatable");
        }

        // 奖励：指令（控制台执行，支持 %player% 与 PAPI）
        for (String command : quest.getCommands()) {
            String executed = TextUtil.papi(player, command.replace("%player%", player.getName()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
        }
        // 奖励/完成动作：Kether
        if (!quest.getKether().isEmpty()) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("@QuestId", quest.getId());
            vars.put("@Player", player);
            KetherRunner.run(player, quest.getKether(), vars);
        }
        return true;
    }

    // ---------------- 事件进度 ----------------

    /** MythicMobs 击杀事件（多目标分别计数，并即时刷新全息视图） */
    public void onMythicMobKill(Player player, String internalName) {
        boolean anyProgressed = false;
        // 复制快照遍历：auto-complete 完成任务会从 accepted 移除条目，直接遍历会抛 ConcurrentModificationException
        for (Map.Entry<String, QuestProgress> entry : new ArrayList<>(storage.get(player).getAccepted().entrySet())) {
            Quest quest = getQuest(entry.getKey());
            if (quest == null || quest.getType() != QuestType.KILL_MOB) continue;
            boolean progressed = false;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.isKill() && objective.getTarget().equalsIgnoreCase(internalName)) {
                    entry.getValue().increment(counterKey(entry.getValue(), objectives, i), 1);
                    progressed = true;
                }
            }
            if (progressed && quest.isAutoComplete() && isProgressComplete(player, quest)) {
                completeQuest(player, quest, false);
            }
            if (progressed) {
                anyProgressed = true;
            }
        }
        // 击杀后即时刷新计分板（无需等待定时刷新）
        if (anyProgressed) {
            QuestBoard.getInstance().update(player);
        }
    }

    // ---------------- 物品 ----------------

    /** 背包中匹配目标要求的物品数量（材料 + 可选的自定义名字；只统计主背包，不含盔甲/副手） */
    private int countItems(Player player, QuestObjective objective) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && matchesItem(item, objective)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** 物品是否匹配目标：材料一致，且（若配置了 item-name）显示名一致 */
    private boolean matchesItem(ItemStack item, QuestObjective objective) {
        if (item.getType() != objective.getMaterial()) {
            return false;
        }
        String requiredName = objective.getItemName();
        if (requiredName == null || requiredName.isEmpty()) {
            return true;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String expected = TextUtil.color(requiredName);
        return item.getItemMeta().getDisplayName().equals(expected);
    }

    private boolean takeItems(Player player, Quest quest) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        // 先按格模拟扣除（不同目标可能匹配同一格物品），任一目标不足则整体失败，避免部分扣除造成物品丢失
        int[] amounts = new int[contents.length];
        for (int i = 0; i < contents.length; i++) {
            amounts[i] = contents[i] == null ? 0 : contents[i].getAmount();
        }
        for (QuestObjective objective : quest.getObjectives()) {
            int remaining = objective.getAmount();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (contents[i] != null && matchesItem(contents[i], objective)) {
                    int take = Math.min(remaining, amounts[i]);
                    amounts[i] -= take;
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        // 模拟通过后一次性写入真实背包
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getAmount() != amounts[i]) {
                contents[i].setAmount(amounts[i]);
            }
        }
        return true;
    }

    /**
     * 击杀计数器使用稳定目标键，避免任务目标重排后把旧进度分配给另一个怪物。
     * 首次读取旧版 objN 键时自动迁移并删除旧键。
     */
    static String counterKey(QuestProgress progress, List<QuestObjective> objectives, int index) {
        String stableKey = stableCounterKey(objectives, index);
        Map<String, Integer> counters = progress.getCounters();
        if (!counters.containsKey(stableKey)) {
            Integer legacyValue = counters.remove("obj" + index);
            if (legacyValue != null) {
                counters.put(stableKey, legacyValue);
            }
        }
        return stableKey;
    }

    static String stableCounterKey(List<QuestObjective> objectives, int index) {
        QuestObjective objective = objectives.get(index);
        String target = objective.getTarget().toLowerCase(Locale.ROOT);
        int occurrence = 1;
        for (int i = 0; i < index; i++) {
            QuestObjective previous = objectives.get(i);
            if (previous.isKill() && previous.getTarget().equalsIgnoreCase(objective.getTarget())) {
                occurrence++;
            }
        }
        return "mob:" + target.length() + ":" + target + ":" + occurrence;
    }

    // ---------------- 消息 ----------------

    private void sendQuestInfo(Player player, Quest quest) {
        for (String line : quest.getDescription()) {
            player.sendMessage(TextUtil.parse(player, line));
        }
        for (int i = 0; i < quest.getObjectives().size(); i++) {
            QuestObjective objective = quest.getObjectives().get(i);
            String key = objective.isKill() ? "quest-target-kill" : "quest-target-item";
            player.sendMessage(Messages.get(key, Map.of(
                    "index", i + 1, "amount", objective.getAmount(), "display", objective.getDisplay())));
        }
    }
}
