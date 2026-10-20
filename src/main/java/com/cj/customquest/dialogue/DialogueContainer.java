package com.cj.customquest.dialogue;

import com.cj.customquest.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 原版箱子对话界面：第 1 格放书本（NPC 标题与正文），第 3 格起依次放选项。
 * 点击选项格直接提交对应选项，不依赖任何客户端模组。
 */
final class DialogueContainer {
    /** 未配置或配置为空白时使用的默认标题（保持 ASCII：容器名匹配不支持中文）。 */
    static final String DEFAULT_TITLE = "NPCdialogue";
    static final int SIZE = 9;
    /** 书本所在槽位（第 1 格） */
    static final int SPEAKER_SLOT = 0;
    /** 选项起始槽位（第 3 格） */
    static final int FIRST_OPTION_SLOT = 2;

    private DialogueContainer() {
    }

    /**
     * 箱子标题，由 config.yml 的 {@code dialogue.title} 自定义。
     * 按容器名匹配的外部插件可能不支持中文，管理员可改成 ASCII 名称。
     */
    static String title() {
        String configured = Settings.dialogueTitle;
        return configured == null || configured.isBlank() ? DEFAULT_TITLE : configured;
    }

    static void open(Player player, DialogueSnapshot.Snapshot snapshot) {
        Holder holder = new Holder(player.getUniqueId(), snapshot.sessionId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, title());
        holder.inventory = inventory;
        render(inventory, snapshot);
        player.openInventory(inventory);
    }

    /**
     * 在同一会话的后续节点上刷新界面内容。
     *
     * <p>只有当玩家仍然看着本次对话的箱子时才就地重绘。若玩家已经打开了别的界面（或把对话箱子关掉后
     * 去开了别的 GUI），这里**不重开自己的箱子**：openInventory 会强行顶掉玩家正在看的界面，
     * 表现为"点完选项后打开的其他界面突然被关掉"。这种情况按会话失联处理，由调用方安静收尾。
     *
     * @return 是否仍然由本会话的箱子承载着界面
     */
    static boolean refresh(Player player, DialogueSnapshot.Snapshot snapshot) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (!matches(inventory, player, snapshot.sessionId())) {
            return false;
        }
        render(inventory, snapshot);
        return true;
    }

    static void close(Player player, UUID sessionId) {
        if (matches(player.getOpenInventory().getTopInventory(), player, sessionId)) {
            player.closeInventory();
        }
    }

    static boolean matches(Inventory inventory, Player player, UUID sessionId) {
        if (inventory == null || player == null
                || !(inventory.getHolder() instanceof Holder holder)) {
            return false;
        }
        return player.getUniqueId().equals(holder.playerId)
                && (sessionId == null || sessionId.equals(holder.sessionId));
    }

    /**
     * 顶栏槽位对应的选项序号；非选项槽位返回 -1。
     * 选项按倒序写入槽位，所以序号要从最靠后的一格往回数。
     */
    static int optionIndex(int rawSlot, int optionCount) {
        int index = FIRST_OPTION_SLOT + optionCount - 1 - rawSlot;
        if (rawSlot < FIRST_OPTION_SLOT || index < 0 || index >= optionCount
                || index >= DialogueSnapshot.MAX_OPTIONS) {
            return -1;
        }
        return index;
    }

    /**
     * 各槽位要显示的名称（按下标对应槽位，空槽位为空串）：0 号槽是 NPC 标题，选项槽是选项文字。
     *
     * <p>用途是喂给页面展示层：页面直接读原版物品名时颜色会丢（颜色在组件的样式里，
     * 不在纯字符串里），所以这里把带 {@code §} 码的原文另外发一份。
     */
    static List<String> slotNames(DialogueSnapshot.Snapshot snapshot) {
        List<String> names = new ArrayList<>(Collections.nCopies(SIZE, ""));
        if (snapshot == null) {
            return names;
        }
        names.set(SPEAKER_SLOT, snapshot.title());
        int optionCount = snapshot.options().size();
        for (int index = 0; index < optionCount; index++) {
            // 与 render 用同一套倒序映射：槽位与选项的对应关系只有一处，改这里要一起改
            int slot = FIRST_OPTION_SLOT + (optionCount - 1 - index);
            if (slot >= SIZE) {
                break;
            }
            names.set(slot, snapshot.options().get(index).text());
        }
        return names;
    }

    private static void render(Inventory inventory, DialogueSnapshot.Snapshot snapshot) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(SPEAKER_SLOT, item(
                Material.BOOK,
                snapshot.title(),
                snapshot.lines().isEmpty() ? List.of("§7暂无对话内容") : snapshot.lines()));
        int optionCount = snapshot.options().size();
        for (int index = 0; index < optionCount; index++) {
            // 倒序写入：第一个选项落在最靠后的一格，最后一个选项落在第一个选项格。
            int slot = FIRST_OPTION_SLOT + (optionCount - 1 - index);
            if (slot >= inventory.getSize()) {
                break;
            }
            DialogueSnapshot.Option option = snapshot.options().get(index);
            inventory.setItem(slot, item(Material.PAPER, option.text(), List.of("§7点击选择")));
        }
    }

    private static ItemStack item(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName == null || displayName.isBlank() ? " " : displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private final UUID playerId;
        private final UUID sessionId;
        private Inventory inventory;

        private Holder(UUID playerId, UUID sessionId) {
            this.playerId = playerId;
            this.sessionId = sessionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
