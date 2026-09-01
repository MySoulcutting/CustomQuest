package com.cj.customquest.dialogue;

import com.cj.customquest.quest.QuestObjective;

import java.util.List;

/**
 * 对话选项（可点击内容，点击后执行任务接取、任务提交或 Kether 动作）。
 */
public final class DialogueOption {

    /** YAML options 下的稳定键，同时作为客户端回传的安全选项 ID。 */
    private final String id;
    /** 选项文本（支持 & 颜色与 PAPI） */
    private final String text;
    /** 接取任务快捷指令：任务 ID（null 表示不接取） */
    private final String acceptQuest;
    /** 接取成功后设置的 NPC data 变量（"key=value" 列表） */
    private final List<String> acceptData;
    /** 提交任务快捷指令：任务 ID（null 表示不提交） */
    private final String submitQuest;
    /** NPC 选项覆盖的实际提交物品；空列表表示沿用任务目标 */
    private final List<QuestObjective> submitItems;
    /** 点击后执行的 Kether 脚本（配置接取/提交任务时仅在对应操作成功后执行） */
    private final List<String> kether;
    /** 点击后是否关闭对话（预留） */
    private final boolean close;

    public DialogueOption(String id, String text, String acceptQuest, List<String> acceptData,
                          String submitQuest, List<QuestObjective> submitItems, List<String> kether, boolean close) {
        this.id = id;
        this.text = text;
        this.acceptQuest = acceptQuest;
        this.acceptData = acceptData;
        this.submitQuest = submitQuest;
        this.submitItems = List.copyOf(submitItems);
        this.kether = kether;
        this.close = close;
    }

    /** 兼容未使用提交快捷字段的旧调用方。 */
    public DialogueOption(String id, String text, String acceptQuest, List<String> acceptData,
                          List<String> kether, boolean close) {
        this(id, text, acceptQuest, acceptData, null, List.of(), kether, close);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getAcceptQuest() {
        return acceptQuest;
    }

    public List<String> getAcceptData() {
        return acceptData;
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
