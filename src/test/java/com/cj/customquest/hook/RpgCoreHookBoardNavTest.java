package com.cj.customquest.hook;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 面板导航按钮的"行号 → 任务"映射。
 *
 * <p>这里曾经漏掉当前窗口的起点 offset：不翻页时窗口起点恒为 0、行号恰好等于全量下标，所以看不出问题；
 * 一旦翻页，每个导航都指着清单最前面的任务，或者落空行上（点了没反应）——表现为"只有滚回顶部才点得动"。
 */
class RpgCoreHookBoardNavTest {

    /** 只列"任务名行"：下标 1、3、4 是描述行，没有可导航的任务。 */
    private static final Map<Integer, String> ROWS = Map.of(
            0, "quest_a",
            2, "quest_b",
            5, "quest_c");

    @Test
    void rowIsResolvedFromTheWindowStart() {
        assertEquals("quest_a", RpgCoreHook.questForRow(ROWS, 0, 1), "第一屏第 1 行就是全量清单第 0 项");
        assertEquals("quest_c", RpgCoreHook.questForRow(ROWS, 5, 1), "窗口起点 5 时第 1 行对应清单第 5 项");
        assertEquals("quest_c", RpgCoreHook.questForRow(ROWS, 3, 3), "第 N 行 = 起点 + N - 1");
    }

    @Test
    void rowsThatAreNotQuestTitlesHaveNoQuest() {
        assertNull(RpgCoreHook.questForRow(ROWS, 0, 2), "清单第 1 项是描述行，不该解析出任务");
        assertNull(RpgCoreHook.questForRow(ROWS, 0, 99), "越界的行号解析不出任务");
    }

    @Test
    void missingBoardSnapshotYieldsNothing() {
        assertNull(RpgCoreHook.questForRow(null, 0, 1), "还没推过面板时不该抛异常");
    }
}
