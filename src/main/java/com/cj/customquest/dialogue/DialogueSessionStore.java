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
    private final ConcurrentHashMap<UUID, UUID> pendingTransitions = new ConcurrentHashMap<>();
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
        pendingTransitions.remove(playerId);
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

    /** 更新当前会话的分支并保留原会话 ID，供对话内部跳转使用。 */
    public Session update(Session previous, String branchId, List<String> optionIds) {
        return replace(previous, branchId, optionIds, false);
    }

    /** 标记已消费的会话正在等待异步跳转。 */
    public void beginTransition(Session session) {
        if (session != null) {
            pendingTransitions.put(session.playerId(), session.id());
        }
    }

    /** 仅恢复仍处于待跳转状态的会话，避免关闭对话后被异步回调重新打开。 */
    public Session updatePending(Session previous, String branchId, List<String> optionIds) {
        return replace(previous, branchId, optionIds, true);
    }

    private Session replace(Session previous, String branchId, List<String> optionIds,
                            boolean requirePendingTransition) {
        if (previous == null) {
            return null;
        }
        long now = clock.getAsLong();
        if (previous.expiresAtNanos() <= now) {
            pendingTransitions.remove(previous.playerId(), previous.id());
            sessions.computeIfPresent(previous.playerId(), (ignored, current) ->
                    current.id().equals(previous.id()) ? null : current);
            return null;
        }
        Session session = new Session(
                previous.id(),
                previous.playerId(),
                previous.npcId(),
                previous.dialogueFile(),
                branchId,
                Set.copyOf(optionIds),
                now + ttlNanos
        );
        AtomicReference<Session> updated = new AtomicReference<>();
        sessions.compute(previous.playerId(), (ignored, current) -> {
            if (requirePendingTransition
                    && !previous.id().equals(pendingTransitions.get(previous.playerId()))) {
                return current;
            }
            if (current == null) {
                updated.set(session);
                pendingTransitions.remove(previous.playerId(), previous.id());
                return session;
            }
            if (current.expiresAtNanos() <= now) {
                pendingTransitions.remove(previous.playerId(), current.id());
                return null;
            }
            if (!current.id().equals(previous.id())) {
                return current;
            }
            updated.set(session);
            pendingTransitions.remove(previous.playerId(), previous.id());
            return session;
        });
        return updated.get();
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
                if (current != null) {
                    pendingTransitions.remove(playerId, current.id());
                }
                pendingTransitions.remove(playerId, sessionId);
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
                if (current != null) {
                    pendingTransitions.remove(playerId, current.id());
                }
                pendingTransitions.remove(playerId, sessionId);
                return null;
            }
            if (current.id().equals(sessionId)) {
                pendingTransitions.remove(playerId, sessionId);
                dismissed.set(current);
                return null;
            }
            return current;
        });
        return dismissed.get();
    }

    /** 移除玩家当前会话。 */
    public Session remove(UUID playerId) {
        pendingTransitions.remove(playerId);
        return sessions.remove(playerId);
    }

    /** 取消指定会话的异步跳转。 */
    public void cancelTransition(UUID playerId, UUID sessionId) {
        if (playerId != null && sessionId != null) {
            pendingTransitions.remove(playerId, sessionId);
        }
    }

    public void clear() {
        sessions.clear();
        pendingTransitions.clear();
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
