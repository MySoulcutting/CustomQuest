package com.cj.customquest.book;

import com.cj.customquest.board.QuestBoard;
import com.cj.customquest.config.Settings;
import com.cj.customquest.navigation.NavigationManager;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.quest.QuestProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务书（/quest 指令打开）：展示当前已接任务，支持翻页与点击导航。
 */
public final class QuestBook {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private QuestBook() {
    }

    public static void open(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("任务书"));
        meta.author(Component.text("CustomQuest"));
        List<Component> pages = buildPages(player);
        for (Component page : pages) {
            meta.addPages(page);
        }
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private static List<Component> buildPages(Player player) {
        List<Quest> acceptedQuests = new ArrayList<>();
        for (Map.Entry<String, QuestProgress> entry : QuestManager.getInstance().getPlayerData(player).getAccepted().entrySet()) {
            Quest quest = QuestManager.getInstance().getQuest(entry.getKey());
            if (quest != null) {
                acceptedQuests.add(quest);
            }
        }
        List<Component> pages = new ArrayList<>();
        if (acceptedQuests.isEmpty()) {
            pages.add(renderNoQuestPage());
            return pages;
        }
        int perPage = Settings.bookPerPage;
        for (int start = 0; start < acceptedQuests.size(); start += perPage) {
            List<Quest> slice = acceptedQuests.subList(start, Math.min(start + perPage, acceptedQuests.size()));
            pages.add(renderPage(player, slice));
        }
        return pages;
    }

    /** 无任务时的单页提示（顶格显示，文本可在 config.yml 的 quest-book.no-quest 自定义） */
    private static Component renderNoQuestPage() {
        return Component.empty()
                .append(LEGACY.deserialize(Settings.bookNoQuestText));
    }

    /** 渲染一页：每页显示多个任务，任务间空行 */
    private static Component renderPage(Player player, List<Quest> tasks) {
        QuestBoard board = QuestBoard.getInstance();
        Component page = Component.empty();
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) {
                // 任务之间的空行（gap 个空行 + 1 个分隔换行）
                for (int g = 0; g <= Settings.bookGapLines; g++) {
                    page = page.append(Component.newline());
                }
            }
            Quest quest = tasks.get(i);
            List<String> lines = board.buildQuestLines(player, quest);
            for (int idx = 0; idx < lines.size(); idx++) {
                if (idx > 0) {
                    page = page.append(Component.newline());
                }
                page = page.append(LEGACY.deserialize(lines.get(idx)));
                if (idx == 0) {
                    // 标题行后追加导航按钮
                    page = page.append(buildNavButton(player, quest));
                }
            }
        }
        return page;
    }

    /** 标题后的导航按钮：未导航点击开始，已导航点击取消 */
    private static Component buildNavButton(Player player, Quest quest) {
        boolean navigating = NavigationManager.getInstance().isNavigating(player, quest.getId());
        if (navigating) {
            return Component.text("  [取消导航]")
                    .color(NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/cq nav cancel"));
        }
        return Component.text("  [导航]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/cq nav " + quest.getId()));
    }
}
