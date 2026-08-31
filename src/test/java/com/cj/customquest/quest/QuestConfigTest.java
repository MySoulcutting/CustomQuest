package com.cj.customquest.quest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestConfigTest {

    @Test
    void loadsConditionCommandsSeparatelyFromCompletionRewards() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                quest-id: command_flow
                name: Command Flow
                type: kill_mob
                mob: TestMob
                amount: 2
                auto-complete: true
                condition-commands:
                  - "cq data set %player% 5 stage ready"
                commands:
                  - "give %player% diamond 1"
                """);
        List<String> warnings = new ArrayList<>();

        Quest quest = Quest.load("fallback", config, warnings);

        assertNotNull(quest);
        assertEquals(List.of("cq data set %player% 5 stage ready"), quest.getConditionCommands());
        assertEquals(List.of("give %player% diamond 1"), quest.getCommands());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("auto-complete 已停用")));
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
        assertTrue(plugin.contains("startConditionTask();"));
        assertTrue(plugin.contains("QuestManager.getInstance().refreshOnlineItemConditions()"));
    }
}
