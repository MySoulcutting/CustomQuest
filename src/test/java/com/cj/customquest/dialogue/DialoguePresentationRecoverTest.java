package com.cj.customquest.dialogue;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 展示层重建（{@code recoverDisplay}）的时序验证。
 *
 * <p>页面推不进内容时靠它把界面救回来，所以这里用假容器 API 把顺序钉死，其中最要紧的一条是
 * <b>必须先把会话从展示层摘掉、再去关箱子</b>：关箱子会同步触发关闭事件回到展示层，
 * 若那一刻本会话还挂着，就会被当成"玩家自己关了界面"而结束整个对话，重建也就白做了。
 *
 * <p>只用 JDK 动态代理伪造 Player，不引入任何测试依赖。
 */
class DialoguePresentationRecoverTest {

    private final Player player = fakePlayer(UUID.randomUUID());
    private final Inventory anyInventory = stub(Inventory.class, Map.of());
    /** 展示层回调与容器操作共用的事件线，用来钉住两者之间的先后顺序。 */
    private final List<String> timeline = new ArrayList<>();
    private final RecordingHost host = new RecordingHost(timeline);
    private final FakeContainerApi containers = new FakeContainerApi(timeline);

    @Test
    void doesNothingWhenNoDialogueIsInProgress() {
        DialoguePresentation presentation = presentation();

        assertFalse(presentation.recoverDisplay(player), "没有进行中的会话时不该重建");
        assertEquals(List.of(), timeline, "不得碰容器");
    }

    @Test
    void doesNotStealTheScreenWhenThePlayerHasMovedOn() {
        DialoguePresentation presentation = presentation();
        presentation.show(player, snapshot(), false);
        timeline.clear();
        containers.owns = false;

        assertFalse(presentation.recoverDisplay(player), "玩家已经不在看本会话的箱子：不抢屏");
        assertEquals(List.of(), timeline, "不得开箱子");
        assertFalse(presentation.recoverDisplay(player), "已按会话失联收尾，再调也不该重生");
    }

    @Test
    void closesAndReopensWithoutLettingTheCloseEventEndTheDialogue() {
        DialoguePresentation presentation = presentation();
        presentation.show(player, snapshot(), false);
        timeline.clear();
        containers.onClose = () -> presentation.handleContainerClose(player, anyInventory);

        assertTrue(presentation.recoverDisplay(player), "重建应成功");
        assertEquals(List.of("close", "open"), timeline, "先关旧箱子，再开同会话的新箱子");
        assertFalse(host.dismissed,
                "关箱子那一刻必须已经摘掉会话，否则关闭事件会被当成玩家自己关了界面而结束对话");
        assertEquals(2, host.bodies, "打开时下发一次、重建后再下发一次");
    }

    @Test
    void notifiesThePageBeforeClosingItsContainer() {
        DialoguePresentation presentation = presentation();
        DialogueSnapshot.Snapshot snapshot = snapshot();
        presentation.show(player, snapshot, false);
        timeline.clear();

        presentation.close(player, snapshot.sessionId());

        assertEquals(List.of("notify", "close"), timeline,
                "必须先通知页面收尾、再关箱子：只让容器消失的话，页面模组只能靠推断，"
                        + "分不清「页面收尾」与「玩家跑去开了别的界面」");
    }

    private DialoguePresentation presentation() {
        return new DialoguePresentation(host, () -> 0L, containers);
    }

    private static DialogueSnapshot.Snapshot snapshot() {
        return new DialogueSnapshot.Snapshot(UUID.randomUUID(), "标题", List.of("正文"),
                List.of(new DialogueSnapshot.Option("a", "选项")));
    }

    /** 记录展示层之外的副作用：重建后必须重新下发内容，且绝不能把会话 dismiss 掉。 */
    private static final class RecordingHost implements DialoguePresentation.Host {
        private final List<String> timeline;
        private int bodies;
        private boolean dismissed;

        private RecordingHost(List<String> timeline) {
            this.timeline = timeline;
        }

        @Override
        public void select(Player player, UUID sessionId, String optionId) {
        }

        @Override
        public void dismiss(Player player, UUID sessionId) {
            dismissed = true;
        }

        @Override
        public void expire(Player player, UUID sessionId) {
        }

        @Override
        public void discard(UUID playerId) {
        }

        @Override
        public void displayClosing(Player player, UUID sessionId) {
            timeline.add("notify");
        }

        @Override
        public void pushBody(Player player, String body, List<String> slotNames) {
            bodies++;
        }

        @Override
        public void warning(String message) {
        }
    }

    /** 假容器 API：记录开关顺序，并可在 close 里模拟"关闭事件同步回到展示层"。 */
    private static final class FakeContainerApi implements DialoguePresentation.ContainerApi {
        private final List<String> timeline;
        private boolean owns = true;
        private Runnable onClose;

        private FakeContainerApi(List<String> timeline) {
            this.timeline = timeline;
        }

        @Override
        public void open(Player player, DialogueSnapshot.Snapshot snapshot) {
            timeline.add("open");
        }

        @Override
        public boolean refresh(Player player, DialogueSnapshot.Snapshot snapshot) {
            return true;
        }

        @Override
        public void close(Player player, UUID sessionId) {
            timeline.add("close");
            if (onClose != null) {
                onClose.run();
            }
        }

        @Override
        public boolean owns(Player player, Inventory inventory, UUID sessionId) {
            return owns;
        }

        @Override
        public boolean ownsOpenContainer(Player player, UUID sessionId) {
            return owns;
        }
    }

    /** 用动态代理伪造接口：只给测试真正用到的几个方法填值，其余按返回类型给默认值。 */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Map<String, Object> overrides) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Stub";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    Object value = overrides.get(method.getName());
                    if (value != null) {
                        return value;
                    }
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) {
                        return false;
                    }
                    if (result == UUID.class) {
                        return UUID.randomUUID();
                    }
                    if (result.isInterface()) {
                        return stub(result, Map.of());
                    }
                    return null;
                });
    }

    private static Player fakePlayer(UUID uuid) {
        World world = stub(World.class, Map.of("getUID", UUID.randomUUID()));
        return stub(Player.class, Map.of(
                "getUniqueId", uuid,
                "getWorld", world,
                "isOnline", true,
                "getName", "Tester"));
    }
}
