package com.tpm.project;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityService;
import com.tpm.common.Exceptions.BadRequestException;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.FractionalIndex;
import com.tpm.project.ProjectDtos.ColumnRequest;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.TaskRepository;

/** Board column management (add, rename, WIP limits, reorder, delete). Requires project ADMIN. */
@Service
public class ColumnService {

    static final int MAX_COLUMNS = 12;

    private final ProjectRepository projects;
    private final BoardColumnRepository columns;
    private final TaskRepository tasks;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final RealtimePublisher realtime;

    public ColumnService(ProjectRepository projects, BoardColumnRepository columns, TaskRepository tasks,
            ProjectAccess access, ActivityService activity, RealtimePublisher realtime) {
        this.projects = projects;
        this.columns = columns;
        this.tasks = tasks;
        this.access = access;
        this.activity = activity;
        this.realtime = realtime;
    }

    @Transactional
    public Long add(Long projectId, ColumnRequest req) {
        access.require(projectId, ProjectRole.ADMIN);
        var project = projects.findByIdForUpdate(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        if (columns.countByProjectId(projectId) >= MAX_COLUMNS) {
            throw new ConflictException("A board can have at most %d columns".formatted(MAX_COLUMNS));
        }
        var last = columns.findFirstByProjectIdOrderByPositionDesc(projectId).map(BoardColumn::getPosition).orElse(null);
        var column = columns.save(new BoardColumn(project, req.name().trim(), FractionalIndex.between(last, null),
                req.wipLimit(), Boolean.TRUE.equals(req.doneColumn())));
        activity.record(projectId, "COLUMN_CREATED", EntityType.COLUMN, column.getId(), "added column " + column.getName());
        changed(projectId);
        return column.getId();
    }

    @Transactional
    public void update(Long projectId, Long columnId, ColumnRequest req) {
        access.require(projectId, ProjectRole.ADMIN);
        var column = load(projectId, columnId);
        column.setName(req.name().trim());
        column.setWipLimit(req.wipLimit());
        if (req.doneColumn() != null) column.setDoneColumn(req.doneColumn());
        activity.record(projectId, "COLUMN_UPDATED", EntityType.COLUMN, columnId, "updated column " + column.getName());
        changed(projectId);
    }

    @Transactional
    public void move(Long projectId, Long columnId, Long afterColumnId) {
        access.require(projectId, ProjectRole.ADMIN);
        projects.findByIdForUpdate(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
        var column = load(projectId, columnId);
        if (columnId.equals(afterColumnId)) throw new BadRequestException("A column can't be placed after itself");

        String lower = afterColumnId == null ? null : load(projectId, afterColumnId).getPosition();
        String upper = (lower == null
                ? columns.findFirstByProjectIdAndIdNotOrderByPositionAsc(projectId, columnId)
                : columns.findFirstByProjectIdAndPositionGreaterThanAndIdNotOrderByPositionAsc(projectId, lower, columnId))
                .map(BoardColumn::getPosition).orElse(null);
        column.setPosition(FractionalIndex.between(lower, upper));
        activity.record(projectId, "COLUMN_MOVED", EntityType.COLUMN, columnId, "reordered column " + column.getName());
        changed(projectId);
    }

    @Transactional
    public void delete(Long projectId, Long columnId) {
        access.require(projectId, ProjectRole.ADMIN);
        var column = load(projectId, columnId);
        if (tasks.countByColumnId(columnId) > 0) {
            throw new ConflictException("Move or delete the tasks in '%s' before deleting it".formatted(column.getName()));
        }
        if (columns.countByProjectId(projectId) <= 1) throw new ConflictException("A board needs at least one column");
        columns.delete(column);
        activity.record(projectId, "COLUMN_DELETED", EntityType.COLUMN, columnId, "deleted column " + column.getName());
        changed(projectId);
    }

    private BoardColumn load(Long projectId, Long columnId) {
        return columns.findById(columnId)
                .filter(c -> c.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("Column", columnId));
    }

    private void changed(Long projectId) {
        realtime.board(projectId, BoardEvent.Type.COLUMNS_CHANGED, Map.of("projectId", projectId));
    }
}
