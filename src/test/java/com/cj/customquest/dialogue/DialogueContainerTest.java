package com.cj.customquest.dialogue;

import com.cj.customquest.config.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueContainerTest {

    @Test
    void putsTheBookInTheFirstSlotAndOptionsFromTheThirdSlot() {
        assertEquals(0, DialogueContainer.SPEAKER_SLOT);
        assertEquals(2, DialogueContainer.FIRST_OPTION_SLOT);
        assertEquals(9, DialogueContainer.SIZE);
    }

    /** 页面展示层用这份显示名来保留颜色码，所以槽位对应关系必须和 render 的写入一致。 */
    @Test
    void slotNamesFollowTheSameReverseMappingAsRenderedItems() {
        DialogueSnapshot.Snapshot snapshot = new DialogueSnapshot.Snapshot(
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                "§6任务发布官",
                List.of("§f正文"),
                List.of(
                        new DialogueSnapshot.Option("a", "§a第一个选项"),
                        new DialogueSnapshot.Option("b", "§e第二个选项")));
        List<String> names = DialogueContainer.slotNames(snapshot);

        assertEquals(DialogueContainer.SIZE, names.size(), "空槽位也要占位，页面按下标取");
        assertEquals("§6任务发布官", names.get(DialogueContainer.SPEAKER_SLOT), "0 号槽是 NPC 标题");
        assertEquals("§a第一个选项", names.get(3), "第一个选项落在最靠后的一格");
        assertEquals("§e第二个选项", names.get(2), "选项倒序写入");
        assertEquals("", names.get(1), "第 2 格始终为空");
        assertEquals("", names.get(4), "没被占用的选项格为空串");
    }

    @Test
    void mapsOnlyOptionSlotsToAnOptionIndex() {
        // 倒序写入：槽位越靠前，选项序号越大（第一个选项在最靠后的一格）。
        assertEquals(5, DialogueContainer.optionIndex(DialogueContainer.FIRST_OPTION_SLOT, 6));
        assertEquals(0, DialogueContainer.optionIndex(DialogueContainer.FIRST_OPTION_SLOT + 5, 6));
        assertEquals(-1, DialogueContainer.optionIndex(0, 6), "书本格不是选项");
        assertEquals(-1, DialogueContainer.optionIndex(1, 6), "第 2 格不是选项");
        assertEquals(-1, DialogueContainer.optionIndex(DialogueContainer.FIRST_OPTION_SLOT + 6, 6),
                "超过最大选项数");
        assertEquals(-1, DialogueContainer.optionIndex(DialogueContainer.SIZE + 3, 6), "玩家背包格不是选项");
        assertEquals(-1, DialogueContainer.optionIndex(-1, 6));
    }

    /** 选项倒序写入：第一个选项在最靠后的一格，点击回读与写入位置一致。 */
    @Test
    void writesOptionsInReverseSlotOrder() {
        int count = 4;
        int firstOptionSlot = DialogueContainer.FIRST_OPTION_SLOT + count - 1;
        assertEquals(0, DialogueContainer.optionIndex(firstOptionSlot, count), "第一个选项在最靠后的一格");
        assertEquals(count - 1, DialogueContainer.optionIndex(DialogueContainer.FIRST_OPTION_SLOT, count),
                "最后一个选项在第一个选项格");
        for (int index = 0; index < count; index++) {
            int slot = DialogueContainer.FIRST_OPTION_SLOT + (count - 1 - index);
            assertEquals(index, DialogueContainer.optionIndex(slot, count), "槽位回读应与写入位置一致");
        }
    }

    @Test
    void usesTheConfiguredTitleSoMatchesCanAvoidNonAsciiNames() {
        String previous = Settings.dialogueTitle;
        try {
            Settings.dialogueTitle = "NPC Dialogue";
            assertEquals("NPC Dialogue", DialogueContainer.title());
            Settings.dialogueTitle = "   ";
            assertEquals(DialogueContainer.DEFAULT_TITLE, DialogueContainer.title());
            Settings.dialogueTitle = null;
            assertEquals(DialogueContainer.DEFAULT_TITLE, DialogueContainer.title());
        } finally {
            Settings.dialogueTitle = previous;
        }
    }
}
