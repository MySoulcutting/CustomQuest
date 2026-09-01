package com.cj.customquest.quest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestConfigTest {

    @Test
    void loadsConditionCommandsAndIgnoresRemovedTaskOptions() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                quest-id: command_flow
                name: Command Flow
                type: kill_mob
                mob: TestMob
                amount: 2
                auto-complete: true
                condition-commands:
                  - "cq data set %player% 5 ready"
                commands:
                  - "give %player% diamond 1"
                kether:
                  - "message old"
                """);
        List<String> warnings = new ArrayList<>();

        Quest quest = Quest.load("fallback", config, warnings);

        assertNotNull(quest);
        assertEquals(List.of("cq data set %player% 5 ready"), quest.getConditionCommands());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("auto-complete, commands, kether")));

        String questSource = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/Quest.java"));
        String managerSource = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/QuestManager.java"));
        assertFalse(questSource.contains("getCommands()"));
        assertFalse(questSource.contains("getKether()"));
        assertFalse(questSource.contains("isAutoComplete()"));
        assertFalse(managerSource.contains("quest.getCommands()"));
        assertFalse(managerSource.contains("quest.getKether()"));
    }

    @Test
    void killEventNeverCompletesTaskAutomatically() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/QuestManager.java"));
        int methodStart = source.indexOf("public void onMythicMobKill");
        int methodEnd = source.indexOf("// ---------------- 条件达成", methodStart);
        String killFlow = source.substring(methodStart, methodEnd);

        assertTrue(killFlow.contains("updateConditionState(player, quest"));
        org.junit.jupiter.api.Assertions.assertFalse(killFlow.contains("completeQuest("));
    }

    @Test
    void hotEnableAndJoinAlwaysScheduleConditionCalibration() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/CustomQuestPlugin.java"));
        String listener = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/listener/PlayerListener.java"));

        assertTrue(plugin.contains("board.update(player);\n            QuestManager.getInstance().queueConditionCheck(player);"));
        assertTrue(listener.contains("QuestManager.getInstance().queueConditionCheck(event.getPlayer())"));
        assertFalse(plugin.contains("startConditionTask();"));
        assertFalse(plugin.contains("refreshOnlineItemConditions()"));
    }

    @Test
    void npcItemOverrideCannotBypassObjectivesOrCauseDoubleDeduction() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/QuestManager.java"));
        int combinedCheck = manager.indexOf("combinedMissingItems(objectivePlan, itemPlan)");
        int deduction = manager.indexOf("applyItemRemoval(itemContents, itemPlan)");

        assertTrue(combinedCheck >= 0 && combinedCheck < deduction);
        assertEquals(1, manager.split("applyItemRemoval\\(itemContents, itemPlan\\)", -1).length - 1);
    }

    @Test
    void conditionCommandsOwnTheCustomMessageAndQuestPlaceholders() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/QuestManager.java"));

        assertTrue(manager.contains("boolean hasCustomMessage"));
        assertTrue(manager.contains("if (!hasCustomMessage)"));
        assertTrue(manager.contains("\"message \""));
        assertTrue(manager.contains("%quest_name%"));
        assertTrue(manager.contains("%quest_id%"));
    }

    @Test
    void itemConditionTriggersBeforeNpcDeductionAndDuringInventoryCalibration() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/quest/QuestManager.java"));
        int conditionCheck = manager.indexOf("updateConditionState(player, quest, acceptedProgress)");
        int applyItems = manager.indexOf("applyItemRemoval(itemContents, itemPlan)");
        int completed = manager.indexOf("data.getCompleted().put(quest.getId()", applyItems);

        assertTrue(conditionCheck >= 0 && conditionCheck < applyItems && applyItems < completed);
        assertTrue(manager.contains("quest.getType() == QuestType.SUBMIT_ITEM"));
        assertTrue(manager.contains("quest.getType() == QuestType.KILL_MOB"));
        assertTrue(manager.contains("quest.getType() == QuestType.KILL_MOB"));
        assertFalse(manager.contains("物品任务只在 NPC 成功校验并扣物后触发一次达成动作"));
    }
}
