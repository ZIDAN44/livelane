package com.tpm.task;

import java.time.Instant;
import java.time.LocalDate;

import com.tpm.user.UserDtos.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TaskDtos {

    private TaskDtos() {}

    /** Card shown on the board and in lists. */
    public record TaskSummary(Long id, Long projectId, String key, String title, Task.Priority priority, Long columnId,
            String columnName, String position, UserSummary assignee, LocalDate dueDate, Instant completedAt,
            long commentCount, Instant createdAt, long version) {

        public static TaskSummary of(Task t, long commentCount) {
            return new TaskSummary(t.getId(), t.getProject().getId(), t.getKey(), t.getTitle(), t.getPriority(), t.getColumn().getId(),
                    t.getColumn().getName(), t.getPosition(), UserSummary.of(t.getAssignee()), t.getDueDate(),
                    t.getCompletedAt(), commentCount, t.getCreatedAt(), t.getVersion());
        }
    }

    public record TaskDetail(Long id, String key, Long projectId, String title, String description,
            Task.Priority priority, Long columnId, String columnName, UserSummary assignee, UserSummary reporter,
            LocalDate dueDate, Instant completedAt, Instant createdAt, Instant updatedAt, long version) {

        public static TaskDetail of(Task t) {
            return new TaskDetail(t.getId(), t.getKey(), t.getProject().getId(), t.getTitle(), t.getDescription(),
                    t.getPriority(), t.getColumn().getId(), t.getColumn().getName(), UserSummary.of(t.getAssignee()),
                    UserSummary.of(t.getReporter()), t.getDueDate(), t.getCompletedAt(), t.getCreatedAt(),
                    t.getUpdatedAt(), t.getVersion());
        }
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10_000) String description,
            Task.Priority priority,
            Long columnId,
            Long assigneeId,
            LocalDate dueDate) {}

    /**
     * Full replacement of the editable fields. {@code version} must match the current version
     * (optimistic locking) or the API answers 409 Conflict.
     */
    public record UpdateTaskRequest(
            @NotNull Long version,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10_000) String description,
            @NotNull Task.Priority priority,
            Long assigneeId,
            LocalDate dueDate) {}

    /** Drop the task into {@code columnId} right after {@code afterTaskId} ({@code null} = top). */
    public record MoveTaskRequest(@NotNull Long columnId, Long afterTaskId) {}

    /** Query filters for task search (all optional). */
    public record TaskFilter(String q, Long assigneeId, Task.Priority priority, Long columnId, Boolean overdue,
            Boolean open) {}
}
