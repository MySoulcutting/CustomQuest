package com.cj.customquest.quest;

import org.bukkit.Material;

/**
 * 任务目标（一个任务可包含多个目标）。
 * <p>
 * 击杀类目标：{@link #getTarget()} 为 MythicMobs 怪物内部名；<br>
 * 提交类目标：{@link #getMaterial()} 为物品类型。
 * <p>
 * 均支持自定义显示名 {@link #getDisplay()}（追踪面板/提示中的文字）；
 * 提交类目标额外支持 {@link #getItemName()}（只收集指定名字的物品）。
 */
public final class QuestObjective {

    /** 目标标识：击杀类为怪物内部名，提交类为材料名 */
    private final String target;
    /** 提交类物品类型（击杀类为 null） */
    private final Material material;
    /** 需求数量 */
    private final int amount;
    /** 自定义显示名（null 时使用默认：怪物内部名 / 材料名） */
    private final String display;
    /** 收集物品的自定义名字要求（null = 任意名字；支持 & 颜色代码） */
    private final String itemName;
    /** 该目标在全息视图（计分板）上的自定义显示行格式（null = 使用全局默认格式） */
    private final String boardLine;

    private QuestObjective(String target, Material material, int amount, String display, String itemName,
                           String boardLine) {
        this.target = target;
        this.material = material;
        this.amount = amount;
        this.display = display;
        this.itemName = itemName;
        this.boardLine = boardLine;
    }

    public String getTarget() {
        return target;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    /** 显示名：自定义 name 优先，否则怪物内部名 / 材料名 */
    public String getDisplay() {
        if (display != null && !display.isEmpty()) {
            return display;
        }
        return material == null ? target : material.name();
    }

    /** 物品名字要求（null = 不限制） */
    public String getItemName() {
        return itemName;
    }

    /** 该目标在全息视图（计分板）上的自定义显示行格式（null = 使用全局默认格式） */
    public String getBoardLine() {
        return boardLine;
    }

    public boolean isKill() {
        return material == null;
    }

    /** 击杀目标（可指定自定义显示名） */
    public static QuestObjective kill(String mob, int amount, String display) {
        return kill(mob, amount, display, null);
    }

    /** 击杀目标（可指定自定义显示名与计分板行格式） */
    public static QuestObjective kill(String mob, int amount, String display, String boardLine) {
        return new QuestObjective(mob, null, amount, display, null, boardLine);
    }

    /** 提交物品目标（可指定自定义显示名与物品名字要求） */
    public static QuestObjective item(Material material, int amount, String display, String itemName) {
        return item(material, amount, display, itemName, null);
    }

    /** 提交物品目标（可指定自定义显示名、物品名字要求与计分板行格式） */
    public static QuestObjective item(Material material, int amount, String display, String itemName, String boardLine) {
        return new QuestObjective(material.name(), material, amount, display, itemName, boardLine);
    }

    /** 解析 "材料:数量" 提交目标，失败返回 null */
    public static QuestObjective parseItem(String text) {
        return parseItem(text, null, null, null);
    }

    /** 解析 "材料:数量" 提交目标（带显示名与名字要求），失败返回 null */
    public static QuestObjective parseItem(String text, String display, String itemName) {
        return parseItem(text, display, itemName, null);
    }

    /** 解析 "材料:数量" 提交目标（带显示名、名字要求与计分板行格式），失败返回 null */
    public static QuestObjective parseItem(String text, String display, String itemName, String boardLine) {
        if (text == null) return null;
        String raw = text.trim();
        String[] parts = raw.split("[:\\s]+");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (amount < 1) amount = 1;
        return item(material, amount, display, itemName, boardLine);
    }
}
