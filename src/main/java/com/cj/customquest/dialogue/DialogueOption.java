package com.cj.customquest.dialogue;

import com.cj.customquest.quest.QuestObjective;

import java.util.List;

/**
 * 对话选项（可点击内容，点击后执行任务提交或 Kether 动作）。
 */
public final class DialogueOption {

    /** YAML options 下的稳定键，同时作为客户端回传的安全选项 ID。 */
    private final String id;
    /** 选项文本（支持 & 颜色与 PAPI） */
    private final String text;
    /** 提交任务快捷指令：任务 ID（null 表示不提交） */
    private final String submitQuest;
    /** NPC 选项覆盖的实际提交物品；空列表表示沿用任务目标 */
    private final List<QuestObjective> submitItems;
    /** 点击后执行的 Kether 脚本（提交任务时仅在操作成功后执行） */
    private final List<String> kether;
    /** 点击后是否关闭对话（预留） */
    private final boolean close;

    public DialogueOption(String id, String text,
                          String submitQuest, List<QuestObjective> submitItems,
                          List<String> kether, boolean close) {
        this.id = id;
        this.text = text;
        this.submitQuest = submitQuest;
        this.submitItems = List.copyOf(submitItems);
        this.kether = List.copyOf(kether);
        this.close = close;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getSubmitQuest() {
        return submitQuest;
    }

    public List<QuestObjective> getSubmitItems() {
        return submitItems;
    }

    public List<String> getKether() {
        return kether;
    }

    public boolean isClose() {
        return close;
    }
}
