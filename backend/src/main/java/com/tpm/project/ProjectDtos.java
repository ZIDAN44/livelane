package com.tpm.project;

import java.time.Instant;
import java.util.List;

import com.tpm.task.TaskDtos.TaskSummary;
import com.tpm.user.UserDtos.UserSummary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Schema(example = "WEB", description = "2-10 uppercase letters/digits, used in task ids like WEB-12")
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$", message = "must be 2-10 letters/digits, starting with a letter")
            String key,
            @Size(max = 2000) String description) {}

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description) {}

    public record ProjectSummary(Long id, String key, String name, String description, UserSummary owner,
            ProjectRole myRole, long taskCount, long doneCount, long memberCount, Instant updatedAt) {}

    public record ProjectDetail(Long id, String key, String name, String description, UserSummary owner,
            ProjectRole myRole, Instant createdAt, Instant updatedAt) {}

    public record MemberView(UserSummary user, ProjectRole role, Instant joinedAt) {
        public static MemberView of(ProjectMember m) {
            return new MemberView(UserSummary.of(m.getUser()), m.getRole(), m.getJoinedAt());
        }
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull ProjectRole role) {}

    public record UpdateMemberRequest(@NotNull ProjectRole role) {}

    public record ColumnRequest(
            @NotBlank @Size(max = 50) String name,
            @Min(1) @Max(100) Integer wipLimit,
            Boolean doneColumn) {}

    /** Place the column right after {@code afterColumnId} ({@code null} = first). */
    public record MoveColumnRequest(Long afterColumnId) {}

    public record ColumnView(Long id, String name, String position, Integer wipLimit, boolean doneColumn,
            List<TaskSummary> tasks) {}

    public record BoardView(ProjectDetail project, List<ColumnView> columns, List<MemberView> members) {}
}
