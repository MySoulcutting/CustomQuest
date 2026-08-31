package com.cj.customquest.dialogue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 玩家当前任务对话的一次性会话存储。 */
public final class DialogueSessionStore {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final LongSupplier clock;
    private final Supplier<UUID> sessionIds;

    public DialogueSessionStore() {
        this(DEFAULT_TTL.toNanos(), System::nanoTime, UUID::randomUUID);
    }

    DialogueSessionStore(long ttlNanos, LongSupplier clock, Supplier<UUID> sessionIds) {
        if (ttlNanos <= 0) {
            throw new IllegalArgumentException("Dialogue session TTL must be positive");
        }
        this.ttlNanos = ttlNanos;
        this.clock = clock;
        this.sessionIds = sessionIds;
    }

    /** 为玩家创建新会话；同一玩家之前的会话会被替换。 */
    public Session open(UUID playerId, int npcId, String dialogueFile, String branchId,
                        List<String> optionIds) {
        UUID sessionId = sessionIds.get();
        if (sessionId == null) {
            throw new IllegalStateException("Dialogue session ID supplier returned null");
        }
        Session session = new Session(
                sessionId,
                playerId,
                npcId,
                dialogueFile,
                branchId,
                Set.copyOf(optionIds),
                clock.getAsLong() + ttlNanos
        );
        sessions.put(playerId, session);
        return session;
    }

    /**
     * 原子消费一个合法选项。会话、玩家或选项不匹配时不会移除仍有效的会话；
     * 已过期会话会被清理。
     */
    public Session consume(UUID playerId, UUID sessionId, String optionId) {
        AtomicReference<Session> consumed = new AtomicReference<>();
        long now = clock.getAsLong();
        sessions.compute(playerId, (ignored, current) -> {
            if (current == null || current.expiresAtNanos() <= now) {
                return null;
            }
            if (current.id().equals(sessionId) && current.optionIds().contains(optionId)) {
                consumed.set(current);
                return null;
            }
            return current;
        });
        return consumed.get();
    }

    /** 玩家关闭匹配的对话时移除会话。 */
    public Session dismiss(UUID playerId, UUID sessionId) {
        AtomicReference<Session> dismissed = new AtomicReference<>();
        long now = clock.getAsLong();
        sessions.compute(playerId, (ignored, current) -> {
            if (current == null || current.expiresAtNanos() <= now) {
                return null;
            }
            if (current.id().equals(sessionId)) {
                dismissed.set(current);
                return null;
            }
            return current;
        });
        return dismissed.get();
    }

    /** 移除玩家当前会话。 */
    public Session remove(UUID playerId) {
        return sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }

    int size() {
        return sessions.size();
    }

    public record Session(
            UUID id,
            UUID playerId,
            int npcId,
            String dialogueFile,
            String branchId,
            Set<String> optionIds,
            long expiresAtNanos
    ) {
    }
}
