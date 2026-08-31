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
}
