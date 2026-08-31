package com.cj.customquest.dialogue;

import java.util.List;

/**
 * 对话选项（可点击的聊天内容，点击后执行接取任务指令 / Kether 动作）。
 */
public final class DialogueOption {

    /** YAML options 下的稳定键，同时作为客户端回传的安全选项 ID。 */
    private final String id;
    /** 选项文本（支持 & 颜色与 PAPI） */
    private final String text;
    /** 悬浮提示 */
    private final String hover;
    /** 接取任务快捷指令：任务 ID（null 表示不接取） */
    private final String acceptQuest;
    /** 接取成功后设置的 NPC data 变量（"key=value" 列表） */
    private final List<String> acceptData;
    /** 提交任务快捷指令：任务 ID（null 表示不提交） */
    private final String submitQuest;
    /** 提交成功后设置的 NPC data 变量（"key=value" 列表） */
    private final List<String> submitData;
    /** 点击后执行的 Kether 脚本（配置接取/提交任务时仅在对应操作成功后执行） */
    private final List<String> kether;
    /** 点击后是否关闭对话（预留） */
    private final boolean close;

    public DialogueOption(String id, String text, String hover, String acceptQuest, List<String> acceptData,
                          String submitQuest, List<String> submitData, List<String> kether, boolean close) {
        this.id = id;
        this.text = text;
        this.hover = hover;
        this.acceptQuest = acceptQuest;
        this.acceptData = acceptData;
        this.submitQuest = submitQuest;
        this.submitData = submitData;
        this.kether = kether;
        this.close = close;
    }

    /** 兼容未使用提交快捷字段的旧调用方。 */
    public DialogueOption(String id, String text, String hover, String acceptQuest, List<String> acceptData,
                          List<String> kether, boolean close) {
        this(id, text, hover, acceptQuest, acceptData, null, List.of(), kether, close);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getHover() {
        return hover;
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

    public List<String> getSubmitData() {
        return submitData;
    }

    public List<String> getKether() {
        return kether;
    }

    public boolean isClose() {
        return close;
    }
}
