package com.cj.customquest.quest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestItemRemovalPlanTest {

    @Test
    void sharedItemsCannotSatisfyTwoObjectivesTwice() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 5)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 3, "first", null),
                QuestObjective.item(Material.DIAMOND, 3, "second", null));

        QuestManager.ItemRemovalPlan plan = QuestManager.planItemRemoval(contents, objectives);

        assertFalse(plan.successful());
        assertArrayEquals(new int[]{3, 2}, plan.allocatedByObjective());
        assertEquals("second", plan.missingObjective().getDisplay());
        assertEquals(2, plan.availableForMissing());
        assertEquals(5, contents[0].amount(), "planning must not mutate the real stack");
    }

    @Test
    void successfulPlanConsumesAcrossSlotsAndLeavesUnrelatedItems() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 2),
                new QuestManager.InventorySlot(Material.DIAMOND, null, 4),
                new QuestManager.InventorySlot(Material.IRON_INGOT, null, 8)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, null, null),
                QuestObjective.item(Material.IRON_INGOT, 3, null, null));

        QuestManager.ItemRemovalPlan plan = QuestManager.planItemRemoval(contents, objectives);

        assertTrue(plan.successful());
        assertArrayEquals(new int[]{5, 3}, plan.allocatedByObjective());
        assertArrayEquals(new int[]{0, 1, 5}, plan.remainingAmounts());
        assertArrayEquals(new int[]{2, 4, 8},
                new int[]{contents[0].amount(), contents[1].amount(), contents[2].amount()});
    }

    @Test
    void namedObjectiveReservesMatchingItemsBeforeGenericObjective() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, "§bSpecial", 5),
                new QuestManager.InventorySlot(Material.DIAMOND, null, 5)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, "generic", null),
                QuestObjective.item(Material.DIAMOND, 5, "named", "&bSpecial"));

        QuestManager.ItemRemovalPlan plan = QuestManager.planItemRemoval(contents, objectives);

        assertTrue(plan.successful());
        assertArrayEquals(new int[]{5, 5}, plan.allocatedByObjective());
        assertArrayEquals(new int[]{0, 0}, plan.remainingAmounts());
    }
}
