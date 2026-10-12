package com.tpm.project;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tpm.activity.ActivityLog.EntityType;
import com.tpm.activity.ActivityService;
import com.tpm.comment.CommentRepository;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.common.Exceptions.NotFoundException;
import com.tpm.common.FractionalIndex;
import com.tpm.project.ProjectDtos.BoardView;
import com.tpm.project.ProjectDtos.ColumnView;
import com.tpm.project.ProjectDtos.CreateProjectRequest;
import com.tpm.project.ProjectDtos.MemberView;
import com.tpm.project.ProjectDtos.ProjectDetail;
import com.tpm.project.ProjectDtos.ProjectSummary;
import com.tpm.project.ProjectDtos.UpdateProjectRequest;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.TaskDtos.TaskSummary;
import com.tpm.task.TaskRepository;
import com.tpm.user.CurrentUser;
import com.tpm.user.UserDtos.UserSummary;
import com.tpm.user.UserRepository;

@Service
public class ProjectService {

    /** Columns every new project starts with: name, WIP limit, is "done". */
    private record ColumnTemplate(String name, Integer wipLimit, boolean done) {}

    private static final List<ColumnTemplate> DEFAULT_COLUMNS = List.of(
            new ColumnTemplate("To Do", null, false),
            new ColumnTemplate("In Progress", 5, false),
            new ColumnTemplate("In Review", 3, false),
            new ColumnTemplate("Done", null, true));

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final BoardColumnRepository columns;
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final UserRepository users;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final RealtimePublisher realtime;
    private final CacheManager cacheManager;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, BoardColumnRepository columns,
            TaskRepository tasks, CommentRepository comments, UserRepository users, ProjectAccess access,
            ActivityService activity, RealtimePublisher realtime, CacheManager cacheManager) {
        this.projects = projects;
        this.members = members;
        this.columns = columns;
        this.tasks = tasks;
        this.comments = comments;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.realtime = realtime;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> myProjects() {
        var memberships = members.findMembershipsOf(CurrentUser.id());
        var ids = memberships.stream().map(m -> m.getProject().getId()).toList();
        if (ids.isEmpty()) return List.of();
        // Two grouped queries instead of 2 x N count queries.
        var stats = tasks.statsByProjects(ids).stream()
                .collect(Collectors.toMap(TaskRepository.ProjectTaskStats::getProjectId, Function.identity()));
        var memberCounts = members.countByProjects(ids).stream()
                .collect(Collectors.toMap(ProjectMemberRepository.CountRow::getProjectId,
                        ProjectMemberRepository.CountRow::getCount));
        return memberships.stream().map(m -> {
            var p = m.getProject();
            var s = stats.get(p.getId());
            return new ProjectSummary(p.getId(), p.getKey(), p.getName(), p.getDescription(),
                    UserSummary.of(p.getOwner()), m.getRole(), s == null ? 0 : s.getTotal(),
                    s == null || s.getDone() == null ? 0 : s.getDone(), memberCounts.getOrDefault(p.getId(), 0L),
                    p.getUpdatedAt());
        }).toList();
    }

    @Transactional
    public ProjectDetail create(CreateProjectRequest req) {
        var key = req.key().toUpperCase();
        if (projects.existsByKey(key)) throw new ConflictException("Project key '%s' is already taken".formatted(key));
        var owner = users.getReferenceById(CurrentUser.id());
        var project = projects.save(new Project(key, req.name().trim(), blankToNull(req.description()), owner));
        members.save(new ProjectMember(project, owner, ProjectRole.OWNER));

        var positions = FractionalIndex.evenlySpaced(DEFAULT_COLUMNS.size());
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            var t = DEFAULT_COLUMNS.get(i);
            columns.save(new BoardColumn(project, t.name(), positions.get(i), t.wipLimit(), t.done()));
        }
        activity.record(project.getId(), "PROJECT_CREATED", EntityType.PROJECT, project.getId(),
                "created project " + project.getName());
        return detail(project, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public ProjectDetail get(Long projectId) {
        var role = access.require(projectId, ProjectRole.VIEWER);
        return detail(load(projectId), role);
    }

    @Transactional
    public ProjectDetail update(Long projectId, UpdateProjectRequest req) {
        var role = access.require(projectId, ProjectRole.ADMIN);
        var project = load(projectId);
        project.setName(req.name().trim());
        project.setDescription(blankToNull(req.description()));
        projects.flush();
        activity.record(projectId, "PROJECT_UPDATED", EntityType.PROJECT, projectId, "updated project settings");
        var detail = detail(project, role);
        realtime.board(projectId, BoardEvent.Type.PROJECT_UPDATED, detail);
        return detail;
    }

    @Transactional
    public void delete(Long projectId) {
        access.require(projectId, ProjectRole.OWNER);
        var project = load(projectId);
        realtime.board(projectId, BoardEvent.Type.PROJECT_DELETED, Map.of("id", projectId));
        projects.delete(project);
        // Membership rows cascade away; drop cached roles once the delete is committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                var cache = cacheManager.getCache(MemberRoleCache.CACHE);
                if (cache != null) cache.clear();
            }
        });
    }

    /**
     * The whole board in 4 queries regardless of size: project, columns, tasks (+assignees),
     * comment counts. Tasks are grouped into columns in memory, already ordered by position.
     */
    @Transactional(readOnly = true)
    public BoardView board(Long projectId) {
        var role = access.require(projectId, ProjectRole.VIEWER);
        var project = load(projectId);
        var cols = columns.findByProjectIdOrderByPositionAscIdAsc(projectId);
        var commentCounts = new HashMap<Long, Long>();
        comments.countByProject(projectId).forEach(c -> commentCounts.put(c.getTaskId(), c.getCount()));

        var byColumn = new LinkedHashMap<Long, List<TaskSummary>>();
        cols.forEach(c -> byColumn.put(c.getId(), new java.util.ArrayList<>()));
        for (var t : tasks.findBoardTasks(projectId)) {
            byColumn.get(t.getColumn().getId()).add(TaskSummary.of(t, commentCounts.getOrDefault(t.getId(), 0L)));
        }
        var columnViews = cols.stream()
                .map(c -> new ColumnView(c.getId(), c.getName(), c.getPosition(), c.getWipLimit(), c.isDoneColumn(),
                        byColumn.get(c.getId())))
                .toList();
        var memberViews = members.findWithUsers(projectId).stream()
                .sorted(java.util.Comparator.comparing(ProjectMember::getRole).reversed()
                        .thenComparing(m -> m.getUser().getFullName()))
                .map(MemberView::of).toList();
        return new BoardView(detail(project, role), columnViews, memberViews);
    }

    Project load(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project", projectId));
    }

    private static ProjectDetail detail(Project p, ProjectRole myRole) {
        return new ProjectDetail(p.getId(), p.getKey(), p.getName(), p.getDescription(), UserSummary.of(p.getOwner()),
                myRole, p.getCreatedAt(), p.getUpdatedAt());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
