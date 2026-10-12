package com.tpm.comment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityService;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.PageResponse;
import com.tpm.notification.Notification;
import com.tpm.notification.NotificationService;
import com.tpm.project.ProjectAccess;
import com.tpm.project.ProjectRole;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.Task;
import com.tpm.task.TaskRepository;
import com.tpm.task.TaskService;
import com.tpm.user.CurrentUser;
import com.tpm.user.User;
import com.tpm.user.UserDtos.UserSummary;
import com.tpm.user.UserRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;

    public CommentService(CommentRepository comments, TaskRepository tasks, UserRepository users, ProjectAccess access,
            ActivityService activity, NotificationService notifications, RealtimePublisher realtime) {
        this.comments = comments;
        this.tasks = tasks;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.notifications = notifications;
        this.realtime = realtime;
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String body) {}

    public record CommentView(Long id, Long taskId, String body, UserSummary author, boolean edited,
            Instant createdAt, Instant updatedAt) {
        static CommentView of(Comment c) {
            return new CommentView(c.getId(), c.getTask().getId(), c.getBody(), UserSummary.of(c.getAuthor()),
                    c.isEdited(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentView> list(Long taskId, Pageable pageable) {
        var task = loadTask(taskId);
        access.require(task.getProject().getId(), ProjectRole.VIEWER);
        return PageResponse.of(comments.findByTaskId(taskId, pageable), CommentView::of);
    }

    @Transactional
    public CommentView add(Long taskId, String body) {
        var task = loadTask(taskId);
        var projectId = task.getProject().getId();
        access.require(projectId, ProjectRole.MEMBER);
        var author = users.findById(CurrentUser.id()).orElseThrow();
        var comment = comments.save(new Comment(task, author, body.trim()));

        activity.record(projectId, "COMMENT_ADDED", EntityType.TASK, taskId, "commented on " + task.getKey());
        // Notify the people involved in the task (deduplicated, never the author).
        var recipients = new LinkedHashMap<Long, User>();
        if (task.getAssignee() != null) recipients.put(task.getAssignee().getId(), task.getAssignee());
        recipients.put(task.getReporter().getId(), task.getReporter());
        var preview = body.length() > 140 ? body.substring(0, 137) + "..." : body;
        recipients.values().forEach(u -> notifications.notify(u, Notification.Type.TASK_COMMENTED,
                "[%s] new comment from %s".formatted(task.getKey(), author.getFullName()),
                "%s commented on %s: \"%s\"".formatted(author.getFullName(), task.getKey(), preview),
                TaskService.link(task), author.getId()));
        publishCount(task);
        return CommentView.of(comment);
    }

    @Transactional
    public CommentView edit(Long commentId, String body) {
        var comment = load(commentId);
        access.require(comment.getTask().getProject().getId(), ProjectRole.VIEWER);
        if (!comment.getAuthor().getId().equals(CurrentUser.id())) {
            throw new AccessDeniedException("You can only edit your own comments");
        }
        comment.edit(body.trim());
        publishCount(comment.getTask());
        return CommentView.of(comment);
    }

    @Transactional
    public void delete(Long commentId) {
        var comment = load(commentId);
        var task = comment.getTask();
        var role = access.require(task.getProject().getId(), ProjectRole.VIEWER);
        boolean own = comment.getAuthor().getId().equals(CurrentUser.id());
        if (!own && !role.atLeast(ProjectRole.ADMIN)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }
        comments.delete(comment);
        comments.flush();
        activity.record(task.getProject().getId(), "COMMENT_DELETED", EntityType.TASK, task.getId(),
                "deleted a comment on " + task.getKey());
        publishCount(task);
    }

    private void publishCount(Task task) {
        realtime.board(task.getProject().getId(), BoardEvent.Type.COMMENTS_CHANGED,
                Map.of("taskId", task.getId(), "commentCount", comments.countByTaskId(task.getId())));
    }

    private Task loadTask(Long taskId) {
        return tasks.findDetailed(taskId).orElseThrow(() -> new NotFoundException("Task", taskId));
    }

    private Comment load(Long commentId) {
        return comments.findDetailed(commentId).orElseThrow(() -> new NotFoundException("Comment", commentId));
    }
}
