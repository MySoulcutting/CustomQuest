package com.cj.customquest.board;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务追踪只写右侧计分板；这些断言守住「不与其它插件争夺计分板」的行为。
 */
class QuestBoardScoreboardSourceTest {

    @Test
    void yieldsTheScoreboardInsteadOfStealingItFromAnotherPlugin() throws IOException {
        String board = source("src/main/java/com/cj/customquest/board/QuestBoard.java");

        assertTrue(board.contains("previousBoards"));
        assertTrue(board.contains("playerBoard != mainBoard"));
        assertTrue(board.contains("playerBoard.getObjective(DisplaySlot.SIDEBAR) != null"));
        assertTrue(board.contains("previousBoard.getObjective(DisplaySlot.SIDEBAR) != null"));
        assertTrue(board.contains("player.getScoreboard() != currentBoard"));
        assertTrue(board.contains("suppressed"));
    }

    /**
     * 旧的 SoulCore「任务追踪 HUD」同步协议必须彻底移除。
     *
     * <p>注意：任务导航通道是**明确保留**的（见 {@link #keepsTheNavigationChannelInsideItsPackage()}），
     * 所以这里只禁追踪协议，并且额外要求计分板模块本身不认识导航与客户端通道。
     */
    @Test
    void hasNoSoulCoreTrackingProtocolLeft() throws IOException {
        String board = source("src/main/java/com/cj/customquest/board/QuestBoard.java");
        String plugin = source("src/main/java/com/cj/customquest/CustomQuestPlugin.java");
        String listener = source("src/main/java/com/cj/customquest/listener/PlayerListener.java");

        for (String text : new String[] {board, plugin, listener}) {
            String lower = text.toLowerCase(Locale.ROOT);
            assertFalse(lower.contains("questtracking"));
            assertFalse(lower.contains("quest_tracking"));
        }
        // 计分板/行数据模块不得知道导航与客户端协议：导航只由 hook 与 navigation 包处理
        String boardLower = board.toLowerCase(Locale.ROOT);
        assertFalse(boardLower.contains("navigation"));
        assertFalse(boardLower.contains("soulcore"));
        assertTrue(plugin.contains("QuestBoard.getInstance().clearAll()"));
        assertTrue(board.contains("suppressed.clear()"));
        assertTrue(board.contains("states.clear()"));
    }

    /** 导航通道是明确保留的：通道名固定，且只出现在 navigation 包里。 */
    @Test
    void keepsTheNavigationChannelInsideItsPackage() throws IOException {
        String payload = source("src/main/java/com/cj/customquest/navigation/NavigationPayload.java");

        assertTrue(payload.contains("CHANNEL = \"rpgcore:quest_navigation\""),
                "出站通道名要与 RPGCore 模组一致（改名要两边同时改）");
        assertTrue(payload.contains("REQUEST_CHANNEL = \"rpgcore:quest_navigation_request\""),
                "入站通道名要与 RPGCore 模组一致");
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
