package com.cj.customquest.kether;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import taboolib.module.kether.KetherShell;
import taboolib.module.kether.ScriptOptions;

import java.util.List;
import java.util.Map;

/**
 * Kether 脚本运行器。
 */
public final class KetherRunner {

    private KetherRunner() {
    }

    /**
     * 以指定发送者执行 Kether 脚本（多行合并执行）。
     *
     * @param sender 脚本执行者（玩家/控制台）
     * @param lines  脚本行（列表或单行）
     * @param vars   注入脚本的变量（如 @NpcId、@QuestId）
     */
    public static void run(CommandSender sender, List<String> lines, Map<String, Object> vars) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        String source = String.join("\n", lines);
        runRaw(sender, source, vars);
    }

    /**
     * 执行原始 Kether 脚本。
     */
    public static void runRaw(CommandSender sender, String source, Map<String, Object> vars) {
        if (source == null || source.isBlank()) {
            return;
        }
        try {
            ScriptOptions.ScriptOptionsBuilder builder = ScriptOptions.builder()
                    .sender(sender)
                    .useCache(true)
                    .detailError(false);
            if (vars != null && !vars.isEmpty()) {
                builder.vars(vars);
            }
            KetherShell.INSTANCE.eval(source, builder.build())
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            Bukkit.getLogger().warning("[CustomQuest] Kether 脚本执行失败: " + error.getMessage());
                        }
                    });
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[CustomQuest] Kether 脚本解析失败: " + e.getMessage());
        }
    }
}
