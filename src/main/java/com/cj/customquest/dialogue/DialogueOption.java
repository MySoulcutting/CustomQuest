package com.cj.customquest.dialogue;

import java.util.List;

/**
 * 对话选项（可点击的聊天内容，点击后执行接取任务指令 / Kether 动作）。
 */
public final class DialogueOption {

    /** 选项文本（支持 & 颜色与 PAPI） */
    private final String text;
    /** 悬浮提示 */
    private final String hover;
    /** 接取任务快捷指令：任务 ID（null 表示不接取） */
    private final String acceptQuest;
    /** 接取成功后设置的 NPC data 变量（"key=value" 列表） */
    private final List<String> acceptData;
    /** 点击后执行的 Kether 脚本（在接取任务之后执行） */
    private final List<String> kether;
    /** 点击后是否关闭对话（预留） */
    private final boolean close;

    public DialogueOption(String text, String hover, String acceptQuest, List<String> acceptData,
                          List<String> kether, boolean close) {
        this.text = text;
        this.hover = hover;
        this.acceptQuest = acceptQuest;
        this.acceptData = acceptData;
        this.kether = kether;
        this.close = close;
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

    public List<String> getKether() {
        return kether;
    }

    public boolean isClose() {
        return close;
    }
}
