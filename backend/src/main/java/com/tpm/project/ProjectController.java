package com.tpm.project;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tpm.project.ProjectDtos.AddMemberRequest;
import com.tpm.project.ProjectDtos.BoardView;
import com.tpm.project.ProjectDtos.ColumnRequest;
import com.tpm.project.ProjectDtos.CreateProjectRequest;
import com.tpm.project.ProjectDtos.MemberView;
import com.tpm.project.ProjectDtos.MoveColumnRequest;
import com.tpm.project.ProjectDtos.ProjectDetail;
import com.tpm.project.ProjectDtos.ProjectSummary;
import com.tpm.project.ProjectDtos.UpdateMemberRequest;
import com.tpm.project.ProjectDtos.UpdateProjectRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Projects, board, columns and members")
public class ProjectController {

    private final ProjectService projects;
    private final ColumnService columns;
    private final MemberService members;

    public ProjectController(ProjectService projects, ColumnService columns, MemberService members) {
        this.projects = projects;
        this.columns = columns;
        this.members = members;
    }

    @GetMapping
    @Operation(summary = "Projects I'm a member of, with task/member counts")
    public List<ProjectSummary> myProjects() {
        return projects.myProjects();
    }

    @PostMapping
    @Operation(summary = "Create a project (you become its OWNER; default columns are created)")
    public ResponseEntity<ProjectDetail> create(@Valid @RequestBody CreateProjectRequest req) {
        var created = projects.create(req);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }

    @GetMapping("/{projectId}")
    public ProjectDetail get(@PathVariable Long projectId) {
        return projects.get(projectId);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update name/description (ADMIN)")
    public ProjectDetail update(@PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest req) {
        return projects.update(projectId, req);
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete the project and everything in it (OWNER)")
    public ResponseEntity<Void> delete(@PathVariable Long projectId) {
        projects.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/board")
    @Operation(summary = "Full Kanban board: columns with ordered task cards, and members")
    public BoardView board(@PathVariable Long projectId) {
        return projects.board(projectId);
    }

    // ---- columns ----

    @PostMapping("/{projectId}/columns")
    @Operation(summary = "Add a column at the end of the board (ADMIN)")
    public ResponseEntity<Map<String, Long>> addColumn(@PathVariable Long projectId, @Valid @RequestBody ColumnRequest req) {
        return ResponseEntity.status(201).body(Map.of("id", columns.add(projectId, req)));
    }

    @PutMapping("/{projectId}/columns/{columnId}")
    public ResponseEntity<Void> updateColumn(@PathVariable Long projectId, @PathVariable Long columnId,
            @Valid @RequestBody ColumnRequest req) {
        columns.update(projectId, columnId, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/columns/{columnId}/move")
    @Operation(summary = "Reorder a column (fractional index - only the moved row is updated)")
    public ResponseEntity<Void> moveColumn(@PathVariable Long projectId, @PathVariable Long columnId,
            @RequestBody MoveColumnRequest req) {
        columns.move(projectId, columnId, req.afterColumnId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{projectId}/columns/{columnId}")
    @Operation(summary = "Delete an empty column (ADMIN)")
    public ResponseEntity<Void> deleteColumn(@PathVariable Long projectId, @PathVariable Long columnId) {
        columns.delete(projectId, columnId);
        return ResponseEntity.noContent().build();
    }

    // ---- members ----

    @GetMapping("/{projectId}/members")
    public List<MemberView> listMembers(@PathVariable Long projectId) {
        return members.list(projectId);
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Add an existing user by e-mail (ADMIN; only OWNER can add ADMINs)")
    public ResponseEntity<MemberView> addMember(@PathVariable Long projectId, @Valid @RequestBody AddMemberRequest req) {
        return ResponseEntity.status(201).body(members.add(projectId, req.email(), req.role()));
    }

    @PatchMapping("/{projectId}/members/{userId}")
    public MemberView changeRole(@PathVariable Long projectId, @PathVariable Long userId,
            @Valid @RequestBody UpdateMemberRequest req) {
        return members.changeRole(projectId, userId, req.role());
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @Operation(summary = "Remove a member (ADMIN) or leave the project (yourself)")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        members.remove(projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/members/{userId}/transfer-ownership")
    @Operation(summary = "Make this member the OWNER; the current owner becomes ADMIN")
    public ResponseEntity<Void> transferOwnership(@PathVariable Long projectId, @PathVariable Long userId) {
        members.transferOwnership(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
