package com.cj.customquest.dialogue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogueSubmitOptionTest {

    @Test
    void loadsAtomicSubmitShortcutAndData() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  submit:
                    text: Submit
                    submit-quest: example_submit
                    submit-data:
                      - stage=done
                      - reward=claimed
                """);

        DialogueOption option = DialogueBranch.load("ready", config).getOptions().getFirst();

        assertEquals("example_submit", option.getSubmitQuest());
        assertEquals(java.util.List.of("stage=done", "reward=claimed"), option.getSubmitData());
    }

    @Test
    void rejectsAmbiguousAcceptAndSubmitOption() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  invalid:
                    text: Invalid
                    accept-quest: first
                    submit-quest: second
                """);

        assertThrows(IllegalArgumentException.class, () -> DialogueBranch.load("branch", config));
    }

    @Test
    void blankAcceptQuestDoesNotBlockSubmitShortcut() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  submit:
                    text: Submit
                    accept-quest: "   "
                    submit-quest: example_submit
                """);

        DialogueOption option = DialogueBranch.load("branch", config).getOptions().getFirst();

        org.junit.jupiter.api.Assertions.assertNull(option.getAcceptQuest());
        assertEquals("example_submit", option.getSubmitQuest());
    }
}
