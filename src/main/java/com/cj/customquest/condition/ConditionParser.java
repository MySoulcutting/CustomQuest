package com.cj.customquest.condition;

import com.cj.customquest.util.TextUtil;
import org.bukkit.entity.Player;

/**
 * PAPI 条件解析器。
 * <p>
 * 支持的写法（先替换 PAPI 占位符，再比较）：
 * <ul>
 *   <li>{@code %player_level% >= 10} —— 数值比较</li>
 *   <li>{@code %var% == value} / {@code !=}</li>
 *   <li>{@code %var% > 5} / {@code <} / {@code <=}</li>
 *   <li>{@code %var% contains text} / {@code !contains text}</li>
 *   <li>{@code %var%} —— 单独占位符：值为 true/yes/1 或非空且非 false/no/0/none 时为真</li>
 *   <li>{@code !%var%} —— 取反</li>
 * </ul>
 */
public final class ConditionParser {

    private ConditionParser() {
    }

    /**
     * 判断单条条件是否成立。
     */
    public static boolean check(Player player, String condition) {
        if (condition == null || condition.isBlank()) return true;
        String raw = condition.trim();

        boolean negate = false;
        if (raw.startsWith("!")) {
            negate = true;
            raw = raw.substring(1).trim();
        }

        // 替换占位符
        String value = TextUtil.papi(player, raw);
        boolean result;

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            result = true;
        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")
                || value.equalsIgnoreCase("none") || value.isEmpty() || value.equals("0")) {
            result = false;
        } else {
            // 查找比较操作符
            String[] ops = {"!contains", "contains", ">=", "<=", "!=", "==", ">", "<"};
            result = false;
            for (String op : ops) {
                int index = value.indexOf(op);
                if (index >= 0) {
                    String left = value.substring(0, index).trim();
                    String right = value.substring(index + op.length()).trim();
                    result = compare(left, right, op);
                    break;
                }
            }
            // 没有操作符：非空即真（已在上面处理 false/no/0 等）
            if (value.indexOf(">") < 0 && value.indexOf("<") < 0 && value.indexOf("=") < 0
                    && value.indexOf("contains") < 0) {
                result = true;
            }
        }
        return negate != result;
    }

    /**
     * 批量判断，全部成立返回 true（空列表返回 true）。
     */
    public static boolean checkAll(Player player, Iterable<String> conditions) {
        if (conditions == null) return true;
        for (String condition : conditions) {
            if (!check(player, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compare(String left, String right, String op) {
        switch (op) {
            case "contains":
                return left.contains(right);
            case "!contains":
                return !left.contains(right);
            default:
                break;
        }
        // 尝试数值比较
        Double leftNum = tryNumber(left);
        Double rightNum = tryNumber(right);
        if (leftNum != null && rightNum != null) {
            switch (op) {
                case ">":
                    return leftNum > rightNum;
                case "<":
                    return leftNum < rightNum;
                case ">=":
                    return leftNum >= rightNum;
                case "<=":
                    return leftNum <= rightNum;
                case "==":
                    return leftNum.equals(rightNum);
                case "!=":
                    return !leftNum.equals(rightNum);
                default:
                    return false;
            }
        }
        // 字符串比较
        switch (op) {
            case "==":
                return left.equals(right);
            case "!=":
                return !left.equals(right);
            default:
                return false;
        }
    }

    private static Double tryNumber(String text) {
        if (text == null) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
