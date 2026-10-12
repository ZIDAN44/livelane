package com.tpm.realtime;

import java.time.Instant;

/**
 * Message broadcast to {@code /topic/projects/{projectId}} after a change commits.
 * Clients apply the payload to their local board state (upsert / remove) or refetch.
 */
public record BoardEvent(Type type, Long projectId, Long actorId, Object payload, Instant at) {

    public enum Type {
        TASK_CREATED, TASK_UPDATED, TASK_MOVED, TASK_DELETED,
        COLUMNS_CHANGED, COMMENTS_CHANGED, PROJECT_UPDATED, MEMBERS_CHANGED, PROJECT_DELETED
    }
}
