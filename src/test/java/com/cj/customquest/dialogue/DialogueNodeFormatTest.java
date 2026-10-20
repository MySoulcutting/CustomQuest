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
                        quest accept example_kill
                        npc data set 5 1
                        goto suxing2
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
        assertTrue(dialogue.isNodeFormat());
        assertTrue(dialogue.isEntryBranch("suxing1"));
        assertTrue(dialogue.isEntryBranch("suxing2"));
        assertEquals("Next", first.getOptions().getFirst().getText());
        assertEquals("option_1", first.getOptions().getFirst().getId());
        assertEquals(java.util.List.of("quest accept example_kill", "npc data set 5 1", "goto suxing2"),
                first.getOptions().getFirst().getKether());

        DialogueBranch second = dialogue.getBranches().get(1);
        assertEquals("option_1", second.getOptions().getFirst().getId());
        assertEquals(java.util.List.of("5== 1"), second.getDataConditions());
        assertEquals(java.util.List.of("%player_level% >= 10"), second.getPapiConditions());
    }

    @Test
    void keepsGotoOnlyNodesOutOfAutomaticEntryMatching() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when:
                  - if: "check profile data 20 == null2"
                    open: suxing1
                suxing1:
                  npc: First
                suxing2:
                  npc: Second
                """);

        DialogueConfig dialogue = DialogueConfig.load("node.yml", config);

        assertTrue(dialogue.isNodeFormat());
        assertTrue(dialogue.isEntryBranch("suxing1"));
        assertTrue(!dialogue.isEntryBranch("suxing2"));
    }

    @Test
    void allowsExplicitUnconditionalEntryAndIgnoresUnknownSections() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when:
                  - open: suxing1
                metadata:
                  author: test
                suxing1:
                  npc: Welcome
                """);

        DialogueConfig dialogue = DialogueConfig.load("node.yml", config);

        assertEquals(java.util.List.of("suxing1"),
                dialogue.getBranches().stream().map(DialogueBranch::getId).toList());
        assertTrue(dialogue.isEntryBranch("suxing1"));
    }

    @Test
    void treatsEmptyWhenAsLegacyConfiguration() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when: []
                branches:
                  first:
                    data: 20
                    lines: Welcome
                """);

        DialogueConfig dialogue = DialogueConfig.load("legacy.yml", config);

        assertTrue(!dialogue.isNodeFormat());
        assertEquals(java.util.List.of("first"),
                dialogue.getBranches().stream().map(DialogueBranch::getId).toList());
    }

    @Test
    void legacyGotoDefaultsToKeepingTheDialogueOpen() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                branches:
                  first:
                    lines: Hello
                    options:
                      next:
                        text: Next
                        kether:
                          - goto second
                  second:
                    lines: Done
                """);

        DialogueConfig dialogue = DialogueConfig.load("legacy.yml", config);

        assertTrue(!dialogue.getBranches().getFirst().getOptions().getFirst().isClose());
    }

    @Test
    void warnsWhenWhenOpenIsDuplicatedAndKeepsFirstCondition() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when:
                  - if: "check profile data 20 == null"
                    open: first
                  - if: "check profile data 20 == 1"
                    open: first
                first:
                  npc: Welcome
                """);

        DialogueConfig dialogue = DialogueConfig.load("duplicate.yml", config);

        assertEquals(java.util.List.of("20 == null"),
                dialogue.getBranches().getFirst().getDataConditions());
        assertTrue(dialogue.getWarnings().stream()
                .anyMatch(message -> message.contains("when.open 重复") && message.contains("first")));
    }

    @Test
    void warnsWhenEntryBranchDoesNotExist() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when:
                  - open: missing
                first:
                  npc: Welcome
                """);

        DialogueConfig dialogue = DialogueConfig.load("missing-entry.yml", config);

        assertTrue(dialogue.getWarnings().stream()
                .anyMatch(message -> message.contains("入口分支不存在") && message.contains("missing")));
        assertTrue(dialogue.getBranches().stream().noneMatch(branch -> branch.getId().equals("missing")));
    }

    @Test
    void warnsWhenGotoTargetDoesNotExist() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                title: Test
                npc id: 20
                when:
                  - open: first
                first:
                  npc: Welcome
                  player:
                    - reply: Next
                      then: |
                        goto missing
                """);

        DialogueConfig dialogue = DialogueConfig.load("missing-goto.yml", config);

        assertTrue(dialogue.getWarnings().stream()
                .anyMatch(message -> message.contains("跳转目标不存在") && message.contains("missing")));
    }
}




