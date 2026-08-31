package com.cj.customquest.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainCommandRegistrationSourceTest {

    @Test
    void removesStandaloneQuestCommandButKeepsCqQuestParameters() throws Exception {
        Path questBook = Path.of("src/main/java/com/cj/customquest/book/QuestBook.java");
        String command = Files.readString(Path.of(
                "src/main/java/com/cj/customquest/command/MainCommand.java"));
        Matcher matcher = Pattern.compile("new CommandStructure\\(\\s*\"([^\"]+)\"")
                .matcher(command);
        List<String> roots = new ArrayList<>();
        while (matcher.find()) {
            roots.add(matcher.group(1));
        }

        assertFalse(Files.exists(questBook));
        assertEquals(List.of("cq"), roots);
        assertFalse(command.contains("QuestBook"));
        assertFalse(command.contains("onQuestCommand"));
        assertTrue(command.contains("case \"quest\" -> handleQuest(sender, args)"));
        assertTrue(command.contains("private static void handleQuest("));
        assertTrue(command.contains("private static void handleQuestNav("));
        // 用户只要求删除根 /quest；其他 /cq 参数继续保留。
        assertTrue(command.contains("case \"nav\" -> handleNav(sender, args)"));
    }

    @Test
    void removesQuestBookFromBundledConfigAndDocumentation() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        String readme = Files.readString(Path.of("README.md"));
        String wiki = Files.readString(Path.of("wiki.html"));

        assertFalse(config.contains("quest-book"));
        assertFalse(readme.contains("`/quest`"));
        assertFalse(readme.contains("quest-book"));
        assertFalse(wiki.contains("<code>/quest</code>"));
        assertFalse(wiki.contains("quest-book"));
        assertTrue(readme.contains("`/cq quest accept"));
        assertTrue(wiki.contains("/cq quest accept"));
    }
}
