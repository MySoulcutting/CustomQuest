package com.cj.customquest.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogueSessionStoreTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PLAYER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID FIRST_SESSION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_SESSION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void keepsOnlyOneSessionPerPlayerAndConsumesItOnce() {
        AtomicLong now = new AtomicLong(100L);
        AtomicInteger ids = new AtomicInteger();
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L,
                now::get,
                () -> ids.getAndIncrement() == 0 ? FIRST_SESSION : SECOND_SESSION
        );

        store.open(PLAYER, 5, "npc.yml", "initial", List.of("accept"));
        DialogueSessionStore.Session current = store.open(
                PLAYER, 5, "npc.yml", "doing", List.of("submit"));

        assertEquals(1, store.size());
        assertEquals(SECOND_SESSION, current.id());
        assertNull(store.consume(PLAYER, FIRST_SESSION, "accept"));
        assertNull(store.consume(PLAYER, SECOND_SESSION, "missing"));

        DialogueSessionStore.Session consumed = store.consume(PLAYER, SECOND_SESSION, "submit");
        assertNotNull(consumed);
        assertEquals("doing", consumed.branchId());
        assertEquals(0, store.size());
        assertNull(store.consume(PLAYER, SECOND_SESSION, "submit"));
    }

    @Test
    void bindsSessionsToPlayersAndExpiresThem() {
        AtomicLong now = new AtomicLong(100L);
        DialogueSessionStore store = new DialogueSessionStore(
                50L, now::get, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("accept"));

        assertNull(store.consume(OTHER_PLAYER, FIRST_SESSION, "accept"));
        assertEquals(1, store.size());

        now.set(150L);
        assertNull(store.consume(PLAYER, FIRST_SESSION, "accept"));
        assertEquals(0, store.size());
    }

    @Test
    void dismissesOnlyTheMatchingCurrentSession() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("accept"));

        assertNull(store.dismiss(PLAYER, SECOND_SESSION));
        assertEquals(1, store.size());
        assertNotNull(store.dismiss(PLAYER, FIRST_SESSION));
        assertEquals(0, store.size());
    }

    @Test
    void updatesAConsumedSessionWithoutChangingItsId() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        DialogueSessionStore.Session initial = store.open(
                PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");

        DialogueSessionStore.Session updated = store.update(
                consumed, "next_branch", List.of("finish"));

        assertNotNull(updated);
        assertEquals(initial.id(), updated.id());
        assertEquals("next_branch", updated.branchId());
        assertEquals(1, store.size());
        assertNotNull(store.consume(PLAYER, FIRST_SESSION, "finish"));
    }

    @Test
    void doesNotUpdateAnExpiredSession() {
        AtomicLong now = new AtomicLong(100L);
        DialogueSessionStore store = new DialogueSessionStore(
                50L, now::get, () -> FIRST_SESSION);
        DialogueSessionStore.Session initial = store.open(
                PLAYER, 5, "npc.yml", "initial", List.of("next"));

        now.set(150L);

        assertNull(store.update(initial, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }

    @Test
    void doesNotResurrectAReplacedSession() {
        AtomicInteger ids = new AtomicInteger();
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L,
                () -> ids.getAndIncrement() == 0 ? FIRST_SESSION : SECOND_SESSION);
        DialogueSessionStore.Session initial = store.open(
                PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session replacement = store.open(
                PLAYER, 5, "npc.yml", "replacement", List.of("other"));

        assertNull(store.update(initial, "next_branch", List.of("finish")));
        assertEquals(replacement, store.consume(PLAYER, SECOND_SESSION, "other"));
    }

    @Test
    void pendingUpdateRequiresAnActiveTransition() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        DialogueSessionStore.Session initial = store.open(
                PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");

        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));

        store.beginTransition(consumed);
        DialogueSessionStore.Session updated = store.updatePending(
                consumed, "next_branch", List.of("finish"));
        assertNotNull(updated);
        assertEquals(initial.id(), updated.id());
        assertEquals("next_branch", updated.branchId());
    }

    @Test
    void duplicateSubmissionDoesNotCancelThePendingTransition() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");
        assertNotNull(consumed);
        store.beginTransition(consumed);

        // 同一次点击经两条通道重复到达：第二次必须无副作用，尤其不能清掉待跳转标记，
        // 否则异步跳转回来时 updatePending 会失败并把已经打开的界面关掉。
        assertNull(store.consume(PLAYER, FIRST_SESSION, "next"));

        assertNotNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        assertEquals(1, store.size());
    }

    @Test
    void dismissDuringConsumedPendingTransitionPreventsResurrectionWhenCurrentIsEmpty() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");
        assertNotNull(consumed);
        store.beginTransition(consumed);
        assertEquals(0, store.size());

        assertNull(store.dismiss(PLAYER, FIRST_SESSION));

        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }

    @Test
    void removingPlayerWithOnlyPendingTransitionPreventsLateRestore() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");
        store.beginTransition(consumed);

        assertNull(store.remove(PLAYER));

        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }

    @Test
    void reloadClearInvalidatesPendingTransitionsForAllPlayers() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        store.open(OTHER_PLAYER, 6, "other.yml", "initial", List.of("next"));
        DialogueSessionStore.Session first = store.consume(PLAYER, FIRST_SESSION, "next");
        DialogueSessionStore.Session other = store.consume(OTHER_PLAYER, FIRST_SESSION, "next");
        store.beginTransition(first);
        store.beginTransition(other);

        store.clear();

        assertNull(store.updatePending(first, "next_branch", List.of("finish")));
        assertNull(store.updatePending(other, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }

    @Test
    void oldPendingCallbackAndDismissCannotRemoveReplacementBusiness() {
        AtomicInteger ids = new AtomicInteger();
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L,
                () -> ids.getAndIncrement() == 0 ? FIRST_SESSION : SECOND_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session old = store.consume(PLAYER, FIRST_SESSION, "next");
        store.beginTransition(old);
        DialogueSessionStore.Session replacement = store.open(
                PLAYER, 7, "replacement.yml", "new_branch", List.of("new_option"));

        assertNull(store.updatePending(old, "old_callback", List.of("old_finish")));
        assertNull(store.dismiss(PLAYER, FIRST_SESSION));
        assertEquals(1, store.size());
        assertEquals(replacement, store.consume(PLAYER, SECOND_SESSION, "new_option"));
    }

    @Test
    void oldPendingCallbackAndDismissCannotCancelNewPendingTransition() {
        AtomicInteger ids = new AtomicInteger();
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L,
                () -> ids.getAndIncrement() == 0 ? FIRST_SESSION : SECOND_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session old = store.consume(PLAYER, FIRST_SESSION, "next");
        store.beginTransition(old);
        store.open(PLAYER, 7, "replacement.yml", "new_branch", List.of("new_option"));
        DialogueSessionStore.Session current = store.consume(PLAYER, SECOND_SESSION, "new_option");
        store.beginTransition(current);

        assertNull(store.updatePending(old, "old_callback", List.of("old_finish")));
        assertNull(store.dismiss(PLAYER, FIRST_SESSION));
        store.cancelTransition(PLAYER, FIRST_SESSION);
        DialogueSessionStore.Session restored = store.updatePending(current, "new_callback", List.of("finish"));

        assertNotNull(restored);
        assertEquals(SECOND_SESSION, restored.id());
        assertEquals("new_callback", restored.branchId());
        assertEquals(restored, store.consume(PLAYER, SECOND_SESSION, "finish"));
    }

    @Test
    void pendingTransitionExpiresAtExactDeadlineAndCannotRestoreLater() {
        AtomicLong now = new AtomicLong(100L);
        DialogueSessionStore store = new DialogueSessionStore(50L, now::get, () -> FIRST_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session consumed = store.consume(PLAYER, FIRST_SESSION, "next");
        store.beginTransition(consumed);

        now.set(150L);
        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        now.set(151L);
        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }

    @Test
    void expiredOldPendingCallbackDoesNotRemoveNewSession() {
        AtomicLong now = new AtomicLong(100L);
        AtomicInteger ids = new AtomicInteger();
        DialogueSessionStore store = new DialogueSessionStore(
                50L, now::get,
                () -> ids.getAndIncrement() == 0 ? FIRST_SESSION : SECOND_SESSION);
        store.open(PLAYER, 5, "npc.yml", "initial", List.of("next"));
        DialogueSessionStore.Session old = store.consume(PLAYER, FIRST_SESSION, "next");
        store.beginTransition(old);
        now.set(149L);
        DialogueSessionStore.Session replacement = store.open(
                PLAYER, 7, "replacement.yml", "new_branch", List.of("new_option"));

        now.set(150L);
        assertNull(store.updatePending(old, "old_callback", List.of("old_finish")));
        assertNull(store.dismiss(PLAYER, FIRST_SESSION));
        assertEquals(replacement, store.consume(PLAYER, SECOND_SESSION, "new_option"));
    }

    @Test
    void cancellingPendingTransitionPreventsResurrection() {
        DialogueSessionStore store = new DialogueSessionStore(
                1_000L, () -> 100L, () -> FIRST_SESSION);
        DialogueSessionStore.Session consumed = store.consume(
                PLAYER,
                store.open(PLAYER, 5, "npc.yml", "initial", List.of("next")).id(),
                "next");

        store.beginTransition(consumed);
        store.cancelTransition(PLAYER, FIRST_SESSION);

        assertNull(store.updatePending(consumed, "next_branch", List.of("finish")));
        assertEquals(0, store.size());
    }
}
