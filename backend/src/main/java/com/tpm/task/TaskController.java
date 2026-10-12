package com.tpm.task;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.common.PageResponse;
import com.tpm.task.TaskDtos.CreateTaskRequest;
import com.tpm.task.TaskDtos.MoveTaskRequest;
import com.tpm.task.TaskDtos.TaskDetail;
import com.tpm.task.TaskDtos.TaskFilter;
import com.tpm.task.TaskDtos.TaskSummary;
import com.tpm.task.TaskDtos.UpdateTaskRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Tasks", description = "Task CRUD, drag-and-drop moves and search")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    @Operation(summary = "Create a task (appended to the bottom of the column)")
    public ResponseEntity<TaskDetail> create(@PathVariable Long projectId, @Valid @RequestBody CreateTaskRequest req) {
        var created = tasks.create(projectId, req);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    @Operation(summary = "Search tasks with filters, pagination and sorting",
            description = "Sortable by createdAt, updatedAt, dueDate, priority, title, number. Example: ?q=login&priority=HIGH&sort=dueDate,asc")
    public PageResponse<TaskSummary> search(@PathVariable Long projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Task.Priority priority,
            @RequestParam(required = false) Long columnId,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean open,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tasks.search(projectId, new TaskFilter(q, assigneeId, priority, columnId, overdue, open), pageable);
    }

    @GetMapping("/api/tasks/assigned-to-me")
    @Operation(summary = "My tasks across all projects")
    public PageResponse<TaskSummary> mine(@RequestParam(defaultValue = "true") boolean openOnly,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return tasks.assignedToMe(openOnly, pageable);
    }

    @GetMapping("/api/tasks/{taskId}")
    public TaskDetail get(@PathVariable Long taskId) {
        return tasks.get(taskId);
    }

    @PutMapping("/api/tasks/{taskId}")
    @Operation(summary = "Update a task (optimistic locking: send the version you loaded; 409 if stale)")
    public TaskDetail update(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest req) {
        return tasks.update(taskId, req);
    }

    @PostMapping("/api/tasks/{taskId}/move")
    @Operation(summary = "Drag & drop: move to a column, right after another task (or to the top)")
    public TaskSummary move(@PathVariable Long taskId, @Valid @RequestBody MoveTaskRequest req) {
        return tasks.move(taskId, req);
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable Long taskId) {
        tasks.delete(taskId);
        return ResponseEntity.noContent().build();
    }
}
