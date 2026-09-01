package com.cj.customquest.dialogue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueNodeFormatTest {

    @Test
    void loadsNodeFormatWithNpcIdWhenConditionsAndGotoActions() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: "&8[&6任务发布官&8] &fNPC"
                npc id: '5'
                when:
                  - if: "check profile data 5== null"
                    open: suxing1
                  - if: "check profile data 5== 1 , %player_level% >= 10"
                    open: suxing2
                suxing1:
                  npc:
                    - Hello
                  format: generic
                  player:
                    - reply: Next
                      then: |
                        npc data set 5 1
                        goto suxing2
                      accept-quest: example_kill
                suxing2:
                  npc: Welcome
                  player:
                    - reply: Submit
                      submit-quest: example_submit
                """);

        DialogueConfig dialogue = DialogueConfig.load("node.yml", config);

        assertEquals(java.util.List.of(5), dialogue.getNpcIds());
        assertEquals("&8[&6任务发布官&8] &fNPC", dialogue.getTitle());
        assertEquals(java.util.List.of("suxing1", "suxing2"),
                dialogue.getBranches().stream().map(DialogueBranch::getId).toList());
        DialogueBranch first = dialogue.getBranches().getFirst();
        assertEquals(java.util.List.of("5== null"), first.getDataConditions());
        assertTrue(first.getPapiConditions().isEmpty());
        assertEquals("Next", first.getOptions().getFirst().getText());
        assertEquals("option_1", first.getOptions().getFirst().getId());
        assertEquals("example_kill", first.getOptions().getFirst().getAcceptQuest());
        assertTrue(first.getOptions().getFirst().getKether().stream()
                .anyMatch(line -> line.contains("goto suxing2")));

        DialogueBranch second = dialogue.getBranches().get(1);
        assertEquals("option_1", second.getOptions().getFirst().getId());
        assertEquals(java.util.List.of("5== 1"), second.getDataConditions());
        assertEquals(java.util.List.of("%player_level% >= 10"), second.getPapiConditions());
    }
}




