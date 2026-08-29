package com.cj.customquest.dialogue;

import com.cj.customquest.condition.ConditionParser;
import com.cj.customquest.hook.CitizensHook;
import com.cj.customquest.kether.KetherRunner;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import com.cj.customquest.util.Messages;
import com.cj.customquest.util.TextUtil;
import net.citizensnpcs.api.npc.NPC;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC 对话管理：加载对话配置、分支选择与展示、点击动作执行。
 */
public final class DialogueManager {

    /** 点击对话选项时执行的内部指令 */
    public static final String CLICK_COMMAND = "cq";

    private static DialogueManager instance;

    private File dialoguesFolder;
    private final Map<Integer, List<DialogueConfig>> byNpc = new HashMap<>();

    public static DialogueManager getInstance() {
        return instance;
    }

    public static void create(File dataFolder) {
        instance = new DialogueManager();
        instance.dialoguesFolder = new File(dataFolder, "dialogues");
        instance.reload();
    }

    public void reload() {
        byNpc.clear();
        dialoguesFolder.mkdirs();
        File[] files = dialoguesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                DialogueConfig dialogue = DialogueConfig.load(file.getName(), config);
                if (dialogue.getNpcIds().isEmpty()) {
                    Bukkit.getLogger().warning("[CustomQuest] 对话文件 " + file.getName() + " 未配置 npc，已忽略。");
                    continue;
                }
                for (int npcId : dialogue.getNpcIds()) {
                    byNpc.computeIfAbsent(npcId, k -> new ArrayList<>()).add(dialogue);
                }
            } catch (Throwable e) {
                Bukkit.getLogger().warning("[CustomQuest] 对话文件 " + file.getName() + " 加载失败: " + e.getMessage());
            }
        }
    }

    public File getDialoguesFolder() {
        return dialoguesFolder;
    }

    /**
     * 玩家点击 NPC 时打开对话。
     *
     * @param forceBranchId 强制显示的分支 id（点击选项回调时使用），可为 null
     */
    public void openDialogue(Player player, int npcId, String forceBranchId) {
        if (!CitizensHook.isEnabled()) {
            Messages.sendTo(player, "dialogue-no-config");
            return;
        }
        List<DialogueConfig> configs = byNpc.get(npcId);
        if (configs == null || configs.isEmpty()) {
            return; // 无对话配置时不打扰玩家
        }
        NPC npc = CitizensHook.getNpc(npcId);

        // 首次对话时初始化玩家级 data 变量（default-data）
        for (DialogueConfig config : configs) {
            for (Map.Entry<String, String> entry : config.getDefaultData().entrySet()) {
                if (!CitizensHook.hasData(player, npc, entry.getKey())) {
                    CitizensHook.setData(player, npc, entry.getKey(), entry.getValue());
                }
            }
        }

        // 查找第一个满足条件的分支
        DialogueConfig matched = null;
        DialogueBranch branch = null;
        outer:
        for (DialogueConfig config : configs) {
            for (DialogueBranch candidate : config.getBranches()) {
                if (forceBranchId != null && !candidate.getId().equalsIgnoreCase(forceBranchId)) {
                    continue;
                }
                if (candidate.isDefaultBranch()) {
                    continue; // default 分支放在最后兜底
                }
                if (matches(player, npc, candidate)) {
                    matched = config;
                    branch = candidate;
                    break outer;
                }
            }
        }
        if (branch == null && forceBranchId == null) {
            outer2:
            for (DialogueConfig config : configs) {
                for (DialogueBranch candidate : config.getBranches()) {
                    if (candidate.isDefaultBranch() || !candidate.hasConditions()) {
                        matched = config;
                        branch = candidate;
                        break outer2;
                    }
                }
            }
        }
        if (branch == null) {
            Messages.sendTo(player, "dialogue-no-config");
            return;
        }

        display(player, matched, branch, npcId);
    }

    private boolean matches(Player player, NPC npc, DialogueBranch branch) {
        if (!CitizensHook.checkDataConditions(player, npc, branch.getDataConditions())) {
            return false;
        }
        return ConditionParser.checkAll(player, branch.getPapiConditions());
    }

    private void display(Player player, DialogueConfig config, DialogueBranch branch, int npcId) {
        // 标题
        if (config.getTitle() != null && !config.getTitle().isEmpty()) {
            player.sendMessage(TextUtil.parse(player, config.getTitle()));
        }
        // 对话内容
        for (String line : branch.getLines()) {
            player.sendMessage(TextUtil.parse(player, line));
        }
        // 选项
        for (int i = 0; i < branch.getOptions().size(); i++) {
            DialogueOption option = branch.getOptions().get(i);
            TextComponent component = new TextComponent(TextComponent.fromLegacyText(TextUtil.parse(player, option.getText())));
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/" + CLICK_COMMAND + " click " + npcId + " " + branch.getId() + " " + i));
            if (option.getHover() != null && !option.getHover().isEmpty()) {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new Text(TextComponent.fromLegacyText(TextUtil.parse(player, option.getHover())))));
            }
            player.spigot().sendMessage(component);
        }
    }

    /**
     * 处理选项点击（/cq click <npcId> <branchId> <optionIndex>）。
     */
    public void onOptionClick(Player player, int npcId, String branchId, int optionIndex) {
        List<DialogueConfig> configs = byNpc.get(npcId);
        if (configs == null || configs.isEmpty()) {
            return;
        }
        NPC npc = CitizensHook.getNpc(npcId);
        for (DialogueConfig config : configs) {
            for (DialogueBranch branch : config.getBranches()) {
                if (!branch.getId().equalsIgnoreCase(branchId)) {
                    continue;
                }
                // 再次校验分支条件，防止玩家直接执行指令
                if (branch.hasConditions() && !matches(player, npc, branch)) {
                    return;
                }
                if (optionIndex < 0 || optionIndex >= branch.getOptions().size()) {
                    return;
                }
                DialogueOption option = branch.getOptions().get(optionIndex);

                // 1. 接取任务快捷指令（accept-quest，不校验前置条件）
                if (option.getAcceptQuest() != null && !option.getAcceptQuest().isEmpty()) {
                    Quest quest = QuestManager.getInstance().getQuest(option.getAcceptQuest());
                    if (quest != null && QuestManager.getInstance().acceptQuest(player, quest)) {
                        // 接取成功后设置该玩家在此 NPC 上的 data 变量（推进分支对话）
                        for (String entry : option.getAcceptData()) {
                            String[] kv = entry.split("=", 2);
                            if (kv.length == 2 && npc != null) {
                                String key = kv[0].trim();
                                String value = kv[1].trim().replaceFirst("^=+", "");
                                CitizensHook.setData(player, npc, key, value);
                            }
                        }
                    }
                }

                // 2. 点击动作（Kether）
                if (!option.getKether().isEmpty()) {
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("@NpcId", npcId);
                    vars.put("@BranchId", branchId);
                    vars.put("@Option", optionIndex);
                    vars.put("@Player", player);
                    KetherRunner.run(player, option.getKether(), vars);
                }
                return;
            }
        }
    }
}
