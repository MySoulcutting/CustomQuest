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
import taboolib.platform.BukkitPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务管理器：任务加载、接取/放弃/提交/完成、多目标进度统计与奖励发放。
 */
public final class QuestManager {

    private static QuestManager instance;

    private File questsFolder;
    private QuestStorage storage;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    /** 合并同一玩家在一 tick 内的背包变化与条件检查。 */
    private final Set<UUID> queuedConditionChecks = ConcurrentHashMap.newKeySet();

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
        return planItemRemoval(player.getInventory().getStorageContents(), quest.getObjectives())
                .allocatedByObjective()[index];
    }

    /** 全部目标合计进度（每个目标封顶其需求数） */
    public int getProgress(Player player, Quest quest) {
        if (quest.getType() == QuestType.SUBMIT_ITEM) {
            int total = 0;
            int[] allocated = planItemRemoval(
                    player.getInventory().getStorageContents(), quest.getObjectives()).allocatedByObjective();
            for (int amount : allocated) {
                total += amount;
            }
            return total;
        }
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
        if (quest.getType() == QuestType.SUBMIT_ITEM) {
            return planItemRemoval(player.getInventory().getStorageContents(), quest.getObjectives()).successful();
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
        // 下一 tick 检查：让 NPC 选项的 accept-data 先写入，再执行条件指令。
        queueConditionCheck(player);
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
        if (quest.getType() == QuestType.DESCRIBE) {
            Messages.sendTo(player, "quest-command-only");
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
        ItemStack[] itemContents = quest.getType() == QuestType.SUBMIT_ITEM
                ? player.getInventory().getStorageContents() : null;
        ItemRemovalPlan itemPlan = itemContents == null
                ? null : planItemRemoval(itemContents, quest.getObjectives());
        if (!force) {
            if (quest.getType() == QuestType.DESCRIBE) {
                Messages.sendTo(player, "quest-command-only");
                return false;
            }
            if (!data.isAccepted(quest.getId())) {
                Messages.sendTo(player, "quest-not-accepted");
                return false;
            }
            if (itemPlan != null && !itemPlan.successful()) {
                sendItemsNotEnough(player, itemPlan);
                return false;
            }
            if (itemPlan == null && !isProgressComplete(player, quest)) {
                Messages.sendTo(player, "quest-progress-not-enough",
                        Map.of("current", getProgress(player, quest), "total", quest.getTotalAmount()));
                return false;
            }
        }
        // 即使是管理员强制完成，提交物品任务也必须实际交出配置要求的物品。
        if (itemPlan != null) {
            if (!itemPlan.successful()) {
                sendItemsNotEnough(player, itemPlan);
                return false;
            }
            applyItemRemoval(itemContents, itemPlan);
            player.getInventory().setStorageContents(itemContents);
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
        // 使用快照，避免条件指令在回调中完成或放弃任务时修改 accepted。
        for (Map.Entry<String, QuestProgress> entry : new ArrayList<>(storage.get(player).getAccepted().entrySet())) {
            Quest quest = getQuest(entry.getKey());
            if (quest == null || quest.getType() != QuestType.KILL_MOB) continue;
            boolean progressed = false;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                if (objective.isKill() && objective.getTarget().equalsIgnoreCase(internalName)) {
                    String key = counterKey(entry.getValue(), objectives, i);
                    int current = entry.getValue().getCounter(key);
                    if (current < objective.getAmount()) {
                        entry.getValue().setCounter(key, current + 1);
                        progressed = true;
                    }
                }
            }
            if (progressed) {
                anyProgressed = true;
                updateConditionState(player, quest, entry.getValue());
            }
        }
        // 击杀后即时刷新计分板（无需等待定时刷新）
        if (anyProgressed) {
            QuestBoard.getInstance().update(player);
        }
    }

    // ---------------- 条件达成 ----------------

    /** 背包事件使用：仅在玩家有提交物品任务时安排下一 tick 检查。 */
    public void queueInventoryConditionCheck(Player player) {
        for (String questId : storage.get(player).getAccepted().keySet()) {
            Quest quest = getQuest(questId);
            if (quest != null && quest.getType() == QuestType.SUBMIT_ITEM) {
                queueConditionCheck(player);
                return;
            }
        }
    }

    /** 合并到下一 tick 检查全部已接任务，并刷新任务追踪。 */
    public void queueConditionCheck(Player player) {
        UUID uuid = player.getUniqueId();
        if (!queuedConditionChecks.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTask(BukkitPlugin.getInstance(), () -> {
            queuedConditionChecks.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                checkConditionStates(online);
                QuestBoard.getInstance().update(online);
            }
        });
    }

    /** 校准玩家所有已接任务的条件状态；可供上线、重载和低频兜底刷新调用。 */
    public void checkConditionStates(Player player) {
        for (Map.Entry<String, QuestProgress> entry
                : new ArrayList<>(storage.get(player).getAccepted().entrySet())) {
            Quest quest = getQuest(entry.getKey());
            if (quest != null && quest.getType() != QuestType.DESCRIBE) {
                updateConditionState(player, quest, entry.getValue());
            }
        }
    }

    /**
     * 五秒低频兜底：仅校准持有提交物品任务的在线玩家，覆盖不会触发背包事件的 API 修改。
     */
    public void refreshOnlineItemConditions() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String questId : storage.get(player).getAccepted().keySet()) {
                Quest quest = getQuest(questId);
                if (quest != null && quest.getType() == QuestType.SUBMIT_ITEM) {
                    checkConditionStates(player);
                    break;
                }
            }
        }
    }

    private void updateConditionState(Player player, Quest quest, QuestProgress progress) {
        if (!progress.updateConditionMet(isProgressComplete(player, quest))) {
            return;
        }
        Messages.sendTo(player, "quest-condition-met",
                Map.of("name", TextUtil.parse(player, quest.getName())));
        for (String command : quest.getConditionCommands()) {
            String executed = TextUtil.papi(player, command
                    .replace("%player%", player.getName())
                    .replace("%quest%", quest.getId()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
        }
    }

    // ---------------- 物品 ----------------

    /** 物品是否匹配目标：材料一致，且（若配置了 item-name）显示名一致。 */
    private static boolean matchesItem(InventorySlot item, QuestObjective objective) {
        if (item.material() != objective.getMaterial()) {
            return false;
        }
        String requiredName = objective.getItemName();
        if (requiredName == null || requiredName.isEmpty()) {
            return true;
        }
        String expected = TextUtil.color(requiredName);
        return expected.equals(item.displayName());
    }

    /**
     * 根据背包快照生成原子扣除计划。先分配带自定义名的精确目标，再分配材料通配目标，
     * 防止通配目标抢走只有精确目标能使用的物品。
     */
    private static ItemRemovalPlan planItemRemoval(ItemStack[] contents, List<QuestObjective> objectives) {
        InventorySlot[] snapshot = new InventorySlot[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            String displayName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName() : null;
            snapshot[i] = new InventorySlot(item.getType(), displayName, item.getAmount());
        }
        return planItemRemoval(snapshot, objectives);
    }

    static ItemRemovalPlan planItemRemoval(InventorySlot[] contents, List<QuestObjective> objectives) {
        int[] amounts = new int[contents.length];
        int[] allocated = new int[objectives.size()];
        for (int i = 0; i < contents.length; i++) {
            amounts[i] = contents[i] == null ? 0 : contents[i].amount();
        }

        for (boolean namedPass : new boolean[]{true, false}) {
            for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
                QuestObjective objective = objectives.get(objectiveIndex);
                boolean named = objective.getItemName() != null && !objective.getItemName().isEmpty();
                if (named != namedPass) {
                    continue;
                }
                int remaining = objective.getAmount();
                for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                    if (contents[slot] != null && amounts[slot] > 0
                            && matchesItem(contents[slot], objective)) {
                        int taken = Math.min(remaining, amounts[slot]);
                        amounts[slot] -= taken;
                        remaining -= taken;
                        allocated[objectiveIndex] += taken;
                    }
                }
            }
        }

        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            if (allocated[i] < objective.getAmount()) {
                return new ItemRemovalPlan(amounts, allocated, objective, allocated[i]);
            }
        }
        return new ItemRemovalPlan(amounts, allocated, null, 0);
    }

    /** 模拟全部成功后才把扣除结果写回真实背包。 */
    private static void applyItemRemoval(ItemStack[] contents, ItemRemovalPlan plan) {
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getAmount() != plan.remainingAmounts()[i]) {
                contents[i].setAmount(plan.remainingAmounts()[i]);
            }
        }
    }

    private static void sendItemsNotEnough(Player player, ItemRemovalPlan plan) {
        QuestObjective missing = plan.missingObjective();
        Messages.sendTo(player, "items-not-enough", Map.of(
                "item", TextUtil.parse(player, missing.getDisplay()),
                "need", missing.getAmount(),
                "have", plan.availableForMissing()));
    }

    static record ItemRemovalPlan(int[] remainingAmounts, int[] allocatedByObjective,
                                  QuestObjective missingObjective, int availableForMissing) {
        boolean successful() {
            return missingObjective == null;
        }
    }

    static record InventorySlot(org.bukkit.Material material, String displayName, int amount) {
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
