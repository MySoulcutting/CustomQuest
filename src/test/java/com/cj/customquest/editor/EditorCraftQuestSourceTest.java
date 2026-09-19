package com.cj.customquest.editor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorCraftQuestSourceTest {

    @Test
    void editorSupportsBothEpicCraftingsQuestTypes() throws IOException {
        String editor = read("editor/index.html");
        String importer = read("editor/import.js");

        assertTrue(editor.contains("value=\"craft_item\""));
        assertTrue(editor.contains("value=\"craft_count\""));
        assertTrue(editor.contains("EpicCraftingsPlus 合成名称"));
        assertTrue(editor.contains("function defaultObjective(type)"));
        assertTrue(editor.contains("if(q.type==='craft_item')return {crafting:"));
        assertTrue(editor.contains("if(q.type==='craft_count')return {amount,"));
        assertFalse(editor.contains("if(!blank(q.boardTitle))"));
        assertFalse(editor.contains("if(!blank(q.navigate))"));

        assertTrue(importer.contains("q.type === 'craft_item'"));
        assertTrue(importer.contains("q.type === 'craft_count'"));
        assertTrue(importer.contains("item.crafting ?? item.craft ?? item['crafting-name']"));
    }

    @Test
    void wikiDocumentsCraftingTasksAndEditor() throws IOException {
        String wiki = read("wiki.html");
        String editorReadme = read("editor/README.md");

        assertTrue(wiki.contains("id=\"editor\""));
        assertTrue(wiki.contains("example_craft_item"));
        assertTrue(wiki.contains("example_craft_count"));
        assertTrue(wiki.contains("EpicCraftingsPlus 任意合成次数"));
        assertTrue(editorReadme.contains("五类任务配置"));
        assertTrue(editorReadme.contains("`craft_item`"));
        assertTrue(editorReadme.contains("`craft_count`"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
