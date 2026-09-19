package com.cj.customquest.quest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftQuestConfigTest {

    @Test
    void loadsCraftItemByEpicCraftingsName() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("quest-id", "craft_item");
        config.set("type", "craft_item");
        config.set("crafting", "EpicSword");
        config.set("amount", 3);
        List<String> errors = new ArrayList<>();

        Quest quest = Quest.load("fallback", config, errors);

        assertNotNull(quest);
        assertTrue(errors.isEmpty());
        assertEquals(QuestType.CRAFT_ITEM, quest.getType());
        assertEquals(1, quest.getObjectives().size());
        assertTrue(quest.getObjectives().getFirst().isCrafting());
        assertEquals("EpicSword", quest.getObjectives().getFirst().getTarget());
        assertEquals(3, quest.getObjectives().getFirst().getAmount());
        assertFalse(quest.getObjectives().getFirst().isKill());
    }

    @Test
    void loadsCraftCountWithoutAnItemName() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("quest-id", "craft_count");
        config.set("type", "craft_count");
        config.set("amount", 10);
        List<String> errors = new ArrayList<>();

        Quest quest = Quest.load("fallback", config, errors);

        assertNotNull(quest);
        assertTrue(errors.isEmpty());
        assertEquals(QuestType.CRAFT_COUNT, quest.getType());
        assertEquals("*", quest.getObjectives().getFirst().getTarget());
        assertEquals(10, quest.getTotalAmount());
        assertTrue(quest.getObjectives().getFirst().isCrafting());
    }
}
