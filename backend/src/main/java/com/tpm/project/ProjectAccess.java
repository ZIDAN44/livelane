package com.tpm.project;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.tpm.user.CurrentUser;

/**
 * Project-level RBAC. Exposed as bean {@code projectAccess} so controllers can declare rules
 * with SpEL, e.g. {@code @PreAuthorize("@projectAccess.canEdit(#projectId)")}; services call
 * {@link #require} for checks that depend on loaded data (task -> project).
 *
 * <p>Global {@code ADMIN}s are treated as project owners everywhere.
 */
@Component("projectAccess")
public class ProjectAccess {

    private final MemberRoleCache roles;

    public ProjectAccess(MemberRoleCache roles) {
        this.roles = roles;
    }

    public boolean canView(Long projectId) { return has(projectId, ProjectRole.VIEWER); }

    public boolean canEdit(Long projectId) { return has(projectId, ProjectRole.MEMBER); }

    public boolean canManage(Long projectId) { return has(projectId, ProjectRole.ADMIN); }

    public boolean isOwner(Long projectId) { return has(projectId, ProjectRole.OWNER); }

    public boolean has(Long projectId, ProjectRole required) {
        var role = roleOf(projectId, CurrentUser.id());
        return role != null && role.atLeast(required);
    }

    /** Effective role of a user, or {@code null} if they cannot access the project. */
    public ProjectRole roleOf(Long projectId, long userId) {
        if (CurrentUser.isAdmin() && userId == CurrentUser.id()) return ProjectRole.OWNER;
        return roles.find(projectId, userId);
    }

    public ProjectRole require(Long projectId, ProjectRole required) {
        var role = roleOf(projectId, CurrentUser.id());
        if (role == null || !role.atLeast(required)) {
            throw new AccessDeniedException("Requires %s role in project %d".formatted(required, projectId));
        }
        return role;
    }
}
