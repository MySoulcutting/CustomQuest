package com.cj.customquest.quest;

import com.cj.customquest.dialogue.DialogueConfig;
import com.cj.customquest.dialogue.DialogueOption;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleConfigurationTest {

    @Test
    void bundledExamplesUseConditionCommandsAndAtomicNpcSubmission() {
        Quest kill = loadQuest("src/main/resources/quests/example_kill.yml");
        Quest submit = loadQuest("src/main/resources/quests/example_submit.yml");
        DialogueConfig dialogue = DialogueConfig.load("example_npc.yml",
                YamlConfiguration.loadConfiguration(new File(
                        "src/main/resources/dialogues/example_npc.yml")));

        assertEquals("cq data set %player% 5 stage kill_ready", kill.getConditionCommands().getFirst());
        assertEquals("cq data set %player% 5 stage item_ready", submit.getConditionCommands().getFirst());
        assertTrue(dialogue.getBranches().stream()
                .flatMap(branch -> branch.getOptions().stream())
                .map(DialogueOption::getSubmitQuest)
                .anyMatch("example_submit"::equals));
    }

    private static Quest loadQuest(String path) {
        File file = new File(path);
        ArrayList<String> warnings = new ArrayList<>();
        Quest quest = Quest.load(file.getName().replace(".yml", ""),
                YamlConfiguration.loadConfiguration(file), warnings);
        assertNotNull(quest);
        assertTrue(warnings.isEmpty(), () -> "unexpected warnings: " + warnings);
        return quest;
    }
}
