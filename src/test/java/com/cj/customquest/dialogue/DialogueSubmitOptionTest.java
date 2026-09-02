package com.cj.customquest.dialogue;

import org.bukkit.Material;
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
                """);

        DialogueOption option = DialogueBranch.load("ready", config).getOptions().getFirst();

        assertEquals("example_submit", option.getSubmitQuest());
        assertEquals(java.util.List.of(), option.getSubmitItems());
    }

    @Test
    void loadsStrictNpcSubmitItemsWithExactAmountsAndNames() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  submit:
                    text: Submit
                    submit-quest: example_submit
                    submit-items:
                      - "DIAMOND:5"
                      - item: "IRON_INGOT"
                        amount: 3
                        name: "&f任务铁锭"
                        item-name: "&6指定铁锭"
                """);

        DialogueOption option = DialogueBranch.load("ready", config).getOptions().getFirst();

        assertEquals(2, option.getSubmitItems().size());
        assertEquals(Material.DIAMOND, option.getSubmitItems().get(0).getMaterial());
        assertEquals(5, option.getSubmitItems().get(0).getAmount());
        assertEquals(Material.IRON_INGOT, option.getSubmitItems().get(1).getMaterial());
        assertEquals(3, option.getSubmitItems().get(1).getAmount());
        assertEquals("&f任务铁锭", option.getSubmitItems().get(1).getDisplay());
        assertEquals("&6指定铁锭", option.getSubmitItems().get(1).getItemName());
    }

    @Test
    void rejectsInvalidOrEmptyNpcSubmitItems() throws Exception {
        for (String item : java.util.List.of("UNKNOWN_MATERIAL:5", "DIAMOND:0", "DIAMOND:-1", "DIAMOND:nope")) {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString("""
                    options:
                      submit:
                        text: Submit
                        submit-quest: example_submit
                        submit-items:
                          - "%s"
                    """.formatted(item));
            assertThrows(IllegalArgumentException.class, () -> DialogueBranch.load("ready", config), item);
        }

        YamlConfiguration empty = new YamlConfiguration();
        empty.loadFromString("""
                options:
                  submit:
                    text: Submit
                    submit-quest: example_submit
                    submit-items: []
                """);
        assertThrows(IllegalArgumentException.class, () -> DialogueBranch.load("ready", empty));
    }

    @Test
    void submitItemsRequireSubmitQuest() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                options:
                  invalid:
                    text: Invalid
                    submit-items:
                      - "DIAMOND:5"
                """);

        assertThrows(IllegalArgumentException.class, () -> DialogueBranch.load("branch", config));
    }


}
