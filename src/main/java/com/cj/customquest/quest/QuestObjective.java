package com.cj.customquest.quest;

import org.bukkit.Material;

/** 任务目标（一个任务可包含多个目标）。 */
public final class QuestObjective {
    private final String target;
    private final Material material;
    private final String neigeItemId;
    private final int amount;
    private final String display;
    private final String itemName;
    private final String boardLine;

    private QuestObjective(String target, Material material, String neigeItemId, int amount,
                           String display, String itemName, String boardLine) {
        this.target = target;
        this.material = material;
        this.neigeItemId = neigeItemId;
        this.amount = amount;
        this.display = display;
        this.itemName = itemName;
        this.boardLine = boardLine;
    }

    public String getTarget() { return target; }
    public Material getMaterial() { return material; }
    public String getNeigeItemId() { return neigeItemId; }
    public int getAmount() { return amount; }

    public String getDisplay() {
        return display != null && !display.isEmpty() ? display : target;
    }

    public String getItemName() { return itemName; }
    public String getBoardLine() { return boardLine; }
    public boolean isKill() { return material == null && neigeItemId == null; }

    public static QuestObjective kill(String mob, int amount, String display) {
        return kill(mob, amount, display, null);
    }

    public static QuestObjective kill(String mob, int amount, String display, String boardLine) {
        return new QuestObjective(mob, null, null, amount, display, null, boardLine);
    }

    public static QuestObjective item(Material material, int amount, String display, String itemName) {
        return item(material, amount, display, itemName, null);
    }

    public static QuestObjective item(Material material, int amount, String display, String itemName,
                                      String boardLine) {
        return new QuestObjective(material.name(), material, null, amount, display, itemName, boardLine);
    }

    public static QuestObjective neigeItem(String itemId, int amount, String display, String itemName) {
        return neigeItem(itemId, amount, display, itemName, null);
    }

    public static QuestObjective neigeItem(String itemId, int amount, String display, String itemName,
                                           String boardLine) {
        return new QuestObjective(itemId, null, itemId, amount, display, itemName, boardLine);
    }

    public static QuestObjective parseItem(String text) {
        return parseItem(text, null, null, null);
    }

    public static QuestObjective parseItem(String text, String display, String itemName) {
        return parseItem(text, display, itemName, null);
    }

    public static QuestObjective parseItem(String text, String display, String itemName, String boardLine) {
        if (text == null) return null;
        String raw = text.trim();
        String[] parts = raw.split(":");
        if (parts.length == 0) return null;
        if ((parts[0].equalsIgnoreCase("neige-item") || parts[0].equalsIgnoreCase("ni"))
                && parts.length >= 2) {
            int amount = 1;
            if (parts.length >= 3) {
                try { amount = Integer.parseInt(parts[2]); } catch (NumberFormatException ignored) { }
            }
            return neigeItem(parts[1].trim(), Math.max(1, amount), display, itemName, boardLine);
        }
        String[] materialParts = raw.split("[:\s]+");
        Material material = Material.matchMaterial(materialParts[0]);
        if (material == null) return null;
        int amount = 1;
        if (materialParts.length > 1) {
            try { amount = Integer.parseInt(materialParts[1]); } catch (NumberFormatException ignored) { }
        }
        return item(material, Math.max(1, amount), display, itemName, boardLine);
    }
}