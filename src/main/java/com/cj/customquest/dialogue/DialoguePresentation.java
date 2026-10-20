package com.cj.customquest.dialogue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 主线程对话展示：对话始终由原版箱子承载，玩家点击选项槽位即提交该选项。
 * 展示层不依赖任何客户端模组，也不存在第二种展示后端。
 */
final class DialoguePresentation {
    private final Host host;
    private final LongSupplier clock;
    private final Map<UUID, Active> active = new HashMap<>();
    private final ContainerApi containerApi;
    private BukkitTask cleanup;

    DialoguePresentation(Host host) {
        this(host, System::nanoTime);
    }

    DialoguePresentation(Host host, LongSupplier clock) {
        this(host, clock, new BukkitContainerApi());
    }

    /** 箱子交互可注入：生产用 {@link BukkitContainerApi}，单测用假实现验证重建时序。 */
    DialoguePresentation(Host host, LongSupplier clock, ContainerApi containerApi) {
        this.host = host;
        this.clock = clock;
        this.containerApi = containerApi;
    }

    void initialize(Plugin owner) {
        cleanup = Bukkit.getScheduler().runTaskTimer(owner, () -> tick(Bukkit::getPlayer), 20L, 20L);
    }

    boolean show(Player player, DialogueSnapshot.Snapshot snapshot, boolean continuation) {
        Active previous = active.get(player.getUniqueId());
        if (continuation) {
            if (previous == null || !previous.snapshot.sessionId().equals(snapshot.sessionId())) {
                return false;
            }
            previous.snapshot = snapshot;
            previous.expiresAt = clock.getAsLong() + DialogueSessionStore.DEFAULT_TTL.toNanos();
            boolean stillMine;
            try {
                stillMine = containerApi.refresh(player, snapshot);
            } catch (RuntimeException exception) {
                active.remove(player.getUniqueId(), previous);
                host.warning("刷新对话界面失败：" + exception.getMessage());
                return false;
            }
            if (!stillMine) {
                // 玩家已经不在看对话箱子（多半自己关了界面或去开了别的 GUI）：
                // 不重开箱子抢屏幕，按会话失联收尾，让调用方走 expireDialogue 清理。
                active.remove(player.getUniqueId(), previous);
                return false;
            }
            pushBody(player, snapshot);
            return true;
        }
        closeCurrent(player);
        Active next = new Active(snapshot, player.getWorld().getUID(),
                clock.getAsLong() + DialogueSessionStore.DEFAULT_TTL.toNanos());
        active.put(player.getUniqueId(), next);
        try {
            containerApi.open(player, snapshot);
        } catch (RuntimeException exception) {
            active.remove(player.getUniqueId(), next);
            host.warning("打开对话界面失败：" + exception.getMessage());
            return false;
        }
        pushBody(player, snapshot);
        return true;
    }

    /**
     * 把本节点正文与各槽位显示名交给订阅者（例如页面展示层）。每次打开或刷新后调用，
     * 便于页面在每段开始时从头重播逐字效果；正文取快照里的多行文本。
     *
     * <p>槽位显示名单独下发一份，是因为页面读原版物品名拿不到颜色码（颜色在组件样式里）。
     */
    private void pushBody(Player player, DialogueSnapshot.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            host.pushBody(player, String.join("\n", snapshot.lines()),
                    DialogueContainer.slotNames(snapshot));
        } catch (RuntimeException exception) {
            host.warning("下发对话正文失败：" + exception.getMessage());
        }
    }

    /** 该箱子是否属于当前对话。 */
    boolean isDialogueContainer(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        Active current = active.get(player.getUniqueId());
        return current != null && containerApi.owns(player, inventory, current.snapshot.sessionId());
    }

    /**
     * 原版箱子点击：只有选项槽位会提交对应选项。
     * 返回 true 表示事件已被本插件的对话界面接管，调用方必须取消该点击。
     */
    boolean handleContainerClick(Player player, Inventory inventory, int rawSlot) {
        if (!isDialogueContainer(player, inventory)) {
            return false;
        }
        Active current = active.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        int index = DialogueContainer.optionIndex(rawSlot, current.snapshot.options().size());
        if (index < 0 || index >= current.snapshot.options().size()) {
            return true;
        }
        host.select(player, current.snapshot.sessionId(), current.snapshot.options().get(index).id());
        return true;
    }

    /**
     * 外部请求提交选项：按选项文字匹配当前节点，命中后走与点击槽位完全相同的选择路径。
     * 用于页面组件交互等非原版箱子点击的入口；没有进行中的会话或匹配不到选项时返回 false。
     */
    boolean selectByRequest(Player player, String optionText) {
        Active current = active.get(player.getUniqueId());
        if (current == null || optionText == null) {
            return false;
        }
        String wanted = normalizeOptionText(optionText);
        if (wanted.isEmpty()) {
            return false;
        }
        for (DialogueSnapshot.Option option : current.snapshot.options()) {
            if (normalizeOptionText(option.text()).equals(wanted)) {
                host.select(player, current.snapshot.sessionId(), option.id());
                return true;
            }
        }
        return false;
    }

    /** 去掉原版颜色代码与首尾空白，供文字匹配使用。 */
    private static String normalizeOptionText(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim();
    }

    boolean handleContainerClose(Player player, Inventory inventory) {
        if (!isDialogueContainer(player, inventory)) {
            return false;
        }
        Active current = active.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        if (active.remove(player.getUniqueId(), current)) {
            host.dismiss(player, current.snapshot.sessionId());
        }
        return true;
    }

    void close(Player player, UUID sessionId) {
        Active current = active.get(player.getUniqueId());
        if (current == null || !current.snapshot.sessionId().equals(sessionId)) {
            return;
        }
        active.remove(player.getUniqueId(), current);
        // 先通知"这一屏要收了"，再关箱子：让页面模组能走自己的收尾流程。
        // 只靠箱子消失去推断页面结束，它无法区分"页面收尾"与"玩家去开别的界面"。
        try {
            host.displayClosing(player, sessionId);
        } catch (RuntimeException exception) {
            host.warning("页面收尾通知失败：" + exception.getMessage());
        }
        containerApi.close(player, current.snapshot.sessionId());
    }

    void closeCurrent(Player player) {
        Active current = active.get(player.getUniqueId());
        if (current != null) {
            close(player, current.snapshot.sessionId());
        }
    }

    /**
     * 重建当前会话的箱子界面：先关掉仍属本会话的箱子，再新开一个同会话的箱子。
     *
     * <p>用于页面展示失效后的自愈 —— 新箱子会让客户端重建页面实例、服务端重新建立页面会话，
     * 于是内容下发能重新生效。玩家已经不在看本会话的箱子（自己关了、或去开了别的界面）时
     * 不抢屏，按会话失联收尾并返回 false。
     */
    boolean recoverDisplay(Player player) {
        Active current = active.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        UUID sessionId = current.snapshot.sessionId();
        if (!containerApi.ownsOpenContainer(player, sessionId)) {
            active.remove(player.getUniqueId(), current);
            return false;
        }
        // 先摘掉再关：关箱子会同步触发关闭事件，若这时 active 里还挂着本会话，
        // 那条事件会被当成"玩家自己关了界面"而把整个对话结束掉，重建就没意义了。
        if (!active.remove(player.getUniqueId(), current)) {
            return false;
        }
        containerApi.close(player, sessionId);
        try {
            containerApi.open(player, current.snapshot);
        } catch (RuntimeException exception) {
            host.warning("重建对话界面失败：" + exception.getMessage());
            return false;
        }
        active.put(player.getUniqueId(), current);
        current.expiresAt = clock.getAsLong() + DialogueSessionStore.DEFAULT_TTL.toNanos();
        pushBody(player, current.snapshot);
        return true;
    }

    void forget(Player player) {
        active.remove(player.getUniqueId());
    }

    void tick(Function<UUID, Player> players) {
        long now = clock.getAsLong();
        for (Map.Entry<UUID, Active> entry : List.copyOf(active.entrySet())) {
            Active current = entry.getValue();
            Player player = players.apply(entry.getKey());
            if (player == null || !player.isOnline()) {
                active.remove(entry.getKey(), current);
                host.discard(entry.getKey());
            } else if (!player.getWorld().getUID().equals(current.worldId) || current.expired(now)) {
                host.expire(player, current.snapshot.sessionId());
            }
        }
    }

    void shutdown() {
        if (cleanup != null) {
            cleanup.cancel();
        }
        active.clear();
    }

    interface ContainerApi {
        void open(Player player, DialogueSnapshot.Snapshot snapshot);
        /** @return 是否仍由本会话的箱子承载界面；false 表示玩家已不在看该箱子，调用方不应重开 */
        boolean refresh(Player player, DialogueSnapshot.Snapshot snapshot);
        void close(Player player, UUID sessionId);
        boolean owns(Player player, Inventory inventory, UUID sessionId);

        /** @return 玩家当前打开的界面是否仍是本会话的箱子（重建前用它确认玩家没有走开）。 */
        boolean ownsOpenContainer(Player player, UUID sessionId);
    }

    private static final class BukkitContainerApi implements ContainerApi {
        public void open(Player player, DialogueSnapshot.Snapshot snapshot) {
            DialogueContainer.open(player, snapshot);
        }

        public boolean refresh(Player player, DialogueSnapshot.Snapshot snapshot) {
            return DialogueContainer.refresh(player, snapshot);
        }

        public void close(Player player, UUID sessionId) {
            DialogueContainer.close(player, sessionId);
        }

        public boolean owns(Player player, Inventory inventory, UUID sessionId) {
            return DialogueContainer.matches(inventory, player, sessionId);
        }

        public boolean ownsOpenContainer(Player player, UUID sessionId) {
            return DialogueContainer.matches(player.getOpenInventory().getTopInventory(), player, sessionId);
        }
    }

    interface Host {
        void select(Player player, UUID sessionId, String optionId);
        void dismiss(Player player, UUID sessionId);
        void expire(Player player, UUID sessionId);
        void discard(UUID playerId);

        /** 这一屏即将被收起（关箱子之前调用）：订阅者据此走自己的页面收尾流程。 */
        void displayClosing(Player player, UUID sessionId);
        /** 把本节点正文与各槽位显示名交给展示层之外的订阅者（未安装时什么都不做）。 */
        void pushBody(Player player, String body, java.util.List<String> slotNames);
        void warning(String message);
    }

    private static final class Active {
        private DialogueSnapshot.Snapshot snapshot;
        private final UUID worldId;
        private long expiresAt;

        private Active(DialogueSnapshot.Snapshot snapshot, UUID worldId, long expiresAt) {
            this.snapshot = snapshot;
            this.worldId = worldId;
            this.expiresAt = expiresAt;
        }

        private boolean expired(long now) { return now - expiresAt >= 0; }
    }
}
