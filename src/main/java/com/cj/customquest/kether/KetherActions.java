package com.cj.customquest.kether;

import com.cj.customquest.dialogue.DialogueManager;
import com.cj.customquest.dialogue.DialogueSessionStore;
import com.cj.customquest.hook.CitizensHook;
import com.cj.customquest.quest.Quest;
import com.cj.customquest.quest.QuestManager;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import taboolib.common.platform.ProxyCommandSender;
import taboolib.library.kether.QuestAction;
import taboolib.library.kether.QuestActionParser;
import taboolib.library.kether.QuestContext;
import taboolib.module.kether.Kether;
import taboolib.module.kether.ScriptContext;

import java.util.Optional;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * CustomQuest 自定义 Kether 动作注册。
 * <p>
 * 可用动作：
 * <ul>
 *   <li>{@code quest accept <questId>} —— 接取任务（不校验前置条件）</li>
 *   <li>{@code quest abandon <questId>} —— 放弃任务</li>
 *   <li>{@code quest submit <questId>} —— 提交任务进度（进度足够则完成）</li>
 *   <li>{@code quest complete <questId>} —— 强制完成任务</li>
 *   <li>{@code quest progress <questId>} —— 返回当前进度（数字）</li>
 *   <li>{@code quest has <questId>} —— 是否已接取（布尔）</li>
 *   <li>{@code quest done <questId>} —— 是否已完成（布尔）</li>
 *   <li>{@code dialogue open [npcId]} —— 打开 NPC 对话（缺省使用当前 NPC）</li>
 *   <li>{@code npc data set <npcId> <value>} —— 设置指定 NPC 的唯一变量值</li>
 *   <li>{@code npc data get <npcId>} —— 获取指定 NPC 的唯一变量值</li>
 *   <li>{@code npc data remove <npcId>} —— 删除指定 NPC 的唯一变量值</li>
 * </ul>
 */
public final class KetherActions {

    private static boolean registered = false;

    private KetherActions() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        var registry = Kether.INSTANCE.getScriptRegistry();

        registry.registerAction("quest", questActionParser());
        registry.registerAction("questaccept", acceptAliasParser());
        registry.registerAction("quest-accept", acceptAliasParser());
        registry.registerAction("questabandon", simpleQuest(SimpleQuestType.ABANDON));
        registry.registerAction("quest-abandon", simpleQuest(SimpleQuestType.ABANDON));
        registry.registerAction("questsubmit", simpleQuest(SimpleQuestType.SUBMIT));
        registry.registerAction("quest-submit", simpleQuest(SimpleQuestType.SUBMIT));
        registry.registerAction("questcomplete", simpleQuest(SimpleQuestType.COMPLETE));
        registry.registerAction("quest-complete", simpleQuest(SimpleQuestType.COMPLETE));
        registry.registerAction("dialogue", dialogueParser());
        registry.registerAction("dialogueopen", dialogueOpenParser());
        registry.registerAction("dialogue-open", dialogueOpenParser());
        registry.registerAction("goto", dialogueGotoParser());
        registry.registerAction("close", dialogueCloseParser());
        registry.registerAction("npc", npcDataParser());
    }

    // ---------------- quest <accept|abandon|submit|complete|progress|has|done> <questId> ----------------

    private enum SimpleQuestType {
        ABANDON, SUBMIT, COMPLETE
    }

    private static QuestActionParser questActionParser() {
        return QuestActionParser.of(reader -> {
            if (!reader.hasNext()) {
                return QuestAction.noop();
            }
            String sub = reader.nextToken();
            switch (sub.toLowerCase(Locale.ROOT)) {
                case "accept": {
                    String questId = reader.hasNext() ? reader.nextToken() : "";
                    return acceptAction(questId);
                }
                case "abandon":
                case "quit":
                    return questAction(SimpleQuestType.ABANDON, reader.hasNext() ? reader.nextToken() : "");
                case "submit":
                    return questAction(SimpleQuestType.SUBMIT, reader.hasNext() ? reader.nextToken() : "");
                case "complete":
                    return questAction(SimpleQuestType.COMPLETE, reader.hasNext() ? reader.nextToken() : "");
                case "progress": {
                    String questId = reader.hasNext() ? reader.nextToken() : "";
                    return questValue(frame -> {
                        Player player = playerOf(frame);
                        if (player == null) return 0;
                        return QuestManager.getInstance().getProgress(player, questId);
                    });
                }
                case "has": {
                    String questId = reader.hasNext() ? reader.nextToken() : "";
                    return questValue(frame -> {
                        Player player = playerOf(frame);
                        return player != null && QuestManager.getInstance().isAccepted(player, questId);
                    });
                }
                case "done": {
                    String questId = reader.hasNext() ? reader.nextToken() : "";
                    return questValue(frame -> {
                        Player player = playerOf(frame);
                        return player != null && QuestManager.getInstance().isCompleted(player, questId);
                    });
                }
                default:
                    return QuestAction.noop();
            }
        });
    }

    /** questaccept / quest-accept 别名：与 quest accept 同语法 */
    private static QuestActionParser acceptAliasParser() {
        return QuestActionParser.of(reader -> {
            String questId = reader.hasNext() ? reader.nextToken() : "";
            return acceptAction(questId);
        });
    }

    private static QuestActionParser simpleQuest(SimpleQuestType type) {
        return QuestActionParser.of(reader -> questAction(
                type, reader.hasNext() ? reader.nextToken() : ""));
    }

    /**
     * 接取任务动作：不校验前置条件。
     */
    private static QuestAction<Object> acceptAction(String questId) {
        return new QuestAction<>() {
            @Override
            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                Player player = playerOf(frame);
                if (player == null || questId == null || questId.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                Quest quest = QuestManager.getInstance().getQuest(questId);
                if (quest == null) {
                    return CompletableFuture.completedFuture(null);
                }
                QuestManager.getInstance().acceptQuest(player, quest);
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static QuestAction<Object> questAction(SimpleQuestType type, String questId) {
        return new QuestAction<>() {
            @Override
            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                Player player = playerOf(frame);
                if (player == null || questId == null || questId.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                Quest quest = QuestManager.getInstance().getQuest(questId);
                if (quest == null) {
                    return CompletableFuture.completedFuture(null);
                }
                switch (type) {
                    case ABANDON -> QuestManager.getInstance().abandonQuest(player, quest);
                    case SUBMIT -> QuestManager.getInstance().submitQuest(player, quest);
                    case COMPLETE -> QuestManager.getInstance().completeQuest(player, quest, true);
                }
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    // ---------------- 值动作 ----------------

    private interface FrameValue {
        Object get(QuestContext.Frame frame);
    }

    private static QuestAction<Object> questValue(FrameValue supplier) {
        return new QuestAction<>() {
            @Override
            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                return CompletableFuture.completedFuture(supplier.get(frame));
            }
        };
    }

    // ---------------- dialogue open [npcId] ----------------

    private static QuestActionParser dialogueParser() {
        return QuestActionParser.of(reader -> {
            if (!reader.hasNext()) {
                return QuestAction.noop();
            }
            String sub = reader.nextToken();
            if (sub.equalsIgnoreCase("open")) {
                return dialogueOpenAction(reader.hasNext() ? reader.nextToken() : null);
            }
            return QuestAction.noop();
        });
    }

    private static QuestActionParser dialogueOpenParser() {
        return QuestActionParser.of(reader -> dialogueOpenAction(reader.hasNext() ? reader.nextToken() : null));
    }

    private static QuestAction<Object> dialogueOpenAction(String npcIdText) {
        return new QuestAction<>() {
            @Override
            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                Player player = playerOf(frame);
                if (player == null) {
                    return CompletableFuture.completedFuture(null);
                }
                int npcId = -1;
                if (npcIdText != null && !npcIdText.isEmpty()) {
                    try {
                        npcId = Integer.parseInt(npcIdText);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (npcId < 0) {
                    npcId = varInt(frame, "@NpcId", -1);
                }
                if (npcId >= 0) {
                    DialogueManager.getInstance().openDialogue(player, npcId, null);
                }
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private static QuestActionParser dialogueGotoParser() {
        return QuestActionParser.of(reader -> {
            String branchId = reader.hasNext() ? reader.nextToken().trim() : "";
            if (branchId.length() >= 2 && branchId.startsWith("\"") && branchId.endsWith("\"")) {
                branchId = branchId.substring(1, branchId.length() - 1);
            }
            String target = branchId;
            return new QuestAction<>() {
                @Override
                public CompletableFuture<Object> process(QuestContext.Frame frame) {
                    Player player = playerOf(frame);
                    int npcId = varInt(frame, "@NpcId", -1);
                    if (player != null && npcId >= 0 && !target.isBlank()) {
                        Object sessionValue = var(frame, "@DialogueSession");
                        if (sessionValue instanceof DialogueSessionStore.Session session) {
                            DialogueManager.getInstance().gotoDialogue(player, session, target);
                        } else {
                            DialogueManager.getInstance().openDialogue(player, npcId, target);
                        }
                    }
                    return CompletableFuture.completedFuture(null);
                }
            };
        });
    }

    private static QuestActionParser dialogueCloseParser() {
        return QuestActionParser.of(reader -> new QuestAction<>() {
            @Override
            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                Player player = playerOf(frame);
                if (player != null) {
                    DialogueManager.getInstance().closeDialogue(player);
                }
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    // ---------------- npc data set/get/remove ----------------

    private static QuestActionParser npcDataParser() {
        return QuestActionParser.of(reader -> {
            if (!reader.hasNext()) {
                return QuestAction.noop();
            }
            String sub = reader.nextToken();
            if (sub.equalsIgnoreCase("data") && reader.hasNext()) {
                String op = reader.nextToken();
                switch (op.toLowerCase(Locale.ROOT)) {
                    case "set": {
                        String npcId = reader.hasNext() ? reader.nextToken() : "";
                        String value = reader.hasNext() && reader.peek() != '}'
                                ? reader.nextToken() : "";
                        return new QuestAction<Object>() {
                            @Override
                            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                                Player player = playerOf(frame);
                                NPC npc = npcById(npcId);
                                if (player != null && npc != null) {
                                    CitizensHook.setData(player, npc, value);
                                }
                                return CompletableFuture.completedFuture(null);
                            }
                        };
                    }
                    case "get": {
                        String npcId = reader.hasNext() ? reader.nextToken() : "";
                        return questValue(frame -> {
                            Player player = playerOf(frame);
                            NPC npc = npcById(npcId);
                            if (player == null || npc == null) return "";
                            String value = CitizensHook.getData(player, npc);
                            return value == null ? "" : value;
                        });
                    }
                    case "remove":
                    case "delete": {
                        String npcId = reader.hasNext() ? reader.nextToken() : "";
                        return new QuestAction<Object>() {
                            @Override
                            public CompletableFuture<Object> process(QuestContext.Frame frame) {
                                Player player = playerOf(frame);
                                NPC npc = npcById(npcId);
                                if (player != null && npc != null) {
                                    CitizensHook.removeData(player, npc);
                                }
                                return CompletableFuture.completedFuture(null);
                            }
                        };
                    }
                    default:
                        return QuestAction.noop();
                }
            }
            return QuestAction.noop();
        });
    }

    // ---------------- 上下文工具 ----------------

    /** 获取脚本执行者玩家 */
    private static Player playerOf(QuestContext.Frame frame) {
        try {
            ScriptContext context = (ScriptContext) frame.context();
            ProxyCommandSender sender = context.getSender();
            if (sender == null) return null;
            Object origin = sender.getOrigin();
            return origin instanceof Player player ? player : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static NPC npcById(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return CitizensHook.getNpc(Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Object var(QuestContext.Frame frame, String key) {
        try {
            return frame.context().rootFrame().variables().get(key).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从根变量表读取整型变量 */
    private static int varInt(QuestContext.Frame frame, String key, int def) {
        try {
            Optional<Object> optional = frame.context().rootFrame().variables().get(key);
            if (optional.isEmpty() || optional.get() == null) {
                return def;
            }
            Object value = optional.get();
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable e) {
            return def;
        }
    }
}
