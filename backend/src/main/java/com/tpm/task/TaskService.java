package com.tpm.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityService;
import com.tpm.comment.CommentRepository;
import com.tpm.common.Exceptions.BadRequestException;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.FractionalIndex;
import com.tpm.common.PageResponse;
import com.tpm.notification.Notification;
import com.tpm.notification.NotificationService;
import com.tpm.project.BoardColumn;
import com.tpm.project.BoardColumnRepository;
import com.tpm.project.ProjectAccess;
import com.tpm.project.ProjectMemberRepository;
import com.tpm.project.ProjectRepository;
import com.tpm.project.ProjectRole;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.TaskDtos.CreateTaskRequest;
import com.tpm.task.TaskDtos.MoveTaskRequest;
import com.tpm.task.TaskDtos.TaskDetail;
import com.tpm.task.TaskDtos.TaskFilter;
import com.tpm.task.TaskDtos.TaskSummary;
import com.tpm.task.TaskDtos.UpdateTaskRequest;
import com.tpm.user.CurrentUser;
import com.tpm.user.User;
import com.tpm.user.UserRepository;

@Service
public class TaskService {

    /** Rebalance a column once keys grow this long (only after thousands of same-spot inserts). */
    static final int MAX_POSITION_LENGTH = 48;
    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "dueDate", "priority", "title", "number");

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final BoardColumnRepository columns;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final CommentRepository comments;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;

    public TaskService(TaskRepository tasks, ProjectRepository projects, BoardColumnRepository columns,
            ProjectMemberRepository members, UserRepository users, CommentRepository comments, ProjectAccess access,
            ActivityService activity, NotificationService notifications, RealtimePublisher realtime) {
        this.tasks = tasks;
        this.projects = projects;
        this.columns = columns;
        this.members = members;
        this.users = users;
        this.comments = comments;
        this.access = access;
        this.activity = activity;
        this.notifications = notifications;
        this.realtime = realtime;
    }

    @Transactional
    public TaskDetail create(Long projectId, CreateTaskRequest req) {
        access.require(projectId, ProjectRole.MEMBER);
        // Lock the project row: serialises the per-project task counter (TPM-1, TPM-2, ...).
        var project = projects.findByIdForUpdate(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        var columnId = req.columnId() != null ? req.columnId()
                : columns.findFirstByProjectIdAndDoneColumnFalseOrderByPositionAsc(projectId)
                        .or(() -> columns.findFirstByProjectIdOrderByPositionDesc(projectId))
                        .map(BoardColumn::getId).orElseThrow(() -> new ConflictException("The board has no columns"));
        var column = lockColumn(projectId, columnId);
        checkWipLimit(column);

        var position = FractionalIndex.between(tasks.findLastPosition(column.getId()).orElse(null), null);
        var task = new Task(project, column, project.nextTaskNumber(), req.title().trim(),
                blankToNull(req.description()), req.priority(), position, users.getReferenceById(CurrentUser.id()));
        task.setAssignee(resolveAssignee(projectId, req.assigneeId()));
        task.setDueDate(req.dueDate());
        tasks.saveAndFlush(task);

        activity.record(projectId, "TASK_CREATED", EntityType.TASK, task.getId(),
                "created %s \"%s\"".formatted(task.getKey(), task.getTitle()));
        notifyAssigned(task);
        realtime.board(projectId, BoardEvent.Type.TASK_CREATED, TaskSummary.of(task, 0));
        return TaskDetail.of(task);
    }

    @Transactional(readOnly = true)
    public TaskDetail get(Long taskId) {
        var task = load(taskId);
        access.require(task.getProject().getId(), ProjectRole.VIEWER);
        return TaskDetail.of(task);
    }

    @Transactional
    public TaskDetail update(Long taskId, UpdateTaskRequest req) {
        var task = load(taskId);
        var projectId = task.getProject().getId();
        access.require(projectId, ProjectRole.MEMBER);
        // Optimistic locking: the client must have seen the latest version. (@Version additionally
        // protects against two transactions racing past this check.)
        if (req.version() != task.getVersion()) {
            throw new ConflictException("%s was changed by someone else. Reload to see the latest version."
                    .formatted(task.getKey()));
        }

        var changes = new ArrayList<String>();
        if (!task.getTitle().equals(req.title().trim())) changes.add("title");
        if (!Objects.equals(task.getDescription(), blankToNull(req.description()))) changes.add("description");
        if (task.getPriority() != req.priority()) changes.add("priority → " + req.priority());
        if (!Objects.equals(task.getDueDate(), req.dueDate())) changes.add("due date");
        var oldAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        boolean assigneeChanged = !Objects.equals(oldAssigneeId, req.assigneeId());

        task.setTitle(req.title().trim());
        task.setDescription(blankToNull(req.description()));
        task.setPriority(req.priority());
        if (!Objects.equals(task.getDueDate(), req.dueDate())) task.setDueDate(req.dueDate());
        if (assigneeChanged) {
            task.setAssignee(resolveAssignee(projectId, req.assigneeId()));
            changes.add(task.getAssignee() == null ? "unassigned" : "assignee → " + task.getAssignee().getFullName());
        }
        if (changes.isEmpty()) return TaskDetail.of(task);
        tasks.saveAndFlush(task);

        activity.record(projectId, "TASK_UPDATED", EntityType.TASK, taskId,
                "updated %s: %s".formatted(task.getKey(), String.join(", ", changes)));
        if (assigneeChanged) notifyAssigned(task);
        realtime.board(projectId, BoardEvent.Type.TASK_UPDATED, TaskSummary.of(task, comments.countByTaskId(taskId)));
        return TaskDetail.of(task);
    }

    /**
     * Moves a card to {@code columnId}, directly after {@code afterTaskId} (or to the top).
     *
     * <p>The server is authoritative about ordering: it locks the target column row so concurrent
     * drops into the same column are serialised, reads the anchor's key and its current successor,
     * and assigns a fractional key strictly between them. Only the moved row is updated.
     */
    @Transactional
    public TaskSummary move(Long taskId, MoveTaskRequest req) {
        var task = load(taskId);
        var projectId = task.getProject().getId();
        access.require(projectId, ProjectRole.MEMBER);
        var from = task.getColumn();
        var target = lockColumn(projectId, req.columnId());
        if (!from.getId().equals(target.getId())) checkWipLimit(target);

        String position = positionAfter(target, req.afterTaskId(), task);
        if (position.length() > MAX_POSITION_LENGTH) {
            rebalance(target, task.getId());
            position = positionAfter(target, req.afterTaskId(), task);
        }
        task.moveTo(target, position);
        tasks.saveAndFlush(task);

        var summary = from.getId().equals(target.getId())
                ? "reordered %s in %s".formatted(task.getKey(), target.getName())
                : "moved %s from %s to %s".formatted(task.getKey(), from.getName(), target.getName());
        activity.record(projectId, "TASK_MOVED", EntityType.TASK, taskId, summary);
        var result = TaskSummary.of(task, comments.countByTaskId(taskId));
        realtime.board(projectId, BoardEvent.Type.TASK_MOVED, result);
        return result;
    }

    @Transactional
    public void delete(Long taskId) {
        var task = load(taskId);
        var projectId = task.getProject().getId();
        var role = access.require(projectId, ProjectRole.MEMBER);
        boolean isReporter = task.getReporter().getId().equals(CurrentUser.id());
        if (!isReporter && !role.atLeast(ProjectRole.ADMIN)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the reporter or a project admin can delete a task");
        }
        tasks.delete(task);
        activity.record(projectId, "TASK_DELETED", EntityType.TASK, taskId,
                "deleted %s \"%s\"".formatted(task.getKey(), task.getTitle()));
        realtime.board(projectId, BoardEvent.Type.TASK_DELETED, Map.of("id", taskId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummary> search(Long projectId, TaskFilter filter, Pageable pageable) {
        access.require(projectId, ProjectRole.VIEWER);
        var page = tasks.findAll(TaskSpecifications.inProject(projectId, filter), safeSort(pageable));
        return withCommentCounts(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskSummary> assignedToMe(boolean openOnly, Pageable pageable) {
        var page = tasks.findAll(TaskSpecifications.assignedTo(CurrentUser.id(), openOnly), safeSort(pageable));
        return withCommentCounts(page);
    }

    // ---- helpers ----

    private String positionAfter(BoardColumn column, Long afterTaskId, Task moving) {
        String lower = null;
        if (afterTaskId != null) {
            if (afterTaskId.equals(moving.getId())) throw new BadRequestException("A task can't be placed after itself");
            var anchor = tasks.findById(afterTaskId).orElseThrow(() -> new NotFoundException("Task", afterTaskId));
            if (!anchor.getColumn().getId().equals(column.getId())) {
                throw new ConflictException("The board changed while you were dragging. Please try again.");
            }
            lower = anchor.getPosition();
        }
        var upper = tasks.findNextPosition(column.getId(), lower == null ? "" : lower, moving.getId()).orElse(null);
        return FractionalIndex.between(lower, upper);
    }

    /** Re-spreads all keys in a column evenly (rare maintenance path, column is already locked). */
    private void rebalance(BoardColumn column, Long excludeId) {
        var ordered = tasks.findInColumnOrdered(column.getId()).stream().filter(t -> !t.getId().equals(excludeId)).toList();
        var keys = FractionalIndex.evenlySpaced(ordered.size());
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(keys.get(i));
        tasks.flush();
    }

    private BoardColumn lockColumn(Long projectId, Long columnId) {
        return columns.findByIdForUpdate(columnId)
                .filter(c -> c.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Column", columnId));
    }

    /** Kanban WIP limit: refuse to pull more work into a column that is already full. */
    private void checkWipLimit(BoardColumn column) {
        if (column.getWipLimit() != null && tasks.countByColumnId(column.getId()) >= column.getWipLimit()) {
            throw new ConflictException("WIP limit reached: '%s' allows at most %d tasks"
                    .formatted(column.getName(), column.getWipLimit()));
        }
    }

    private User resolveAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) return null;
        if (!members.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw new BadRequestException("The assignee must be a member of the project");
        }
        return users.getReferenceById(assigneeId);
    }

    private void notifyAssigned(Task task) {
        var assignee = task.getAssignee();
        if (assignee == null) return;
        notifications.notify(assignee, Notification.Type.TASK_ASSIGNED, "[%s] assigned to you".formatted(task.getKey()),
                "You were assigned %s \"%s\" in %s.".formatted(task.getKey(), task.getTitle(), task.getProject().getName()),
                link(task), CurrentUser.id());
    }

    private PageResponse<TaskSummary> withCommentCounts(org.springframework.data.domain.Page<Task> page) {
        var ids = page.getContent().stream().map(Task::getId).toList();
        var counts = new HashMap<Long, Long>();
        if (!ids.isEmpty()) comments.countByTasks(ids).forEach(c -> counts.put(c.getTaskId(), c.getCount()));
        return PageResponse.of(page, t -> TaskSummary.of(t, counts.getOrDefault(t.getId(), 0L)));
    }

    /** Whitelist sortable properties so clients can't sort on arbitrary (or nested) paths. */
    private static Pageable safeSort(Pageable pageable) {
        for (Sort.Order o : pageable.getSort()) {
            if (!SORTABLE.contains(o.getProperty())) {
                throw new BadRequestException("Cannot sort by '%s'. Allowed: %s".formatted(o.getProperty(), SORTABLE));
            }
        }
        return pageable;
    }

    private Task load(Long taskId) {
        return tasks.findDetailed(taskId).orElseThrow(() -> new NotFoundException("Task", taskId));
    }

    public static String link(Task t) {
        return "/projects/%d?task=%d".formatted(t.getProject().getId(), t.getId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
