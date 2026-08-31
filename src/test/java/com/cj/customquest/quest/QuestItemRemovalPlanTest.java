package com.cj.customquest.quest;

import com.cj.customquest.dialogue.DialogueBranch;
import com.cj.customquest.dialogue.DialogueOption;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertEquals(1, plan.missingItems().size());
        assertEquals("second", plan.missingItems().getFirst().objective().getDisplay());
        assertEquals(2, plan.missingItems().getFirst().have());
        assertEquals(1, plan.missingItems().getFirst().missing());
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

    @Test
    void npcOverrideQuantityProducesOneDeductionPlan() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 5)
        };
        List<QuestObjective> npcItems = List.of(
                QuestObjective.item(Material.DIAMOND, 2, null, null));

        QuestManager.ItemRemovalPlan plan = QuestManager.planItemRemoval(contents, npcItems);

        assertTrue(plan.successful());
        assertArrayEquals(new int[]{2}, plan.allocatedByObjective());
        assertArrayEquals(new int[]{3}, plan.remainingAmounts());
        assertEquals(5, contents[0].amount(), "planning must not deduct before submission succeeds");
    }

    @Test
    void originalObjectivesAndNpcOverrideMustBothPass() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 5),
                new QuestManager.InventorySlot(Material.IRON_INGOT, null, 2)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, null, null));
        List<QuestObjective> npcItems = List.of(
                QuestObjective.item(Material.IRON_INGOT, 3, null, null));

        QuestManager.SubmissionPlans plans = QuestManager.planSubmission(contents, objectives, npcItems);

        assertTrue(plans.objectivePlan().successful());
        assertFalse(plans.deductionPlan().successful());
        assertFalse(plans.successful());
        assertArrayEquals(new int[]{5, 2},
                new int[]{contents[0].amount(), contents[1].amount()});
    }

    @Test
    void emptyNpcOverrideFallsBackToLegacyObjectivePlan() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 5)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, null, null));

        QuestManager.SubmissionPlans plans = QuestManager.planSubmission(contents, objectives, List.of());

        assertTrue(plans.successful());
        assertSame(plans.objectivePlan(), plans.deductionPlan());
        assertArrayEquals(new int[]{0}, plans.deductionPlan().remainingAmounts());
    }

    @Test
    void mappedAmountAndCustomNameFlowIntoRemovalPlan() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  submit:
                    text: Submit
                    submit-quest: example_submit
                    submit-items:
                      - item: DIAMOND
                        amount: 5
                        item-name: "&bSpecial"
                """);
        DialogueOption option = DialogueBranch.load("ready", config).getOptions().getFirst();
        QuestObjective requirement = option.getSubmitItems().getFirst();

        QuestManager.ItemRemovalPlan insufficient = QuestManager.planItemRemoval(
                new QuestManager.InventorySlot[]{
                        new QuestManager.InventorySlot(Material.DIAMOND, "§bSpecial", 4)
                }, option.getSubmitItems());
        QuestManager.ItemRemovalPlan exact = QuestManager.planItemRemoval(
                new QuestManager.InventorySlot[]{
                        new QuestManager.InventorySlot(Material.DIAMOND, "§bSpecial", 5)
                }, option.getSubmitItems());
        QuestManager.ItemRemovalPlan wrongName = QuestManager.planItemRemoval(
                new QuestManager.InventorySlot[]{
                        new QuestManager.InventorySlot(Material.DIAMOND, "§cSpecial", 5)
                }, option.getSubmitItems());

        assertEquals(5, requirement.getAmount());
        assertFalse(insufficient.successful());
        assertTrue(exact.successful());
        assertArrayEquals(new int[]{0}, exact.remainingAmounts());
        assertFalse(wrongName.successful());
    }

    @Test
    void reportsEveryMissingSubmissionItemInConfigurationOrder() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 1),
                new QuestManager.InventorySlot(Material.IRON_INGOT, null, 2)
        };
        List<QuestObjective> requirements = List.of(
                QuestObjective.item(Material.DIAMOND, 3, "diamond", null),
                QuestObjective.item(Material.IRON_INGOT, 5, "iron", null));

        QuestManager.ItemRemovalPlan plan = QuestManager.planItemRemoval(contents, requirements);

        assertFalse(plan.successful());
        assertEquals(2, plan.missingItems().size());
        assertEquals("diamond", plan.missingItems().get(0).objective().getDisplay());
        assertEquals(1, plan.missingItems().get(0).have());
        assertEquals(2, plan.missingItems().get(0).missing());
        assertEquals("iron", plan.missingItems().get(1).objective().getDisplay());
        assertEquals(2, plan.missingItems().get(1).have());
        assertEquals(3, plan.missingItems().get(1).missing());
    }

    @Test
    void combinesMissingObjectivesAndNpcItemsWithoutDuplicateRequirements() {
        QuestManager.InventorySlot[] empty = {};
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 3, "diamond", null));
        List<QuestObjective> npcItems = List.of(
                QuestObjective.item(Material.IRON_INGOT, 5, "iron", null));
        QuestManager.SubmissionPlans different = QuestManager.planSubmission(empty, objectives, npcItems);

        List<QuestManager.MissingItem> combined = QuestManager.combinedMissingItems(
                different.objectivePlan(), different.deductionPlan());
        assertEquals(2, combined.size());
        assertEquals(Material.DIAMOND, combined.get(0).objective().getMaterial());
        assertEquals(Material.IRON_INGOT, combined.get(1).objective().getMaterial());

        QuestManager.SubmissionPlans same = QuestManager.planSubmission(empty, objectives, objectives);
        assertEquals(1, QuestManager.combinedMissingItems(
                same.objectivePlan(), same.deductionPlan()).size());

        List<QuestObjective> repeatedNpcItems = List.of(
                QuestObjective.item(Material.DIAMOND, 3, "first", null),
                QuestObjective.item(Material.DIAMOND, 3, "second", null));
        QuestManager.SubmissionPlans repeated = QuestManager.planSubmission(
                empty, objectives, repeatedNpcItems);
        assertEquals(2, QuestManager.combinedMissingItems(
                repeated.objectivePlan(), repeated.deductionPlan()).size());
    }

    @Test
    void combinedMissingItemsKeepTheLargerShortageForSameRequirement() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 3)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, "original", null));
        List<QuestObjective> npcItems = List.of(
                QuestObjective.item(Material.DIAMOND, 2, "fee", null),
                QuestObjective.item(Material.DIAMOND, 5, "submission", null));

        QuestManager.SubmissionPlans plans = QuestManager.planSubmission(contents, objectives, npcItems);
        List<QuestManager.MissingItem> combined = QuestManager.combinedMissingItems(
                plans.objectivePlan(), plans.deductionPlan());

        assertEquals(1, combined.size());
        assertEquals(1, combined.getFirst().have());
        assertEquals(4, combined.getFirst().missing());
    }

    @Test
    void repeatedMissingRequirementsAlignFromTheLastOccurrence() {
        QuestManager.InventorySlot[] contents = {
                new QuestManager.InventorySlot(Material.DIAMOND, null, 6)
        };
        List<QuestObjective> objectives = List.of(
                QuestObjective.item(Material.DIAMOND, 5, "first", null),
                QuestObjective.item(Material.DIAMOND, 5, "second", null));
        List<QuestObjective> npcItems = List.of(
                QuestObjective.item(Material.DIAMOND, 3, "fee", null),
                QuestObjective.item(Material.DIAMOND, 5, "first submission", null),
                QuestObjective.item(Material.DIAMOND, 5, "second submission", null));

        QuestManager.SubmissionPlans plans = QuestManager.planSubmission(contents, objectives, npcItems);
        List<QuestManager.MissingItem> combined = QuestManager.combinedMissingItems(
                plans.objectivePlan(), plans.deductionPlan());

        assertEquals(2, combined.size());
        assertEquals(2, combined.get(0).missing());
        assertEquals(5, combined.get(1).missing());
    }
}
