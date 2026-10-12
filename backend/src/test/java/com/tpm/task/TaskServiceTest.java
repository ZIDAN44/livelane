package com.tpm.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.tpm.activity.ActivityService;
import com.tpm.comment.CommentRepository;
import com.tpm.common.Exceptions.ConflictException;
import com.tpm.notification.NotificationService;
import com.tpm.project.BoardColumn;
import com.tpm.project.BoardColumnRepository;
import com.tpm.project.Project;
import com.tpm.project.ProjectAccess;
import com.tpm.project.ProjectMemberRepository;
import com.tpm.project.ProjectRepository;
import com.tpm.project.ProjectRole;
import com.tpm.realtime.BoardEvent;
import com.tpm.realtime.RealtimePublisher;
import com.tpm.task.TaskDtos.MoveTaskRequest;
import com.tpm.task.TaskDtos.UpdateTaskRequest;
import com.tpm.user.User;
import com.tpm.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository tasks;
    @Mock ProjectRepository projects;
    @Mock BoardColumnRepository columns;
    @Mock ProjectMemberRepository members;
    @Mock UserRepository users;
    @Mock CommentRepository comments;
    @Mock ProjectAccess access;
    @Mock ActivityService activity;
    @Mock NotificationService notifications;
    @Mock RealtimePublisher realtime;
    @InjectMocks TaskService service;

    private Project project;
    private BoardColumn todo;
    private BoardColumn doing;
    private Task task;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("1", null, "ROLE_USER"));
        var alice = withId(new User("alice@demo.tpm", "x", "Alice", User.Role.USER, "#000000"), 1L);
        project = withId(new Project("WEB", "Website", null, alice), 10L);
        todo = withId(new BoardColumn(project, "To Do", "V", null, false), 100L);
        doing = withId(new BoardColumn(project, "Doing", "l", 2, false), 200L);
        task = withId(new Task(project, todo, 1, "Write tests", null, Task.Priority.HIGH, "V", alice), 1000L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moveComputesKeyBetweenAnchorAndItsSuccessor() {
        var anchor = withId(new Task(project, doing, 2, "Anchor", null, null, "V", task.getReporter()), 2000L);
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenReturn(ProjectRole.MEMBER);
        when(columns.findByIdForUpdate(200L)).thenReturn(Optional.of(doing));
        when(tasks.countByColumnId(200L)).thenReturn(1L);
        when(tasks.findById(2000L)).thenReturn(Optional.of(anchor));
        when(tasks.findNextPosition(200L, "V", 1000L)).thenReturn(Optional.of("l"));

        var result = service.move(1000L, new MoveTaskRequest(200L, 2000L));

        assertThat(result.columnId()).isEqualTo(200L);
        assertThat(result.position()).isGreaterThan("V").isLessThan("l");
        verify(realtime).board(eq(10L), eq(BoardEvent.Type.TASK_MOVED), any());
    }

    @Test
    void moveToTopOfEmptyColumnUsesFullKeySpace() {
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenReturn(ProjectRole.MEMBER);
        when(columns.findByIdForUpdate(200L)).thenReturn(Optional.of(doing));
        when(tasks.findNextPosition(200L, "", 1000L)).thenReturn(Optional.empty());

        var result = service.move(1000L, new MoveTaskRequest(200L, null));

        assertThat(result.position()).isEqualTo("V");
    }

    @Test
    void moveIntoFullColumnIsRejectedByWipLimit() {
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenReturn(ProjectRole.MEMBER);
        when(columns.findByIdForUpdate(200L)).thenReturn(Optional.of(doing));
        when(tasks.countByColumnId(200L)).thenReturn(2L); // wip limit is 2

        assertThatThrownBy(() -> service.move(1000L, new MoveTaskRequest(200L, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("WIP limit");
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void moveWithAnchorFromAnotherColumnSignalsStaleBoard() {
        var anchorInTodo = withId(new Task(project, todo, 3, "Other", null, null, "l", task.getReporter()), 3000L);
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenReturn(ProjectRole.MEMBER);
        when(columns.findByIdForUpdate(200L)).thenReturn(Optional.of(doing));
        when(tasks.countByColumnId(200L)).thenReturn(0L);
        when(tasks.findById(3000L)).thenReturn(Optional.of(anchorInTodo));

        assertThatThrownBy(() -> service.move(1000L, new MoveTaskRequest(200L, 3000L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("board changed");
    }

    @Test
    void updateWithStaleVersionIsAConflict() {
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenReturn(ProjectRole.MEMBER);

        var stale = new UpdateTaskRequest(task.getVersion() + 1, "New title", null, Task.Priority.LOW, null, null);

        assertThatThrownBy(() -> service.update(1000L, stale)).isInstanceOf(ConflictException.class);
        assertThat(task.getTitle()).isEqualTo("Write tests");
    }

    @Test
    void viewersCannotEdit() {
        when(tasks.findDetailed(1000L)).thenReturn(Optional.of(task));
        when(access.require(10L, ProjectRole.MEMBER)).thenThrow(new AccessDeniedException("viewer"));

        assertThatThrownBy(() -> service.move(1000L, new MoveTaskRequest(100L, null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(columns, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void movingIntoDoneColumnMarksTaskCompleted() {
        var done = withId(new BoardColumn(project, "Done", "z", null, true), 300L);
        task.moveTo(done, "V");
        assertThat(task.getCompletedAt()).isNotNull();
        task.moveTo(todo, "V");
        assertThat(task.getCompletedAt()).isNull();
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
